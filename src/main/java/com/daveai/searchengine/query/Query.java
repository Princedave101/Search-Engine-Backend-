package com.daveai.searchengine.query;

import com.daveai.searchengine.models.Token;
import com.daveai.searchengine.query.ast.AndNode;
import com.daveai.searchengine.query.ast.NotNode;
import com.daveai.searchengine.query.ast.OrNode;
import com.daveai.searchengine.query.ast.PhraseNode;
import com.daveai.searchengine.query.ast.QueryNode;
import com.daveai.searchengine.query.ast.TermNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A parsed search query.
 *
 * @param rawText the text the user typed
 * @param root    the AST that says what the query means; null when nothing searchable is left
 *                (blank text, or only stop words)
 */
public record Query(String rawText, QueryNode root) {

    /**
     * @return true if there is nothing to search for
     */
    public boolean isEmpty() {
        return root == null;
    }

    /**
     * Returns the words the ranker (T9) scores documents by: every word in a Term or Phrase node,
     * <b>except</b> words under a NOT. A document shouldn't score higher for containing a word the user excluded.
     * <p>
     * Each word appears once, in the order it first appears in the query, so a repeated word
     * ({@code java OR java}) isn't counted twice.
     * <p>
     * Example: {@code java -spring "machine learning"} gives {@code [java, machine, learning]}.
     *
     * @return the words; empty for an empty query; cannot be modified
     */
    public List<String> terms() {
        Set<String> terms = new LinkedHashSet<>();
        collectTerms(root, terms);
        return List.copyOf(terms);
    }

    /** Walks the tree and adds each word it finds, skipping everything under a NOT. */
    private static void collectTerms(QueryNode node, Set<String> terms) {
        switch (node) {
            case null -> { }
            case TermNode term -> terms.add(term.term());
            case PhraseNode phrase -> {
                for (Token word : phrase.words()) {
                    terms.add(word.value());
                }
            }
            case AndNode and -> {
                for (QueryNode child : and.children()) {
                    collectTerms(child, terms);
                }
            }
            case OrNode or -> {
                for (QueryNode child : or.children()) {
                    collectTerms(child, terms);
                }
            }
            case NotNode not -> { }     // excluded words are not scored
        }
    }
}
