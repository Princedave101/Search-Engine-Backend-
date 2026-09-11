package com.daveai.searchengine.extraction;

import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that the Tika dependency added in T0 can read the sample documents.
 * T5 replaces this with per-format extractor tests.
 */
class TikaDependencyTest {

    private final Tika tika = new Tika();

    @Test
    void extractsTextFromPlainText() throws Exception {
        assertThat(tika.parseToString(Path.of("documents/welcome.txt"))).contains("University Intranet");
    }

    @Test
    void extractsTextFromHtml() throws Exception {
        assertThat(tika.parseToString(Path.of("documents/computer-science-department.html")))
                .contains("Information retrieval and search engines");
    }

    @Test
    void extractsTextFromXml() throws Exception {
        assertThat(tika.parseToString(Path.of("documents/course-catalogue.xml")))
                .contains("A Modern Programming Language");
    }
}
