package com.daveai.searchengine.indexing;

import com.daveai.searchengine.models.Token;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import java.util.*;
import java.util.regex.Pattern;


/**
 * Turns raw text into normalised {@link Token}s: splits it into words, lowercases them
 * and removes stop words.
 * <p>
 * The same processor is used for documents (T4) and queries (T7), so both are normalised
 * identically. Otherwise query terms would never match indexed terms.
 * <p>
 * Stop words are loaded once from {@code static/stopwords.txt} on the classpath, one word
 * per line. Because the file is under {@code static/}, the list can also be viewed in a
 * browser at {@code /stopwords.txt}.
 */
public class TextProcessor {
    private static final String STOP_WORD_FILE_PATH = "static/stopwords.txt";
    private static final Pattern WORD_SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final Set<String> stopWords;

    /**
     * Creates a processor and loads the stop-word list.
     *
     * @throws RuntimeException if {@code static/stopwords.txt} cannot be read
     */
    public TextProcessor() {
        stopWords = readStopWords();
    }

    /**
     * Reads the stop-word file and returns its words, lowercased.
     *
     * @return the stop words; empty if the file is empty
     */
    private Set<String> readStopWords(){
        Resource resource = new ClassPathResource(STOP_WORD_FILE_PATH);
        try (InputStream inputStream = resource.getInputStream()) {
            String stopword = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            if(stopword.isEmpty()){
                return Set.of();
            }
            return Set.of(WORD_SEPARATOR.split(stopword.toLowerCase(Locale.ROOT)));
        } catch (IOException e) {
            throw new RuntimeException("Error reading stopwords file", e);
        }
    }

    /**
     * Splits text into words, lowercases them and drops stop words. Each remaining word
     * becomes a {@link Token} tagged with its source document and position.
     * <p>
     * A position is the word's index in the original text, counted from 0 and
     * <b>including</b> stop words, so {@code "state of the art"} gives
     * {@code state@0, art@3}. This keeps phrase search correct.
     *
     * @param text       the text to process; null or blank gives an empty list
     * @param documentID the document the text came from, copied onto every token; may be null for queries
     * @return the tokens in the order they appear in the text; never null
     */
    public List<Token> processText(String text, String documentID){
        if(Objects.isNull(text) || text.isEmpty()){
            return List.of();
        }
        List<Token> tokens = new ArrayList<>();
        String[] words = WORD_SEPARATOR.split(text);
        int position = 0;
        for (String word : words){
            if(word.isEmpty()){
                continue;
            }
            word = word.trim().toLowerCase(Locale.ROOT);
            if(!this.stopWords.contains(word)){
                tokens.add(new Token(word, documentID,  position));
            }
            position++;
        }
        return  tokens;
    }
}
