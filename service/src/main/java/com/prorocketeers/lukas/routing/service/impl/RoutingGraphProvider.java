package com.prorocketeers.lukas.routing.service.impl;

import com.prorocketeers.lukas.routing.country.connector.CountryDataConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the id-indexed country graph and caches the result (see "routingGraph" cache, configured in
 * application.yml) — this is the expensive-ish, request-independent step (fetch + map + index), so it's
 * cached instead of the raw server response, which spared {@link ShortestPathFinder} from redoing the
 * mapping/indexing on every request even on a cache hit.
 * <p>
 * {@link CountryDataConnector#fetchCountries()} throws (never returns a partial/placeholder result) when the
 * data source is unavailable, and that exception is left to propagate out of {@link #getGraph()}
 * uncaught — Spring's cache abstraction only caches a normal return value, so a failed fetch is never
 * cached and is retried on the next call.
 */
@Component
public class RoutingGraphProvider {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingGraphProvider.class);

    private final CountryDataConnector countryDataConnector;
    private final CountryGraphMapper countryGraphMapper;

    public RoutingGraphProvider(CountryDataConnector countryDataConnector, CountryGraphMapper countryGraphMapper) {
        this.countryDataConnector = countryDataConnector;
        this.countryGraphMapper = countryGraphMapper;
    }

    @Cacheable("routingGraph")
    public Map<String, GraphNode> getGraph() {
        var nodes = countryGraphMapper.toGraphNodes(countryDataConnector.fetchCountries());

        var neighborsById = new LinkedHashMap<String, Set<String>>();
        for (var node : nodes) {
            if (neighborsById.putIfAbsent(node.id(), new LinkedHashSet<>()) != null) {
                LOG.warn("Duplicate country code {} in routing data source, keeping the first occurrence",
                        node.id());
            }
        }

        // Sanitize data
        for (var node : nodes) {
            for (var neighborId : node.neighbors()) {
                var reverseNeighbors = neighborsById.get(neighborId);
                if (reverseNeighbors == null) {
                    continue;
                }
                neighborsById.get(node.id()).add(neighborId);
                reverseNeighbors.add(node.id());
            }
        }

        var nodesById = new LinkedHashMap<String, GraphNode>();
        neighborsById.forEach((id, neighbors) -> nodesById.put(id, new GraphNode(id, List.copyOf(neighbors))));
        return nodesById;
    }
}
