package com.daveai.searchengine.health;

import com.daveai.searchengine.indexing.DocumentStore;
import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.models.RefreshReport;
import com.daveai.searchengine.services.SearchEngine;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Health of the in-memory index, shown as {@code searchIndex} in /actuator/health.
 * <ul>
 *   <li><b>DOWN</b> if the inverted index holds a document the store doesn't: a search could then
 *       return a result with no title or text.</li>
 *   <li><b>UP</b> otherwise, with how many documents are indexed and what the last rescan did.
 *       An empty index is still UP: it is valid, just not useful yet.</li>
 * </ul>
 */
@Component("searchIndex")
public class SearchIndexHealthIndicator implements HealthIndicator {

    private final DocumentStore documentStore;
    private final InvertedIndex invertedIndex;
    private final SearchEngine searchEngine;

    public SearchIndexHealthIndicator(DocumentStore documentStore, InvertedIndex invertedIndex, SearchEngine searchEngine) {
        this.documentStore = documentStore;
        this.invertedIndex = invertedIndex;
        this.searchEngine = searchEngine;
    }

    @Override
    public Health health() {
        // documentsWithTerms can be lower than documentsStored: a document with no words (e.g. a scanned PDF) has no postings
        Health.Builder builder = Health.up()
                .withDetail("documentsStored", documentStore.size())
                .withDetail("documentsWithTerms", invertedIndex.documentCount())
                .withDetail("lastRefresh", lastRefresh());

        long missingFromStore = invertedIndex.allDocumentIds().stream()
                .filter(id -> !documentStore.contains(id))
                .count();
        if (missingFromStore > 0) {
            builder.down().withDetail("error", missingFromStore + " indexed documents are missing from the document store");
        }
        return builder.build();
    }

    private Object lastRefresh() {
        Optional<RefreshReport> report = searchEngine.lastRefresh();
        if (report.isEmpty()) {
            return "never";
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("finishedAt", report.get().finishedAt().toString());
        details.put("indexed", report.get().indexed());
        details.put("removed", report.get().removed());
        details.put("failed", report.get().failed());
        details.put("tookMillis", report.get().tookMillis());
        return details;
    }
}
