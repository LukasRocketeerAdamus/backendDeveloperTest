package com.prorocketeers.lukas.routing.service.impl;

import com.prorocketeers.lukas.routing.service.PathFinder;
import com.prorocketeers.lukas.routing.service.exception.CountryNotFoundException;
import com.prorocketeers.lukas.routing.service.exception.InvalidCountryCodeException;
import com.prorocketeers.lukas.routing.service.exception.RouteNotFoundException;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the shortest path (fewest border crossings) between two countries via BFS —
 * the graph is unweighted, so BFS already yields the shortest path.
 */
@Component
public class ShortestPathFinder implements PathFinder {

    private static final Pattern ISO_3166_ALPHA_3 = Pattern.compile("[A-Za-z]{3}");

    private final RoutingGraphProvider routingGraphProvider;

    public ShortestPathFinder(RoutingGraphProvider routingGraphProvider) {
        this.routingGraphProvider = routingGraphProvider;
    }

    @Override
    public List<String> findPath(String origin, String destination) {
        var nodesById = routingGraphProvider.getGraph();

        var originId = validateCca3Code(origin, nodesById);
        var destinationId = validateCca3Code(destination, nodesById);

        if (originId.equals(destinationId)) {
            return List.of(originId);
        }

        var predecessors = new HashMap<String, String>();
        var visited = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(originId);
        visited.add(originId);

        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (current.equals(destinationId)) {
                return buildPath(predecessors, originId, destinationId);
            }

            var node = nodesById.get(current);
            if (node == null) {
                continue;
            }
            for (var neighbor : node.neighbors()) {
                if (visited.add(neighbor)) {
                    predecessors.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        throw new RouteNotFoundException(originId, destinationId);
    }

    private static @NonNull String validateCca3Code(String code, Map<String, GraphNode> nodesById) {
        if (!ISO_3166_ALPHA_3.matcher(code).matches()) {
            throw new InvalidCountryCodeException(code);
        }

        var id = code.toUpperCase(Locale.ROOT);
        if (!nodesById.containsKey(id)) {
            throw new CountryNotFoundException(id);
        }
        return id;
    }

    private List<String> buildPath(Map<String, String> predecessors, String origin, String destination) {
        var path = new ArrayList<String>();
        var current = destination;
        while (!current.equals(origin)) {
            path.add(current);
            current = predecessors.get(current);
        }
        path.add(origin);
        Collections.reverse(path);
        return path;
    }

}
