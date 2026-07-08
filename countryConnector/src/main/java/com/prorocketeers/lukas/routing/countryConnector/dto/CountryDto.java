package com.prorocketeers.lukas.routing.countryConnector.dto;

import java.util.List;


public record CountryDto(String code, List<String> borders) {
}