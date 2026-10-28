package com.daveai.searchengine.services;

import com.daveai.searchengine.autocomplete.Trie;
import com.daveai.searchengine.config.SearchProperties;
import com.daveai.searchengine.extraction.DocumentExtractionException;
import com.daveai.searchengine.extraction.DocumentLoader;
import com.daveai.searchengine.extraction.ExtractorFactory;
import com.daveai.searchengine.extraction.UnsupportedDocumentTypeException;
import com.daveai.searchengine.indexing.DocumentStore;
import com.daveai.searchengine.indexing.Indexer;
import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.indexing.TextProcessor;
import com.daveai.searchengine.matching.MatchingEngine;
import com.daveai.searchengine.models.Document;
import com.daveai.searchengine.models.RefreshReport;
import com.daveai.searchengine.models.SearchResponse;
import com.daveai.searchengine.models.SearchResult;
import com.daveai.searchengine.query.QueryParser;
import com.daveai.searchengine.ranking.TfIdfRanking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Facade test for T10: index, then search, then autocomplete, through {@link SearchEngine} only.
 * <p>
 * The components are real, wired the same way as {@code ConfigurationComponent}, so these tests also
 * check that the facade passes the shared index, store and trie between the indexing and search sides.
 */
class SearchEngineTest {

    @TempDir
    Path repository;

    private final TextProcessor processor = new TextProcessor();
    private final InvertedIndex index = new InvertedIndex();
    private final DocumentStore store = new DocumentStore();
    private final Trie trie = new Trie();

    private SearchEngine engine;

    /** search.autocomplete-limit in these tests. */
    private static final int MAX_SUGGESTIONS = 5;

    @BeforeEach
    void wire() {
        ExtractorFactory extractors = ExtractorFactory.withDefaultExtractors();
        // @RequiredArgsConstructor follows the field order in SearchEngine.
        engine = new SearchEngine(
                store,
                new MatchingEngine(index),
                new TfIdfRanking(index, store),
                new QueryParser(processor),
                new Indexer(processor, store, index, trie),
                trie,
                new DocumentLoader(repository, extractors),
                extractors,
                new SearchProperties(repository, Duration.ofMinutes(30), MAX_SUGGESTIONS));
    }

    private static InputStream text(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private void indexThreeDocuments() {
        engine.indexDocument(new Document("java.txt", "Java Guide", "java spring framework"));
        engine.indexDocument(new Document("ml.txt", "Machine Learning", "machine learning with java"));
        engine.indexDocument(new Document("boot.txt", "Spring Boot", "spring boot starters"));
    }

    // ---- index, then search ----

    @Test
    void findsAnIndexedDocument() {
        indexThreeDocuments();

        SearchResponse response = engine.search("machine", 1, 10);

        assertThat(response.query()).isEqualTo("machine");
        assertThat(response.totalHits()).isEqualTo(1);
        assertThat(response.results()).extracting(SearchResult::documentID).containsExactly("ml.txt");
        assertThat(response.results().getFirst().title()).isEqualTo("Machine Learning");
        assertThat(response.results().getFirst().snippet()).containsIgnoringCase("machine");
    }

    @Test
    void searchesUseTheQueryLanguage() {
        indexThreeDocuments();

        assertThat(engine.search("java AND spring", 1, 10).results())
                .extracting(SearchResult::documentID).containsExactly("java.txt");
        assertThat(engine.search("spring -java", 1, 10).results())
                .extracting(SearchResult::documentID).containsExactly("boot.txt");
        assertThat(engine.search("\"machine learning\"", 1, 10).results())
                .extracting(SearchResult::documentID).containsExactly("ml.txt");
    }

    @Test
    void unknownWordsAndBlankQueriesFindNothing() {
        indexThreeDocuments();

        for (String query : new String[]{"kotlin", "", "   ", null, "the"}) {
            SearchResponse response = engine.search(query, 1, 10);
            assertThat(response.totalHits()).as("query %s", query).isZero();
            assertThat(response.results()).as("query %s", query).isEmpty();
        }
    }

    @Test
    void reportsHowLongTheSearchTook() {
        indexThreeDocuments();

        assertThat(engine.search("java", 1, 10).tookMillis()).isNotNegative();
    }

    // ---- paging ----

    @Test
    void pagesSplitTheResultsWithoutOverlap() {
        for (int i = 1; i <= 5; i++) {
            engine.indexDocument(new Document("doc-" + i, "Doc " + i, "java " + "filler ".repeat(i)));
        }

        SearchResponse page1 = engine.search("java", 1, 2);
        SearchResponse page2 = engine.search("java", 2, 2);
        SearchResponse page3 = engine.search("java", 3, 2);
        SearchResponse page4 = engine.search("java", 4, 2);

        assertThat(page1.results()).hasSize(2);
        assertThat(page2.results()).hasSize(2);
        assertThat(page3.results()).hasSize(1);
        assertThat(page4.results()).isEmpty();

        // totalHits counts every match, not just the page
        assertThat(page1.totalHits()).isEqualTo(5);
        assertThat(page4.totalHits()).isEqualTo(5);

        // the pages together are the full ranking, in order
        SearchResponse all = engine.search("java", 1, 5);
        assertThat(page1.results()).containsExactlyElementsOf(all.results().subList(0, 2));
        assertThat(page2.results()).containsExactlyElementsOf(all.results().subList(2, 4));
        assertThat(page3.results()).containsExactlyElementsOf(all.results().subList(4, 5));
    }

    @Test
    void rejectsPageOrPageSizeBelowOne() {
        assertThatThrownBy(() -> engine.search("java", 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.search("java", 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.search("java", -1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPageSizeAboveTheMaximum() {
        assertThat(engine.search("java", 1, SearchEngine.MAX_PAGE_SIZE).results()).isEmpty();
        assertThatThrownBy(() -> engine.search("java", 1, SearchEngine.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAPageWhoseOffsetWouldOverflow() {
        // 30,000,000 * 100 is larger than Integer.MAX_VALUE; it used to wrap round to a negative limit
        assertThatThrownBy(() -> engine.search("java", 30_000_000, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    // ---- autocomplete ----

    @Test
    void autocompleteSuggestsIndexedWords() {
        indexThreeDocuments();

        // "spring" is in two documents, "starters" in one, so "spring" comes first
        assertThat(engine.autocomplete("s", 10)).containsExactly("spring", "starters");
        assertThat(engine.autocomplete("ma", 10)).containsExactly("machine");
        assertThat(engine.autocomplete("x", 10)).isEmpty();
    }

    @Test
    void autocompleteRespectsTheLimit() {
        indexThreeDocuments();

        assertThat(engine.autocomplete("s", 1)).containsExactly("spring");
    }

    @Test
    void autocompleteNeverReturnsMoreThanTheConfiguredMaximum() {
        engine.indexDocument(new Document("d", "", "aa ab ac ad ae af ag"));

        assertThat(engine.autocomplete("a", 1000)).hasSize(MAX_SUGGESTIONS);
    }

    @Test
    void autocompleteRejectsALimitBelowOne() {
        indexThreeDocuments();

        assertThatThrownBy(() -> engine.autocomplete("s", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- remove ----

    @Test
    void removedDocumentsAreNoLongerFoundOrSuggested() {
        indexThreeDocuments();

        engine.removeDocument("ml.txt");

        assertThat(engine.search("machine", 1, 10).totalHits()).isZero();
        assertThat(engine.autocomplete("ma", 10)).isEmpty();
        assertThatThrownBy(() -> engine.getDocument("ml.txt")).isInstanceOf(NoSuchElementException.class);
        // other documents are untouched
        assertThat(engine.search("java", 1, 10).results())
                .extracting(SearchResult::documentID).containsExactlyInAnyOrder("java.txt");
    }

    @Test
    void reindexingADocumentReplacesTheOldVersion() {
        engine.indexDocument(new Document("doc-1", "Old", "kotlin"));
        engine.indexDocument(new Document("doc-1", "New", "scala"));

        assertThat(engine.search("kotlin", 1, 10).totalHits()).isZero();
        assertThat(engine.search("scala", 1, 10).totalHits()).isEqualTo(1);
        assertThat(engine.getDocument("doc-1").title()).isEqualTo("New");
    }

    // ---- getDocument ----

    @Test
    void getDocumentReturnsTheStoredDocument() {
        indexThreeDocuments();

        assertThat(engine.getDocument("java.txt"))
                .isEqualTo(new Document("java.txt", "Java Guide", "java spring framework"));
    }

    @Test
    void getDocumentThrowsForAnUnknownId() {
        assertThatThrownBy(() -> engine.getDocument("missing.txt"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("missing.txt");
    }

    // ---- indexFile ----

    @Test
    void indexFileLoadsTheFileAndMakesItSearchable() throws IOException {
        Path file = Files.createDirectories(repository.resolve("notes")).resolve("welcome.txt");
        Files.writeString(file, "welcome to the computer science department");

        Document document = engine.indexFile(file);

        // the id is the path relative to the repository, with forward slashes
        assertThat(document.id()).isEqualTo("notes/welcome.txt");
        assertThat(document.title()).isEqualTo("welcome");
        assertThat(engine.search("computer science", 1, 10).results())
                .extracting(SearchResult::documentID).containsExactly("notes/welcome.txt");
        assertThat(engine.getDocument("notes/welcome.txt")).isEqualTo(document);
    }

    @Test
    void indexFileRejectsUnsupportedTypes() throws IOException {
        Path file = Files.writeString(repository.resolve("image.png"), "not text");

        assertThatThrownBy(() -> engine.indexFile(file)).isInstanceOf(UnsupportedDocumentTypeException.class);
        assertThat(store.size()).isZero();
    }

    @Test
    void indexFileRejectsFilesOutsideTheRepository(@TempDir Path elsewhere) throws IOException {
        Path file = Files.writeString(elsewhere.resolve("outside.txt"), "outside");

        assertThatThrownBy(() -> engine.indexFile(file)).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.size()).isZero();
    }

    // ---- getDocumentFile ----

    @Test
    void getDocumentFileReturnsTheOriginalFile() throws IOException {
        Path file = Files.createDirectories(repository.resolve("notes")).resolve("welcome.txt");
        Files.writeString(file, "welcome");
        engine.indexFile(file);

        assertThat(engine.getDocumentFile("notes/welcome.txt")).isEqualTo(file.toAbsolutePath().normalize());
    }

    @Test
    void getDocumentFileThrowsForAnUnknownId() {
        assertThatThrownBy(() -> engine.getDocumentFile("missing.txt")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getDocumentFileThrowsWhenTheFileWasDeleted() throws IOException {
        Path file = Files.writeString(repository.resolve("gone.txt"), "soon gone");
        engine.indexFile(file);
        Files.delete(file);

        assertThatThrownBy(() -> engine.getDocumentFile("gone.txt")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getDocumentFileThrowsForADocumentWithNoFile() {
        // indexed straight from a Document, so nothing exists on disk
        engine.indexDocument(new Document("java.txt", "Java", "java"));

        assertThatThrownBy(() -> engine.getDocumentFile("java.txt")).isInstanceOf(NoSuchElementException.class);
    }

    // ---- uploadFile ----

    @Test
    void uploadSavesTheFileAndIndexesIt() throws IOException {
        Document document = engine.uploadFile("lecture.txt", text("distributed systems lecture"));

        assertThat(document.id()).isEqualTo("lecture.txt");
        assertThat(repository.resolve("lecture.txt")).hasContent("distributed systems lecture");
        assertThat(engine.search("distributed", 1, 10).results())
                .extracting(SearchResult::documentID).containsExactly("lecture.txt");
    }

    @Test
    void uploadDropsAnyFolderInTheFileName() throws IOException {
        Document windows = engine.uploadFile("C:\\fakepath\\a.txt", text("alpha"));
        Document traversal = engine.uploadFile("../../b.txt", text("beta"));

        assertThat(windows.id()).isEqualTo("a.txt");
        assertThat(traversal.id()).isEqualTo("b.txt");
        assertThat(repository.resolve("b.txt")).exists();
        assertThat(repository.getParent().resolve("b.txt")).doesNotExist();
    }

    @Test
    void uploadReplacesAFileWithTheSameName() throws IOException {
        engine.uploadFile("notes.txt", text("kotlin"));
        engine.uploadFile("notes.txt", text("scala"));

        assertThat(engine.search("kotlin", 1, 10).totalHits()).isZero();
        assertThat(engine.search("scala", 1, 10).totalHits()).isEqualTo(1);
    }

    @Test
    void uploadRejectsBlankAndInvalidNames() {
        for (String name : new String[]{null, "", "  ", "folder/", ".."}) {
            assertThatThrownBy(() -> engine.uploadFile(name, text("x")))
                    .as("name %s", name)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void uploadOfAnUnsupportedTypeSavesNothing() throws IOException {
        assertThatThrownBy(() -> engine.uploadFile("image.png", text("x")))
                .isInstanceOf(UnsupportedDocumentTypeException.class);

        try (var files = Files.list(repository)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void uploadOfAnUnreadableFileKeepsNothing() throws IOException {
        // a .docx must be a zip file; plain text in it can't be parsed
        assertThatThrownBy(() -> engine.uploadFile("broken.docx", text("not a word document")))
                .isInstanceOf(DocumentExtractionException.class);

        assertThat(repository.resolve("broken.docx")).doesNotExist();
        assertThat(store.size()).isZero();
    }

    // ---- reindexAll ----

    @Test
    void reindexAllIndexesEveryFileInTheRepository() throws IOException {
        Files.writeString(repository.resolve("a.txt"), "alpha");
        Files.writeString(Files.createDirectories(repository.resolve("sub/deeper")).resolve("b.txt"), "beta");
        Files.writeString(repository.resolve("ignored.png"), "not a document");

        RefreshReport report = engine.reindexAll();

        assertThat(report.indexed()).isEqualTo(2);
        assertThat(report.removed()).isZero();
        assertThat(report.failed()).isEmpty();
        assertThat(engine.search("beta", 1, 10).results())
                .extracting(SearchResult::documentID).containsExactly("sub/deeper/b.txt");
    }

    @Test
    void reindexAllPicksUpChangedFiles() throws IOException {
        Path file = Files.writeString(repository.resolve("a.txt"), "kotlin");
        engine.reindexAll();
        Files.writeString(file, "scala");

        engine.reindexAll();

        assertThat(engine.search("kotlin", 1, 10).totalHits()).isZero();
        assertThat(engine.search("scala", 1, 10).totalHits()).isEqualTo(1);
    }

    @Test
    void reindexAllDropsDocumentsWhoseFileIsGone() throws IOException {
        Path file = Files.writeString(repository.resolve("a.txt"), "alpha");
        Files.writeString(repository.resolve("b.txt"), "beta");
        engine.reindexAll();
        Files.delete(file);

        RefreshReport report = engine.reindexAll();

        assertThat(report.indexed()).isEqualTo(1);
        assertThat(report.removed()).isEqualTo(1);
        assertThat(engine.search("alpha", 1, 10).totalHits()).isZero();
        assertThat(engine.autocomplete("al", 10)).isEmpty();
    }

    @Test
    void reindexAllSkipsUnreadableFilesAndCarriesOn() throws IOException {
        Files.writeString(repository.resolve("broken.docx"), "not a word document");
        Files.writeString(repository.resolve("good.txt"), "good");

        RefreshReport report = engine.reindexAll();

        assertThat(report.indexed()).isEqualTo(1);
        assertThat(report.failed()).containsExactly("broken.docx");
        assertThat(store.contains("broken.docx")).isFalse();
        assertThat(store.contains("good.txt")).isTrue();
    }

    @Test
    void reindexAllOfAMissingRepositoryEmptiesTheIndex() throws IOException {
        Path file = Files.writeString(repository.resolve("a.txt"), "alpha");
        engine.reindexAll();
        Files.delete(file);
        Files.delete(repository);

        RefreshReport report = engine.reindexAll();

        assertThat(report.removed()).isEqualTo(1);
        assertThat(store.size()).isZero();
    }
}
