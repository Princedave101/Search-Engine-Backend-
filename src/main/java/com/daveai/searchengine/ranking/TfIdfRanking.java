package com.daveai.searchengine.ranking;

import com.daveai.searchengine.indexing.DocumentStore;
import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.models.SearchResult;
import com.daveai.searchengine.query.Query;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class TfIdfRanking implements RankingAlgorithm {

    private final InvertedIndex invertedIndex;
    private final DocumentStore documentStore;

    @Override
    public List<SearchResult> rank(Set<String> docIds, Query query, int limit) {
        //  score calculation for each document and term
        List<RankingScore> rankingScores = new ArrayList<>();
        for(String docId : docIds) {
            double sum = 0;
            String snippetTerm = null; // first query term this document contains
            for(String term: query.terms()){
                double tf = this.termFrequency(docId, term);
                if (tf > 0 && snippetTerm == null) {
                    snippetTerm = term;
                }
                double idf = this.inverseDocumentFrequency(term);
                sum += tf * idf;
            }
            // one score per document, normalised by its own length (at least 1, so no division by 0)
            double rankingScore = sum / Math.sqrt(Math.max(1, invertedIndex.documentLength(docId)));
            rankingScores.add(new RankingScore(docId, snippetTerm, rankingScore));
        }

        // sort by ranking score. if two score have the same ranking sort by title
        rankingScores = rankingScores.stream().sorted((score1, score2) -> {
            int value = score2.score().compareTo(score1.score()); // highest first
            if (value == 0) {
                value = documentStore.get(score1.docId()).orElseThrow().title().compareTo(documentStore.get(score2.docId()).orElseThrow().title());
            }

            return value;
        }).limit(limit).toList(); // a List keeps the sorted order; a Set would lose it

        // returning search results

        return rankingScores.stream().map(
                rankingScore -> {
                    // extracted documents
                    String content = documentStore.get(rankingScore.docId()).orElseThrow().rawText();

                    String title = documentStore.get(rankingScore.docId()).orElseThrow().title();

                    // search result for the higher ranked scores by term
                    String snippet = SnippetBuilder.buildSnippet(content, rankingScore.term());

                    return new SearchResult(rankingScore.docId(), title, snippet, rankingScore.score());
                }
        ).toList();
    }

    private Double termFrequency(String docId, String term) {
        // the posting's frequency is how often the term occurs; count() would only say whether it occurs
        int frequency = invertedIndex.getPostings(term).stream().filter(posting -> Objects.equals(posting.documentID(), docId)).mapToInt(posting -> posting.frequency()).sum();
        if (frequency == 0) {
            return 0.0; // e.g. an OR query; log(0) would be -infinity
        }

        return 1 + Math.log(frequency);
    }

    private double inverseDocumentFrequency(String term) {
        long docFrequency = invertedIndex.getPostings(term).size();
        if (docFrequency == 0) {
            return 0; // unknown term; N / 0 would be infinity, and 0 * infinity is NaN
        }

        return Math.log(documentStore.size()/(double) docFrequency);
    }
}
