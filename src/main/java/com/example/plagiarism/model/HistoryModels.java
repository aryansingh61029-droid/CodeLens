package com.example.plagiarism.model;

import com.example.plagiarism.model.Models.Pair;
import java.time.Instant;
import java.util.List;

/** Persisted metadata; source text and upload contents are deliberately excluded. */
public final class HistoryModels {
    private HistoryModels() {}

    public record Summary(String id, Instant createdAt, String type, int submissionCount,
                          int pairCount, Double topSimilarity) {}
    public record Detail(Summary summary, List<Pair> pairs) {}
    public record Page(List<Summary> items, int page, int size, boolean hasMore) {}
}
