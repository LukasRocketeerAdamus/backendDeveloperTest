package com.prorocketeers.lukas.routing;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot bootstrap for this module. {@code @SpringBootTest} without an explicit
 * {@code classes=} finds its configuration by searching packages upwards from the test class; the real
 * {@link com.prorocketeers.lukas.Application} lives in the {@code api} module, which {@code service}
 * cannot depend on (that would invert the {@code api -> service} direction). Sitting one package above
 * both {@code .service} and {@code .countryConnector}, this class's default component scan covers this
 * module's own beans plus the {@code countryConnector} module's (classpath-visible via the module
 * dependency, scanning doesn't care about jar boundaries) — exactly what
 * {@link com.prorocketeers.lukas.routing.service.impl.RoutingGraphProviderCachingTest} needs.
 */
@SpringBootApplication
class ServiceTestApplication {
}
