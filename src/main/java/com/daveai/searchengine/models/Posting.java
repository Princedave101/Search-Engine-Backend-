package com.daveai.searchengine.models;

import java.util.List;
import java.util.Objects;

/**
 * One entry in a term's postings list: a document containing the term, and where.
 *
 * @param documentID the document containing the term; not blank
 * @param frequency  how many times the term occurs in the document; at least 1 and equal to positions.size()
 * @param positions  word positions of each occurrence; each >= 0, strictly increasing, and unmodifiable
 */
public record Posting(String documentID, int frequency, List<Integer> positions) {

    public Posting {
        Objects.requireNonNull(documentID, "documentID must not be null");
        if (documentID.isBlank()) {
            throw new IllegalArgumentException("documentID must not be blank");
        }
        Objects.requireNonNull(positions, "positions must not be null");
        if (frequency < 1) {
            throw new IllegalArgumentException("frequency must be at least 1, was " + frequency);
        }
        if (frequency != positions.size()) {
            throw new IllegalArgumentException(
                    "frequency (" + frequency + ") must equal the number of positions (" + positions.size() + ")");
        }

        // Copy first so the checks below run on the same list the record keeps.
        // List.copyOf also rejects null elements and returns an unmodifiable list.
        positions = List.copyOf(positions);

        int previous = -1;
        for (int position : positions) {
            if (position <= previous) {
                throw new IllegalArgumentException(
                        "positions must be >= 0 and strictly increasing, was " + positions);
            }
            previous = position;
        }
    }
}
