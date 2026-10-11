package com.example.plagiarism.persistence;

import com.example.plagiarism.model.HistoryModels.*;
import com.example.plagiarism.model.Models.Pair;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Parameterized SQL keeps uploaded filenames out of SQL syntax. */
@Repository
public class HistoryRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Summary> SUMMARY = (row, number) -> new Summary(
            row.getString("id"), row.getTimestamp("created_at").toInstant(), row.getString("run_type"),
            row.getInt("submission_count"), row.getInt("pair_count"),
            row.getObject("top_similarity") == null ? null : row.getDouble("top_similarity"));

    public HistoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(Summary summary, List<Pair> pairs) {
        jdbc.update("""
                INSERT INTO comparison_runs
                  (id, created_at, run_type, submission_count, pair_count, top_similarity)
                VALUES (?, ?, ?, ?, ?, ?)
                """, summary.id(), Timestamp.from(summary.createdAt()), summary.type(),
                summary.submissionCount(), summary.pairCount(), summary.topSimilarity());
        // At most 190 pairs (20 submissions). The service wraps all inserts in one transaction.
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            Pair pair = pairs.get(i);
            rows.add(new Object[]{summary.id(), i, pair.first(), pair.second(), pair.similarity(), pair.verdict()});
        }
        if (!rows.isEmpty()) jdbc.batchUpdate("""
                INSERT INTO comparison_pairs
                  (run_id, pair_position, first_name, second_name, similarity, verdict)
                VALUES (?, ?, ?, ?, ?, ?)
                """, rows);
    }

    public Page page(int page, int size) {
        List<Summary> rows = jdbc.query("""
                SELECT * FROM comparison_runs ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, SUMMARY, size + 1, page * size);
        return new Page(List.copyOf(rows.subList(0, Math.min(rows.size(), size))), page, size, rows.size() > size);
    }

    public Optional<Detail> find(String id) {
        List<Summary> runs = jdbc.query("SELECT * FROM comparison_runs WHERE id = ?", SUMMARY, id);
        if (runs.isEmpty()) return Optional.empty();
        List<Pair> pairs = jdbc.query("""
                SELECT first_name, second_name, similarity, verdict FROM comparison_pairs
                WHERE run_id = ? ORDER BY pair_position
                """, (row, number) -> new Pair(row.getString("first_name"), row.getString("second_name"),
                row.getDouble("similarity"), row.getString("verdict")), id);
        return Optional.of(new Detail(runs.getFirst(), List.copyOf(pairs)));
    }
}
