package com.prorocketeers.lukas.routing.api.config;

import com.prorocketeers.lukas.routing.api.LogSanitizer;
import com.prorocketeers.lukas.routing.api.response.ErrorResponse;
import com.prorocketeers.lukas.routing.country.connector.exception.CountryDataUnavailableException;
import com.prorocketeers.lukas.routing.service.exception.CountryNotFoundException;
import com.prorocketeers.lukas.routing.service.exception.InvalidCountryCodeException;
import com.prorocketeers.lukas.routing.service.exception.RouteNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class RoutingExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingExceptionHandler.class);

    @ExceptionHandler(InvalidCountryCodeException.class)
    ResponseEntity<ErrorResponse> handleInvalidCountryCode(InvalidCountryCodeException ex) {
        LOG.warn("Rejecting request: {}", LogSanitizer.sanitize(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(CountryNotFoundException.class)
    ResponseEntity<ErrorResponse> handleCountryNotFound(CountryNotFoundException ex) {
        LOG.warn("Rejecting request: {}", LogSanitizer.sanitize(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RouteNotFoundException.class)
    ResponseEntity<ErrorResponse> handleRouteNotFound(RouteNotFoundException ex) {
        LOG.warn("Rejecting request: {}", LogSanitizer.sanitize(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(CountryDataUnavailableException.class)
    ResponseEntity<ErrorResponse> handleRoutingDataUnavailable(CountryDataUnavailableException ex) {
        LOG.error("Routing data source unavailable: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        LOG.error("Unexpected error handling request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal server error"));
    }
}
