package com.daveai.searchengine.indexing;

import com.daveai.searchengine.models.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TextProcessorTest {

    private final TextProcessor processor = new TextProcessor();

    @Test
    void removesStopWordsButKeepsOriginalPositions() {
        List<Token> tokens = processor.processText("The cat sat on the mat", "doc-1");

        assertThat(tokens)
                .extracting(Token::value, Token::positions)
                .containsExactly(tuple("cat", 1), tuple("sat", 2), tuple("mat", 5));
    }

    @Test
    void stopWordsBetweenWordsStillCountTowardsPosition() {
        // Needed for phrase search: "state" and "art" are not next to each other.
        List<Token> tokens = processor.processText("state of the art", "doc-1");

        assertThat(tokens)
                .extracting(Token::value, Token::positions)
                .containsExactly(tuple("state", 0), tuple("art", 3));
    }

    @Test
    void lowercasesWordsAndIgnoresPunctuation() {
        List<Token> tokens = processor.processText("Hello, WORLD!", "doc-1");

        assertThat(tokens)
                .extracting(Token::value, Token::positions)
                .containsExactly(tuple("hello", 0), tuple("world", 1));
    }

    @Test
    void recognisesStopWordsWrittenInCapitals() {
        List<Token> tokens = processor.processText("THE Cat", "doc-1");

        assertThat(tokens)
                .extracting(Token::value, Token::positions)
                .containsExactly(tuple("cat", 1));
    }

    @Test
    void leadingPunctuationDoesNotShiftPositions() {
        List<Token> tokens = processor.processText(", hello world", "doc-1");

        assertThat(tokens)
                .extracting(Token::value, Token::positions)
                .containsExactly(tuple("hello", 0), tuple("world", 1));
    }

    @Test
    void treatsAnyRunOfNonLetterCharactersAsOneSeparator() {
        List<Token> tokens = processor.processText("cat---dog\n\t bird...", "doc-1");

        assertThat(tokens)
                .extracting(Token::value, Token::positions)
                .containsExactly(tuple("cat", 0), tuple("dog", 1), tuple("bird", 2));
    }

    @Test
    void keepsNumbersAndWordsContainingDigits() {
        List<Token> tokens = processor.processText("CSC322 project 2024", "doc-1");

        assertThat(tokens)
                .extracting(Token::value, Token::positions)
                .containsExactly(tuple("csc322", 0), tuple("project", 1), tuple("2024", 2));
    }

    @Test
    void keepsLettersWithAccents() {
        List<Token> tokens = processor.processText("Café résumé", "doc-1");

        assertThat(tokens).extracting(Token::value).containsExactly("café", "résumé");
    }

    @Test
    void returnsEmptyListForNullText() {
        assertThat(processor.processText(null, "doc-1")).isEmpty();
    }

    @Test
    void returnsEmptyListForEmptyOrBlankText() {
        assertThat(processor.processText("", "doc-1")).isEmpty();
        assertThat(processor.processText("   \n\t", "doc-1")).isEmpty();
    }

    @Test
    void returnsEmptyListWhenEveryWordIsAStopWord() {
        assertThat(processor.processText("the and of to in", "doc-1")).isEmpty();
    }

    @Test
    void copiesDocumentIdOntoEveryToken() {
        List<Token> tokens = processor.processText("search engine project", "reports/csc322.pdf");

        assertThat(tokens).hasSize(3).allSatisfy(token ->
                assertThat(token.documentID()).isEqualTo("reports/csc322.pdf"));
    }

    @Test
    void acceptsNullDocumentIdForQueries() {
        List<Token> tokens = processor.processText("machine learning", null);

        assertThat(tokens).extracting(Token::value).containsExactly("machine", "learning");
        assertThat(tokens).allSatisfy(token -> assertThat(token.documentID()).isNull());
    }
}
