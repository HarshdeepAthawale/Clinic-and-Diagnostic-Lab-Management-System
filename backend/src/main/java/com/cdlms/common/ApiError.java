package com.cdlms.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Standard error body for every API error: {@code { "error": ..., "code": ... }} (see Docs/API.md).
 * {@code fields} is only present for validation errors.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String code, Map<String, String> fields) {

    public ApiError(String error, String code) {
        this(error, code, null);
    }
}
