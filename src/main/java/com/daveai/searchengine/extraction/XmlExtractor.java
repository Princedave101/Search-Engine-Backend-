package com.daveai.searchengine.extraction;

import org.apache.tika.parser.xml.XMLParser;

import java.util.Map;

/** Extracts the text content of XML files (xml); tags and attribute names are left out. */
public class XmlExtractor extends TikaTextExtractor {

    public XmlExtractor() {
        super(Map.of("xml", new XMLParser()));
    }
}
