package com.daveai.searchengine.autocomplete;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrieTest {

    private final Trie trie = new Trie();

    /** Inserts a word as if it appeared in the given number of documents. */
    private void insert(String word, int documents) {
        for (int i = 0; i < documents; i++) {
            trie.insert(word);
        }
    }

    private void remove(String word, int documents) {
        for (int i = 0; i < documents; i++) {
            trie.remove(word);
        }
    }

    /** java in 3 documents, javascript in 2, jar in 1. */
    private void addJavaWords() {
        insert("java", 3);
        insert("javascript", 2);
        insert("jar", 1);
    }

    @Test
    void suggestsEveryWordStartingWithThePrefixMostCommonFirst() {
        addJavaWords();

        assertThat(trie.getSuggestions("ja", 10)).containsExactly("java", "javascript", "jar");
    }

    @Test
    void longerPrefixNarrowsTheSuggestions() {
        addJavaWords();

        assertThat(trie.getSuggestions("jav", 10)).containsExactly("java", "javascript");
    }

    @Test
    void prefixThatIsItselfAWordIsSuggested() {
        addJavaWords();

        assertThat(trie.getSuggestions("java", 10)).containsExactly("java", "javascript");
    }

    @Test
    void prefixCaseIsIgnored() {
        addJavaWords();

        assertThat(trie.getSuggestions("JA", 10)).containsExactly("java", "javascript", "jar");
    }

    @Test
    void wordsAreStoredLowercased() {
        trie.insert("Java");

        assertThat(trie.getSuggestions("ja", 10)).containsExactly("java");
    }

    @Test
    void unknownPrefixReturnsEmptyList() {
        addJavaWords();

        assertThat(trie.getSuggestions("x", 10)).isEmpty();
        assertThat(trie.getSuggestions("jxy", 10)).isEmpty();
    }

    @Test
    void nullEmptyOrBlankPrefixReturnsEmptyList() {
        addJavaWords();

        assertThat(trie.getSuggestions(null, 10)).isEmpty();
        assertThat(trie.getSuggestions("", 10)).isEmpty();
        assertThat(trie.getSuggestions("   ", 10)).isEmpty();
    }

    @Test
    void wordsWithEqualWeightAreSortedAlphabetically() {
        trie.insert("cat");
        trie.insert("car");
        trie.insert("cab");

        assertThat(trie.getSuggestions("ca", 10)).containsExactly("cab", "car", "cat");
    }

    @Test
    void returnsNoMoreThanTheLimit() {
        addJavaWords();

        assertThat(trie.getSuggestions("ja", 2)).containsExactly("java", "javascript");
    }

    @Test
    void rejectsLimitBelowOne() {
        addJavaWords();

        assertThatThrownBy(() -> trie.getSuggestions("ja", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeLowersTheWeightAndTheWordStaysWhileWeightIsAboveZero() {
        addJavaWords();

        remove("java", 2);   // java now in 1 document, the same as jar

        assertThat(trie.getSuggestions("ja", 10)).containsExactly("javascript", "jar", "java");
    }

    @Test
    void wordDisappearsWhenItsWeightReachesZero() {
        addJavaWords();

        remove("java", 3);

        assertThat(trie.getSuggestions("ja", 10)).containsExactly("javascript", "jar");
    }

    @Test
    void removingAShorterWordKeepsLongerWordsBelowIt() {
        addJavaWords();

        remove("java", 3);

        assertThat(trie.getSuggestions("jav", 10)).containsExactly("javascript");
    }

    @Test
    void removingAWordThatWasNeverInsertedDoesNothing() {
        addJavaWords();

        assertThatCode(() -> {
            trie.remove("python");
            trie.remove("jav");     // only part of longer words, not a word itself
        }).doesNotThrowAnyException();
        assertThat(trie.getSuggestions("ja", 10)).containsExactly("java", "javascript", "jar");
    }

    @Test
    void insertRejectsNullOrBlankWord() {
        assertThatThrownBy(() -> trie.insert(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> trie.insert("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
