package com.example.plagiarism;

import com.example.plagiarism.fingerprinting.Winnowing;
import com.example.plagiarism.model.Models.Fingerprint;
import com.example.plagiarism.model.Models.Token;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class WinnowingTest {
    private final Winnowing winnowing = new Winnowing();

    @Test void choosesRightmostMinimumAndDoesNotRepeatSelections() {
        assertEquals(List.of(2, 4, 5), winnowing.select(new long[]{4, 2, 2, 5, 1, 1}, 3));
    }

    @Test void comparesHashesAsUnsignedNumbers() {
        assertEquals(List.of(1), winnowing.select(new long[]{Long.MIN_VALUE, 1}, 2));
    }

    @Test void shortProgramsStillHaveOneFingerprint() {
        List<Fingerprint> result = winnowing.fingerprint(tokens("return", "ID", ";"), 7, 5);
        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().index());
        assertEquals(3, result.getFirst().length());
    }

    @Test void exactKgramAndEmptyInputAreHandled() {
        assertEquals(1, winnowing.fingerprint(tokens("a", "b", "c"), 3, 5).size());
        assertTrue(winnowing.fingerprint(List.of(), 7, 5).isEmpty());
    }

    @Test void hashesAreDeterministicAndRespectTokenBoundaries() {
        List<Token> input = tokens("if", "(", "ID", ")", "return", "ID", ";", "else", "return", "NUM");
        assertEquals(winnowing.fingerprint(input, 3, 2), winnowing.fingerprint(input, 3, 2));
        assertNotEquals(winnowing.fingerprint(tokens("ab", "c"), 2, 1).getFirst().hash(),
                winnowing.fingerprint(tokens("a", "bc"), 2, 1).getFirst().hash());
    }

    @Test void rollingHashesEqualFreshHashesOfEveryKgram() {
        List<Token> input = IntStream.range(0, 24).mapToObj(i -> new Token("token" + i, i, i + 1)).toList();
        List<Fingerprint> rolling = winnowing.fingerprint(input, 7, 1);
        assertEquals(input.size() - 7 + 1, rolling.size());
        for (int i = 0; i < rolling.size(); i++) {
            long fresh = winnowing.fingerprint(input.subList(i, i + 7), 7, 1).getFirst().hash();
            assertEquals(fresh, rolling.get(i).hash(), "Rolling hash at position " + i);
        }
    }

    @Test void elevenCopiedTokensAreDetectedDespiteUnequalPrefixes() {
        // The Winnowing detection threshold is k + window - 1 = 7 + 5 - 1 = 11.
        List<Token> block = IntStream.range(0, 11).mapToObj(i -> new Token("copied" + i, i, i + 1)).toList();
        List<Token> left = new ArrayList<>(tokens("left", "prefix"));
        left.addAll(block); left.addAll(tokens("left-suffix"));
        List<Token> right = new ArrayList<>(tokens("another", "longer", "different", "right", "prefix"));
        right.addAll(block); right.addAll(tokens("right-suffix", "end"));
        long guaranteed = winnowing.fingerprint(block, 7, 5).getFirst().hash();
        assertTrue(winnowing.fingerprint(left, 7, 5).stream().anyMatch(fp -> fp.hash() == guaranteed
                && fp.index() >= 2 && fp.index() + fp.length() <= 13));
        assertTrue(winnowing.fingerprint(right, 7, 5).stream().anyMatch(fp -> fp.hash() == guaranteed
                && fp.index() >= 5 && fp.index() + fp.length() <= 16));
    }

    private List<Token> tokens(String... values) {
        return IntStream.range(0, values.length).mapToObj(i -> new Token(values[i], i, i + 1)).toList();
    }
}
