package com.example.plagiarism.controller;

import com.example.plagiarism.model.Language;
import com.example.plagiarism.model.Models.*;
import com.example.plagiarism.service.*;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/compare")
public class CompareController {
    private final ComparisonService comparisons;
    private final SourceService sources;
    private final HistoryService history;
    public CompareController(ComparisonService comparisons, SourceService sources, HistoryService history) {
        this.comparisons = comparisons; this.sources = sources; this.history = history;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Comparison compareFiles(@RequestPart("file1") MultipartFile first,
                                   @RequestPart("file2") MultipartFile second, HttpServletResponse response) throws IOException {
        Comparison result = comparisons.compare(sources.file(first), sources.file(second));
        response.setHeader("X-Comparison-Id", history.save(result));
        return result;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Comparison compareSnippets(@RequestBody SnippetRequest request, HttpServletResponse response) {
        Language language = Language.fromName(request.language());
        Comparison result = comparisons.compare(sources.snippet("Snippet A", request.code1(), language),
                sources.snippet("Snippet B", request.code2(), language));
        response.setHeader("X-Comparison-Id", history.save(result));
        return result;
    }

    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BatchResult batch(@RequestPart("file") MultipartFile archive, HttpServletResponse response) throws IOException {
        BatchResult result = comparisons.batch(sources.archive(archive));
        response.setHeader("X-Comparison-Id", history.save(result));
        return result;
    }
}
