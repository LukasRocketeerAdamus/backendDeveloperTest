package com.prorocketeers.lukas.routing.service.impl;

import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CountryGraphMapper {

    @Mapping(source = "code", target = "id")
    @Mapping(source = "borders", target = "neighbors")
    GraphNode toGraphNode(CountryDto country);

    List<GraphNode> toGraphNodes(List<CountryDto> countries);
}
