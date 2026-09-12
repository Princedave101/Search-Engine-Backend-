package com.daveai.searchengine.extraction;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Picks the {@link TextExtractor} for a file based on its extension (Factory pattern).
 * <p>
 * Keeps the "which format is this?" decision in one place, so the loader and indexer never
 * contain format-specific code. Supporting a new format means adding one extractor to the list.
 */
public class ExtractorFactory {

    private final Map<String, TextExtractor> extractorsByExtension;

    /**
     * @param extractors the extractors to choose from; no two may handle the same extension
     * @throws IllegalArgumentException if two extractors claim the same extension
     */
    public ExtractorFactory(List<TextExtractor> extractors) {
        Map<String, TextExtractor> byExtension = new HashMap<>();
        for (TextExtractor extractor : extractors) {
            for (String extension : extractor.supportedExtensions()) {
                if (byExtension.putIfAbsent(extension, extractor) != null) {
                    throw new IllegalArgumentException("More than one extractor handles ." + extension);
                }
            }
        }
        this.extractorsByExtension = Map.copyOf(byExtension);
    }

    /**
     * Creates a factory with an extractor for every format required by the brief:
     * txt, pdf, doc, docx, ppt, pptx, pps, ppsx, xls, xlsx, html, htm and xml.
     *
     * @return a factory with all built-in extractors
     */
    public static ExtractorFactory withDefaultExtractors() {
        return new ExtractorFactory(List.of(
                new PlainTextExtractor(),
                new PdfExtractor(),
                new WordExtractor(),
                new PowerPointExtractor(),
                new ExcelExtractor(),
                new HtmlExtractor(),
                new XmlExtractor()));
    }

    /**
     * Returns the extractor for a file. The extension is matched case-insensitively.
     *
     * @param file the file to read; not null
     * @return the extractor that handles the file's extension
     * @throws UnsupportedDocumentTypeException if no extractor handles the extension
     */
    public TextExtractor forFile(Path file) {
        Objects.requireNonNull(file, "file must not be null");
        TextExtractor extractor = extractorsByExtension.get(FileExtension.of(file));
        if (extractor == null) {
            throw new UnsupportedDocumentTypeException(file);
        }
        return extractor;
    }

    /**
     * Checks whether a file can be read, so callers such as the repository scanner can skip other files.
     *
     * @param file the file; not null
     * @return true if an extractor handles the file's extension
     */
    public boolean isSupported(Path file) {
        Objects.requireNonNull(file, "file must not be null");
        return extractorsByExtension.containsKey(FileExtension.of(file));
    }

    /**
     * Returns every supported extension.
     *
     * @return lowercase extensions without the dot
     */
    public Set<String> supportedExtensions() {
        return extractorsByExtension.keySet();
    }
}
