package com.example.plagiarism;

import com.example.plagiarism.fingerprinting.Winnowing;
import com.example.plagiarism.model.Language;
import com.example.plagiarism.model.Models.Comparison;
import com.example.plagiarism.model.Models.Source;
import com.example.plagiarism.service.ComparisonService;
import com.example.plagiarism.tokenizer.CodeTokenizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComparisonServiceTest {
    private final ComparisonService service = new ComparisonService(new CodeTokenizer(), new Winnowing());
    private static final String SUM = """
            int sum(int[] values) {
                int total = 0;
                for (int i = 0; i < values.length; i++) { total += values[i]; }
                return total;
            }
            """;
    private static final String MAX = """
            int max(int[] items) {
                int best = items[0];
                for (int item : items) { if (item > best) { best = item; } }
                return best;
            }
            """;
    private static final String SEARCH = """
            boolean contains(int[] items, int target) {
                for (int item : items) { if (item == target) { return true; } }
                return false;
            }
            """;

    @Test void identicalCodeScoresOneHundredAndHighlightsMatches() {
        Comparison result = compare(SUM, SUM);
        assertEquals(100.0, result.similarity());
        assertEquals("high", result.verdict().toLowerCase());
        assertFalse(result.first().matches().isEmpty());
        result.first().matches().forEach(range -> {
            assertTrue(range.start() >= 0 && range.end() <= SUM.length());
            assertTrue(range.end() > range.start());
        });
    }

    @Test void renamedVariablesAndCommentsKeepTheSameScore() {
        String renamed = SUM.replace("values", "numbers").replace("total", "answer")
                + "\n/* annotation */";
        assertEquals(100.0, compare(SUM, renamed).similarity());
    }

    @Test void reorderedFunctionsKeepSubstantialSimilarity() {
        assertTrue(compare(SUM + MAX + SEARCH, SEARCH + SUM + MAX).similarity() > 50.0);
    }

    @Test void structurallyDifferentProgramsHaveLowSimilarity() {
        String other = """
                abstract class Dispatcher {
                    abstract void close();
                    String route(String key) {
                        try {
                            switch (key) {
                                case "start": throw new IllegalArgumentException("blocked");
                                case "stop": synchronized (this) { close(); break; }
                                default: assert key != null;
                            }
                        } catch (RuntimeException error) { error.printStackTrace(); }
                        finally { close(); }
                        return key.toUpperCase();
                    }
                }
                """;
        assertTrue(compare(SUM + MAX, other).similarity() < 35.0);
    }

    @Test void jaccardIsSymmetricAndBounded() {
        Comparison forward = compare(SUM + MAX, SUM + SEARCH);
        Comparison backward = compare(SUM + SEARCH, SUM + MAX);
        assertEquals(forward.similarity(), backward.similarity());
        assertTrue(forward.similarity() >= 0 && forward.similarity() <= 100);
        double expected = 100.0 * forward.sharedFingerprints() / forward.totalFingerprints();
        assertEquals(expected, forward.similarity(), 0.11);
    }

    private Comparison compare(String first, String second) {
        return service.compare(new Source("A.java", Language.JAVA, first),
                new Source("B.java", Language.JAVA, second));
    }
}
