package com.prorocketeers.lukas.routing.country.connector;

import com.prorocketeers.lukas.routing.country.connector.dto.CountryDto;
import com.prorocketeers.lukas.routing.country.connector.dto.CountryDtoMapper;
import com.prorocketeers.lukas.routing.country.connector.dto.CountryDtoMapperImpl;
import com.prorocketeers.lukas.routing.country.connector.dto.CountryJsonDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CountryDtoMapperTest {

    private final CountryDtoMapper countryDtoMapper = new CountryDtoMapperImpl();

    @Test
    void mapsCca3AndBorders() {
        var countries = countryDtoMapper.toCountryDtos(List.of(
                new CountryJsonDto("BEL", List.of("FRA", "DEU", "NLD")),
                new CountryJsonDto("ABW", List.of())
        ));

        assertThat(countries).containsExactly(
                new CountryDto("BEL", List.of("FRA", "DEU", "NLD")),
                new CountryDto("ABW", List.of())
        );
    }
}
