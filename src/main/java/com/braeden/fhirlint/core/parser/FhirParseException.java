package com.braeden.fhirlint.core.parser;

/**
 * Thrown when an input cannot be parsed or fails pre-flight boundary verification.
 */
public class FhirParseException extends RuntimeException {
    public FhirParseException(String message) {
        super(message);
    }

    public FhirParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
