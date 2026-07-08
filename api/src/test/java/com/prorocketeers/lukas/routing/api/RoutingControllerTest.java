package com.prorocketeers.lukas.routing.api;

import com.prorocketeers.lukas.routing.api.response.IPathMapper;
import com.prorocketeers.lukas.routing.api.response.RoutingResponse;
import com.prorocketeers.lukas.routing.service.IPathFinder;
import com.prorocketeers.lukas.routing.service.exception.CountryNotFoundException;
import com.prorocketeers.lukas.routing.service.exception.RouteNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoutingController.class)
class RoutingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPathFinder pathFinder;

    @MockitoBean
    private IPathMapper pathMapper;

    @Test
    void returnsShortestPath() throws Exception {
        given(pathFinder.findPath(anyString(), anyString())).willReturn(List.of("BEL", "FRA"));
        given(pathMapper.map(any())).willReturn(new RoutingResponse(List.of("BEL", "FRA")));

        mockMvc.perform(get("/routing/BEL/FRA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route[0]").value("BEL"))
                .andExpect(jsonPath("$.route[1]").value("FRA"));
    }

    @Test
    void returns400WhenCountryUnknown() throws Exception {
        given(pathFinder.findPath(anyString(), anyString()))
                .willThrow(new CountryNotFoundException("ZZZ"));

        mockMvc.perform(get("/routing/ZZZ/FRA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown country code: ZZZ"));
    }

    @Test
    void returns400WhenRouteNotFound() throws Exception {
        given(pathFinder.findPath(anyString(), anyString()))
                .willThrow(new RouteNotFoundException("ABW", "FRA"));

        mockMvc.perform(get("/routing/ABW/FRA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No route found between ABW and FRA"));
    }

    @Test
    void returns500WithErrorResponseBodyForAnUnexpectedException() throws Exception {
        given(pathFinder.findPath(anyString(), anyString())).willThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/routing/BEL/FRA"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }
}
