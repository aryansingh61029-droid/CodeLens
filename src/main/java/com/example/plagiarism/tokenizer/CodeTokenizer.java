package com.example.plagiarism.tokenizer;

import com.example.plagiarism.model.Language;
import com.example.plagiarism.model.Models.Token;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Lightweight lexical normalization, not a compiler. Never executes submitted code. */
@Component
public class CodeTokenizer {
    private static final Set<String> JAVA = words("abstract assert boolean break byte case catch char class const continue default do double else enum exports extends final finally float for if implements import instanceof int interface long module native new non-sealed open opens package permits private protected provides public record requires return sealed short static strictfp super switch synchronized this throw throws to transient transitive try uses var void volatile while with yield true false null");
    private static final Set<String> PYTHON = words("False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case");
    private static final Set<String> CPP = words("alignas alignof asm auto bool break case catch char char8_t char16_t char32_t class concept const consteval constexpr constinit const_cast continue co_await co_return co_yield decltype default delete do double dynamic_cast else enum explicit export extern false float for friend goto if inline int long mutable namespace new noexcept nullptr operator private protected public register reinterpret_cast requires return short signed sizeof static static_assert static_cast struct switch template this thread_local throw true try typedef typeid typename union unsigned using virtual void volatile wchar_t while _Bool _Complex _Atomic _Generic _Noreturn _Static_assert _Thread_local");
    private static final Pattern NUMBER = Pattern.compile("(?:0[xX][0-9a-fA-F_]+(?:\\.[0-9a-fA-F_]*)?(?:[pP][+-]?[0-9_]+)?|0[bB][01_]+|0[oO][0-7_]+|(?:[0-9][0-9_]*(?:\\.[0-9_]*)?|\\.[0-9][0-9_]*)(?:[eE][+-]?[0-9_]+)?)[uUlLfFdDjJ]*");
    private static final List<String> OPERATORS = List.of(">>>=", "<<=", ">>=", "**=", "//=", "...", "->*", ">>>", "==", "!=", "<=", ">=", "++", "--", "+=", "-=", "*=", "/=", "%=", "&&", "||", "<<", ">>", "->", "::", "**", "//", ":=", "&=", "|=", "^=", "=>", ".*");
    private static Set<String> words(String text) { return Set.of(text.split(" ")); }

    public List<Token> tokenize(String source, Language language) {
        Set<String> keywords = switch (language) { case JAVA -> JAVA; case PYTHON -> PYTHON; case CPP -> CPP; };
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c) || c == '\uFEFF') { i++; continue; }
            if ((language == Language.PYTHON && c == '#') ||
                    (language != Language.PYTHON && source.startsWith("//", i))) {
                int end = source.indexOf('\n', i); i = end < 0 ? source.length() : end + 1; continue;
            }
            if (language != Language.PYTHON && source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                if (end < 0) throw invalid("Unterminated block comment.");
                i = end + 2; continue;
            }
            int start = i;
            int quote = quoteStart(source, i, language);
            if (quote >= 0) {
                i = stringEnd(source, quote, language);
                tokens.add(new Token("STR", start, i));
            } else if (Character.isJavaIdentifierStart(c)) {
                while (++i < source.length() && Character.isJavaIdentifierPart(source.charAt(i))) { /* scan */ }
                String word = source.substring(start, i);
                tokens.add(new Token(keywords.contains(word) ? word : "ID", start, i));
            } else {
                var number = NUMBER.matcher(source).region(i, source.length());
                if (number.lookingAt()) { i = number.end(); tokens.add(new Token("NUM", start, i)); }
                else {
                    String op = null;
                    for (String candidate : OPERATORS) if (source.startsWith(candidate, i)) { op = candidate; break; }
                    i += op == null ? 1 : op.length();
                    tokens.add(new Token(source.substring(start, i), start, i));
                }
            }
            if (tokens.size() > 40_000) throw invalid("Source exceeds the 40,000-token limit.");
        }
        return List.copyOf(tokens);
    }

    private int quoteStart(String text, int start, Language language) {
        if (text.charAt(start) == '\'' || text.charAt(start) == '"') return start;
        List<String> prefixes = switch (language) {
            case PYTHON -> List.of("fr", "rf", "br", "rb", "r", "u", "b", "f");
            case CPP -> List.of("u8R", "uR", "UR", "LR", "R", "u8", "u", "U", "L");
            default -> List.of();
        };
        for (String p : prefixes) {
            int q = start + p.length();
            if (q < text.length() && text.regionMatches(language == Language.PYTHON, start, p, 0, p.length())
                    && (text.charAt(q) == '"' || text.charAt(q) == '\'')) return q;
        }
        return -1;
    }

    private int stringEnd(String text, int quote, Language language) {
        char delimiter = text.charAt(quote);
        if (language == Language.CPP && quote > 0 && text.charAt(quote - 1) == 'R' && delimiter == '"') {
            int open = text.indexOf('(', quote + 1);
            if (open < 0 || open - quote > 17) throw invalid("Invalid C++ raw string.");
            String close = ")" + text.substring(quote + 1, open) + "\"";
            int end = text.indexOf(close, open + 1);
            if (end < 0) throw invalid("Unterminated C++ raw string.");
            return end + close.length();
        }
        String triple = String.valueOf(delimiter).repeat(3);
        int width = language != Language.CPP && text.startsWith(triple, quote) ? 3 : 1;
        String close = String.valueOf(delimiter).repeat(width);
        for (int i = quote + width; i < text.length();) {
            if (text.charAt(i) == '\\') { i += 2; continue; }
            if (text.startsWith(close, i)) return i + width;
            i++;
        }
        throw invalid("Unterminated string or character literal.");
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
