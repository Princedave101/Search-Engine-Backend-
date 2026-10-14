package com.daveai.searchengine.ranking;

import com.daveai.searchengine.indexing.DocumentStore;
import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.models.Document;
import com.daveai.searchengine.models.Posting;
import com.daveai.searchengine.models.SearchResult;
import com.daveai.searchengine.query.Query;
import com.daveai.searchengine.query.ast.TermNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TfIdfRankingTest {

    @Test
    void ranksHigherTermFrequencyFirstWhenDocumentsHaveTheSameLength() {
        InvertedIndex index = new InvertedIndex();
        DocumentStore store = new DocumentStore();
        addDocument(index, store, "high", "High", "java java java filler", "java", 0, 1, 2);
        index.addPosting("filler", new Posting("high", 1, List.of(3)));
        addDocument(index, store, "low", "Low", "java filler filler filler", "java", 0);
        index.addPosting("filler", new Posting("low", 3, List.of(1, 2, 3)));
        addDocument(index, store, "other", "Other", "filler filler filler filler", "filler", 0, 1, 2, 3);

        List<SearchResult> results = new TfIdfRanking(index, store)
                .rank(Set.of("high", "low"), termQuery("java"), 2);

        assertThat(results).extracting(SearchResult::documentID).containsExactly("high", "low");
    }

    @Test
    void sumsEachTermsTfIdfContributionBeforeNormalisingTheDocumentLength() {
        InvertedIndex index = new InvertedIndex();
        DocumentStore store = new DocumentStore();
        addDocument(index, store, "match", "Match", "rare common", "rare", 0);
        index.addPosting("common", new Posting("match", 1, List.of(1)));
        addDocument(index, store, "common-1", "Common one", "common", "common", 0);
        addDocument(index, store, "common-2", "Common two", "common", "common", 0);

        SearchResult result = new TfIdfRanking(index, store)
                .rank(Set.of("match"), new Query("rare common", new com.daveai.searchengine.query.ast.AndNode(List.of(new TermNode("rare"), new TermNode("common")))), 1)
                .getFirst();

        assertThat(result.rankingScore()).isEqualTo(Math.log(3) / Math.sqrt(2));
    }

    @Test
    void honoursTheLimitAfterRankingDocumentsRatherThanTermContributions() {
        InvertedIndex index = new InvertedIndex();
        DocumentStore store = new DocumentStore();
        addDocument(index, store, "one", "One", "java", "java", 0);
        addDocument(index, store, "two", "Two", "java", "java", 0);
        addDocument(index, store, "three", "Three", "python", "python", 0);

        List<SearchResult> results = new TfIdfRanking(index, store)
                .rank(Set.of("one", "two"), termQuery("java"), 1);

        assertThat(results).hasSize(1);
    }

    @Test
    void breaksEqualScoresByTitleInAscendingOrder() {
        InvertedIndex index = new InvertedIndex();
        DocumentStore store = new DocumentStore();
        addDocument(index, store, "zulu", "Zulu", "java", "java", 0);
        addDocument(index, store, "alpha", "Alpha", "java", "java", 0);
        addDocument(index, store, "other", "Other", "python", "python", 0);

        List<SearchResult> results = new TfIdfRanking(index, store)
                .rank(Set.of("zulu", "alpha"), termQuery("java"), 2);

        assertThat(results).extracting(SearchResult::title).containsExactly("Alpha", "Zulu");
    }

    private static Query termQuery(String term) {
        return new Query(term, new TermNode(term));
    }

    private static void addDocument(InvertedIndex index, DocumentStore store, String id, String title,
                                    String text, String term, int... positions) {
        store.save(new Document(id, title, text));
        index.addPosting(term, new Posting(id, positions.length,
                java.util.Arrays.stream(positions).boxed().toList()));
    }
}
