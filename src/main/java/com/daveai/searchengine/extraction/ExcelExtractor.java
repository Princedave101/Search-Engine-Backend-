package com.daveai.searchengine.extraction;

import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;

import java.util.Map;

/** Extracts the cell text from Excel workbooks: xls (Excel 97-2003) and xlsx. */
public class ExcelExtractor extends TikaTextExtractor {

    public ExcelExtractor() {
        super(Map.of(
                "xls", new OfficeParser(),     // older binary format
                "xlsx", new OOXMLParser()));   // Office Open XML format
    }
}
