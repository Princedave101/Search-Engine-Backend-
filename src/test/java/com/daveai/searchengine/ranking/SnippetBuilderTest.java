package com.daveai.searchengine.ranking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SnippetBuilderTest {

    @Test
    void buildsASnippetForAMatchNearTheEndOfShortText() {
        assertThatCode(() -> SnippetBuilder.buildSnippet("A short java document", "java"))
                .doesNotThrowAnyException();
        assertThat(SnippetBuilder.buildSnippet("A short java document", "java")).contains("java");
    }

    @Test
    void doesNotLeakTextFromEarlierSnippets() {
        String firstText = "a".repeat(40) + " java " + "a".repeat(40);
        String secondText = "b".repeat(40) + " java " + "b".repeat(40);
        String first = SnippetBuilder.buildSnippet(firstText, "java");
        String second = SnippetBuilder.buildSnippet(secondText, "java");

        assertThat(second).doesNotContain(first);
    }

    @Test
    void findsTheTermWhenTextBeforeItChangesLengthWhenLowercased() {
        // "İ".toLowerCase() is two characters, which would shift an index found in a lowercased copy.
        assertThat(SnippetBuilder.buildSnippet("İİİİİİİİİİ java", "java")).contains("java");
    }

    @Test
    void fallsBackToTheStartOfTheTextWhenTheTermIsMissing() {
        assertThat(SnippetBuilder.buildSnippet("matched by title only", "java")).startsWith("matched");
    }

    // A long text: 30 five-letter words, then "java", then 30 more. Each side is 179 characters.
    private static final String LONG_TEXT =
            "alpha ".repeat(30) + "java" + " omega".repeat(30);

    @Test
    void keepsAboutEightyCharactersOnEachSideOfTheTerm() {
        String snippet = SnippetBuilder.buildSnippet(LONG_TEXT, "java");
        String inside = snippet.replace("…", "");
        int termAt = inside.indexOf("java");

        // Cutting at a word boundary can only move each edge inwards, by less than one word.
        assertThat(termAt).isBetween(80 - 6, 80);
        assertThat(inside.length() - termAt - "java".length()).isBetween(80 - 6, 80);
    }

    @Test
    void cutsAtWordBoundaries() {
        String inside = SnippetBuilder.buildSnippet(LONG_TEXT, "java").replace("…", "");

        assertThat(inside).startsWith("alpha ").endsWith(" omega");
    }

    @Test
    void marksBothCutEndsWithAnEllipsis() {
        String snippet = SnippetBuilder.buildSnippet(LONG_TEXT, "java");

        assertThat(snippet).startsWith("…").endsWith("…").doesNotContain("....");
    }

    @Test
    void addsNoEllipsisAtAnEndThatWasNotCut() {
        String atStart = SnippetBuilder.buildSnippet("java" + " omega".repeat(30), "java");
        String atEnd = SnippetBuilder.buildSnippet("alpha ".repeat(30) + "java", "java");

        assertThat(atStart).startsWith("java").endsWith("…");
        assertThat(atEnd).startsWith("…").endsWith("java");
    }

    @Test
    void keepsTheWholeTextWhenItFitsInTheWindow() {
        assertThat(SnippetBuilder.buildSnippet("A short java document", "java"))
                .isEqualTo("A short java document");
    }

    @Test
    void cutsMidWordRatherThanDroppingEverythingWhenThereIsNoSpace() {
        String snippet = SnippetBuilder.buildSnippet("java" + "x".repeat(200), "java");

        assertThat(snippet).startsWith("javaxxx").endsWith("…");
    }
}
