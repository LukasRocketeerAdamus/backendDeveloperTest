package com.prorocketeers.lukas.routing.service.exception;

public class CountryNotFoundException extends RuntimeException {

    public CountryNotFoundException(String code) {
        super("Unknown country code: %s".formatted(code));
    }
}