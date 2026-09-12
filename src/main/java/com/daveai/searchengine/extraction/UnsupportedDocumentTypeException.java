package com.daveai.searchengine.extraction;

import java.nio.file.Path;

/** Thrown when a file's extension is not one of the supported document types. */
public class UnsupportedDocumentTypeException extends RuntimeException {

    /**
     * @param file the file whose type is not supported
     */
    public UnsupportedDocumentTypeException(Path file) {
        super("Unsupported document type: " + file.getFileName());
    }
}
