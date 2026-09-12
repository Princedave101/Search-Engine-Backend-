package com.daveai.searchengine.extraction;

import com.daveai.searchengine.autocomplete.Trie;
import com.daveai.searchengine.indexing.DocumentStore;
import com.daveai.searchengine.indexing.Indexer;
import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.indexing.TextProcessor;
import com.daveai.searchengine.models.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static com.daveai.searchengine.extraction.TextExtractorsTest.SAMPLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentLoaderTest {

    @TempDir
    Path repository;

    private DocumentLoader loader() {
        return new DocumentLoader(repository, ExtractorFactory.withDefaultExtractors());
    }

    /** Copies a sample into the temporary repository under a new relative path. */
    private Path copySample(String sampleName, String relativeTarget) throws IOException {
        Path target = repository.resolve(relativeTarget);
        Files.createDirectories(target.getParent());
        return Files.copy(SAMPLES.resolve(sampleName), target);
    }

    @Test
    void idIsThePathRelativeToTheRepositoryWithForwardSlashes() throws IOException {
        Path file = copySample("sample.docx", "reports/2024/annual.docx");

        Document doc = loader().load(file);

        assertThat(doc.id()).isEqualTo("reports/2024/annual.docx");
    }

    @Test
    void titleComesFromTheFileMetadataWhenPresent() throws IOException {
        Path file = copySample("sample.docx", "annual.docx");

        assertThat(loader().load(file).title()).isEqualTo("Annual Research Report");
    }

    @Test
    void titleFallsBackToTheFileNameWithoutExtension() throws IOException {
        Path file = copySample("sample.txt", "welcome-note.txt");

        assertThat(loader().load(file).title()).isEqualTo("welcome-note");
    }

    @Test
    void textIsTheExtractedContent() throws IOException {
        Path file = copySample("sample.html", "pages/about.html");

        Document doc = loader().load(file);

        assertThat(doc.title()).isEqualTo("HTML Sample Title");
        assertThat(doc.rawText()).contains("This is the HTML sample about the inverted index.");
    }

    @Test
    void rejectsAFileOutsideTheRepository(@TempDir Path elsewhere) throws IOException {
        Path outside = Files.copy(SAMPLES.resolve("sample.txt"), elsewhere.resolve("outside.txt"));

        assertThatThrownBy(() -> loader().load(outside)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnsupportedFileType() throws IOException {
        Path image = Files.writeString(repository.resolve("photo.png"), "not really an image");

        assertThatThrownBy(() -> loader().load(image)).isInstanceOf(UnsupportedDocumentTypeException.class);
    }

    @Test
    void everySampleFormatCanBeLoadedAndIndexed() throws IOException {
        // End to end: file -> DocumentLoader -> Indexer -> searchable term.
        InvertedIndex index = new InvertedIndex();
        Indexer indexer = new Indexer(new TextProcessor(), new DocumentStore(), index, new Trie());
        DocumentLoader loader = loader();

        try (Stream<Path> samples = Files.list(SAMPLES)) {
            for (Path sample : samples.toList()) {
                indexer.indexDocument(loader.load(copySample(sample.getFileName().toString(), sample.getFileName().toString())));
            }
        }

        // All 12 formats contain the word "inverted".
        assertThat(index.documentFrequency("inverted")).isEqualTo(12);
    }
}
