package com.daveai.searchengine.ranking;

import com.daveai.searchengine.models.SearchResult;
import com.daveai.searchengine.query.Query;

import java.util.List;
import java.util.Set;

public interface RankingAlgorithm {

    List<SearchResult> rank(Set<String> docIds, Query query, int limit);

}
