package com.prorocketeers.lukas.routing.countryConnector;

import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDto;
import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDtoMapper;
import com.prorocketeers.lukas.routing.countryConnector.dto.CountryJsonDto;
import com.prorocketeers.lukas.routing.countryConnector.exception.CountryDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Fetches the source country data from the configured URL.
 * Not cached here — {@link com.prorocketeers.lukas.routing.service.impl.RoutingGraphProvider} caches the mapped/indexed
 * graph built from this data instead, so this method runs on every real cache miss of that graph cache.
 */
@Component
public class CountryDataConnector {

    private static final Logger log = LoggerFactory.getLogger(CountryDataConnector.class);

    private final RestClient restClient;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final CountryDtoMapper countryDtoMapper;
    private final String dataUrl;

    public CountryDataConnector(RestClient.Builder restClientBuilder, CountryDtoMapper countryDtoMapper,
                                 @Value("${routing.data-url:}") String dataUrl) {
        this.restClient = restClientBuilder.build();
        this.countryDtoMapper = countryDtoMapper;
        this.dataUrl = dataUrl;
    }


    public List<CountryDto> fetchCountries() {
        if (!StringUtils.hasText(dataUrl)) {
            throw new CountryDataUnavailableException("routing.data-url is not configured");
        }

        // only reached on a RoutingGraphProvider cache miss, so this also doubles as an "actually hit
        // the network" signal.
        log.info("Fetching routing data from {}", dataUrl);
        try {
            // raw.githubusercontent.com serves .json files as text/plain, so the body is read as
            // a String and parsed explicitly rather than relying on RestClient's content-type-based
            // message converter selection.
            String rawJson = restClient.get().uri(dataUrl).retrieve().body(String.class);
            List<CountryJsonDto> rawCountries = jsonMapper.readValue(rawJson,
                    jsonMapper.getTypeFactory().constructCollectionType(List.class, CountryJsonDto.class));
            List<CountryDto> countries = countryDtoMapper.toCountryDtos(rawCountries);
            log.info("Fetched {} countries from {}", countries.size(), dataUrl);
            return countries;
        } catch (Exception e) {
            log.error("Failed to fetch routing data from {}", dataUrl, e);
            throw new CountryDataUnavailableException("Failed to fetch routing data from " + dataUrl, e);
        }
    }
}
