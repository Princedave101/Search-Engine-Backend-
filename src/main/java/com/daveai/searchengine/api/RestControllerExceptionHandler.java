package com.daveai.searchengine.api;


import com.daveai.searchengine.extraction.DocumentExtractionException;
import com.daveai.searchengine.extraction.UnsupportedDocumentTypeException;
import com.daveai.searchengine.models.ExceptionResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice
public class RestControllerExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ExceptionResponse> handleException(IllegalArgumentException e, HttpServletRequest request) {

        return response(HttpStatus.BAD_REQUEST, e.getMessage(), request);

    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ExceptionResponse> handleException(NoSuchElementException e, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<ExceptionResponse> handleException(UnsupportedDocumentTypeException e, HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage(), request);
    }

    @ExceptionHandler(DocumentExtractionException.class)
    public ResponseEntity<ExceptionResponse> handleException(DocumentExtractionException e, HttpServletRequest request) {
        // the message holds the full server path, so log it and send a fixed message instead
        log.warn(e.getMessage(), e);
        return response(HttpStatus.UNPROCESSABLE_CONTENT, "Could not read the document", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ExceptionResponse> handleException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        // e.g. ?page=abc
        return response(HttpStatus.BAD_REQUEST, "Invalid value '" + e.getValue() + "' for parameter '" + e.getName() + "'", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleException(Exception e, HttpServletRequest request) {
        // Spring's own exceptions (missing ?q=, unknown URL, wrong method, upload too large) carry their status; keep it
        if (e instanceof ErrorResponse errorResponse) {
            return response(errorResponse.getStatusCode(), errorResponse.getBody().getDetail(), request);
        }
        log.error("Unexpected error", e);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private static ResponseEntity<ExceptionResponse> response(HttpStatusCode status, String message, HttpServletRequest request) {
        return new ResponseEntity<>(ExceptionResponse.of(status, message, request.getRequestURI()), status);
    }
}
