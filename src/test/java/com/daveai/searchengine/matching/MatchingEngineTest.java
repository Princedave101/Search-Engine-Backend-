package com.daveai.searchengine.matching;

import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.models.Posting;
import com.daveai.searchengine.models.Token;
import com.daveai.searchengine.query.Query;
import com.daveai.searchengine.query.ast.AndNode;
import com.daveai.searchengine.query.ast.NotNode;
import com.daveai.searchengine.query.ast.OrNode;
import com.daveai.searchengine.query.ast.PhraseNode;
import com.daveai.searchengine.query.ast.QueryNode;
import com.daveai.searchengine.query.ast.TermNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for T8, written before {@code MatchingEngine} existed (the brief asks for test-first).
 * <p>
 * The contract they check:
 * <ul>
 *   <li>{@code MatchingEngine(InvertedIndex)} — the same constructor style as {@code Indexer}.</li>
 *   <li>{@code Set<String> retrieve(Query)} — the ids of the documents satisfying the query,
 *       in no particular order. Ordering is the ranker's job (T9).</li>
 *   <li>A query with a null root matches nothing; a null query is rejected, the way
 *       {@code Indexer.indexDocument(null)} is.</li>
 * </ul>
 */
class MatchingEngineTest {

    private final InvertedIndex index = new InvertedIndex();
    private final MatchingEngine engine = new MatchingEngine(index);

    /*
     * The index every test below runs against, built by hand so each expected result
     * can be read straight off this table.
     *
     *   doc-1   java spring framework            java@0      spring@1    framework@2
     *   doc-2   java machine learning            java@0      machine@1   learning@2
     *   doc-3   learning machine                 learning@0  machine@1
     *   doc-4   spring boot                      spring@0    boot@1
     *   doc-5   "... state of the art ..."       state@10    art@13
     *   doc-6   "... state art ..."              state@10    art@11
     *   doc-7   machine java ... machine learning   machine@0,5   java@1   learning@6
     *
     * doc-5 and doc-6 are the "state of the art" case: after stop-word removal the phrase is
     * stored as state@0, art@3, so the gap to match on is 3, not 1.
     *
     * doc-7 holds "machine" twice. The phrase only matches at the second occurrence, which
     * catches an implementation that checks just the first position of the first word.
     */
    @BeforeEach
    void buildIndex() {
        index.addPosting("java", posting("doc-1", 0));
        index.addPosting("java", posting("doc-2", 0));
        index.addPosting("java", posting("doc-7", 1));

        index.addPosting("spring", posting("doc-1", 1));
        index.addPosting("spring", posting("doc-4", 0));

        index.addPosting("framework", posting("doc-1", 2));

        index.addPosting("machine", posting("doc-2", 1));
        index.addPosting("machine", posting("doc-3", 1));
        index.addPosting("machine", posting("doc-7", 0, 5));

        index.addPosting("learning", posting("doc-2", 2));
        index.addPosting("learning", posting("doc-3", 0));
        index.addPosting("learning", posting("doc-7", 6));

        index.addPosting("boot", posting("doc-4", 1));

        index.addPosting("state", posting("doc-5", 10));
        index.addPosting("state", posting("doc-6", 10));

        index.addPosting("art", posting("doc-5", 13));
        index.addPosting("art", posting("doc-6", 11));
    }

    // ---------------------------------------------------------------- AND, OR, NOT

    @Test
    void termReturnsEveryDocumentContainingIt() {
        assertThat(idsFor(term("java"))).containsExactlyInAnyOrder("doc-1", "doc-2", "doc-7");
    }

    @Test
    void andReturnsOnlyDocumentsContainingEveryTerm() {
        assertThat(idsFor(and(term("java"), term("spring")))).containsExactly("doc-1");
    }

    @Test
    void orReturnsDocumentsContainingAnyTerm() {
        assertThat(idsFor(or(term("java"), term("boot"))))
                .containsExactlyInAnyOrder("doc-1", "doc-2", "doc-4", "doc-7");
    }

    @Test
    void notReturnsEveryOtherIndexedDocument() {
        assertThat(idsFor(not(term("java"))))
                .containsExactlyInAnyOrder("doc-3", "doc-4", "doc-5", "doc-6");
    }

    @Test
    void andWithNotExcludesDocuments() {
        assertThat(idsFor(and(term("spring"), not(term("boot"))))).containsExactly("doc-1");
    }

    @Test
    void nestedGroupsAreEvaluatedInTheRightOrder() {
        // (java OR boot) AND NOT spring -> {1, 2, 4, 7} minus {1, 4}
        QueryNode root = and(or(term("java"), term("boot")), not(term("spring")));

        assertThat(idsFor(root)).containsExactlyInAnyOrder("doc-2", "doc-7");
    }

    @Test
    void notLeavesTheIndexUntouched() {
        // allDocumentIds() is a live unmodifiable view, so NOT has to copy it before subtracting.
        idsFor(not(term("java")));

        assertThat(index.allDocumentIds()).hasSize(7);
        assertThat(idsFor(not(term("java"))))
                .containsExactlyInAnyOrder("doc-3", "doc-4", "doc-5", "doc-6");
    }

    // ---------------------------------------------------------------- phrases

    @Test
    void phraseMatchesWordsThatAreAdjacentAndInOrder() {
        assertThat(idsFor(phrase(word("machine", 0), word("learning", 1))))
                .containsExactlyInAnyOrder("doc-2", "doc-7");
    }

    @Test
    void phraseDoesNotMatchTheSameWordsInTheWrongOrder() {
        // doc-3 is "learning machine", so it must not match "machine learning".
        assertThat(idsFor(phrase(word("machine", 0), word("learning", 1)))).doesNotContain("doc-3");
    }

    @Test
    void reversedPhraseMatchesTheDocumentWithThatOrder() {
        assertThat(idsFor(phrase(word("learning", 0), word("machine", 1)))).containsExactly("doc-3");
    }

    @Test
    void phraseMatchesAtAnyOccurrenceOfTheFirstWord() {
        // doc-7 has machine@0 (no learning at 1) and machine@5 (learning at 6).
        assertThat(idsFor(phrase(word("machine", 0), word("learning", 1)))).contains("doc-7");
    }

    @Test
    void phraseKeepsTheGapsLeftByRemovedStopWords() {
        // "state of the art" is stored as state@0, art@3: doc-5 has the gap of 3, doc-6 does not.
        assertThat(idsFor(phrase(word("state", 0), word("art", 3)))).containsExactly("doc-5");
    }

    @Test
    void phraseWithAnUnknownWordMatchesNothing() {
        assertThat(idsFor(phrase(word("machine", 0), word("kotlin", 1)))).isEmpty();
    }

    // ---------------------------------------------------------------- nothing to match

    @Test
    void unknownTermMatchesNothing() {
        assertThat(idsFor(term("kotlin"))).isEmpty();
    }

    @Test
    void unknownTermInAnOrStillReturnsTheOtherSide() {
        assertThat(idsFor(or(term("kotlin"), term("boot")))).containsExactly("doc-4");
    }

    @Test
    void unknownTermInAnAndMatchesNothing() {
        assertThat(idsFor(and(term("kotlin"), term("java")))).isEmpty();
    }

    @Test
    void queryWithNothingSearchableMatchesNothing() {
        // QueryParser gives a null root for blank text or nothing but stop words.
        assertThat(engine.retrieve(new Query("the", null))).isNotNull().isEmpty();
    }

    @Test
    void emptyIndexMatchesNothing() {
        MatchingEngine onEmptyIndex = new MatchingEngine(new InvertedIndex());

        assertThat(onEmptyIndex.retrieve(new Query("java", term("java")))).isEmpty();
        assertThat(onEmptyIndex.retrieve(new Query("NOT java", not(term("java"))))).isEmpty();
    }

    @Test
    void nullQueryIsRejected() {
        assertThatThrownBy(() -> engine.retrieve(null)).isInstanceOf(NullPointerException.class);
    }

    // ---------------------------------------------------------------- helpers

    /** Runs a query tree against the index. The raw text is irrelevant to matching. */
    private Set<String> idsFor(QueryNode root) {
        return engine.retrieve(new Query("test query", root));
    }

    /** Builds a posting whose frequency is the number of positions given. */
    private static Posting posting(String documentID, int... positions) {
        return new Posting(documentID, positions.length, Arrays.stream(positions).boxed().toList());
    }

    // Short builders, so each query below reads like the worked examples in task.md.
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
}
