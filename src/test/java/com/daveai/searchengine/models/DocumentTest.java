package com.daveai.searchengine.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {

    @Test
    void storesIdTitleAndRawText() {
        Document doc = new Document("reports/annual.pdf", "Annual Report", "Revenue grew this year.");

        assertThat(doc.id()).isEqualTo("reports/annual.pdf");
        assertThat(doc.title()).isEqualTo("Annual Report");
        assertThat(doc.rawText()).isEqualTo("Revenue grew this year.");
    }

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new Document(null, "Title", "text"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlankId() {
        assertThatThrownBy(() -> new Document(" ", "Title", "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRawText() {
        assertThatThrownBy(() -> new Document("doc-1", "Title", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void allowsEmptyRawText() {
        // e.g. a scanned PDF with no text layer can still be found by its title
        assertThatCode(() -> new Document("scan.pdf", "Scanned form", "")).doesNotThrowAnyException();
    }

    @Test
    void documentsWithSameValuesAreEqual() {
        Document a = new Document("doc-1", "Title", "text");
        Document b = new Document("doc-1", "Title", "text");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
