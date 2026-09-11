package com.daveai.searchengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Typed view of the {@code search.*} settings in application.yaml.
 * Invalid values fail at startup instead of surfacing later at runtime.
 *
 * @param repositoryPath    folder whose files are indexed
 * @param reindexInterval   time between repository scans; greater than zero and at most 2 hours
 * @param autocompleteLimit maximum number of autocomplete suggestions; at least 1
 */
@ConfigurationProperties(prefix = "search")
public record SearchProperties(Path repositoryPath, Duration reindexInterval, int autocompleteLimit) {

    /** The brief requires new documents to be indexed within 2 hours. */
    public static final Duration MAX_REINDEX_INTERVAL = Duration.ofHours(2);

    public SearchProperties {
        Objects.requireNonNull(repositoryPath, "search.repository-path must be set");
        Objects.requireNonNull(reindexInterval, "search.reindex-interval must be set");
        if (reindexInterval.isNegative() || reindexInterval.isZero()
                || reindexInterval.compareTo(MAX_REINDEX_INTERVAL) > 0) {
            throw new IllegalArgumentException(
                    "search.reindex-interval must be greater than 0 and at most " + MAX_REINDEX_INTERVAL
                            + ", was " + reindexInterval);
        }
        if (autocompleteLimit < 1) {
            throw new IllegalArgumentException(
                    "search.autocomplete-limit must be at least 1, was " + autocompleteLimit);
        }
    }
}
