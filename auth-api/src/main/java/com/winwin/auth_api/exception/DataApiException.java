package com.winwin.auth_api.exception;

public class DataApiException extends RuntimeException {
    public DataApiException() {
        super("Transformation service unavailable");
    }
}
