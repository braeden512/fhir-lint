package com.braeden.fhirlint.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String title,
        String detail,
        Instant timestamp
) {
    public ErrorResponse(int status, String title, String detail) {
        this(status, title, detail, Instant.now());
    }
}
