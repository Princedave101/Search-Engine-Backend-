package com.daveai.searchengine.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchResultTest {

    @Test
    void storesAllFields() {
        SearchResult result = new SearchResult("doc-1", "Title", "...matched text...", 1.5);

        assertThat(result.documentID()).isEqualTo("doc-1");
        assertThat(result.title()).isEqualTo("Title");
        assertThat(result.snippet()).isEqualTo("...matched text...");
        assertThat(result.score()).isEqualTo(1.5);
    }

    @Test
    void rejectsNegativeScore() {
        assertThatThrownBy(() -> new SearchResult("doc-1", "Title", "snippet", -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsScoreOfZero() {
        assertThatCode(() -> new SearchResult("doc-1", "Title", "snippet", 0.0)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNaNScore() {
        // "score < 0" is false for NaN, so a plain less-than check lets it through.
        // NaN can appear in T9 if a score is divided by a zero document length.
        assertThatThrownBy(() -> new SearchResult("doc-1", "Title", "snippet", Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultsWithSameValuesAreEqual() {
        SearchResult a = new SearchResult("doc-1", "Title", "snippet", 2.0);
        SearchResult b = new SearchResult("doc-1", "Title", "snippet", 2.0);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
