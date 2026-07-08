package com.prorocketeers.lukas.routing.service.impl;

import com.prorocketeers.lukas.routing.service.IPathFinder;
import com.prorocketeers.lukas.routing.service.exception.CountryNotFoundException;
import com.prorocketeers.lukas.routing.service.exception.RouteNotFoundException;
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

/**
 * Finds the shortest path (fewest border crossings) between two countries via BFS —
 * the graph is unweighted, so BFS already yields the shortest path.
 */
@Component
public class ShortestPathFinder implements IPathFinder {

    private final RoutingGraphProvider routingGraphProvider;

    public ShortestPathFinder(RoutingGraphProvider routingGraphProvider) {
        this.routingGraphProvider = routingGraphProvider;
    }

    @Override
    public List<String> findPath(String origin, String destination) {
        Map<String, GraphNode> nodesById = routingGraphProvider.getGraph();

        // Locale.ROOT, not the platform default: under a Turkish/Azerbaijani default locale,
        // "fin".toUpperCase() yields "FİN" (dotted İ), not "FIN", which would wrongly reject valid codes.
        String originId = origin.toUpperCase(Locale.ROOT);
        String destinationId = destination.toUpperCase(Locale.ROOT);

        if (!nodesById.containsKey(originId)) {
            throw new CountryNotFoundException(originId);
        }
        if (!nodesById.containsKey(destinationId)) {
            throw new CountryNotFoundException(destinationId);
        }

        if (originId.equals(destinationId)) {
            return List.of(originId);
        }

        Map<String, String> predecessors = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(originId);
        visited.add(originId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(destinationId)) {
                return buildPath(predecessors, originId, destinationId);
            }

            GraphNode node = nodesById.get(current);
            if (node == null) {
                continue;
            }
            for (String neighbor : node.neighbors()) {
                if (visited.add(neighbor)) {
                    predecessors.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        throw new RouteNotFoundException(originId, destinationId);
    }

    private List<String> buildPath(Map<String, String> predecessors, String origin, String destination) {
        List<String> path = new ArrayList<>();
        String current = destination;
        while (!current.equals(origin)) {
            path.add(current);
            current = predecessors.get(current);
        }
        path.add(origin);
        Collections.reverse(path);
        return path;
    }

}
