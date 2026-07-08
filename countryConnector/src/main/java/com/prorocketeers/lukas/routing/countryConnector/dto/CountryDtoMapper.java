package com.prorocketeers.lukas.routing.countryConnector.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CountryDtoMapper {

    @Mapping(source = "cca3", target = "code")
    @Mapping(source = "borders", target = "borders")
    CountryDto toCountryDto(CountryJsonDto raw);

    List<CountryDto> toCountryDtos(List<CountryJsonDto> raw);
}
