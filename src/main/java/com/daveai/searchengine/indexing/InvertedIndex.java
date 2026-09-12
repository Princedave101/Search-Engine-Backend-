package com.daveai.searchengine.indexing;

import com.daveai.searchengine.models.Posting;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The inverted index: for every term, which documents contain it, how often and where.
 * <p>
 * Structure (term → documentID → Posting):
 * <pre>
 * "search" → { "doc-1": Posting(doc-1, 2, [0, 7]),
 *              "doc-4": Posting(doc-4, 1, [3]) }
 * </pre>
 * The inner map is keyed by documentID, so when a document changes or is deleted its
 * posting can be replaced or removed directly, without scanning a list. It also means a
 * document can never appear twice under the same term.
 * <p>
 * <b>Thread safety:</b> searches read the index while the indexer writes to it. Every map is a
 * {@link ConcurrentHashMap}, so reads never lock or wait. The two write methods are
 * {@code synchronized} so an add and a remove can't interleave and lose a posting.
 */
public class InvertedIndex {

    /** term → (documentID → the posting for that term in that document). */
    private final Map<String, Map<String, Posting>> postings = new ConcurrentHashMap<>();

    /** documentID → every term that document contains, so removeDocument only visits those terms. */
    private final Map<String, Set<String>> termsByDocument = new ConcurrentHashMap<>();

    /** documentID → number of non-stop-word tokens in the document (the sum of its posting frequencies). */
    private final Map<String, Integer> documentLengths = new ConcurrentHashMap<>();

    /**
     * Adds the posting for one term in one document. If that document already has a posting
     * for this term, the new posting replaces it.
     *
     * @param term    normalised term; not null or blank
     * @param posting the posting to store; not null
     */
    public synchronized void addPosting(String term, Posting posting) {
        Objects.requireNonNull(term, "term must not be null");
        if (term.isBlank()) {
            throw new IllegalArgumentException("term must not be blank");
        }
        Objects.requireNonNull(posting, "posting must not be null");

        String documentID = posting.documentID();

        // 1. Store the posting under term -> documentID. computeIfAbsent creates the inner map
        //    the first time a term is seen. put returns the posting it replaced, or null.
        Posting replaced = postings
                .computeIfAbsent(term, t -> new ConcurrentHashMap<>())
                .put(documentID, posting);

        // 2. Record that this document contains this term, for removeDocument.
        termsByDocument
                .computeIfAbsent(documentID, id -> ConcurrentHashMap.newKeySet())
                .add(term);

        // 3. Update the document length. If a posting was replaced, take its old frequency off first.
        int replacedFrequency = (replaced == null) ? 0 : replaced.frequency();
        documentLengths.merge(documentID, posting.frequency() - replacedFrequency, Integer::sum);
    }

    /**
     * Returns every posting for a term: one per document that contains it.
     * This is what the matching engine (T8) and the ranker (T9) call for each query term.
     *
     * @param term the term to look up; not null
     * @return a read-only view of the term's postings; empty (never null) if the term is unknown
     */
    public Collection<Posting> getPostings(String term) {
        Objects.requireNonNull(term, "term must not be null");
        Map<String, Posting> byDocument = postings.get(term);
        if (byDocument == null) {
            return Collections.emptyList();
        }
        // A read-only view, not a copy: no time is spent copying on every query.
        return Collections.unmodifiableCollection(byDocument.values());
    }

    /**
     * Removes a document from every term it appears under. Terms left with no documents are
     * deleted. Does nothing if the document is not indexed.
     * <p>
     * The removed terms are returned so the Indexer (T4) can also remove them from the trie;
     * only the index knows which terms a document contained.
     *
     * @param documentID the document to remove; not null
     * @return the terms the document contained; empty if the document was not indexed
     */
    public synchronized Set<String> removeDocument(String documentID) {
        Objects.requireNonNull(documentID, "documentID must not be null");

        // The reverse map says exactly which terms to visit. Without it, every term in the index would be scanned.
        Set<String> terms = termsByDocument.remove(documentID);
        if (terms == null) {
            return Set.of();
        }
        for (String term : terms) {
            Map<String, Posting> postingByDocument = postings.get(term);
            postingByDocument.remove(documentID);
            if (postingByDocument.isEmpty()) {
                postings.remove(term);
            }
        }
        documentLengths.remove(documentID);
        return Collections.unmodifiableSet(terms);
    }

    /**
     * Returns how many documents contain a term: the "df" in TF-IDF (T9).
     *
     * @param term the term to look up; not null
     * @return the number of documents containing the term; 0 if the term is unknown
     */
    public int documentFrequency(String term) {
        Objects.requireNonNull(term, "term must not be null");
        Map<String, Posting> postingByDocument = postings.get(term);
        return (postingByDocument == null) ? 0 : postingByDocument.size();
    }

    /**
     * Returns how many documents are indexed: the "N" in TF-IDF (T9).
     * Only documents with at least one term are counted.
     *
     * @return the number of indexed documents
     */
    public int documentCount() {
        return termsByDocument.size();
    }

    /**
     * Returns a document's length in non-stop-word tokens, used to stop long documents
     * winning in ranking just because they are long (T9).
     *
     * @param documentID the document; not null
     * @return the document's token count; 0 if the document is not indexed
     */
    public int documentLength(String documentID) {
        Objects.requireNonNull(documentID, "documentID must not be null");
        return documentLengths.getOrDefault(documentID, 0);
    }

    /**
     * Returns the ids of every indexed document, needed to answer NOT queries (T8).
     *
     * @return a read-only view of the indexed document ids
     */
    public Set<String> allDocumentIds() {
        return Collections.unmodifiableSet(termsByDocument.keySet());
    }
}
