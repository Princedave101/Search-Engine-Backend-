package com.daveai.searchengine.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenTest {

    @Test
    void storesValueDocumentIdAndPosition() {
        Token token = new Token("search", "doc-1", 4);

        assertThat(token.value()).isEqualTo("search");
        assertThat(token.documentID()).isEqualTo("doc-1");
        assertThat(token.position()).isEqualTo(4);
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new Token(null, "doc-1", 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> new Token("", "doc-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Token("   ", "doc-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePosition() {
        assertThatThrownBy(() -> new Token("search", "doc-1", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsPositionZero() {
        assertThatCode(() -> new Token("search", "doc-1", 0)).doesNotThrowAnyException();
    }

    @Test
    void tokensWithSameValuesAreEqual() {
        Token a = new Token("search", "doc-1", 4);
        Token b = new Token("search", "doc-1", 4);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
