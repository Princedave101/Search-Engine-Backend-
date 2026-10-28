package com.daveai.searchengine.models;

import java.util.List;
import java.util.Objects;

/**
 * What the facade returns for one page of a search.
 *
 * @param query      the text the user typed
 * @param totalHits  how many documents matched in total, across all pages; not negative
 * @param tookMillis how long the search took, in milliseconds; not negative
 * @param results    the ranked results for the requested page; not null, cannot be modified
 */
public record SearchResponse(String query, int totalHits, long tookMillis, List<SearchResult> results) {

    public SearchResponse {
        if (totalHits < 0) {
            throw new IllegalArgumentException("totalHits must be >= 0, was " + totalHits);
        }
        if (tookMillis < 0) {
            throw new IllegalArgumentException("tookMillis must be >= 0, was " + tookMillis);
        }
        results = List.copyOf(Objects.requireNonNull(results, "results must not be null"));
    }
}
