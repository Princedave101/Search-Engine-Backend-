package com.daveai.searchengine.indexing;

import com.daveai.searchengine.models.Document;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the extracted content of every indexed document, looked up by document id.
 * <p>
 * It is kept separate from {@link InvertedIndex} so that showing a result (title, snippet or
 * full text) never requires opening or re-parsing the original file.
 * <p>
 * <b>Thread safety:</b> backed by a {@link ConcurrentHashMap}, so searches can read documents
 * while the indexer saves or removes them.
 */
public class DocumentStore {

    /** document id → document. */
    private final Map<String, Document> documents;

    /** Creates an empty store. */
    public DocumentStore() {
        this.documents = new ConcurrentHashMap<>();
    }

    /**
     * Saves a document, replacing any document already stored with the same id.
     *
     * @param doc the document to save; not null
     * @throws NullPointerException if {@code doc} is null
     */
    public void save(Document doc){
        if (Objects.isNull(doc)){
            throw new NullPointerException("document is null");
        }
        documents.put(doc.id(), doc);
    }

    /**
     * Looks up a document by id.
     *
     * @param id the document id; not null
     * @return the document, or {@link Optional#empty()} if no document has this id
     * @throws NullPointerException if {@code id} is null
     */
    public Optional<Document> get(String id){
        if (Objects.isNull(id)){
            throw new NullPointerException("id is null");
        }
        // ofNullable, not of: documents.get returns null for an unknown id, and Optional.of(null) throws.
        return Optional.ofNullable(documents.get(id));
    }

    /**
     * Returns every stored document.
     *
     * @return a copy of the stored documents, in no particular order; changing it does not change the store
     */
    public List<Document> getDocs(){
        return new ArrayList<>(documents.values());
    }

    /**
     * Removes a document. Does nothing if no document has this id.
     *
     * @param id the id of the document to remove; not null
     * @throws NullPointerException if {@code id} is null
     */
    public void remove(String id){
        if (Objects.isNull(id)){
            throw new NullPointerException("id is null");
        }
        documents.remove(id);
    }

    /**
     * Checks whether a document is stored.
     *
     * @param id the document id; not null
     * @return true if a document with this id is stored
     * @throws NullPointerException if {@code id} is null
     */
    public boolean contains(String id){
        if (Objects.isNull(id)){
            throw new NullPointerException("id is null");
        }
        return documents.containsKey(id);
    }

    /**
     * Returns how many documents are stored.
     *
     * @return the number of stored documents
     */
    public int size(){
        return documents.size();
    }

    /**
     * Creates a new, empty store. Same as {@code new DocumentStore()}.
     *
     * @return a new, empty store
     */
    public static DocumentStore getDocumentStore(){
        return new DocumentStore();
    }
}
