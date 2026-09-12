package com.daveai.searchengine.extraction;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared implementation for extractors backed by an Apache Tika parser.
 * <p>
 * Subclasses only say which Tika {@link Parser} handles which extension; reading the file,
 * collecting the text and reading the title happen here.
 */
public abstract class TikaTextExtractor implements TextExtractor {

    /** No limit on the amount of text extracted (Tika's default stops at 100,000 characters). */
    private static final int UNLIMITED = -1;

    private final Map<String, Parser> parsersByExtension;

    /**
     * @param parsersByExtension lowercase extension (without dot) → the Tika parser for it
     */
    protected TikaTextExtractor(Map<String, Parser> parsersByExtension) {
        this.parsersByExtension = Map.copyOf(parsersByExtension);
    }

    @Override
    public Set<String> supportedExtensions() {
        return parsersByExtension.keySet();
    }

    @Override
    public ExtractedContent extract(Path file) {
        Objects.requireNonNull(file, "file must not be null");
        Parser parser = parsersByExtension.get(FileExtension.of(file));
        if (parser == null) {
            throw new UnsupportedDocumentTypeException(file);
        }

        BodyContentHandler text = new BodyContentHandler(UNLIMITED);
        Metadata metadata = new Metadata();
        try (TikaInputStream stream = TikaInputStream.get(file)) {
            parser.parse(stream, text, metadata, new ParseContext());
        } catch (IOException | SAXException | TikaException e) {
            throw new DocumentExtractionException(file, e);
        }
        return new ExtractedContent(text.toString().strip(), metadata.get(TikaCoreProperties.TITLE));
    }
}
