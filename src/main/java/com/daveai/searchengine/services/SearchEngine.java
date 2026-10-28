package com.daveai.searchengine.services;

import com.daveai.searchengine.autocomplete.Trie;
import com.daveai.searchengine.config.SearchProperties;
import com.daveai.searchengine.extraction.DocumentExtractionException;
import com.daveai.searchengine.extraction.DocumentLoader;
import com.daveai.searchengine.extraction.ExtractorFactory;
import com.daveai.searchengine.extraction.UnsupportedDocumentTypeException;
import com.daveai.searchengine.indexing.DocumentStore;
import com.daveai.searchengine.indexing.Indexer;
import com.daveai.searchengine.matching.MatchingEngine;
import com.daveai.searchengine.models.Document;
import com.daveai.searchengine.models.RefreshReport;
import com.daveai.searchengine.models.SearchResponse;
import com.daveai.searchengine.models.SearchResult;
import com.daveai.searchengine.query.Query;
import com.daveai.searchengine.query.QueryParser;
import com.daveai.searchengine.ranking.RankingAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchEngine {

    /** The most results one page may hold, so a client can't ask for the whole index at once. */
    public static final int MAX_PAGE_SIZE = 100;

    private final DocumentStore documentStore;
    private final MatchingEngine matchingEngine;
    private final RankingAlgorithm rankingAlgorithm;
    private final QueryParser queryParser;
    private final Indexer indexer;
    private final Trie trie;
    private final DocumentLoader documentLoader;
    private final ExtractorFactory extractorFactory;
    private final SearchProperties properties;

    /** The report of the most recent rescan; null until the first one. Read by the health check. */
    private volatile RefreshReport lastRefresh;


    public void indexDocument(Document document) {
        indexer.indexDocument(document);
    }

    public Document indexFile(Path path){
        // the loader throws for files outside the repository, unsupported types and unreadable files
        Document document = documentLoader.load(path);
        indexer.indexDocument(document);
        return document;
    }

    public void removeDocument( String DocId){
        indexer.removeDocument(DocId);
    }

    public SearchResponse search(String query, int page, int pageSize){
        // pages start at 1
        if (page < 1 || pageSize < 1) {
            throw new IllegalArgumentException("page and pageSize must be at least 1, were " + page + " and " + pageSize);
        }
        if (pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be at most " + MAX_PAGE_SIZE + ", was " + pageSize);
        }
        // page * pageSize is passed to rank as an int, so it must not overflow
        if ((long) page * pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page " + page + " is too large");
        }
        long start = System.nanoTime();
        Query queryAst = queryParser.parse(query);
        Set<String> documentIDs = matchingEngine.retrieve(queryAst);
        // rank returns the top page * pageSize results; keep only the last pageSize of them
        List<SearchResult> results = rankingAlgorithm.rank(documentIDs, queryAst, page *  pageSize).stream()
                .skip((long) (page - 1) * pageSize)
                .toList();
        long tookMillis = (System.nanoTime() - start) / 1_000_000;
        return new SearchResponse(query, documentIDs.size(), tookMillis, results);
    }

    public List<String> autocomplete(String input, int limit){
        // search.autocomplete-limit is the most suggestions ever returned
        return trie.getSuggestions(input, Math.min(limit, properties.autocompleteLimit()));
    }


    public Document getDocument(String docId){
        Objects.requireNonNull(docId, "docId must not be null");
        return documentStore.get(docId).orElseThrow(() -> new NoSuchElementException("no document with id " + docId));
    }

    /**
     * Returns the original file of an indexed document, for viewing or download.
     *
     * @throws NoSuchElementException if no document has this id, or its file is gone
     */
    public Path getDocumentFile(String docId) {
        Objects.requireNonNull(docId, "docId must not be null");
        if (!documentStore.contains(docId)) {
            throw new NoSuchElementException("no document with id " + docId);
        }
        Path root = repositoryRoot();
        Path file = root.resolve(docId).normalize();
        // only indexed ids get here, but check anyway that the id can't point outside the repository
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new NoSuchElementException("the file of document " + docId + " no longer exists");
        }
        return file;
    }

    /**
     * Saves an uploaded file in the top folder of the repository and indexes it straight away.
     * A file with the same name is replaced. If the file can't be read, it is deleted again.
     *
     * @param filename the name the client sent; any folder part is dropped
     * @param content  the file's bytes
     * @return the indexed document
     * @throws IllegalArgumentException         if the name is blank or not a valid file name
     * @throws UnsupportedDocumentTypeException if the file type is not supported (nothing is saved)
     * @throws DocumentExtractionException      if the file can't be read (nothing is kept)
     */
    public synchronized Document uploadFile(String filename, InputStream content) throws IOException {
        Objects.requireNonNull(content, "content must not be null");
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("the file name must not be blank");
        }
        // some browsers send the full client path, e.g. C:\fakepath\report.pdf; keep only the name
        String name = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1).strip();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("invalid file name: " + filename);
        }

        Path root = repositoryRoot();
        Path target = root.resolve(name); // InvalidPathException is an IllegalArgumentException, so a bad name is a 400
        // check the type before writing, so an unsupported file is never saved
        if (!extractorFactory.isSupported(target)) {
            throw new UnsupportedDocumentTypeException(target);
        }

        Files.createDirectories(root);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        try {
            return indexFile(target);
        } catch (RuntimeException e) {
            // don't keep a file that can't be indexed, nor the old version it replaced
            Files.deleteIfExists(target);
            indexer.removeDocument(idOf(root, target));
            throw e;
        }
    }

    /**
     * Rescans the whole repository: indexes every supported file again and drops documents whose
     * file is gone. The repository folder is the source of truth, so a document indexed with
     * {@link #indexDocument(Document)} that has no file is dropped too.
     * <p>
     * A file that can't be read is logged, left out of the index and listed in the report;
     * the rest of the scan carries on.
     */
    public synchronized RefreshReport reindexAll() {
        long start = System.nanoTime();
        Path root = repositoryRoot();

        List<Path> files = List.of();
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(Files::isRegularFile).filter(extractorFactory::isSupported).toList();
            } catch (IOException e) {
                throw new UncheckedIOException("could not scan the document repository", e);
            }
        }

        Set<String> seen = new HashSet<>();
        List<String> failed = new ArrayList<>();
        int indexed = 0;
        for (Path file : files) {
            String id = idOf(root, file);
            seen.add(id);
            try {
                indexFile(file);
                indexed++;
            } catch (DocumentExtractionException e) {
                log.warn("Skipping {}: {}", id, e.getMessage());
                indexer.removeDocument(id); // don't keep serving an older version of an unreadable file
                failed.add(id);
            }
        }

        int removed = 0;
        for (Document document : documentStore.getDocs()) {
            if (!seen.contains(document.id())) {
                indexer.removeDocument(document.id());
                removed++;
            }
        }

        long tookMillis = (System.nanoTime() - start) / 1_000_000;
        lastRefresh = new RefreshReport(indexed, removed, failed, tookMillis, Instant.now());
        return lastRefresh;
    }

    /**
     * @return the report of the most recent {@link #reindexAll()}, or empty if none has run yet
     */
    public Optional<RefreshReport> lastRefresh() {
        return Optional.ofNullable(lastRefresh);
    }

    private Path repositoryRoot() {
        return properties.repositoryPath().toAbsolutePath().normalize();
    }

    /** The same id DocumentLoader gives: the path relative to the repository, with forward slashes. */
    private static String idOf(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
