package com.daveai.searchengine.models;

/**
 * One ranked result returned for a query.
 *
 * @param documentID the matching document
 * @param title      title shown in the results list
 * @param snippet    short excerpt around a matched term
 * @param score      relevance score; not negative and not NaN
 */
public record SearchResult(String documentID, String title, String snippet, double score) {

    public SearchResult {
        // "score < 0" alone would let NaN through, because every comparison with NaN is false.
        if (Double.isNaN(score) || score < 0) {
            throw new IllegalArgumentException("score must be a number >= 0, was " + score);
        }
    }
}
