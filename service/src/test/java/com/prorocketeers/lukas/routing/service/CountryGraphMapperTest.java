package com.prorocketeers.lukas.routing.service;

import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDto;
import com.prorocketeers.lukas.routing.service.impl.CountryGraphMapper;
import com.prorocketeers.lukas.routing.service.impl.CountryGraphMapperImpl;
import com.prorocketeers.lukas.routing.service.impl.GraphNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CountryGraphMapperTest {

    private final CountryGraphMapper countryGraphMapper = new CountryGraphMapperImpl();

    @Test
    void mapsCodeToIdAndBordersToNeighbors() {
        List<CountryDto> countries = List.of(
                new CountryDto("BEL", List.of("FRA", "DEU", "NLD")),
                new CountryDto("ABW", List.of())
        );

        List<GraphNode> nodes = countryGraphMapper.toGraphNodes(countries);

        assertThat(nodes).containsExactly(
                new GraphNode("BEL", List.of("FRA", "DEU", "NLD")),
                new GraphNode("ABW", List.of())
        );
    }
}
