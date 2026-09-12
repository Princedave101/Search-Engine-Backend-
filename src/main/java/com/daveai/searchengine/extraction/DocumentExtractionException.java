package com.daveai.searchengine.extraction;

import java.nio.file.Path;

/** Thrown when a supported file cannot be read: it is missing, unreadable or corrupt. */
public class DocumentExtractionException extends RuntimeException {

    /**
     * @param file  the file that could not be read
     * @param cause the underlying error from the file system or Tika
     */
    public DocumentExtractionException(Path file, Throwable cause) {
        super("Could not extract text from " + file, cause);
    }
}
