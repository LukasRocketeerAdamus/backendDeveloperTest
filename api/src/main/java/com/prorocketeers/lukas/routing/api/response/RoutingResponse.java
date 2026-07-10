package com.prorocketeers.lukas.routing.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RoutingResponse(
        @Schema(description = "Ordered cca3 country codes from origin to destination, inclusive",
                example = "[\"BEL\", \"FRA\"]")
        List<String> route) {
}
