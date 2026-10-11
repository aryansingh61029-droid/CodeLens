package com.example.plagiarism.service;

import com.example.plagiarism.model.HistoryModels.*;
import com.example.plagiarism.model.Models.*;
import com.example.plagiarism.persistence.HistoryRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistoryService {
    private final HistoryRepository repository;
    public HistoryService(HistoryRepository repository) { this.repository = repository; }

    @Transactional
    public String save(Comparison comparison) {
        Pair pair = new Pair(comparison.first().name(), comparison.second().name(),
                comparison.similarity(), comparison.verdict());
        return save("single", 2, List.of(pair));
    }

    @Transactional
    public String save(BatchResult result) {
        return save("batch", result.files().size(), result.pairs());
    }

    private String save(String type, int submissions, List<Pair> pairs) {
        String id = UUID.randomUUID().toString();
        Double top = pairs.isEmpty() ? null : pairs.stream().mapToDouble(Pair::similarity).max().orElseThrow();
        repository.insert(new Summary(id, Instant.now(), type, submissions, pairs.size(), top), pairs);
        return id;
    }

    @Transactional(readOnly = true)
    public Page page(int page, int size) {
        if (page < 0 || page > 1000 || size < 1 || size > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 0–1000 and size 1–50.");
        return repository.page(page, size);
    }

    @Transactional(readOnly = true)
    public Detail find(String id) {
        String canonical;
        try { canonical = UUID.fromString(id).toString(); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "History ID must be a UUID.");
        }
        return repository.find(canonical).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Comparison history entry not found."));
    }
}
