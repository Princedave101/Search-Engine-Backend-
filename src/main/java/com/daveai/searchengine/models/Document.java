package com.daveai.searchengine.models;

import java.util.Objects;

/**
 * One document after text extraction, before indexing.
 *
 * @param id      unique id: the file's path relative to the documents folder; not blank
 * @param title   display title shown in search results
 * @param rawText extracted text; not null, may be empty (e.g. a scanned PDF with no text layer)
 */
public record Document(String id, String title, String rawText) {

    public Document {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(rawText, "rawText must not be null");
    }
}
