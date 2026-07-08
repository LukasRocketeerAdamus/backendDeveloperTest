package com.prorocketeers.lukas.routing.countryConnector;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot bootstrap for this module, needed by tests that require the real
 * autoconfigured {@code RestClient.Builder} (so {@code spring.http.clients.*} timeouts from
 * {@code src/test/resources/application.yml} actually apply) rather than a bare
 * {@code RestClient.builder()}.
 */
@SpringBootApplication
class CountryConnectorTestApplication {
}
