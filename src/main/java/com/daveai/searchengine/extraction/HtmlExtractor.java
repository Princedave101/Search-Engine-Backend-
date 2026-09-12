package com.daveai.searchengine.extraction;

import org.apache.tika.parser.html.JSoupParser;

import java.util.Map;

/** Extracts the visible text from web pages (html, htm). The {@code <title>} becomes the title. */
public class HtmlExtractor extends TikaTextExtractor {

    public HtmlExtractor() {
        super(Map.of(
                "html", new JSoupParser(),
                "htm", new JSoupParser()));
    }
}
