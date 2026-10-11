package com.example.plagiarism;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiTest {
    @Autowired private MockMvc mvc;
    private static final String CODE = "int sum(int a, int b) { return a + b; }";

    @Test void filesAndPastedCodeReturnSuccessfulComparisons() throws Exception {
        mvc.perform(multipart("/api/compare").file(file("file1", "A.java", CODE))
                .file(file("file2", "B.java", CODE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.similarity").value(100.0));
        mvc.perform(post("/api/compare").contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"JAVA\",\"code1\":\"" + CODE + "\",\"code2\":\"" + CODE + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.similarity").value(100.0));
    }

    @Test void invalidInputsReturnReadableProblemDetails() throws Exception {
        for (MockMultipartFile invalid : new MockMultipartFile[]{file("file1", "empty.java", ""),
                file("file1", "unsupported.txt", CODE)}) {
            mvc.perform(multipart("/api/compare").file(invalid).file(file("file2", "B.java", CODE)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isNotEmpty());
        }
        mvc.perform(multipart("/api/compare").file(file("file1", "large.java", "x".repeat(262145)))
                .file(file("file2", "B.java", CODE)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test void missingPartsAndInvalidJsonReturnProblemDetails() throws Exception {
        mvc.perform(multipart("/api/compare").file(file("file1", "A.java", CODE)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").isNotEmpty());
        mvc.perform(post("/api/compare").contentType(MediaType.APPLICATION_JSON).content("{\"language\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test void batchReturnsSymmetricMatrixAndRankedPairs() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : new String[]{"A.java", "B.java"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(CODE.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        mvc.perform(multipart("/api/compare/batch").file(new MockMultipartFile("file", "batch.zip",
                "application/zip", bytes.toByteArray())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.files.length()").value(2))
                .andExpect(jsonPath("$.matrix[0][1]").value(100.0))
                .andExpect(jsonPath("$.matrix[1][0]").value(100.0))
                .andExpect(jsonPath("$.pairs[0].similarity").value(100.0));
    }

    @Test void healthIsAvailable() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test void batchRejectsOversizedActualExpandedContent() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] content = "a".repeat(250_000).getBytes(StandardCharsets.UTF_8);
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            // Every entry is below the per-file cap; their combined expansion exceeds 2 MiB.
            for (int i = 0; i < 9; i++) {
                zip.putNextEntry(new ZipEntry("Submission" + i + ".java"));
                zip.write(content);
                zip.closeEntry();
            }
        }
        mvc.perform(multipart("/api/compare/batch").file(new MockMultipartFile("file", "batch.zip",
                "application/zip", bytes.toByteArray())))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test void batchRejectsTraversalPaths() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String name : new String[]{"../outside.java", "B.java"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(CODE.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        mvc.perform(multipart("/api/compare/batch").file(new MockMultipartFile("file", "batch.zip",
                "application/zip", bytes.toByteArray())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").isNotEmpty());
    }

    private MockMultipartFile file(String field, String name, String content) {
        return new MockMultipartFile(field, name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }
}
