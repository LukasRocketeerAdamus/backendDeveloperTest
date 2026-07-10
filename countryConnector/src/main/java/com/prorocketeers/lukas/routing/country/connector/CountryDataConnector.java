package com.prorocketeers.lukas.routing.country.connector;

import com.prorocketeers.lukas.routing.country.connector.dto.CountryDto;
import com.prorocketeers.lukas.routing.country.connector.dto.CountryDtoMapper;
import com.prorocketeers.lukas.routing.country.connector.dto.CountryJsonDto;
import com.prorocketeers.lukas.routing.country.connector.exception.CountryDataUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
public class CountryDataConnector {

    private static final Logger LOG = LoggerFactory.getLogger(CountryDataConnector.class);

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

        LOG.info("Fetching routing data from {}", dataUrl);
        try {
            var rawJson = restClient.get().uri(dataUrl).retrieve().body(String.class);
            List<CountryJsonDto> rawCountries = jsonMapper.readValue(rawJson,
                    jsonMapper.getTypeFactory().constructCollectionType(List.class, CountryJsonDto.class));
            var countries = countryDtoMapper.toCountryDtos(rawCountries);
            LOG.info("Fetched {} countries from {}", countries.size(), dataUrl);
            return countries;
        } catch (Exception e) {
            LOG.error("Failed to fetch routing data from {}", dataUrl, e);
            throw new CountryDataUnavailableException("Failed to fetch routing data from " + dataUrl, e);
        }
    }
}
