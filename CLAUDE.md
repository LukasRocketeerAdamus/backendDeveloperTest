# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Spring Boot 4.1.0 (Spring Framework 7, Jackson 3) REST application on Java 25, group/artifact
`com.prorocketeers:beDevTest`. Endpoint: `GET /routing/{origin}/{destination}`. Data source is the country
list from https://github.com/mledoze/countries (`routing.data-url`), reduced to a graph of `{id, neighbors}`
per country (`cca3`/`borders` fields). The endpoint runs BFS (fewest border crossings) over that graph and
returns the path, or an error body: `400` if a code is malformed/unknown or no route exists, `503` if
`routing.data-url` isn't configured or the data source can't be reached/parsed.

3-module Maven reactor (`countryConnector`, `service`, `api`); root `pom.xml` is an aggregator
(`packaging=pom`) with no source of its own.

## Build & run

All commands run from the repo root; Maven's reactor handles module build order automatically.

- Compile: `mvn compile`
- Run tests (all modules): `mvn test`
- Run tests for one module: `mvn test -pl service` (or `-pl countryConnector`, `-pl api`)
- Run a single test: `mvn test -pl service -Dtest=ClassName#methodName` (`-pl` required once there's more
  than one module)
- Run the app: `mvn spring-boot:run -pl api` (port 8080; `api` is the only module with the Spring Boot
  plugin/an `Application` class)
- Package: `mvn package` — produces `api/target/api-1.0-SNAPSHOT.jar` (runnable fat jar) plus plain library
  jars for `countryConnector`/`service`
- Docker: `docker build -t bedevtest .` then `docker run -p 8080:8080 bedevtest` — multi-stage
  (`maven:3.9-eclipse-temurin-25` build, `eclipse-temurin:25-jre` run), non-root `appuser`. Override
  `routing.data-url` at runtime with `-e ROUTING_DATA_URL=...`.

## Architecture

Each layer is its own Maven module (compile-enforced dependency direction): `api` → `service` →
`countryConnector`. Package names mirror module names (`com.prorocketeers.lukas.routing.{service,api}`),
except `countryConnector`, whose package is `com.prorocketeers.lukas.routing.country.connector`. Don't add
cross-layer classes directly under `com.prorocketeers.lukas.routing`.

- `api` (`com.prorocketeers.lukas` + `.routing.api`) — runnable app + web layer. Depends on `service`.
  - `RoutingController` — `GET /routing/{origin}/{destination}`; depends on `service.PathFinder` and
    `PathMapper`. Annotated with springdoc (`@Tag`/`@Operation`/`@ApiResponses`) for OpenAPI docs.
  - `PathMapper` — MapStruct `@Mapper(componentModel = "spring")`, wraps `List<String>` into
    `RoutingResponse` via a `default` method (see MapStruct gotcha below).
  - `RoutingResponse` — `{route}`, ordered cca3 codes origin→destination inclusive.
  - `ErrorResponse` — `{error}`.
  - `RoutingExceptionHandler` (`@RestControllerAdvice`) — maps `InvalidCountryCodeException`,
    `CountryNotFoundException`, `RouteNotFoundException` → `400`; `CountryDataUnavailableException` → `503`;
    any other `Exception` → `500` with a generic `ErrorResponse("Internal server error")` (exception logged
    server-side, never leaked to the client).
  - `LogSanitizer` — strips control characters (`\p{Cntrl}`) from any value before logging. Always sanitize
    user-supplied `origin`/`destination` (or messages derived from them) before logging, to prevent CR/LF
    log injection.
  - `RequestTimingInterceptor` / `WebMvcConfig` — logs method, URI, status, duration for every request
    under `/routing/**`.
- `service` (`com.prorocketeers.lukas.routing.service`) — business logic. Depends on `countryConnector`.
  - `PathFinder` — `findPath(origin, destination) -> List<String>`.
  - `impl.GraphNode` — `{id, neighbors}`.
  - `exception.InvalidCountryCodeException` / `CountryNotFoundException` / `RouteNotFoundException`.
  - `impl.CountryGraphMapper` — MapStruct mapper, `CountryDto` → `GraphNode` (code→id, borders→neighbors).
  - `impl.RoutingGraphProvider` — fetches + maps + indexes into `Map<String, GraphNode>` keyed by `id`;
    symmetrizes the graph (adds edges in both directions, since the source data doesn't always list a
    border on both sides). `@Cacheable("routingGraph")` — see Caching.
  - `impl.ShortestPathFinder implements PathFinder` — BFS over the indexed graph (unweighted, so BFS
    yields the shortest path). See "Graph search rules" below.
- `countryConnector` (`com.prorocketeers.lukas.routing.country.connector`) — external data source. No
  dependency on `service`/`api`.
  - `CountryDataConnector` — fetches `routing.data-url` via `RestClient`; response is read as `String` and
    parsed explicitly with a Jackson `JsonMapper` (raw.githubusercontent.com serves `.json` as
    `text/plain`, so content-type-based conversion doesn't apply). A blank `dataUrl`, connection failure, or
    parse failure all become `CountryDataUnavailableException` — never a partial/placeholder result, and
    never caught/swallowed, so `RoutingGraphProvider` retries on the next call.
  - `CountryDataUnavailableException`, `CountryJsonDto` (raw Jackson binding, `cca3`/`borders`,
    `@JsonIgnoreProperties(ignoreUnknown = true)`), `CountryDtoMapper` (MapStruct, `CountryJsonDto` →
    `CountryDto`), `CountryDto` (`{code, borders}`).

## Graph search rules (`ShortestPathFinder`)

- Validate each code in two separate steps with distinct exceptions: format check against
  `[A-Za-z]{3}` (ISO 3166-1 alpha-3 shape) → `InvalidCountryCodeException`; dataset lookup (only for
  well-formed codes) → `CountryNotFoundException`. Keep them distinct so the error message tells a caller
  whether they sent garbage input or a plausible-but-nonexistent code.
- Normalize codes with `code.toUpperCase(Locale.ROOT)`, never the platform default locale (a Turkish
  default turns `"fin".toUpperCase()` into `"FİN"`, not `"FIN"`).
- BFS is unweighted, so first-reached == shortest; don't add weights/priority-queue logic.
- `RouteNotFoundException` only after exhausting BFS on two valid, connected-graph-checked codes.

## Spring Boot 4 / Jackson 3 conventions

- Use the new starter names: `spring-boot-starter-webmvc`, `spring-boot-starter-jackson`,
  `spring-boot-starter-restclient` — not the deprecated `-web`/`-json` aliases.
- Jackson 3 package is `tools.jackson.*` (e.g. `tools.jackson.databind.json.JsonMapper`), not
  `com.fasterxml.jackson.databind`; `jackson-annotations` is the exception, still under
  `com.fasterxml.jackson.annotation`.
- `@WebMvcTest` lives in `spring-boot-starter-webmvc-test`
  (`org.springframework.boot.webmvc.test.autoconfigure`). Use
  `org.springframework.test.context.bean.override.mockito.MockitoBean`, not `@MockBean`.

## MapStruct

- `mapstruct`/`mapstruct-processor` are declared independently in all three module `pom.xml`s (Maven
  annotation processing is per-module); version comes from the root `org.mapstruct.version` property.
- Bean-to-bean mappers (`CountryGraphMapper`, `CountryDtoMapper`): interface + `@Mapper(componentModel =
  "spring")` + explicit `@Mapping` for every field, even same-named ones.
- **Gotcha:** a method taking a bare `List<...>` and returning a non-iterable type gets classified as an
  "iterable mapping" and fails to generate ("Can't generate mapping method from iterable type ... to
  non-iterable type") — `@Mapping` doesn't fix this, it's only consulted for bean-mapping methods. For
  trivial wrapping (e.g. `List<String>` → `RoutingResponse`), write a `default` method on the `@Mapper`
  interface instead (see `PathMapper`); MapStruct still generates an `Impl` `@Component` for it.

## Caching

- `RoutingGraphProvider.getGraph()` is `@Cacheable("routingGraph")`, Caffeine-backed
  (`spring.cache.caffeine.spec` in `application.yml`, `expireAfterWrite=1h,maximumSize=1`). Caching sits
  here (not on `CountryDataConnector.fetchCountries()`) so the mapping/indexing work is cached too, not
  just the raw fetch.
- `@Cacheable` only intercepts calls through the Spring proxy — never call it via `this.getGraph()` from
  inside the same bean; keep it on its own bean (`RoutingGraphProvider`) called from `ShortestPathFinder`.
- `@EnableCaching` lives on `CacheConfig` in `service` (not `api`'s `Application`), so `@WebMvcTest` slices
  don't need a `CacheManager` bean.
- A failed fetch is never cached (Spring's cache abstraction only caches normal returns), so the next
  request retries rather than replaying the failure for the TTL.

## Logging

Plain SLF4J (no Lombok `@Slf4j`). Levels: `RoutingController` `DEBUG` per request;
`CountryDataConnector` `INFO` before/after the outbound fetch, `ERROR` on failure;
`RoutingExceptionHandler` `WARN` for `400`s, `ERROR` for `503`s, logged once centrally per request rather
than at each throw site; `RequestTimingInterceptor` `INFO` with method/URI/status/duration for every
`/routing/**` request. Always sanitize logged user input via `LogSanitizer`.

## Testing conventions

- `@Autowired`/`@MockitoBean` only work inside a Spring-managed test (`@SpringBootTest`, `@WebMvcTest`, or
  `@ExtendWith(SpringExtension.class)`) — a plain test class leaves them `null`.
- Pure-logic classes (`ShortestPathFinder`, `RoutingGraphProvider`, `CountryGraphMapper`) get plain unit
  tests: construct collaborators directly (`Mockito.mock(...)`, `new CountryGraphMapperImpl()`). Note this
  bypasses the Spring proxy, so `@Cacheable` has no effect in those tests.
- `RoutingGraphProviderCachingTest` (`@SpringBootTest`) is what actually exercises caching behavior, via
  `service/src/test/.../ServiceTestApplication` (test-only `@SpringBootApplication` scanning `service` +
  `countryConnector`).
- `CountryDataConnectorTimeoutTest` needs a real autoconfigured `RestClient.Builder` (via
  `countryConnector/src/test/.../CountryConnectorTestApplication`, a `@SpringBootTest`) to exercise
  `spring.http.clients.read-timeout`; `CountryDataConnectorTest`'s other cases (blank URL, refused
  connection) fail before timeout matters, so they stay plain `RestClient.builder()` unit tests.
