package com.prorocketeers.lukas.routing.api.response;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PathMapper {

    default RoutingResponse map(List<String> path) {
        return new RoutingResponse(path);
    }
}
