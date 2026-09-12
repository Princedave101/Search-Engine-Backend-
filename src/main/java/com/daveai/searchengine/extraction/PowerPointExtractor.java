package com.daveai.searchengine.extraction;

import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;

import java.util.Map;

/**
 * Extracts text from PowerPoint files: presentations (ppt, pptx) and slide shows (pps, ppsx).
 * The brief's "ppts" is read as these slide-show formats.
 */
public class PowerPointExtractor extends TikaTextExtractor {

    public PowerPointExtractor() {
        super(Map.of(
                "ppt", new OfficeParser(),     // older binary formats
                "pps", new OfficeParser(),
                "pptx", new OOXMLParser(),     // Office Open XML formats
                "ppsx", new OOXMLParser()));
    }
}
