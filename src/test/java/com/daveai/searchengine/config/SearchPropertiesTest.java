package com.daveai.searchengine.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchPropertiesTest {

    private static final Path REPO = Path.of("documents");

    @Test
    void acceptsIntervalOfExactlyTwoHours() {
        assertThatCode(() -> new SearchProperties(REPO, Duration.ofHours(2), 10)).doesNotThrowAnyException();
    }

    @Test
    void rejectsIntervalLongerThanTwoHours() {
        assertThatThrownBy(() -> new SearchProperties(REPO, Duration.ofHours(2).plusSeconds(1), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reindex-interval");
    }

    @Test
    void rejectsZeroOrNegativeInterval() {
        assertThatThrownBy(() -> new SearchProperties(REPO, Duration.ZERO, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchProperties(REPO, Duration.ofMinutes(-5), 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAutocompleteLimitBelowOne() {
        assertThatThrownBy(() -> new SearchProperties(REPO, Duration.ofMinutes(30), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("autocomplete-limit");
    }

    @Test
    void rejectsMissingValues() {
        assertThatThrownBy(() -> new SearchProperties(null, Duration.ofMinutes(30), 10))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SearchProperties(REPO, null, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordExposesValues() {
        SearchProperties props = new SearchProperties(REPO, Duration.ofMinutes(30), 10);
        assertThat(props.repositoryPath()).isEqualTo(REPO);
        assertThat(props.reindexInterval()).isEqualTo(Duration.ofMinutes(30));
        assertThat(props.autocompleteLimit()).isEqualTo(10);
    }
}
