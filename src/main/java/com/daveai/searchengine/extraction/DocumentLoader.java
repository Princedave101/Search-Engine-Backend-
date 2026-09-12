package com.daveai.searchengine.extraction;

import com.daveai.searchengine.models.Document;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Turns a file in the document repository into a {@link Document} ready for the Indexer.
 * <pre>
 * file ──► ExtractorFactory.forFile ──► TextExtractor.extract ──► Document(id, title, text)
 * </pre>
 * <ul>
 *   <li><b>id</b>: the file's path relative to the repository folder, with forward slashes
 *       (e.g. {@code reports/annual.pdf}). The original file can always be found again from it.</li>
 *   <li><b>title</b>: the title from the file's metadata; if it has none, the file name without
 *       its extension (e.g. {@code annual}).</li>
 *   <li><b>text</b>: the extracted plain text.</li>
 * </ul>
 */
public class DocumentLoader {

    private final Path repositoryRoot;
    private final ExtractorFactory extractors;

    /**
     * @param repositoryRoot the folder that document ids are relative to; not null
     * @param extractors     chooses the extractor for each file; not null
     */
    public DocumentLoader(Path repositoryRoot, ExtractorFactory extractors) {
        this.repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot must not be null")
                .toAbsolutePath().normalize();
        this.extractors = Objects.requireNonNull(extractors, "extractors must not be null");
    }

    /**
     * Reads a file and builds its Document.
     *
     * @param file a file inside the repository folder; not null
     * @return the document, with id, title and extracted text
     * @throws IllegalArgumentException         if the file is outside the repository folder
     * @throws UnsupportedDocumentTypeException if the file type is not supported
     * @throws DocumentExtractionException      if the file cannot be read
     */
    public Document load(Path file) {
        Objects.requireNonNull(file, "file must not be null");
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException(file + " is not inside the document repository " + repositoryRoot);
        }

        ExtractedContent content = extractors.forFile(absolute).extract(absolute);

        // Forward slashes, so ids look the same on Windows and Linux and can be used in URLs.
        String id = repositoryRoot.relativize(absolute).toString().replace('\\', '/');
        String title = (content.title() == null || content.title().isBlank())
                ? titleFromFileName(absolute)
                : content.title().strip();
        return new Document(id, title, content.text());
    }

    /** "annual-report.pdf" gives "annual-report"; a name with no extension is used as it is. */
    private static String titleFromFileName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }
}
