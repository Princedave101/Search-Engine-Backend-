package com.daveai.searchengine.models;

/**
 * One ranked result returned for a query.
 *
 * @param documentID   the matching document
 * @param title        title shown in the results list
 * @param snippet      short excerpt around a matched term
 * @param rankingScore relevance score; not negative and not NaN
 */
public record SearchResult(String documentID, String title, String snippet, double rankingScore) {

    public SearchResult {
        // "rankingScore < 0" alone would let NaN through, because every comparison with NaN is false.
        if (Double.isNaN(rankingScore) || rankingScore < 0) {
            throw new IllegalArgumentException("rankingScore must be a number >= 0, was " + rankingScore);
        }
    }
}
