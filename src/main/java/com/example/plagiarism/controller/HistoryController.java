package com.example.plagiarism.controller;

import com.example.plagiarism.model.HistoryModels.*;
import com.example.plagiarism.service.HistoryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final HistoryService history;
    public HistoryController(HistoryService history) { this.history = history; }

    @GetMapping
    public Page page(@RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return history.page(page, size);
    }

    @GetMapping("/{id}")
    public Detail detail(@PathVariable String id) { return history.find(id); }
}
