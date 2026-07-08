package com.prorocketeers.lukas.routing.countryConnector;

import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDtoMapper;
import com.prorocketeers.lukas.routing.countryConnector.exception.CountryDataUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Separate from {@link CountryDataConnectorTest} because this needs the real autoconfigured
 * {@code RestClient.Builder} (so {@code spring.http.clients.read-timeout} actually applies) rather than a
 * bare {@code RestClient.builder()}, hence {@code @SpringBootTest} here and not there.
 */
@SpringBootTest
class CountryDataConnectorTimeoutTest {

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private CountryDtoMapper countryDtoMapper;

    @Test
    void fetchTimesOutInsteadOfHangingIndefinitelyOnASlowDataSource() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow.json", exchange -> {
            try {
                // far longer than spring.http.clients.read-timeout in application.yml, so the timeout
                // (not this sleep) is what ends the request.
                Thread.sleep(Duration.ofSeconds(30));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String slowDataUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow.json";
            CountryDataConnector client = new CountryDataConnector(restClientBuilder, countryDtoMapper, slowDataUrl);

            long start = System.currentTimeMillis();
            assertThatThrownBy(client::fetchCountries).isInstanceOf(CountryDataUnavailableException.class);
            long elapsedMs = System.currentTimeMillis() - start;

            // well below the endpoint's 30s sleep, proving the configured read-timeout aborted the request
            assertThat(elapsedMs).isLessThan(15_000);
        } finally {
            server.stop(0);
        }
    }
}
