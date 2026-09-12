package com.daveai.searchengine.extraction;

import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;

import java.util.Map;

/** Extracts text from Word documents: doc (Word 97-2003) and docx. */
public class WordExtractor extends TikaTextExtractor {

    public WordExtractor() {
        super(Map.of(
                "doc", new OfficeParser(),     // older binary format
                "docx", new OOXMLParser()));   // Office Open XML format
    }
}
