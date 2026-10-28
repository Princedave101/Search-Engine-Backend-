package com.daveai.searchengine.ranking;

import com.daveai.searchengine.indexing.DocumentStore;
import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.models.Document;
import com.daveai.searchengine.models.Posting;
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
        List<String> terms = query.terms();

        // look each term's postings up once: term -> (docId -> frequency), and its idf
        Map<String, Map<String, Integer>> frequencies = new HashMap<>();
        Map<String, Double> idfs = new HashMap<>();
        for (String term : terms) {
            Map<String, Integer> byDocument = new HashMap<>();
            for (Posting posting : invertedIndex.getPostings(term)) {
                byDocument.put(posting.documentID(), posting.frequency());
            }
            frequencies.put(term, byDocument);
            idfs.put(term, this.inverseDocumentFrequency(byDocument.size()));
        }

        //  score calculation for each document and term
        List<RankingScore> rankingScores = new ArrayList<>();
        Map<String, Document> documents = new HashMap<>();
        for(String docId : docIds) {
            // a document removed after matching is skipped rather than failing the whole search
            Optional<Document> document = documentStore.get(docId);
            if (document.isEmpty()) {
                continue;
            }
            documents.put(docId, document.get());

            double sum = 0;
            String snippetTerm = null; // first query term this document contains
            for(String term: terms){
                double tf = this.termFrequency(frequencies.get(term).getOrDefault(docId, 0));
                if (tf > 0 && snippetTerm == null) {
                    snippetTerm = term;
                }
                sum += tf * idfs.get(term);
            }
            // one score per document, normalised by its own length (at least 1, so no division by 0)
            double rankingScore = sum / Math.sqrt(Math.max(1, invertedIndex.documentLength(docId)));
            rankingScores.add(new RankingScore(docId, snippetTerm, rankingScore));
        }

        // highest score first; ties by title (documents without one last), then by id so the order never changes
        Comparator<RankingScore> order = Comparator.comparing(RankingScore::score, Comparator.reverseOrder())
                .thenComparing(score -> documents.get(score.docId()).title(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RankingScore::docId);

        // build snippets only for the results that are returned; it is the slow part
        return rankingScores.stream()
                .sorted(order)
                .limit(limit)
                .map(rankingScore -> {
                    Document document = documents.get(rankingScore.docId());
                    String snippet = SnippetBuilder.buildSnippet(document.rawText(), rankingScore.term());
                    return new SearchResult(rankingScore.docId(), document.title(), snippet, rankingScore.score());
                })
                .toList();
    }

    private double termFrequency(int frequency) {
        if (frequency == 0) {
            return 0.0; // e.g. an OR query; log(0) would be -infinity
        }

        return 1 + Math.log(frequency);
    }

    private double inverseDocumentFrequency(int docFrequency) {
        if (docFrequency == 0) {
            return 0; // unknown term; N / 0 would be infinity, and 0 * infinity is NaN
        }

        // N from the index too, not the store: the indexer updates them one after the other, and
        // df > N for a moment would make the log negative
        return Math.log(invertedIndex.documentCount()/(double) docFrequency);
    }
}
