package com.daveai.searchengine.models;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;

/**
 * The JSON body of every error response.
 *
 * @param status    the HTTP status code, e.g. 404
 * @param error     the status name, e.g. "Not Found"
 * @param message   what went wrong, safe to show to the client
 * @param path      the request path that failed, e.g. /api/v1/documents/missing.txt
 * @param timestamp when the error happened
 */
public record ExceptionResponse(int status, String error, String message, String path, Instant timestamp) {

    public static ExceptionResponse of(HttpStatusCode status, String message, String path) {
        // the status name for a known code; codes Spring has no name for fall back to the number
        HttpStatus known = HttpStatus.resolve(status.value());
        String error = (known == null) ? String.valueOf(status.value()) : known.getReasonPhrase();
        return new ExceptionResponse(status.value(), error, message, path, Instant.now());
    }
}
