package com.example.plagiarism.service;

import com.example.plagiarism.fingerprinting.Winnowing;
import com.example.plagiarism.model.Models.*;
import com.example.plagiarism.tokenizer.CodeTokenizer;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ComparisonService {
    private final CodeTokenizer tokenizer;
    private final Winnowing winnowing;
    public ComparisonService(CodeTokenizer tokenizer, Winnowing winnowing) {
        this.tokenizer = tokenizer; this.winnowing = winnowing;
    }
    // Keeping normalized grams alongside hashes verifies collisions before scoring/highlighting.
    private record Key(long hash, List<String> gram) {}
    private record Prepared(Source source, List<Token> tokens, Map<Key, List<Range>> fingerprints) {}

    public Comparison compare(Source first, Source second) {
        if (first.language() != second.language()) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Compare two programs in the same language; C and C++ share one family.");
        return compare(prepare(first), prepare(second));
    }

    private Prepared prepare(Source source) {
        return prepare(source, tokenizer.tokenize(source.code(), source.language()));
    }

    private Prepared prepare(Source source, List<Token> tokens) {
        if (tokens.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Source must contain code, not only whitespace or comments: " + source.name());
        Map<Key, List<Range>> map = new HashMap<>();
        for (Fingerprint fp : winnowing.fingerprint(tokens, 7, 5)) {
            List<Token> gram = tokens.subList(fp.index(), fp.index() + fp.length());
            Key key = new Key(fp.hash(), gram.stream().map(Token::value).toList());
            map.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(new Range(gram.getFirst().start(), gram.getLast().end()));
        }
        return new Prepared(source, tokens, map);
    }

    private Comparison compare(Prepared first, Prepared second) {
        Set<Key> shared = new HashSet<>(first.fingerprints().keySet());
        shared.retainAll(second.fingerprints().keySet());
        int union = first.fingerprints().size() + second.fingerprints().size() - shared.size();
        double score = Math.round(10_000.0 * shared.size() / union) / 100.0;
        return new Comparison(score, verdict(score), shared.size(), union,
                document(first, shared), document(second, shared));
    }

    private DocumentResult document(Prepared value, Set<Key> shared) {
        List<Range> ranges = shared.stream().flatMap(key -> value.fingerprints().get(key).stream())
                .sorted(Comparator.comparingInt(Range::start)).toList();
        List<Range> merged = new ArrayList<>();
        for (Range range : ranges) {
            if (merged.isEmpty() || merged.getLast().end() < range.start()) merged.add(range);
            else {
                Range last = merged.removeLast();
                merged.add(new Range(last.start(), Math.max(last.end(), range.end())));
            }
        }
        return new DocumentResult(value.source().name(), value.source().language(), value.source().code(),
                value.tokens().size(), value.fingerprints().size(), List.copyOf(merged));
    }

    public BatchResult batch(List<Source> sources) {
        List<Prepared> prepared = new ArrayList<>();
        int totalTokens = 0;
        for (Source source : sources) {
            List<Token> tokens = tokenizer.tokenize(source.code(), source.language());
            totalTokens += tokens.size();
            if (totalTokens > 160_000) throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Batch exceeds 160,000 tokens.");
            prepared.add(prepare(source, tokens));
        }
        Double[][] matrix = new Double[sources.size()][sources.size()];
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            matrix[i][i] = 100.0;
            for (int j = i + 1; j < sources.size(); j++) {
                if (sources.get(i).language() != sources.get(j).language()) continue;
                // Batch scoring does not build the potentially large highlighting response.
                Set<Key> shared = new HashSet<>(prepared.get(i).fingerprints().keySet());
                shared.retainAll(prepared.get(j).fingerprints().keySet());
                int union = prepared.get(i).fingerprints().size() + prepared.get(j).fingerprints().size() - shared.size();
                double score = Math.round(10_000.0 * shared.size() / union) / 100.0;
                matrix[i][j] = matrix[j][i] = score;
                pairs.add(new Pair(sources.get(i).name(), sources.get(j).name(), score, verdict(score)));
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::similarity).reversed().thenComparing(Pair::first).thenComparing(Pair::second));
        return new BatchResult(sources.stream().map(Source::name).toList(), matrix, List.copyOf(pairs));
    }

    private String verdict(double score) { return score >= 70 ? "high" : score >= 35 ? "medium" : "low"; }
}
