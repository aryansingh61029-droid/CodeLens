package com.example.plagiarism;

import com.example.plagiarism.model.Language;
import com.example.plagiarism.model.Models.Token;
import com.example.plagiarism.tokenizer.CodeTokenizer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CodeTokenizerTest {
    private final CodeTokenizer tokenizer = new CodeTokenizer();

    @Test void renamingWhitespaceAndCommentsDoNotChangeNormalizedTokens() {
        String original = "int sum(int value) { return value + 1; }";
        String edited = "/* header */ int total ( int number ) { // renamed\n return number+1; }";
        assertEquals(values(original, Language.JAVA), values(edited, Language.JAVA));
    }

    @Test void commentMarkersInsideAStringRemainInsideOneToken() {
        String literal = "\"https://host/*path*/\"";
        String source = "String url = " + literal + "; // ignored\nreturn url;";
        List<Token> tokens = tokenizer.tokenize(source, Language.JAVA);
        assertTrue(tokens.stream().anyMatch(t -> source.substring(t.start(), t.end()).equals(literal)));
        assertFalse(tokens.stream().anyMatch(t -> source.substring(t.start(), t.end()).contains("ignored")));
        for (Token token : tokens) {
            assertTrue(token.start() >= 0 && token.end() <= source.length());
            assertTrue(token.start() < token.end());
        }
    }

    @Test void pythonTripleQuotedStringsProtectEmbeddedComments() {
        String literal = "\"\"\"line one\n# inside string\nline three\"\"\"";
        String source = "message = " + literal + "\n# ignored\nprint(message)";
        assertTrue(tokenizer.tokenize(source, Language.PYTHON).stream()
                .anyMatch(t -> source.substring(t.start(), t.end()).equals(literal)));
    }

    @Test void cppRawStringsProtectEmbeddedQuotesAndCommentMarkers() {
        String literal = "R\"tag(a \"quote\" // text /* text */)tag\"";
        String source = "const char* message = " + literal + "; // ignored";
        assertTrue(tokenizer.tokenize(source, Language.CPP).stream()
                .anyMatch(t -> source.substring(t.start(), t.end()).equals(literal)));
    }

    @Test void operatorsAndKeywordsRemainDistinguishable() {
        assertNotEquals(values("return a + b;", Language.JAVA), values("throw a - b;", Language.JAVA));
    }

    private List<String> values(String code, Language language) {
        return tokenizer.tokenize(code, language).stream().map(Token::value).toList();
    }
}
