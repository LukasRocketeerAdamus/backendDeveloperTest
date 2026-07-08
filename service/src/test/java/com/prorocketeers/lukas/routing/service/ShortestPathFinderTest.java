package com.prorocketeers.lukas.routing.service;

import com.prorocketeers.lukas.routing.countryConnector.dto.CountryDto;
import com.prorocketeers.lukas.routing.countryConnector.CountryDataConnector;
import com.prorocketeers.lukas.routing.service.exception.CountryNotFoundException;
import com.prorocketeers.lukas.routing.service.exception.RouteNotFoundException;
import com.prorocketeers.lukas.routing.service.impl.CountryGraphMapper;
import com.prorocketeers.lukas.routing.service.impl.CountryGraphMapperImpl;
import com.prorocketeers.lukas.routing.service.impl.RoutingGraphProvider;
import com.prorocketeers.lukas.routing.service.impl.ShortestPathFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ShortestPathFinderTest {

    // A - B - C - D
    //     |       |
    //     E ------+
    private final List<CountryDto> countries = List.of(
            new CountryDto("A", List.of("B")),
            new CountryDto("B", List.of("A", "C", "E")),
            new CountryDto("C", List.of("B", "D")),
            new CountryDto("D", List.of("C", "E")),
            new CountryDto("E", List.of("B", "D")),
            new CountryDto("F", List.of())
    );

    private final CountryDataConnector countryDataConnector = mock(CountryDataConnector.class);
    private final CountryGraphMapper countryGraphMapper = new CountryGraphMapperImpl();
    private final RoutingGraphProvider routingGraphProvider =
            new RoutingGraphProvider(countryDataConnector, countryGraphMapper);
    private final ShortestPathFinder shortestPathFinder = new ShortestPathFinder(routingGraphProvider);

    @BeforeEach
    void prepareMocks() {
        given(countryDataConnector.fetchCountries()).willReturn(countries);
    }

    @Test
    void findsShortestPathOverDirectShortcut() {
        // A -> D via B -> E -> D (3 hops) is shorter than B -> C -> D (also 3 hops via A-B-C-D),
        // BFS must return a shortest (not just any) path of minimal length.
        List<String> path = shortestPathFinder.findPath("A", "D");

        assertThat(path).hasSize(4);
        assertThat(path.getFirst()).isEqualTo("A");
        assertThat(path.getLast()).isEqualTo("D");
    }

    @Test
    void returnsSingleElementPathWhenOriginEqualsDestination() {
        assertThat(shortestPathFinder.findPath("A", "A")).containsExactly("A");
    }

    @Test
    void isCaseInsensitive() {
        assertThat(shortestPathFinder.findPath("a", "b")).containsExactly("A", "B");
    }

    @Test
    void throwsCountryNotFoundExceptionForUnknownOrigin() {
        assertThatThrownBy(() -> shortestPathFinder.findPath("ZZZ", "A"))
                .isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    void throwsCountryNotFoundExceptionForUnknownDestination() {
        assertThatThrownBy(() -> shortestPathFinder.findPath("A", "ZZZ"))
                .isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    void throwsRouteNotFoundExceptionWhenNodesAreDisconnected() {
        assertThatThrownBy(() -> shortestPathFinder.findPath("A", "F"))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void routeIsFoundInBothDirectionsEvenWhenSourceDataOnlyListsTheBorderOneWay() {
        // Mirrors a real data quirk in the mledoze dataset: LKA lists IND as a border, but IND's own list
        // omits LKA back. The graph must be treated as undirected regardless of which side records the edge.
        given(countryDataConnector.fetchCountries()).willReturn(List.of(
                new CountryDto("X", List.of("Y")),
                new CountryDto("Y", List.of())
        ));

        assertThat(shortestPathFinder.findPath("X", "Y")).containsExactly("X", "Y");
        assertThat(shortestPathFinder.findPath("Y", "X")).containsExactly("Y", "X");
    }

    @Test
    void isCaseInsensitiveRegardlessOfJvmDefaultLocale() {
        // Under a Turkish/Azerbaijani default locale, "fin".toUpperCase() (without Locale.ROOT) yields
        // "FİN" (dotted İ) instead of "FIN", which would wrongly reject this valid code.
        given(countryDataConnector.fetchCountries()).willReturn(List.of(new CountryDto("FIN", List.of())));

        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertThat(shortestPathFinder.findPath("fin", "fin")).containsExactly("FIN");
        } finally {
            Locale.setDefault(previousLocale);
        }
    }
}
