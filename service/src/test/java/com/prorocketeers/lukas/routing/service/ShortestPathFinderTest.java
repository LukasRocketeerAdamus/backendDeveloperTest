package com.prorocketeers.lukas.routing.service;

import com.prorocketeers.lukas.routing.country.connector.dto.CountryDto;
import com.prorocketeers.lukas.routing.country.connector.CountryDataConnector;
import com.prorocketeers.lukas.routing.service.exception.CountryNotFoundException;
import com.prorocketeers.lukas.routing.service.exception.InvalidCountryCodeException;
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

    // AAA - BBB - CCC - DDD
    //        |           |
    //        EEE --------+
    private final List<CountryDto> countries = List.of(
            new CountryDto("AAA", List.of("BBB")),
            new CountryDto("BBB", List.of("AAA", "CCC", "EEE")),
            new CountryDto("CCC", List.of("BBB", "DDD")),
            new CountryDto("DDD", List.of("CCC", "EEE")),
            new CountryDto("EEE", List.of("BBB", "DDD")),
            new CountryDto("FFF", List.of())
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
        // AAA -> DDD via BBB -> EEE -> DDD (3 hops) is shorter than BBB -> CCC -> DDD (also 3 hops via
        // AAA-BBB-CCC-DDD), BFS must return a shortest (not just any) path of minimal length.
        var path = shortestPathFinder.findPath("AAA", "DDD");

        assertThat(path).hasSize(4);
        assertThat(path.getFirst()).isEqualTo("AAA");
        assertThat(path.getLast()).isEqualTo("DDD");
    }

    @Test
    void returnsSingleElementPathWhenOriginEqualsDestination() {
        assertThat(shortestPathFinder.findPath("AAA", "AAA")).containsExactly("AAA");
    }

    @Test
    void isCaseInsensitive() {
        assertThat(shortestPathFinder.findPath("aaa", "bbb")).containsExactly("AAA", "BBB");
    }

    @Test
    void throwsInvalidCountryCodeExceptionForMalformedOrigin() {
        assertThatThrownBy(() -> shortestPathFinder.findPath("A1", "AAA"))
                .isInstanceOf(InvalidCountryCodeException.class);
    }

    @Test
    void throwsInvalidCountryCodeExceptionForMalformedDestination() {
        assertThatThrownBy(() -> shortestPathFinder.findPath("AAA", "TOOLONG"))
                .isInstanceOf(InvalidCountryCodeException.class);
    }

    @Test
    void throwsCountryNotFoundExceptionForUnknownOrigin() {
        assertThatThrownBy(() -> shortestPathFinder.findPath("ZZZ", "AAA"))
                .isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    void throwsCountryNotFoundExceptionForUnknownDestination() {
        assertThatThrownBy(() -> shortestPathFinder.findPath("AAA", "ZZZ"))
                .isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    void throwsRouteNotFoundExceptionWhenNodesAreDisconnected() {
        assertThatThrownBy(() -> shortestPathFinder.findPath("AAA", "FFF"))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void routeIsFoundInBothDirectionsEvenWhenSourceDataOnlyListsTheBorderOneWay() {
        // Mirrors a real data quirk in the mledoze dataset: LKA lists IND as a border, but IND's own list
        // omits LKA back. The graph must be treated as undirected regardless of which side records the edge.
        given(countryDataConnector.fetchCountries()).willReturn(List.of(
                new CountryDto("XXX", List.of("YYY")),
                new CountryDto("YYY", List.of())
        ));

        assertThat(shortestPathFinder.findPath("XXX", "YYY")).containsExactly("XXX", "YYY");
        assertThat(shortestPathFinder.findPath("YYY", "XXX")).containsExactly("YYY", "XXX");
    }

    @Test
    void isCaseInsensitiveRegardlessOfJvmDefaultLocale() {
        // Under a Turkish/Azerbaijani default locale, "fin".toUpperCase() (without Locale.ROOT) yields
        // "FİN" (dotted İ) instead of "FIN", which would wrongly reject this valid code.
        given(countryDataConnector.fetchCountries()).willReturn(List.of(new CountryDto("FIN", List.of())));

        var previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertThat(shortestPathFinder.findPath("fin", "fin")).containsExactly("FIN");
        } finally {
            Locale.setDefault(previousLocale);
        }
    }
}
