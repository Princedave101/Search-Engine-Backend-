package com.daveai.searchengine.extraction;

import java.util.Objects;

/**
 * What a {@link TextExtractor} reads from a file.
 *
 * @param text  the file's plain text; not null, may be empty
 * @param title the title stored in the file's metadata (e.g. an HTML {@code <title>} or a Word
 *              document's Title property); null if the file has none
 */
public record ExtractedContent(String text, String title) {

    public ExtractedContent {
        Objects.requireNonNull(text, "text must not be null");
    }
}
