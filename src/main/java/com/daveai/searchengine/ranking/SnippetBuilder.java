package com.daveai.searchengine.ranking;

import java.util.regex.Pattern;

public class SnippetBuilder {

    private static final int SUBSTRING_LENGTH = 80;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public static String buildSnippet(String text, String term){
        // a new builder per call, so text from earlier snippets doesn't leak into this one
        StringBuilder stringBuilder = new StringBuilder();

        // find first instance of the string current index
        // query terms are lowercase but the text isn't, so ignore case
        int currentIndexOfTerm = (term == null) ? -1 : indexOfIgnoreCase(text, term);
        if(currentIndexOfTerm == -1){
            // a document can match by title only, so fall back to the start of the text
            currentIndexOfTerm = 0;
            term = "";
        }
        int termEnd = currentIndexOfTerm + term.length();

        // start up to 80 characters before the term; if that is mid-word, move on to the next word
        int start = Math.max(currentIndexOfTerm - SUBSTRING_LENGTH, 0);
        if (start > 0) {
            int nextSpace = text.indexOf(' ', start);
            start = (nextSpace == -1 || nextSpace >= currentIndexOfTerm) ? currentIndexOfTerm : nextSpace + 1;
        }

        // end up to 80 characters after the term; if that is mid-word, move back to the last space.
        // With no space after the term, cut mid-word rather than drop the whole tail.
        int end = Math.min(text.length(), termEnd + SUBSTRING_LENGTH);
        if (end < text.length()) {
            int lastSpace = text.lastIndexOf(' ', end);
            if (lastSpace > termEnd) {
                end = lastSpace;
            }
        }

        // "…" marks each end that was cut
        if (start > 0) {
            stringBuilder.append("…");
        }
        // extracted text keeps the file's line breaks and indentation; a snippet reads better on one line
        stringBuilder.append(WHITESPACE.matcher(text.substring(start, end)).replaceAll(" ").strip());
        if (end < text.length()) {
            stringBuilder.append("…");
        }

        return stringBuilder.toString();
    }

    // Not text.toLowerCase().indexOf(term): lowercasing can change the text's length (e.g. "İ"
    // becomes two characters), so the index found would point to the wrong place in the original.
    private static int indexOfIgnoreCase(String text, String term) {
        for (int i = 0; i + term.length() <= text.length(); i++) {
            if (text.regionMatches(true, i, term, 0, term.length())) {
                return i;
            }
        }
        return -1;
    }
}
