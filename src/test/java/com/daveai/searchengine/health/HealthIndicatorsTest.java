package com.daveai.searchengine.health;

import com.daveai.searchengine.autocomplete.Trie;
import com.daveai.searchengine.config.SearchProperties;
import com.daveai.searchengine.extraction.DocumentLoader;
import com.daveai.searchengine.extraction.ExtractorFactory;
import com.daveai.searchengine.indexing.DocumentStore;
import com.daveai.searchengine.indexing.Indexer;
import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.indexing.TextProcessor;
import com.daveai.searchengine.matching.MatchingEngine;
import com.daveai.searchengine.models.Posting;
import com.daveai.searchengine.query.QueryParser;
import com.daveai.searchengine.ranking.TfIdfRanking;
import com.daveai.searchengine.services.SearchEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthIndicatorsTest {

    @TempDir
    Path repository;

    private static SearchProperties propertiesFor(Path folder) {
        return new SearchProperties(folder, Duration.ofMinutes(30), 10);
    }

    // ---- documentRepository ----

    @Test
    void repositoryIsUpAndWritableWhenTheFolderExists() {
        Health health = new DocumentRepositoryHealthIndicator(propertiesFor(repository)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("writable", true).containsKey("path");
    }

    @Test
    void repositoryIsDownWhenTheFolderIsMissing() {
        Health health = new DocumentRepositoryHealthIndicator(propertiesFor(repository.resolve("missing"))).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "folder does not exist");
    }

    @Test
    void repositoryIsDownWhenThePathIsAFile() throws IOException {
        Path file = Files.writeString(repository.resolve("not-a-folder.txt"), "x");

        Health health = new DocumentRepositoryHealthIndicator(propertiesFor(file)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "not a folder");
    }

    @Test
    void repositoryShowsThePathAsConfiguredNotTheFullServerPath() {
        Health health = new DocumentRepositoryHealthIndicator(propertiesFor(Path.of("./documents"))).health();

        assertThat(health.getDetails()).containsEntry("path", "./documents");
    }

    // ---- searchIndex ----

    private final TextProcessor processor = new TextProcessor();
    private final InvertedIndex index = new InvertedIndex();
    private final DocumentStore store = new DocumentStore();

    private SearchEngine engine() {
        Trie trie = new Trie();
        ExtractorFactory extractors = ExtractorFactory.withDefaultExtractors();
        return new SearchEngine(store, new MatchingEngine(index), new TfIdfRanking(index, store),
                new QueryParser(processor), new Indexer(processor, store, index, trie), trie,
                new DocumentLoader(repository, extractors), extractors, propertiesFor(repository));
    }

    @Test
    void emptyIndexIsUpAndNeverRefreshed() {
        Health health = new SearchIndexHealthIndicator(store, index, engine()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("documentsStored", 0)
                .containsEntry("documentsWithTerms", 0)
                .containsEntry("lastRefresh", "never");
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexReportsItsSizeAndTheLastRefresh() throws IOException {
        Files.writeString(repository.resolve("a.txt"), "alpha");
        Files.writeString(repository.resolve("b.txt"), "beta");
        Files.writeString(repository.resolve("broken.docx"), "not a word document");
        SearchEngine engine = engine();
        engine.reindexAll();

        Health health = new SearchIndexHealthIndicator(store, index, engine).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("documentsStored", 2).containsEntry("documentsWithTerms", 2);
        Map<String, Object> lastRefresh = (Map<String, Object>) health.getDetails().get("lastRefresh");
        assertThat(lastRefresh)
                .containsEntry("indexed", 2)
                .containsEntry("removed", 0)
                .containsEntry("failed", List.of("broken.docx"))
                .containsKeys("finishedAt", "tookMillis");
    }

    @Test
    void indexIsDownWhenTheIndexAndStoreDisagree() {
        // a posting for a document the store has never seen
        index.addPosting("ghost", new Posting("ghost.txt", 1, List.of(0)));

        Health health = new SearchIndexHealthIndicator(store, index, engine()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "1 indexed documents are missing from the document store");
    }
}
