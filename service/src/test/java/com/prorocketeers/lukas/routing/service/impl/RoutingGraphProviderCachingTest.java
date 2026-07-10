package com.prorocketeers.lukas.routing.service.impl;

import com.prorocketeers.lukas.routing.country.connector.CountryDataConnector;
import com.prorocketeers.lukas.routing.country.connector.dto.CountryDto;
import com.prorocketeers.lukas.routing.country.connector.exception.CountryDataUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link RoutingGraphProvider#getGraph()}'s {@code @Cacheable} behavior through the real Spring
 * proxy. {@link CountryDataConnector} is mocked rather than exercised over real HTTP — this module trusts
 * that {@code fetchCountries()} itself is already correct (that's covered by countryConnector's own
 * tests) and only cares here about how many times it gets called.
 */
@SpringBootTest
class RoutingGraphProviderCachingTest {

    @Autowired
    private RoutingGraphProvider routingGraphProvider;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private CountryDataConnector countryDataConnector;

    @BeforeEach
    void resetCache() {
        cacheManager.getCache("routingGraph").clear();
    }

    @Test
    void secondCallIsServedFromCacheInsteadOfHittingTheDataSourceAgain() {
        when(countryDataConnector.fetchCountries()).thenReturn(List.of(new CountryDto("BEL", List.of("FRA"))));

        assertThat(routingGraphProvider.getGraph()).containsKey("BEL");
        assertThat(routingGraphProvider.getGraph()).containsKey("BEL");

        verify(countryDataConnector, times(1)).fetchCountries();
    }

    @Test
    void failedFetchIsNotCachedSoTheNextCallRetriesInsteadOfReplayingTheFailure() {
        when(countryDataConnector.fetchCountries())
                .thenThrow(new CountryDataUnavailableException("boom"))
                .thenReturn(List.of(new CountryDto("BEL", List.of("FRA"))));

        assertThatThrownBy(() -> routingGraphProvider.getGraph())
                .isInstanceOf(CountryDataUnavailableException.class);
        assertThat(routingGraphProvider.getGraph()).containsKey("BEL");

        verify(countryDataConnector, times(2)).fetchCountries();
    }
}
