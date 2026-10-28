package com.daveai.searchengine.models;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The outcome of a full rescan of the document repository.
 *
 * @param indexed    how many files were (re)indexed
 * @param removed    how many documents were dropped because their file is gone
 * @param failed     ids of the files that could not be read; they are left out of the index
 * @param tookMillis how long the rescan took, in milliseconds
 * @param finishedAt when the rescan finished
 */
public record RefreshReport(int indexed, int removed, List<String> failed, long tookMillis, Instant finishedAt) {

    public RefreshReport {
        failed = List.copyOf(Objects.requireNonNull(failed, "failed must not be null"));
        Objects.requireNonNull(finishedAt, "finishedAt must not be null");
    }
}
