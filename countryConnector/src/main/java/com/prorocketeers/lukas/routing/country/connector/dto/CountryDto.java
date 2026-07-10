package com.prorocketeers.lukas.routing.country.connector.dto;

import java.util.List;


public record CountryDto(String code, List<String> borders) {
}