package com.daveai.searchengine.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * One real sample file per required format lives in src/test/resources/samples.
 * Each contains the sentence "This is the {FORMAT} sample about the inverted index."
 * The Office and PDF samples were created with Microsoft Office.
 */
class TextExtractorsTest {

    static final Path SAMPLES = Path.of("src/test/resources/samples");

    static Stream<Arguments> everyFormat() {
        return Stream.of(
                arguments("sample.txt", new PlainTextExtractor()),
                arguments("sample.pdf", new PdfExtractor()),
                arguments("sample.doc", new WordExtractor()),
                arguments("sample.docx", new WordExtractor()),
                arguments("sample.ppt", new PowerPointExtractor()),
                arguments("sample.pptx", new PowerPointExtractor()),
                arguments("sample.pps", new PowerPointExtractor()),
                arguments("sample.ppsx", new PowerPointExtractor()),
                arguments("sample.xls", new ExcelExtractor()),
                arguments("sample.xlsx", new ExcelExtractor()),
                arguments("sample.html", new HtmlExtractor()),
                arguments("sample.xml", new XmlExtractor()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyFormat")
    void extractsTheSampleSentence(String fileName, TextExtractor extractor) {
        String format = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);

        ExtractedContent content = extractor.extract(SAMPLES.resolve(fileName));

        assertThat(content.text()).contains("This is the " + format + " sample about the inverted index.");
    }

    @Test
    void htmlTitleComesFromTheTitleTag() {
        ExtractedContent content = new HtmlExtractor().extract(SAMPLES.resolve("sample.html"));

        assertThat(content.title()).isEqualTo("HTML Sample Title");
        assertThat(content.text()).doesNotContain("HTML Sample Title");
    }

    @Test
    void wordTitleComesFromTheDocumentProperties() {
        ExtractedContent content = new WordExtractor().extract(SAMPLES.resolve("sample.docx"));

        assertThat(content.title()).isEqualTo("Annual Research Report");
    }

    @Test
    void xmlTagNamesAreNotPartOfTheText() {
        ExtractedContent content = new XmlExtractor().extract(SAMPLES.resolve("sample.xml"));

        assertThat(content.text()).doesNotContain("<note>").doesNotContain("body");
    }

    @Test
    void eachExtractorDeclaresTheExtensionsItHandles() {
        assertThat(new PlainTextExtractor().supportedExtensions()).containsExactlyInAnyOrder("txt");
        assertThat(new PdfExtractor().supportedExtensions()).containsExactlyInAnyOrder("pdf");
        assertThat(new WordExtractor().supportedExtensions()).containsExactlyInAnyOrder("doc", "docx");
        assertThat(new PowerPointExtractor().supportedExtensions()).containsExactlyInAnyOrder("ppt", "pptx", "pps", "ppsx");
        assertThat(new ExcelExtractor().supportedExtensions()).containsExactlyInAnyOrder("xls", "xlsx");
        assertThat(new HtmlExtractor().supportedExtensions()).containsExactlyInAnyOrder("html", "htm");
        assertThat(new XmlExtractor().supportedExtensions()).containsExactlyInAnyOrder("xml");
    }

    @Test
    void extractorRejectsAFormatItDoesNotHandle() {
        assertThatThrownBy(() -> new PdfExtractor().extract(SAMPLES.resolve("sample.txt")))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
    }

    @Test
    void missingFileThrowsDocumentExtractionException() {
        assertThatThrownBy(() -> new PdfExtractor().extract(SAMPLES.resolve("does-not-exist.pdf")))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("does-not-exist.pdf");
    }

    @Test
    void corruptFileThrowsDocumentExtractionException(@TempDir Path folder) throws IOException {
        Path broken = Files.writeString(folder.resolve("broken.pdf"), "this is not really a pdf");

        assertThatThrownBy(() -> new PdfExtractor().extract(broken))
                .isInstanceOf(DocumentExtractionException.class);
    }
}
