package com.prorocketeers.lukas.routing.service.exception;

public class InvalidCountryCodeException extends RuntimeException {

    public InvalidCountryCodeException(String code) {
        super("Invalid country code: %s (expected ISO 3166-1 alpha-3 format)".formatted(code));
    }
}
