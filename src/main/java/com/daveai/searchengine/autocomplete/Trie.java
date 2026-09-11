package com.daveai.searchengine.autocomplete;


import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prefix tree of indexed terms, used for query autocomplete.
 * <p>
 * Each node is one letter. Words that start with the same letters share a path, so every word
 * beginning with a prefix sits below that prefix's node:
 * <pre>
 * (root)
 *  └─ j
 *     └─ a
 *        ├─ r                  ← "jar"         (weight 1)
 *        └─ v
 *           └─ a               ← "java"        (weight 3)
 *              └─ s─c─r─i─p─t  ← "javascript"  (weight 2)
 * </pre>
 * A word's <b>weight</b> is the number of documents that contain it. The Indexer calls
 * {@link #insert} once per unique term per document, and {@link #remove} once per term when a
 * document is removed. Common words are therefore suggested first, and words from deleted
 * documents disappear.
 * <p>
 * <b>Thread safety:</b> autocomplete reads while the indexer writes. Children are stored in a
 * {@link ConcurrentHashMap}, {@code insert} and {@code remove} are {@code synchronized}, and node
 * fields are {@code volatile}, so readers always see the latest writes.
 */
public class Trie {

    private final TrieNode root;

    /** Creates an empty trie. */
    public Trie() {
        this.root = new TrieNode();
    }

    /**
     * Adds one occurrence of a word. Call it once for each document that contains the word;
     * inserting the same word again increases its weight.
     *
     * @param word the word to add; not null or blank; stored lowercased
     */
    public synchronized void insert(String word) {
        Objects.requireNonNull(word, "word must not be null");
        if (word.isBlank()) {
            throw new IllegalArgumentException("word must not be blank");
        }

        TrieNode node = root;
        for (char ch : word.toLowerCase(Locale.ROOT).toCharArray()) {
            // Move to the child for this letter, creating it the first time the letter appears here.
            // Does the same as containsKey + put + get, in one call.
            node = node.getChildren().computeIfAbsent(ch, c -> new TrieNode());
        }
        node.setWord(true);
        node.setWeight(node.getWeight() + 1);
    }

    /**
     * Returns the known words that start with a prefix, most common first.
     *
     * @param prefix what the user has typed so far; null or blank gives an empty list; case is ignored
     * @param limit  the maximum number of suggestions; at least 1
     * @return up to {@code limit} words, sorted by weight (highest first), then alphabetically
     * @throws IllegalArgumentException if {@code limit} is less than 1
     */
    public List<String> getSuggestions(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        if (limit < 1) {
            throw new IllegalArgumentException("the limit cannot be less than one");
        }
        // Lowercase once, then walk using the lowercased letters.
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);

        // Job 1: walk down to the node of the prefix's last letter ("jav" -> j, a, v).
        TrieNode node = root;
        for (char ch : lowerPrefix.toCharArray()) {
            node = node.getChildren().get(ch);
            if (node == null) {
                return List.of();   // a letter is missing, so no word starts with this prefix
            }
        }

        // Job 2: collect every word at or below that node. The word being built starts as the prefix.
        List<Suggestion> found = new ArrayList<>();
        collect(node, new StringBuilder(lowerPrefix), found);

        // Most common first, ties A to Z, then keep only the first `limit` words.
        return found.stream()
                .sorted(Comparator.comparingInt(Suggestion::weight).reversed()
                        .thenComparing(Suggestion::word))
                .limit(limit)
                .map(Suggestion::word)
                .toList();
    }

    /**
     * Depth-first search: adds the word ending at {@code node}, if there is one, then every word below it.
     *
     * @param node        the node reached so far
     * @param currentWord the letters from the root down to {@code node}
     * @param found       where the words are collected
     */
    private void collect(TrieNode node, StringBuilder currentWord, List<Suggestion> found) {
        if (node.isWord()) {
            found.add(new Suggestion(currentWord.toString(), node.getWeight()));
        }
        for (Map.Entry<Character, TrieNode> child : node.getChildren().entrySet()) {
            currentWord.append(child.getKey());                  // go down one letter
            collect(child.getValue(), currentWord, found);
            currentWord.deleteCharAt(currentWord.length() - 1);  // come back up, so the next child starts from the same letters
        }
    }

    /**
     * Removes one occurrence of a word. Call it once for each removed document that contained the word.
     * When the weight reaches 0 the word is no longer suggested; longer words below it (such as
     * "javascript" below "java") are kept. Does nothing if the word was never inserted.
     *
     * @param word the word to remove; not null
     */
    public synchronized void remove(String word) {
        Objects.requireNonNull(word, "word must not be null");

        TrieNode node = root;
        for (char ch : word.toLowerCase(Locale.ROOT).toCharArray()) {
            node = node.getChildren().get(ch);
            if (node == null) {
                return;   // the word was never inserted
            }
        }
        if (!node.isWord()) {
            return;       // the path exists only as part of a longer word, e.g. "jav"
        }
        int weight = node.getWeight() - 1;
        if (weight == 0) {
            node.setWord(false);
        }
        node.setWeight(weight);
    }

    /** A word found during a search, with its weight, used for sorting. */
    private record Suggestion(String word, int weight) {
    }

    /** One letter in the trie. */
    @Getter
    @Setter
    private static class TrieNode {
        /** The next letters. ConcurrentHashMap so reading while inserting is safe. */
        private final Map<Character, TrieNode> children = new ConcurrentHashMap<>();
        /** True if a word ends at this node. */
        private volatile boolean isWord;
        /** Number of documents containing the word that ends here; 0 if no word ends here. */
        private volatile int weight;
    }

}
