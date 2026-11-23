package com.smartedu.common.api;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        Instant timestamp,
        boolean success,
        T data,
        String message
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Instant.now(), true, data, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(Instant.now(), true, data, message);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(Instant.now(), false, null, message);
    }
}
