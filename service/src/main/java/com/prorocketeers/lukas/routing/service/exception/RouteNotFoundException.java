package com.prorocketeers.lukas.routing.service.exception;

public class RouteNotFoundException extends RuntimeException {

    public RouteNotFoundException(String origin, String destination) {
        super("No route found between %s and %s".formatted(origin, destination));
    }
}