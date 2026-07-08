package com.prorocketeers.lukas.routing.api;

import com.prorocketeers.lukas.routing.api.response.IPathMapper;
import com.prorocketeers.lukas.routing.api.response.RoutingResponse;
import com.prorocketeers.lukas.routing.service.IPathFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/routing")
public class RoutingController {

    private static final Logger log = LoggerFactory.getLogger(RoutingController.class);

    private final IPathFinder pathFinder;
    private final IPathMapper pathMapper;

    public RoutingController(IPathFinder pathFinder, IPathMapper pathMapper) {
        this.pathFinder = pathFinder;
        this.pathMapper = pathMapper;
    }

    @GetMapping("/{origin}/{destination}")
    public RoutingResponse route(@PathVariable String origin, @PathVariable String destination) {
        log.debug("Routing request: {} -> {}", LogSanitizer.sanitize(origin), LogSanitizer.sanitize(destination));

        var path = pathFinder.findPath(origin, destination);
        return pathMapper.map(path);
    }
}
