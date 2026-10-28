package com.daveai.searchengine.extraction;

import org.apache.tika.parser.pdf.OcrConfig;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pdf.PDFParserConfig;

import java.util.Map;

/** Extracts text from PDF files (pdf). Scanned PDFs with no text layer give empty text. */
public class PdfExtractor extends TikaTextExtractor {

    public PdfExtractor() {
        super(Map.of("pdf", new PDFParser(withoutOcr())));
    }

    /**
     * Tika's default tries OCR on pages with little or no text, and crashes with a
     * NullPointerException when no OCR engine (Tesseract) is installed. We don't ship one,
     * so OCR is switched off and such pages simply give no text.
     */
    private static PDFParserConfig withoutOcr() {
        OcrConfig ocr = new OcrConfig();
        ocr.setStrategy(OcrConfig.Strategy.NO_OCR);
        PDFParserConfig config = new PDFParserConfig();
        config.setOcr(ocr);
        return config;
    }
}
