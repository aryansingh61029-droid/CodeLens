package com.example.plagiarism.fingerprinting;

import com.example.plagiarism.model.Models.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class Winnowing {
    private static final long BASE = 1_000_003L;

    /** Rolling polynomial hashes modulo 2^64; Java long overflow is intentional. */
    public List<Fingerprint> fingerprint(List<Token> tokens, int k, int window) {
        if (k < 1 || window < 1) throw new IllegalArgumentException("k and window must be positive");
        if (tokens.isEmpty()) return List.of();
        int width = Math.min(k, tokens.size());
        long[] values = tokens.stream().mapToLong(t -> tokenHash(t.value())).toArray();
        long[] hashes = new long[values.length - width + 1];
        long power = 1, hash = 0;
        for (int i = 0; i < width; i++) {
            hash = hash * BASE + values[i];
            if (i < width - 1) power *= BASE;
        }
        hashes[0] = hash;
        for (int i = 1; i < hashes.length; i++) {
            hash = (hash - values[i - 1] * power) * BASE + values[i + width - 1];
            hashes[i] = hash;
        }
        return select(hashes, window).stream().map(i -> new Fingerprint(hashes[i], i, width)).toList();
    }

    /** Monotonic deque: O(n) selection; equal minima choose the rightmost position. */
    public List<Integer> select(long[] hashes, int window) {
        if (window < 1) throw new IllegalArgumentException("window must be positive");
        if (hashes.length == 0) return List.of();
        int width = Math.min(window, hashes.length);
        Deque<Integer> queue = new ArrayDeque<>();
        List<Integer> selected = new ArrayList<>();
        int last = -1;
        for (int i = 0; i < hashes.length; i++) {
            while (!queue.isEmpty() && queue.peekFirst() <= i - width) queue.removeFirst();
            while (!queue.isEmpty() && Long.compareUnsigned(hashes[queue.peekLast()], hashes[i]) >= 0) queue.removeLast();
            queue.addLast(i);
            if (i >= width - 1 && queue.peekFirst() != last) {
                last = queue.peekFirst(); selected.add(last);
            }
        }
        return List.copyOf(selected);
    }

    private long tokenHash(String token) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < token.length(); i++) { hash ^= token.charAt(i); hash *= 0x100000001b3L; }
        return hash;
    }
}
