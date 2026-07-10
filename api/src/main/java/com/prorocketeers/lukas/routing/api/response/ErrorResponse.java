package com.prorocketeers.lukas.routing.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ErrorResponse(
        @Schema(description = "Human-readable error message", example = "Unknown country code: ZZZ")
        String error) {
}
