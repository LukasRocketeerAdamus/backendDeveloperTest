package com.prorocketeers.lukas.routing.country.connector.exception;

public class CountryDataUnavailableException extends RuntimeException {

    public CountryDataUnavailableException(String message) {
        super(message);
    }

    public CountryDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
