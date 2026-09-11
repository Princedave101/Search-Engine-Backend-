package com.daveai.searchengine.indexing;

import com.daveai.searchengine.models.Posting;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvertedIndexTest {

    private final InvertedIndex index = new InvertedIndex();

    /** Builds a posting whose frequency is the number of positions given. */
    private static Posting posting(String documentID, int... positions) {
        return new Posting(documentID, positions.length, Arrays.stream(positions).boxed().toList());
    }

    @Test
    void unknownTermReturnsEmptyCollectionNotNull() {
        assertThat(index.getPostings("missing")).isNotNull().isEmpty();
    }

    @Test
    void returnsPostingThatWasAdded() {
        Posting posting = posting("doc-1", 0, 7);

        index.addPosting("search", posting);

        assertThat(index.getPostings("search")).containsExactly(posting);
    }

    @Test
    void returnsPostingsFromEveryDocumentContainingTheTerm() {
        Posting inDoc1 = posting("doc-1", 0, 7);
        Posting inDoc4 = posting("doc-4", 3);

        index.addPosting("search", inDoc1);
        index.addPosting("search", inDoc4);

        assertThat(index.getPostings("search")).containsExactlyInAnyOrder(inDoc1, inDoc4);
    }

    @Test
    void addingSameDocumentAgainReplacesItsPostingInsteadOfDuplicating() {
        index.addPosting("search", posting("doc-1", 0));
        Posting updated = posting("doc-1", 2, 5, 9);

        index.addPosting("search", updated);

        assertThat(index.getPostings("search")).containsExactly(updated);
    }

    @Test
    void removeDocumentRemovesItFromEveryTermButKeepsOtherDocuments() {
        index.addPosting("search", posting("doc-1", 0));
        index.addPosting("engine", posting("doc-1", 1));
        Posting otherDoc = posting("doc-2", 4);
        index.addPosting("search", otherDoc);

        index.removeDocument("doc-1");

        assertThat(index.getPostings("search")).containsExactly(otherDoc);
        assertThat(index.getPostings("engine")).isEmpty();
    }

    @Test
    void removeDocumentDeletesTermsThatHaveNoDocumentsLeft() {
        index.addPosting("engine", posting("doc-1", 1));

        index.removeDocument("doc-1");

        assertThat(index.documentFrequency("engine")).isZero();
        assertThat(index.getPostings("engine")).isEmpty();
    }

    @Test
    void removingUnknownDocumentDoesNothing() {
        index.addPosting("search", posting("doc-1", 0));

        assertThatCode(() -> index.removeDocument("never-indexed")).doesNotThrowAnyException();
        assertThat(index.getPostings("search")).hasSize(1);
    }

    @Test
    void documentFrequencyCountsDocumentsNotOccurrences() {
        index.addPosting("search", posting("doc-1", 0, 3, 8));
        index.addPosting("search", posting("doc-2", 1));

        assertThat(index.documentFrequency("search")).isEqualTo(2);
        assertThat(index.documentFrequency("missing")).isZero();
    }

    @Test
    void documentCountAndAllDocumentIdsTrackAddsAndRemoves() {
        index.addPosting("search", posting("doc-1", 0));
        index.addPosting("engine", posting("doc-1", 1));
        index.addPosting("search", posting("doc-2", 0));

        assertThat(index.documentCount()).isEqualTo(2);
        assertThat(index.allDocumentIds()).containsExactlyInAnyOrder("doc-1", "doc-2");

        index.removeDocument("doc-1");

        assertThat(index.documentCount()).isEqualTo(1);
        assertThat(index.allDocumentIds()).containsExactly("doc-2");
    }

    @Test
    void documentLengthIsTheSumOfItsPostingFrequencies() {
        index.addPosting("search", posting("doc-1", 0, 4));   // 2 occurrences
        index.addPosting("engine", posting("doc-1", 1));      // 1 occurrence

        assertThat(index.documentLength("doc-1")).isEqualTo(3);
        assertThat(index.documentLength("unknown")).isZero();
    }

    @Test
    void documentLengthIsCorrectAfterAPostingIsReplaced() {
        index.addPosting("search", posting("doc-1", 0, 4));   // length 2
        index.addPosting("search", posting("doc-1", 7));      // replaced: length 1

        assertThat(index.documentLength("doc-1")).isEqualTo(1);
    }

    @Test
    void documentLengthIsZeroAfterDocumentIsRemoved() {
        index.addPosting("search", posting("doc-1", 0, 4));

        index.removeDocument("doc-1");

        assertThat(index.documentLength("doc-1")).isZero();
    }

    @Test
    void returnedPostingsCannotBeModifiedByTheCaller() {
        index.addPosting("search", posting("doc-1", 0));
        Collection<Posting> postings = index.getPostings("search");

        assertThatThrownBy(postings::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnedDocumentIdsCannotBeModifiedByTheCaller() {
        index.addPosting("search", posting("doc-1", 0));

        assertThatThrownBy(() -> index.allDocumentIds().remove("doc-1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void postingsCanBeReadWhileADocumentIsBeingRemoved() {
        index.addPosting("search", posting("doc-1", 0));
        index.addPosting("search", posting("doc-2", 0));
        index.addPosting("search", posting("doc-3", 0));

        // Simulates a search looping over postings while the indexer removes a document.
        // With a plain HashMap this throws ConcurrentModificationException.
        assertThatCode(() -> {
            for (Posting ignored : index.getPostings("search")) {
                index.removeDocument("doc-2");
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> index.addPosting(null, posting("doc-1", 0)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> index.addPosting("  ", posting("doc-1", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> index.addPosting("search", null))
                .isInstanceOf(NullPointerException.class);
    }
}
