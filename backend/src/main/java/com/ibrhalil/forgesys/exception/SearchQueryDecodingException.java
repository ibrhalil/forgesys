package com.ibrhalil.forgesys.exception;

/**
 * Malformed or oversized {@code ?sq=} search query param (K-55) — mapped to
 * 400 {@code validation_error} by the global handler.
 */
public class SearchQueryDecodingException extends RuntimeException {

    public SearchQueryDecodingException(String message) {
        super(message);
    }
}
