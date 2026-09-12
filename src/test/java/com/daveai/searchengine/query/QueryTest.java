package com.daveai.searchengine.query;

import com.daveai.searchengine.models.Token;
import com.daveai.searchengine.query.ast.AndNode;
import com.daveai.searchengine.query.ast.NotNode;
import com.daveai.searchengine.query.ast.OrNode;
import com.daveai.searchengine.query.ast.PhraseNode;
import com.daveai.searchengine.query.ast.QueryNode;
import com.daveai.searchengine.query.ast.TermNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryTest {

    private static Query query(QueryNode root) {
        return new Query("raw", root);
    }

    private static TermNode term(String term) {
        return new TermNode(term);
    }

    private static AndNode and(QueryNode... children) {
        return new AndNode(List.of(children));
    }

    private static OrNode or(QueryNode... children) {
        return new OrNode(List.of(children));
    }

    /** A phrase whose words sit next to each other: positions 0, 1, 2, ... */
    private static PhraseNode phrase(String... words) {
        Token[] tokens = new Token[words.length];
        for (int i = 0; i < words.length; i++) {
            tokens[i] = new Token(words[i], null, i);
        }
        return new PhraseNode(List.of(tokens));
    }

    @Test
    void isEmptyOnlyWhenThereIsNoRoot() {
        assertThat(query(null).isEmpty()).isTrue();
        assertThat(query(term("java")).isEmpty()).isFalse();
    }

    @Test
    void emptyQueryHasNoTerms() {
        assertThat(query(null).terms()).isEmpty();
    }

    @Test
    void termsIncludeTermAndPhraseWordsInQueryOrder() {
        assertThat(query(and(term("java"), phrase("machine", "learning"))).terms())
                .containsExactly("java", "machine", "learning");
    }

    @Test
    void termsIncludeEverySideOfAnOr() {
        assertThat(query(or(term("java"), and(term("spring"), term("boot")))).terms())
                .containsExactly("java", "spring", "boot");
    }

    @Test
    void termsSkipWordsUnderNot() {
        assertThat(query(and(term("java"), new NotNode(term("spring")))).terms())
                .containsExactly("java");
    }

    @Test
    void termsSkipEverythingUnderANot() {
        QueryNode excluded = new NotNode(or(term("spring"), phrase("machine", "learning")));

        assertThat(query(and(term("java"), excluded)).terms()).containsExactly("java");
    }

    @Test
    void repeatedWordsAppearOnce() {
        assertThat(query(or(term("java"), and(term("java"), term("spring")))).terms())
                .containsExactly("java", "spring");
    }

    @Test
    void termsCannotBeModified() {
        List<String> terms = query(term("java")).terms();

        assertThatThrownBy(() -> terms.add("spring")).isInstanceOf(UnsupportedOperationException.class);
    }
}
