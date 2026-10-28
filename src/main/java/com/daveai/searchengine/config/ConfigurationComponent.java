package com.daveai.searchengine.config;

import com.daveai.searchengine.autocomplete.Trie;
import com.daveai.searchengine.extraction.DocumentLoader;
import com.daveai.searchengine.extraction.ExtractorFactory;
import com.daveai.searchengine.indexing.DocumentStore;
import com.daveai.searchengine.indexing.Indexer;
import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.indexing.TextProcessor;
import com.daveai.searchengine.matching.MatchingEngine;
import com.daveai.searchengine.query.QueryParser;
import com.daveai.searchengine.ranking.RankingAlgorithm;
import com.daveai.searchengine.ranking.TfIdfRanking;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration()
public class ConfigurationComponent {


    @Bean
    public Indexer indexer(TextProcessor textProcessor, DocumentStore documentStore, InvertedIndex invertedIndex, Trie trie) {
        return new Indexer(textProcessor, documentStore, invertedIndex,  trie);
    }

    @Bean
    public MatchingEngine matchingEngine(InvertedIndex invertedIndex) {
        return new MatchingEngine(invertedIndex);
    }

    @Bean
    public RankingAlgorithm rankingAlgorithm(InvertedIndex invertedIndex, DocumentStore  documentStore) {
        return new TfIdfRanking(invertedIndex, documentStore);
    }

    @Bean
    public DocumentStore documentStore() {
        return new DocumentStore();
    }

    @Bean
    public TextProcessor textProcessor() {
        return new TextProcessor();
    }

    @Bean
    public QueryParser queryParser(TextProcessor textProcessor) {
        return new QueryParser(textProcessor);
    }

    @Bean
    public Trie trie() {
        return new Trie();
    }

    @Bean
    public InvertedIndex invertedIndex(){
        return new InvertedIndex();
    }

    @Bean
    public ExtractorFactory extractorFactory() {
        return ExtractorFactory.withDefaultExtractors();
    }

    @Bean
    public DocumentLoader documentLoader(SearchProperties searchProperties, ExtractorFactory extractorFactory) {
        return new DocumentLoader(searchProperties.repositoryPath(), extractorFactory);
    }





}
