package com.prorocketeers.lukas.routing.api;

import com.prorocketeers.lukas.routing.api.response.ErrorResponse;
import com.prorocketeers.lukas.routing.api.response.PathMapper;
import com.prorocketeers.lukas.routing.api.response.RoutingResponse;
import com.prorocketeers.lukas.routing.service.PathFinder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/routing")
@Tag(name = "Routing", description = "Shortest border-crossing path between two countries")
public class RoutingController {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingController.class);

    private final PathFinder pathFinder;
    private final PathMapper pathMapper;

    public RoutingController(PathFinder pathFinder, PathMapper pathMapper) {
        this.pathFinder = pathFinder;
        this.pathMapper = pathMapper;
    }

    @Operation(
            summary = "Find the shortest route between two countries",
            description = "Runs a BFS over the country border graph to find the path with the fewest "
                    + "border crossings between the origin and destination country."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Route found",
                    content = @Content(schema = @Schema(implementation = RoutingResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Origin/destination isn't a well-formed ISO 3166-1 alpha-3 code, the code "
                            + "is unknown, or no route exists between them",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Country border data source is unavailable",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{origin}/{destination}")
    public RoutingResponse route(
            @Parameter(description = "ISO 3166-1 alpha-3 code of the origin country", example = "BEL")
            @PathVariable String origin,
            @Parameter(description = "ISO 3166-1 alpha-3 code of the destination country", example = "FRA")
            @PathVariable String destination) {
        LOG.debug("Routing request: {} -> {}", LogSanitizer.sanitize(origin), LogSanitizer.sanitize(destination));

        var path = pathFinder.findPath(origin, destination);
        return pathMapper.map(path);
    }
}
