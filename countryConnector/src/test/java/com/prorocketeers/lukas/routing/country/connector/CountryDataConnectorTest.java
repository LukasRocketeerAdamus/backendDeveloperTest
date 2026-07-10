package com.prorocketeers.lukas.routing.country.connector;

import com.prorocketeers.lukas.routing.country.connector.dto.CountryDtoMapper;
import com.prorocketeers.lukas.routing.country.connector.dto.CountryDtoMapperImpl;
import com.prorocketeers.lukas.routing.country.connector.exception.CountryDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryDataConnectorTest {

    private final CountryDtoMapper countryDtoMapper = new CountryDtoMapperImpl();

    @Test
    void throwsWhenDataUrlIsBlank() {
        var client = new CountryDataConnector(RestClient.builder(), countryDtoMapper, "");

        assertThatThrownBy(client::fetchCountries)
                .isInstanceOf(CountryDataUnavailableException.class);
    }

    @Test
    void throwsWhenConnectionFails() {
        // port 1 is reserved/unassigned, so the connection is refused immediately.
        var client = new CountryDataConnector(RestClient.builder(), countryDtoMapper, "http://127.0.0.1:1/countries.json");

        assertThatThrownBy(client::fetchCountries)
                .isInstanceOf(CountryDataUnavailableException.class);
    }
}
