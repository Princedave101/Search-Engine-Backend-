package com.daveai.searchengine.models;

import java.util.Objects;

/**
 * A single normalised word taken from a document or a query.
 *
 * @param value      the normalised term; not blank
 * @param documentID the document the word came from
 * @param positions  the word's position in the original text, counted from 0; not negative
 */
public record Token(String value, String documentID, int positions) {

    public Token {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (positions < 0) {
            throw new IllegalArgumentException("position must not be negative, was " + positions);
        }
    }
}
