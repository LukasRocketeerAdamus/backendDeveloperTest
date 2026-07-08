package com.prorocketeers.lukas.routing.service;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Kept out of {@code com.prorocketeers.lukas.Application} (in the {@code api} module) deliberately:
 * {@code @WebMvcTest} slice tests there use {@code Application} as their root configuration, and
 * {@code @EnableCaching} on {@code Application} itself would require a {@code CacheManager} bean that the
 * web slice doesn't provide. Living in {@code service} instead — the module that actually owns the
 * {@code @Cacheable} method on {@link com.prorocketeers.lukas.routing.service.impl.RoutingGraphProvider}
 * — also makes this module self-sufficient: its own tests can exercise real caching behavior without any
 * dependency on {@code api}.
 */
@Configuration
@EnableCaching
class CacheConfig {
}
