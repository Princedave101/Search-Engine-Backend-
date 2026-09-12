package com.daveai.searchengine.extraction;

import org.apache.tika.parser.pdf.PDFParser;

import java.util.Map;

/** Extracts text from PDF files (pdf). Scanned PDFs with no text layer give empty text. */
public class PdfExtractor extends TikaTextExtractor {

    public PdfExtractor() {
        super(Map.of("pdf", new PDFParser()));
    }
}
