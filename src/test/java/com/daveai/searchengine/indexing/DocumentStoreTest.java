package com.daveai.searchengine.indexing;

import com.daveai.searchengine.models.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentStoreTest {

    private final DocumentStore store = new DocumentStore();

    private final Document welcome = new Document("welcome.txt", "Welcome", "Welcome to the intranet");
    private final Document catalogue = new Document("course-catalogue.xml", "Course catalogue", "CSC322 CSC311");

    @Test
    void newStoreIsEmpty() {
        assertThat(store.size()).isZero();
        assertThat(store.getDocs()).isEmpty();
    }

    @Test
    void savedDocumentCanBeRetrievedById() {
        store.save(welcome);

        assertThat(store.get("welcome.txt")).contains(welcome);
    }

    @Test
    void getReturnsEmptyOptionalForUnknownId() {
        assertThat(store.get("missing.pdf")).isEmpty();
    }

    @Test
    void savingADocumentWithTheSameIdReplacesIt() {
        store.save(welcome);
        Document updated = new Document("welcome.txt", "Welcome (updated)", "New text");

        store.save(updated);

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.get("welcome.txt")).contains(updated);
    }

    @Test
    void removeDeletesTheDocument() {
        store.save(welcome);
        store.save(catalogue);

        store.remove("welcome.txt");

        assertThat(store.get("welcome.txt")).isEmpty();
        assertThat(store.contains("welcome.txt")).isFalse();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void removingUnknownIdDoesNothing() {
        store.save(welcome);

        assertThatCode(() -> store.remove("missing.pdf")).doesNotThrowAnyException();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void containsReportsWhetherADocumentIsStored() {
        store.save(welcome);

        assertThat(store.contains("welcome.txt")).isTrue();
        assertThat(store.contains("missing.pdf")).isFalse();
    }

    @Test
    void getDocsReturnsEveryStoredDocument() {
        store.save(welcome);
        store.save(catalogue);

        assertThat(store.getDocs()).containsExactlyInAnyOrder(welcome, catalogue);
    }

    @Test
    void changingTheListFromGetDocsDoesNotChangeTheStore() {
        store.save(welcome);
        List<Document> docs = store.getDocs();

        docs.clear();

        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void rejectsNullArguments() {
        assertThatThrownBy(() -> store.save(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.get(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.remove(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> store.contains(null)).isInstanceOf(NullPointerException.class);
    }
}
