package com.prorocketeers.lukas.routing.country.connector.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CountryDtoMapper {

    @Mapping(source = "cca3", target = "code")
    CountryDto toCountryDto(CountryJsonDto raw);

    List<CountryDto> toCountryDtos(List<CountryJsonDto> raw);
}
