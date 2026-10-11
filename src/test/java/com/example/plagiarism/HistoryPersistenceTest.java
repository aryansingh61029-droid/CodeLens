package com.example.plagiarism;

import com.example.plagiarism.model.Models.*;
import com.example.plagiarism.service.HistoryService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HistoryPersistenceTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HistoryService history;

    @Test void comparisonIsStoredAndReadableWithoutSourceCode() throws Exception {
        String id = mvc.perform(post("/api/compare").contentType(MediaType.APPLICATION_JSON).content("""
                {"language":"JAVA","code1":"return a;","code2":"return b;"}
                """))
                .andExpect(status().isOk()).andExpect(header().exists("X-Comparison-Id"))
                .andReturn().getResponse().getHeader("X-Comparison-Id");
        mvc.perform(get("/api/history/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.type").value("single"))
                .andExpect(jsonPath("$.summary.submissionCount").value(2))
                .andExpect(jsonPath("$.pairs[0].similarity").value(100.0))
                .andExpect(jsonPath("$.pairs[0].code").doesNotExist());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM comparison_pairs WHERE run_id = ?", Integer.class, id));
    }

    @Test void invalidComparisonDoesNotCreateHistory() throws Exception {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM comparison_runs", Long.class);
        mvc.perform(post("/api/compare").contentType(MediaType.APPLICATION_JSON).content("""
                {"language":"JAVA","code1":"","code2":"return b;"}
                """ )).andExpect(status().isBadRequest());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM comparison_runs", Long.class));
    }

    @Test void filenamesAreStoredLiterallyWithParameterizedSql() throws Exception {
        String name = "quote' OR 1=1;--.java";
        String id = mvc.perform(multipart("/api/compare")
                .file(new MockMultipartFile("file1", name, "text/plain", "return a;".getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("file2", "Other.java", "text/plain", "return b;".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("X-Comparison-Id");
        mvc.perform(get("/api/history/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs[0].first").value(name));
    }

    @Test void batchPersistsOnlySameLanguagePairs() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : new String[]{"A.java", "B.java", "Other.py"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write((name.endsWith(".py") ? "print('hello')" : "return a;").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        String id = mvc.perform(multipart("/api/compare/batch").file(new MockMultipartFile(
                "file", "batch.zip", "application/zip", bytes.toByteArray())))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("X-Comparison-Id");
        mvc.perform(get("/api/history/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.type").value("batch"))
                .andExpect(jsonPath("$.summary.submissionCount").value(3))
                .andExpect(jsonPath("$.summary.pairCount").value(1))
                .andExpect(jsonPath("$.pairs.length()").value(1));
    }

    @Test void historyPaginationAndMissingEntriesAreValidated() throws Exception {
        mvc.perform(get("/api/history?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/history?size=51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/history/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/history/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/history?size=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedPairInsertRollsBackTheWholeRun() {
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM comparison_runs", Long.class);
        BatchResult invalid = new BatchResult(List.of("A.java", "B.java"), new Double[0][0],
                List.of(new Pair("A.java", "B.java", 100.0, "invalid-verdict")));
        assertThrows(DataIntegrityViolationException.class, () -> history.save(invalid));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM comparison_runs", Long.class));
    }
}
