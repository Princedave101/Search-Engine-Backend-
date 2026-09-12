package com.daveai.searchengine.indexing;

import com.daveai.searchengine.autocomplete.Trie;
import com.daveai.searchengine.models.Document;
import com.daveai.searchengine.models.Posting;
import com.daveai.searchengine.models.Token;
import lombok.RequiredArgsConstructor;

import java.util.*;

/**
 * Runs the indexing pipeline for a document and undoes it when the document is removed.
 * <p>
 * The Indexer stores nothing itself. It coordinates the four shared components:
 * <pre>
 * Document ──► TextProcessor ──► tokens ──► group by term ──► InvertedIndex  (postings)
 *     │                                            └────────► Trie           (autocomplete)
 *     └──────────────────────────────────────────────────────► DocumentStore  (title and text)
 * </pre>
 * The components are passed in rather than created here, so the search side reads the same
 * index, store and trie that the Indexer writes to.
 * <p>
 * <b>Thread safety:</b> both methods are {@code synchronized}, so two documents are never indexed
 * or removed at the same time (for example a scheduled rescan and an upload).
 */
@RequiredArgsConstructor
public class Indexer {
    private final TextProcessor processor;
    private final DocumentStore db;
    private final InvertedIndex invertedIndex;
    private final Trie trie;

    /**
     * Indexes a document, replacing any earlier version with the same id.
     * <p>
     * The title and body are indexed as one text (title first), so title words are searchable.
     * Each unique term gets one {@link Posting} with all its positions, and is added to the trie
     * once, so the trie weight equals the number of documents containing the term.
     *
     * @param doc the document to index; not null
     * @throws NullPointerException if {@code doc} is null
     */
    public synchronized void indexDocument(Document doc){
        Objects.requireNonNull(doc, "document can't be null");

//        clean the index, trie and store initially, by docId
        this.removeDocument(doc.id());

//        save documents
        db.save(doc);

//        index document contents
        String title = (doc.title() == null) ? "" : doc.title();
        String content = title + "\n" + doc.rawText();
        List<Token> tokens = processor.processText(content, doc.id());

//        group each positions by the term
//        A plain HashMap and ArrayList are enough: this map is local to one call, so only one thread uses it.
        Map<String, List<Integer>> grouping = new HashMap<>();
        for (Token token : tokens) {
            grouping.computeIfAbsent(token.value(), term -> new ArrayList<>()).add(token.position());
        }

        for (Map.Entry<String, List<Integer>> entry : grouping.entrySet()) {
//            created inverted index
            invertedIndex.addPosting(entry.getKey(), new Posting(doc.id(), entry.getValue().size(), entry.getValue()));

//            insert into trie for autocomplete feature
            trie.insert(entry.getKey());

        }
    }

    /**
     * Removes a document from the index, the trie and the store.
     * Does nothing if the document was never indexed.
     *
     * @param documentID the id of the document to remove; not null
     */
    public synchronized void removeDocument(String documentID) {
        Set<String> terms = invertedIndex.removeDocument(documentID);
        terms.forEach(trie::remove);
        db.remove(documentID);
    }
}
