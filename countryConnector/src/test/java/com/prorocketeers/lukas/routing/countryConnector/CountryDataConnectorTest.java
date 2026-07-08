package com.prorocketeers.lukas.routing.countryConnector;

import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDtoMapper;
import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDtoMapperImpl;
import com.prorocketeers.lukas.routing.countryConnector.exception.CountryDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryDataConnectorTest {

    private final CountryDtoMapper countryDtoMapper = new CountryDtoMapperImpl();

    @Test
    void throwsWhenDataUrlIsBlank() {
        CountryDataConnector client = new CountryDataConnector(RestClient.builder(), countryDtoMapper, "");

        assertThatThrownBy(client::fetchCountries)
                .isInstanceOf(CountryDataUnavailableException.class);
    }

    @Test
    void throwsWhenConnectionFails() {
        // port 1 is reserved/unassigned, so the connection is refused immediately.
        CountryDataConnector client = new CountryDataConnector(RestClient.builder(), countryDtoMapper, "http://127.0.0.1:1/countries.json");

        assertThatThrownBy(client::fetchCountries)
                .isInstanceOf(CountryDataUnavailableException.class);
    }
}
