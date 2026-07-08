package com.prorocketeers.lukas.routing.countryConnector;

import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDto;
import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDtoMapper;
import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDtoMapperImpl;
import com.prorocketeers.lukas.routing.countryConnector.dto.CountryJsonDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CountryDtoMapperTest {

    private final CountryDtoMapper countryDtoMapper = new CountryDtoMapperImpl();

    @Test
    void mapsCca3AndBorders() {
        List<CountryDto> countries = countryDtoMapper.toCountryDtos(List.of(
                new CountryJsonDto("BEL", List.of("FRA", "DEU", "NLD")),
                new CountryJsonDto("ABW", List.of())
        ));

        assertThat(countries).containsExactly(
                new CountryDto("BEL", List.of("FRA", "DEU", "NLD")),
                new CountryDto("ABW", List.of())
        );
    }
}
