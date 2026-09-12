package com.daveai.searchengine.extraction;

import org.apache.tika.parser.txt.TXTParser;

import java.util.Map;

/** Extracts text from plain-text files (txt). Tika detects the character encoding. */
public class PlainTextExtractor extends TikaTextExtractor {

    public PlainTextExtractor() {
        super(Map.of("txt", new TXTParser()));
    }
}
