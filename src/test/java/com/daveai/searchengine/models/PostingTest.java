package com.daveai.searchengine.models;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostingTest {

    @Test
    void storesDocumentIdFrequencyAndPositions() {
        Posting posting = new Posting("doc-1", 2, List.of(3, 7));

        assertThat(posting.documentID()).isEqualTo("doc-1");
        assertThat(posting.frequency()).isEqualTo(2);
        assertThat(posting.positions()).containsExactly(3, 7);
    }

    @Test
    void rejectsNullDocumentId() {
        assertThatThrownBy(() -> new Posting(null, 1, List.of(0)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankDocumentId() {
        assertThatThrownBy(() -> new Posting("  ", 1, List.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFrequencyBelowOne() {
        assertThatThrownBy(() -> new Posting("doc-1", 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullPositions() {
        assertThatThrownBy(() -> new Posting("doc-1", 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsFrequencyThatDoesNotMatchNumberOfPositions() {
        assertThatThrownBy(() -> new Posting("doc-1", 2, List.of(4)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePosition() {
        assertThatThrownBy(() -> new Posting("doc-1", 1, List.of(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPositionsThatAreNotStrictlyIncreasing() {
        // Phrase matching (T8) walks positions in order, so they must be sorted with no duplicates.
        assertThatThrownBy(() -> new Posting("doc-1", 2, List.of(7, 3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Posting("doc-1", 2, List.of(3, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changingTheOriginalListDoesNotChangeThePosting() {
        List<Integer> positions = new ArrayList<>(List.of(1, 5));
        Posting posting = new Posting("doc-1", 2, positions);

        positions.add(9);

        assertThat(posting.positions()).containsExactly(1, 5);
    }

    @Test
    void positionsCannotBeModifiedThroughTheAccessor() {
        Posting posting = new Posting("doc-1", 1, new ArrayList<>(List.of(0)));

        assertThatThrownBy(() -> posting.positions().add(4))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void postingsWithSameValuesAreEqual() {
        Posting a = new Posting("doc-1", 2, new ArrayList<>(List.of(1, 5)));
        Posting b = new Posting("doc-1", 2, List.of(1, 5));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
