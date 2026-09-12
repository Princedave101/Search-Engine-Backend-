package com.daveai.searchengine.extraction;

import java.nio.file.Path;
import java.util.Set;

/**
 * Strategy for reading plain text out of one family of file formats.
 * <p>
 * Each implementation handles a group of related extensions (for example Word: doc and docx).
 * {@link ExtractorFactory} picks the right one for a file, so nothing else in the application
 * needs to know which format it is dealing with.
 */
public interface TextExtractor {

    /**
     * Returns the file extensions this extractor handles.
     *
     * @return lowercase extensions without the dot, e.g. {@code ["doc", "docx"]}
     */
    Set<String> supportedExtensions();

    /**
     * Reads the text and metadata title from a file.
     *
     * @param file the file to read; not null
     * @return the extracted text and title
     * @throws UnsupportedDocumentTypeException if the file's extension is not one of {@link #supportedExtensions()}
     * @throws DocumentExtractionException      if the file is missing, unreadable or corrupt
     */
    ExtractedContent extract(Path file);
}
