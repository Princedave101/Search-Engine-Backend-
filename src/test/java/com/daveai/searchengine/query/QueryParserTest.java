package com.daveai.searchengine.query;

import com.daveai.searchengine.indexing.TextProcessor;
import com.daveai.searchengine.models.Token;
import com.daveai.searchengine.query.ast.AndNode;
import com.daveai.searchengine.query.ast.NotNode;
import com.daveai.searchengine.query.ast.OrNode;
import com.daveai.searchengine.query.ast.PhraseNode;
import com.daveai.searchengine.query.ast.QueryNode;
import com.daveai.searchengine.query.ast.TermNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryParserTest {

    private final QueryParser parser = new QueryParser(new TextProcessor());

    private QueryNode rootOf(String raw) {
        return parser.parse(raw).root();
    }

    // Short helpers so each expected tree reads like the worked examples in task.md.
    private static TermNode term(String term) {
        return new TermNode(term);
    }

    private static AndNode and(QueryNode... children) {
        return new AndNode(List.of(children));
    }

    private static OrNode or(QueryNode... children) {
        return new OrNode(List.of(children));
    }

    private static NotNode not(QueryNode child) {
        return new NotNode(child);
    }

    private static PhraseNode phrase(Token... words) {
        return new PhraseNode(List.of(words));
    }

    /** A query word: queries have no document, so documentID is null. */
    private static Token word(String value, int position) {
        return new Token(value, null, position);
    }

    @Test
    void wordsSideBySideMeanAnd() {
        assertThat(rootOf("java spring")).isEqualTo(and(term("java"), term("spring")));
    }

    @Test
    void orCombinesAlternatives() {
        assertThat(rootOf("java OR python")).isEqualTo(or(term("java"), term("python")));
    }

    @Test
    void andBindsTighterThanOr() {
        assertThat(rootOf("java OR spring boot"))
                .isEqualTo(or(term("java"), and(term("spring"), term("boot"))));
    }

    @Test
    void bracketsOverridePrecedence() {
        assertThat(rootOf("(java OR python) AND spring"))
                .isEqualTo(and(or(term("java"), term("python")), term("spring")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"java -spring", "java NOT spring"})
    void minusAndNotExcludeAWord(String raw) {
        assertThat(rootOf(raw)).isEqualTo(and(term("java"), not(term("spring"))));
    }

    @Test
    void symbolOperatorsWorkLikeWordOperators() {
        assertThat(rootOf("java & spring | python"))
                .isEqualTo(or(and(term("java"), term("spring")), term("python")));
    }

    @Test
    void wordsAreLowercased() {
        assertThat(rootOf("JAVA")).isEqualTo(term("java"));
    }

    @Test
    void lowercaseOperatorWordsAreOrdinaryWords() {
        // "or" is not the OR operator; it is a normal word, and also a stop word.
        assertThat(rootOf("java or python")).isEqualTo(and(term("java"), term("python")));
    }

    @Test
    void phraseKeepsWordPositions() {
        assertThat(rootOf("\"Machine Learning\"")).isEqualTo(phrase(word("machine", 0), word("learning", 1)));
    }

    @Test
    void phrasePositionsStillCountRemovedStopWords() {
        assertThat(rootOf("\"state of the art\"")).isEqualTo(phrase(word("state", 0), word("art", 3)));
    }

    @Test
    void oneWordPhraseIsATerm() {
        assertThat(rootOf("\"java\"")).isEqualTo(term("java"));
    }

    @Test
    void stopWordsAreDroppedAndOneChildIsNotWrapped() {
        assertThat(rootOf("the AND java")).isEqualTo(term("java"));
        assertThat(rootOf("java OR the")).isEqualTo(term("java"));
    }

    @Test
    void punctuationAndQuestionWordsAreIgnored() {
        assertThat(rootOf("what is java?")).isEqualTo(term("java"));
    }

    @Test
    void excludingOnlyAStopWordLeavesTheRest() {
        assertThat(rootOf("java -the")).isEqualTo(term("java"));
    }

    @Test
    void queryOfOnlyStopWordsHasNoRoot() {
        assertThat(rootOf("the OR of")).isNull();
    }

    @Test
    void unclosedBracketFallsBackToAndOfWords() {
        assertThat(rootOf("(java spring")).isEqualTo(and(term("java"), term("spring")));
    }

    @Test
    void strayClosingBracketFallsBackToAndOfWords() {
        assertThat(rootOf("java spring)")).isEqualTo(and(term("java"), term("spring")));
    }

    @Test
    void unclosedQuoteIsIgnored() {
        assertThat(rootOf("\"machine learning")).isEqualTo(and(term("machine"), term("learning")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "?!"})
    void blankOrSymbolOnlyTextIsAnEmptyQuery(String raw) {
        assertThat(parser.parse(raw).isEmpty()).isTrue();
    }

    @Test
    void termsOfAParsedQuerySkipExcludedWords() {
        assertThat(parser.parse("java -spring \"machine learning\"").terms())
                .containsExactly("java", "machine", "learning");
    }

    @Test
    void keepsTheRawText() {
        assertThat(parser.parse("Java OR python").rawText()).isEqualTo("Java OR python");
    }

    @Test
    void rejectsNullProcessor() {
        assertThatThrownBy(() -> new QueryParser(null)).isInstanceOf(NullPointerException.class);
    }
}
