package com.prorocketeers.lukas.routing.country.connector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.prorocketeers.lukas.routing.country.connector.CountryDataConnector;

import java.util.List;

/**
 * Raw Jackson binding target for the source country JSON (https://github.com/mledoze/countries) —
 * mirrors the source field names exactly. {@link CountryDtoMapper} maps this to {@link CountryDto},
 * so a future source field rename only requires touching this record and the mapper, not
 * {@link CountryDataConnector}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CountryJsonDto(String cca3, List<String> borders) {
}
