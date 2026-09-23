package com.daveai.searchengine.matching;

import com.daveai.searchengine.indexing.InvertedIndex;
import com.daveai.searchengine.models.Posting;
import com.daveai.searchengine.models.Token;
import com.daveai.searchengine.query.Query;
import com.daveai.searchengine.query.ast.*;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Finds the documents that satisfy a parsed query (T8).
 * <p>
 * This is the Interpreter pattern: each node of the query tree knows how to turn itself into a
 * set of document ids, and a parent combines what its children produced. A term looks itself up
 * in the index; AND intersects; OR unions; NOT subtracts from every indexed document; a phrase
 * checks that the words sit at the right distance from each other.
 * <p>
 * Matching only answers <em>which</em> documents qualify. Putting them in a useful order is the
 * ranker's job (T9), so the result is an unordered set.
 */
@RequiredArgsConstructor
public class MatchingEngine {

    private final InvertedIndex invertedIndex;

    /**
     * Returns the ids of the documents satisfying the query.
     *
     * @param query the parsed query; not null
     * @return the matching document ids, in no particular order; empty if nothing matches or if
     *         the query has nothing searchable in it
     * @throws NullPointerException if {@code query} is null
     */
    public Set<String> retrieve(Query query) {
        Objects.requireNonNull(query, "query must not be null");
        return evaluate(query.root());
    }

    /**
     * Turns one node into the set of documents matching it, calling itself for the node's
     * children. Every branch returns a <b>new</b> set, because a parent combines its children's
     * results in place.
     * <p>
     * {@code QueryNode} is sealed, so the compiler checks that every node type is handled here;
     * adding a node type without a case is a compile error rather than a surprise at runtime.
     */
    private Set<String> evaluate(QueryNode node) {
        return switch (node) {
            // A blank query, or one that was nothing but stop words, matches nothing.
            case null -> new HashSet<>();
            case TermNode term -> documentsContaining(term.term());
            case AndNode and -> intersection(and.children());
            case OrNode or -> union(or.children());
            case NotNode not -> everythingExcept(evaluate(not.child()));
            case PhraseNode phrase -> documentsWithPhrase(phrase.words());
        };
    }

    /** The documents a term appears in: one id per posting. */
    private Set<String> documentsContaining(String term) {
        Set<String> documentIDs = new HashSet<>();
        for (Posting posting : invertedIndex.getPostings(term)) {
            documentIDs.add(posting.documentID());
        }
        return documentIDs;
    }

    /**
     * The documents matching every child.
     * <p>
     * The children are intersected smallest first. The running set can only shrink, so starting
     * from the smallest does the fewest lookups: {@code rare AND common} costs the size of
     * {@code rare}, not the size of {@code common}.
     */
    private Set<String> intersection(List<QueryNode> children) {
        List<Set<String>> results = new ArrayList<>(children.size());
        for (QueryNode child : children) {
            Set<String> documentIDs = evaluate(child);
            if (documentIDs.isEmpty()) {
                return new HashSet<>();     // nothing survives an intersection with nothing
            }
            results.add(documentIDs);
        }
        if (results.isEmpty()) {
            return new HashSet<>();         // the parser never builds a childless AND
        }

        results.sort(Comparator.comparingInt(Set::size));
        Set<String> matches = new HashSet<>(results.getFirst());
        for (Set<String> documentIDs : results.subList(1, results.size())) {
            matches.retainAll(documentIDs);
            if (matches.isEmpty()) {
                break;
            }
        }
        return matches;
    }

    /** The documents matching at least one child. */
    private Set<String> union(List<QueryNode> children) {
        Set<String> matches = new HashSet<>();
        for (QueryNode child : children) {
            matches.addAll(evaluate(child));
        }
        return matches;
    }

    /**
     * Every indexed document that is not in {@code excluded}.
     * <p>
     * {@link InvertedIndex#allDocumentIds()} hands back a read-only view of the index's own key
     * set, so it is copied before anything is removed: subtracting from it directly would throw,
     * and it changes underneath as documents are indexed.
     */
    private Set<String> everythingExcept(Set<String> excluded) {
        Set<String> matches = new HashSet<>(invertedIndex.allDocumentIds());
        matches.removeAll(excluded);
        return matches;
    }

    /**
     * The documents containing the words in the given order and at the given distances.
     * <p>
     * The distances come from the words' own positions, not from their place in the list, so the
     * stop words the {@code TextProcessor} removed still count. {@code "state of the art"} is
     * stored as {@code state@0, art@3}, and matches a document with {@code state@10, art@13} but
     * not one with {@code state@10, art@11}.
     */
    private Set<String> documentsWithPhrase(List<Token> words) {
        Set<String> matches = new HashSet<>();
        if (words.isEmpty()) {
            return matches;
        }

        // Where each word of the phrase occurs, per document.
        List<Map<String, Set<Integer>>> occurrences = new ArrayList<>(words.size());
        for (Token word : words) {
            Map<String, Set<Integer>> positions = positionsByDocument(word.value());
            if (positions.isEmpty()) {
                return matches;     // no document holds this word, so none holds the phrase
            }
            occurrences.add(positions);
        }

        // Only a document holding every word can hold the phrase. Checking that first keeps the
        // position work below to the few documents that could still match.
        Set<String> candidates = new HashSet<>(occurrences.getFirst().keySet());
        for (Map<String, Set<Integer>> positions : occurrences) {
            candidates.retainAll(positions.keySet());
        }

        int firstPosition = words.getFirst().position();
        for (String documentID : candidates) {
            // The phrase may start at any occurrence of its first word, not just the earliest:
            // "machine java ... machine learning" matches "machine learning" at the second
            // "machine".
            for (int start : occurrences.getFirst().get(documentID)) {
                boolean everyWordInPlace = true;
                for (int i = 1; i < words.size() && everyWordInPlace; i++) {
                    int gap = words.get(i).position() - firstPosition;
                    everyWordInPlace = occurrences.get(i).get(documentID).contains(start + gap);
                }
                if (everyWordInPlace) {
                    matches.add(documentID);
                    break;          // one occurrence is enough
                }
            }
        }
        return matches;
    }

    /**
     * Where a term occurs in each document containing it: document id to its positions.
     * A set, because the phrase check only ever asks "is there a word at this position?".
     */
    private Map<String, Set<Integer>> positionsByDocument(String term) {
        Map<String, Set<Integer>> positions = new HashMap<>();
        for (Posting posting : invertedIndex.getPostings(term)) {
            positions.put(posting.documentID(), new HashSet<>(posting.positions()));
        }
        return positions;
    }
}
