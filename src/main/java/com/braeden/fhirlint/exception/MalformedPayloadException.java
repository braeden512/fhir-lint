package com.braeden.fhirlint.exception;

public class MalformedPayloadException extends RuntimeException {
    public MalformedPayloadException(String message) {
        super(message);
    }
}
