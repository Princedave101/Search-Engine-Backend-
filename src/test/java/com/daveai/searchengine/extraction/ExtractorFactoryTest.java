package com.daveai.searchengine.extraction;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractorFactoryTest {

    private final ExtractorFactory factory = ExtractorFactory.withDefaultExtractors();

    @Test
    void supportsEveryFormatRequiredByTheBrief() {
        assertThat(factory.supportedExtensions()).containsExactlyInAnyOrder(
                "txt", "pdf", "doc", "docx", "ppt", "pptx", "pps", "ppsx", "xls", "xlsx", "html", "htm", "xml");
    }

    @Test
    void picksTheExtractorByExtension() {
        assertThat(factory.forFile(Path.of("report.pdf"))).isInstanceOf(PdfExtractor.class);
        assertThat(factory.forFile(Path.of("notes/lecture.docx"))).isInstanceOf(WordExtractor.class);
        assertThat(factory.forFile(Path.of("slides.pps"))).isInstanceOf(PowerPointExtractor.class);
        assertThat(factory.forFile(Path.of("marks.xlsx"))).isInstanceOf(ExcelExtractor.class);
        assertThat(factory.forFile(Path.of("index.htm"))).isInstanceOf(HtmlExtractor.class);
    }

    @Test
    void extensionMatchingIgnoresCase() {
        assertThat(factory.forFile(Path.of("REPORT.PDF"))).isInstanceOf(PdfExtractor.class);
    }

    @Test
    void unsupportedExtensionThrows() {
        assertThatThrownBy(() -> factory.forFile(Path.of("photo.png")))
                .isInstanceOf(UnsupportedDocumentTypeException.class)
                .hasMessageContaining("photo.png");
    }

    @Test
    void fileWithoutExtensionIsUnsupported() {
        assertThatThrownBy(() -> factory.forFile(Path.of("README")))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
        assertThat(factory.isSupported(Path.of("README"))).isFalse();
    }

    @Test
    void isSupportedReportsWhetherAFileCanBeRead() {
        assertThat(factory.isSupported(Path.of("lecture.ppt"))).isTrue();
        assertThat(factory.isSupported(Path.of("archive.zip"))).isFalse();
    }

    @Test
    void rejectsTwoExtractorsForTheSameExtension() {
        assertThatThrownBy(() -> new ExtractorFactory(List.of(new PdfExtractor(), new PdfExtractor())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".pdf");
    }
}
