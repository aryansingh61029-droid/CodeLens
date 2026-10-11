package com.example.plagiarism.model;

import java.util.List;

/** Immutable request, response, and algorithm values; offsets are UTF-16, end exclusive. */
public final class Models {
    private Models() {}
    public record Token(String value, int start, int end) {}
    public record Fingerprint(long hash, int index, int length) {}
    public record Range(int start, int end) {}
    public record Source(String name, Language language, String code) {}
    public record SnippetRequest(String language, String code1, String code2) {}
    public record DocumentResult(String name, Language language, String code, int tokenCount,
                                 int fingerprintCount, List<Range> matches) {}
    public record Comparison(double similarity, String verdict, int sharedFingerprints,
                             int totalFingerprints, DocumentResult first, DocumentResult second) {}
    public record Pair(String first, String second, double similarity, String verdict) {}
    public record BatchResult(List<String> files, Double[][] matrix, List<Pair> pairs) {}
}
