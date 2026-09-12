package com.daveai.searchengine.indexing;

import com.daveai.searchengine.autocomplete.Trie;
import com.daveai.searchengine.models.Document;
import com.daveai.searchengine.models.Posting;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexerTest {

    // Real instances, kept as fields so each test can check what the Indexer wrote into them.
    private final TextProcessor processor = new TextProcessor();
    private final InvertedIndex index = new InvertedIndex();
    private final DocumentStore store = new DocumentStore();
    private final Trie trie = new Trie();

    // @RequiredArgsConstructor follows the field order in Indexer: processor, store, index, trie.
    private final Indexer indexer = new Indexer(processor, store, index, trie);

    /** Builds a posting whose frequency is the number of positions given. */
    private static Posting posting(String documentID, int... positions) {
        return new Posting(documentID, positions.length, Arrays.stream(positions).boxed().toList());
    }

    @Test
    void groupsRepeatedWordsIntoOnePostingWithAllTheirPositions() {
        indexer.indexDocument(new Document("doc-1", "", "java java spring"));

        assertThat(index.getPostings("java")).containsExactly(posting("doc-1", 0, 1));
        assertThat(index.getPostings("spring")).containsExactly(posting("doc-1", 2));
    }

    @Test
    void indexesTitleWordsFollowedByBodyWords() {
        // Text becomes "Java Guide\nJava and Spring. Java rocks."
        // java@0 guide@1 java@2 (and@3 is a stop word) spring@4 java@5 rocks@6
        indexer.indexDocument(new Document("doc-1", "Java Guide", "Java and Spring. Java rocks."));

        assertThat(index.getPostings("java")).containsExactly(posting("doc-1", 0, 2, 5));
        assertThat(index.getPostings("guide")).containsExactly(posting("doc-1", 1));
        assertThat(index.getPostings("spring")).containsExactly(posting("doc-1", 4));
        assertThat(index.getPostings("rocks")).containsExactly(posting("doc-1", 6));
        assertThat(index.documentLength("doc-1")).isEqualTo(6);
    }

    @Test
    void nullTitleIsNotIndexedAsTheWordNull() {
        indexer.indexDocument(new Document("doc-1", null, "java"));

        assertThat(index.getPostings("null")).isEmpty();
        assertThat(index.getPostings("java")).containsExactly(posting("doc-1", 0));
    }

    @Test
    void stopWordsNeverReachTheIndex() {
        indexer.indexDocument(new Document("doc-1", "", "the cat and the hat"));

        assertThat(index.getPostings("the")).isEmpty();
        assertThat(index.getPostings("and")).isEmpty();
        assertThat(index.getPostings("cat")).hasSize(1);
    }

    @Test
    void savesTheDocumentInTheStore() {
        Document doc = new Document("doc-1", "Java Guide", "Java rocks");

        indexer.indexDocument(doc);

        assertThat(store.get("doc-1")).contains(doc);
    }

    @Test
    void trieWeightCountsDocumentsNotOccurrences() {
        // java: 3 occurrences but only 1 document. jar: 2 documents.
        indexer.indexDocument(new Document("doc-1", "", "java java java jar"));
        indexer.indexDocument(new Document("doc-2", "", "jar"));

        // Counting documents, jar (2) ranks above java (1). Counting occurrences would put java (3) first.
        assertThat(trie.getSuggestions("ja", 10)).containsExactly("jar", "java");
    }

    @Test
    void reindexingADocumentReplacesItsTermsInTheIndexAndTheTrie() {
        indexer.indexDocument(new Document("doc-1", "", "java spring"));

        indexer.indexDocument(new Document("doc-1", "", "python"));

        assertThat(index.getPostings("java")).isEmpty();
        assertThat(index.getPostings("python")).containsExactly(posting("doc-1", 0));
        assertThat(index.documentCount()).isEqualTo(1);
        assertThat(trie.getSuggestions("ja", 10)).isEmpty();
        assertThat(trie.getSuggestions("py", 10)).containsExactly("python");
    }

    @Test
    void reindexingTheSameDocumentDoesNotInflateTrieWeights() {
        // doc-1 is indexed three times (e.g. the file was edited twice), but it is still one document.
        indexer.indexDocument(new Document("doc-1", "", "java"));
        indexer.indexDocument(new Document("doc-1", "", "java"));
        indexer.indexDocument(new Document("doc-1", "", "java"));
        indexer.indexDocument(new Document("doc-2", "", "jar"));
        indexer.indexDocument(new Document("doc-3", "", "jar"));

        // java is in 1 document, jar in 2, so jar comes first.
        assertThat(trie.getSuggestions("ja", 10)).containsExactly("jar", "java");
    }

    @Test
    void removeDocumentRemovesItFromIndexStoreAndTrieButKeepsOtherDocuments() {
        indexer.indexDocument(new Document("doc-1", "", "java spring"));
        indexer.indexDocument(new Document("doc-2", "", "spring boot"));

        indexer.removeDocument("doc-1");

        assertThat(index.getPostings("java")).isEmpty();
        assertThat(index.getPostings("spring")).containsExactly(posting("doc-2", 0));
        assertThat(store.contains("doc-1")).isFalse();
        assertThat(store.contains("doc-2")).isTrue();
        assertThat(trie.getSuggestions("ja", 10)).isEmpty();
        assertThat(trie.getSuggestions("sp", 10)).containsExactly("spring");
    }

    @Test
    void removingADocumentThatWasNeverIndexedDoesNothing() {
        indexer.indexDocument(new Document("doc-1", "", "java"));

        assertThatCode(() -> indexer.removeDocument("never-indexed")).doesNotThrowAnyException();
        assertThat(index.getPostings("java")).hasSize(1);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void documentWithNoTextIsStoredButHasNoPostings() {
        indexer.indexDocument(new Document("scan.pdf", null, ""));

        assertThat(store.contains("scan.pdf")).isTrue();
        assertThat(index.documentCount()).isZero();
    }

    @Test
    void rejectsNullDocument() {
        assertThatThrownBy(() -> indexer.indexDocument(null)).isInstanceOf(NullPointerException.class);
    }
}
