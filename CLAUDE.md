# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Spring Boot 4.1.0 (Spring Framework 7, Jackson 3) REST application on Java 25, group/artifact
`com.prorocketeers:beDevTest`. First endpoint is `/routing/{origin}/{destination}`. The data source is the
country list from https://github.com/mledoze/countries (`routing.data-url`), fetched and reduced to a
graph of `{id, neighbors}` per country (sourced from each entry's `cca3`/`borders` fields). The endpoint
runs a BFS shortest-path search (fewest border crossings) over that graph and returns the resulting path,
or an error body with `400` if the origin/destination code is unknown or no route exists between them, or
`503` if `routing.data-url` isn't configured or the data source can't be reached/parsed.

The project is a 3-module Maven reactor (`countryConnector`, `service`, `api`) — see Architecture below.
The root `pom.xml` is an aggregator (`packaging=pom`) with no source of its own.

## Build & run

All commands run from the repo root; Maven's reactor handles module build order automatically.

- Compile: `mvn compile`
- Run tests (all modules): `mvn test`
- Run tests for one module: `mvn test -pl service` (or `-pl countryConnector`, `-pl api`)
- Run a single test: `mvn test -pl service -Dtest=ClassName#methodName` (`-pl` is required once there's
  more than one module — a bare class name is ambiguous across modules otherwise)
- Run the app: `mvn spring-boot:run -pl api` (starts on port 8080) — `api` is the only module with the
  Spring Boot plugin/an `Application` class, so this must target it explicitly
- Package: `mvn package` — produces `api/target/api-1.0-SNAPSHOT.jar` (the runnable fat jar) plus plain
  library jars `countryConnector/target/countryConnector-1.0-SNAPSHOT.jar` and
  `service/target/service-1.0-SNAPSHOT.jar`
- Docker: `docker build -t bedevtest .` then `docker run -p 8080:8080 bedevtest` — multi-stage build
  (`maven:3.9-eclipse-temurin-25` to compile, `eclipse-temurin:25-jre` to run), runs as a non-root
  `appuser`. The build stage copies all 4 `pom.xml` files before any module's `src/`, so
  `dependency:go-offline` is cached as its own layer independent of source changes; the runtime stage
  copies the jar from `api/target/*.jar` specifically (the only module producing an executable jar).
  Override `routing.data-url` at container run time with `-e ROUTING_DATA_URL=...` (Spring's relaxed env
  binding maps `ROUTING_DATA_URL` → `routing.data-url`).

## Architecture

Each layer is its own Maven module, not just a subpackage — this makes the dependency direction a compile
error to violate, not just a convention: `api` depends on `service`, `service` depends on
`countryConnector`, and `countryConnector` depends on neither. `countryConnector` code physically cannot
import a `service` or `api` type; `service` code cannot import an `api` type. Package names still mirror
the module names (`com.prorocketeers.lukas.routing.{countryConnector,service,api}`) — don't add
cross-layer classes directly under `com.prorocketeers.lukas.routing` itself.

- `api` module (`com.prorocketeers.lukas` + `com.prorocketeers.lukas.routing.api`) — the runnable
  application and the web layer. Holds `Application` (the only `@SpringBootApplication` in the reactor),
  `src/main/resources/application.yml`, and is the only module with `spring-boot-maven-plugin` (produces
  the executable fat jar). Depends on `service`.
  - `RoutingController` — `GET /routing/{origin}/{destination}`, depends on `service.IPathFinder` (to
    compute the path) and `IPathMapper` (to wrap it into the response).
  - `IPathMapper` — MapStruct `@Mapper(componentModel = "spring")` interface wrapping `List<String>`
    into `RoutingResponse`. Implemented as a `default` method (`return new RoutingResponse(path);`),
    *not* generated — see the MapStruct gotcha below for why.
  - `RoutingResponse` — response record `{route}`; the ordered list of cca3 codes from origin to
    destination (inclusive). No longer echoes `origin`/`destination` back.
  - `RoutingExceptionHandler` (`@RestControllerAdvice`) — maps `CountryNotFoundException` and
    `RouteNotFoundException` (from `service.exception`) to `400`, and `CountryDataUnavailableException`
    (from `countryConnector`) to `503`, each with an `ErrorResponse { error }` body. A catch-all
    `@ExceptionHandler(Exception.class)` maps anything else to `500` with a generic
    `ErrorResponse("Internal server error")` body — the full exception is logged server-side, but the
    client never sees exception details, so an unexpected failure still returns the same response shape
    as every other error instead of falling through to Spring Boot's default error response.
  - `LogSanitizer` — strips control characters (`\p{Cntrl}`) from any string before it's logged.
    `RoutingController` and `RoutingExceptionHandler` both run user-supplied `origin`/`destination` (or
    messages derived from them) through it before logging, since an unsanitized CR/LF in a path segment
    (e.g. `BEL%0d%0aFAKE LOG LINE`) would otherwise forge what looks like a second, unrelated log entry.
  - `RequestTimingInterceptor` / `WebMvcConfig` — logs method, URI, status and duration for every
    request under `/routing/**` (see Logging section).
- `service` module (`com.prorocketeers.lukas.routing.service`) — business logic, orchestrates
  `countryConnector` internally. Depends on `countryConnector`; has no visibility into `api`.
  - `IPathFinder` — `findPath(origin, destination) -> List<String>` abstraction the API layer depends on.
  - `impl.GraphNode` — graph node record `{id, neighbors}`.
  - `exception.CountryNotFoundException` / `exception.RouteNotFoundException`.
  - `impl.CountryGraphMapper` — MapStruct `@Mapper(componentModel = "spring")` interface mapping
    `CountryDto` (from `countryConnector`) → `GraphNode` (code → id, borders → neighbors) and the
    `List<...>` overload; MapStruct generates `CountryGraphMapperImpl` at build time (see
    `target/generated-sources/annotations`). Tests that need a real instance without Spring
    (`ShortestPathFinderTest`, `CountryGraphMapperTest`) `new` the generated `CountryGraphMapperImpl`
    directly — it has a public no-arg constructor.
  - `impl.RoutingGraphProvider` — pulls countries via `CountryDataConnector`, maps them with
    `CountryGraphMapper`, and indexes the result into a `Map<String, GraphNode>` keyed by `id`. This is
    the cached step (`@Cacheable("routingGraph")`) — see the Caching section below.
  - `impl.ShortestPathFinder implements IPathFinder` — gets the indexed graph from
    `RoutingGraphProvider` and runs BFS over it (unweighted, so BFS already yields the shortest path).
    Case-insensitive on origin/destination — normalizes with `Locale.ROOT`, not the platform default,
    since e.g. a Turkish default locale turns `"fin".toUpperCase()` into `"FİN"` (dotted İ), not `"FIN"`.
    Throws `CountryNotFoundException` if either code isn't in the dataset, `RouteNotFoundException` if
    both are valid but no path connects them (e.g. an island with no borders). Note the graph is
    symmetrized in `RoutingGraphProvider` (edges added in both directions) because the source data
    doesn't always list a border on both sides (e.g. LKA lists IND as a border, IND's own list omits LKA).
- `countryConnector` module (`com.prorocketeers.lukas.routing.countryConnector`) — external data source.
  No dependency on `service` or `api` — nothing here should know how the graph is built or how it's
  exposed over HTTP.
  - `CountryDataConnector` — fetches the country list from `routing.data-url`
    (`api/src/main/resources/application.yml`, bound via `@Value("${routing.data-url:}")` — note the empty
    default: a *missing* property must not crash the app at startup, it must surface as a `503` at
    request time instead) via Spring's `RestClient`, deserialized as `List<CountryJsonDto>` and mapped to
    `List<CountryDto>` via `CountryDtoMapper`. raw.githubusercontent.com serves `.json` with
    `Content-Type: text/plain`, so the body is read as a `String` and parsed explicitly with a Jackson
    `JsonMapper` rather than relying on RestClient's content-type-based converter selection (that path
    throws `UnknownContentTypeException`). A blank `dataUrl`, any `RestClient`/connection failure, and any
    JSON parse failure are all caught in `fetchCountries()` and rewrapped as
    `CountryDataUnavailableException` (mapped to `503` by `RoutingExceptionHandler`). Not cached itself —
    `service.impl.RoutingGraphProvider` caches the mapped graph built from it instead; see the Caching
    section below.
  - `CountryDataUnavailableException` — thrown by `CountryDataConnector` for the "data source is
    unavailable" family of failures (not configured, unreachable, malformed response). Always thrown, never
    swallowed into a placeholder return value, so it propagates uncaught out of
    `RoutingGraphProvider.getGraph()` and is never cached — the next call retries the fetch.
  - `CountryJsonDto` — raw Jackson binding target for the source JSON; mirrors the source field names
    (`cca3`, `borders`) exactly, `@JsonIgnoreProperties(ignoreUnknown = true)` drops the rest. Kept
    separate from `CountryDto` so a future source field rename only requires touching this record and
    `CountryDtoMapper`, not `CountryDataConnector` itself.
  - `CountryDtoMapper` — MapStruct `@Mapper(componentModel = "spring")` interface mapping
    `CountryJsonDto` → `CountryDto` (`@Mapping(source = "cca3", target = "code")`, plus an explicit
    same-named `@Mapping` for `borders` too, per the project's convention of stating each mapped field
    explicitly even when a rename isn't involved) and the `List<...>` overload. This is the one mapper
    that lives in `countryConnector` — see the MapStruct section below for why
    `countryConnector/pom.xml` carries the mapstruct dependency/plugin config too.
  - `CountryDto` — the DTO the rest of the app (`service`'s `CountryGraphMapper`) consumes; only `code`
    (renamed from the source's `cca3` by `CountryDtoMapper`) and `borders`.

## Spring Boot 4 notes (module renames)

Spring Boot 4 modularized its starters; this project intentionally uses the new artifact names rather
than the deprecated aliases:
- `spring-boot-starter-webmvc` (not `spring-boot-starter-web`)
- `spring-boot-starter-jackson` (not `spring-boot-starter-json`)
- `spring-boot-starter-restclient` for the imperative `RestClient` used by `CountryDataConnector`

Jackson 3 changed its group ID/package from `com.fasterxml.jackson.*` to `tools.jackson.*` (e.g.
`tools.jackson.databind.JsonNode`) — `jackson-annotations` is the one module that keeps the old
`com.fasterxml.jackson.core` group ID. Don't reach for `com.fasterxml.jackson.databind` imports in new
code in this project.

Each starter now has a matching test-scoped companion; `@WebMvcTest` moved out of `spring-boot-test` into
`spring-boot-starter-webmvc-test` (and its package moved to
`org.springframework.boot.webmvc.test.autoconfigure`). `@MockBean` is gone — use
`org.springframework.test.context.bean.override.mockito.MockitoBean` instead (it now lives in
`spring-test`, not a Boot-specific module).

## MapStruct

`mapstruct` + `mapstruct-processor` (`${org.mapstruct.version}`, declared as a property on the root
aggregator `pom.xml`) are wired via `maven-compiler-plugin`'s `annotationProcessorPaths` — but declared
independently in **all three** of `countryConnector/pom.xml`, `service/pom.xml` and `api/pom.xml`, since
each module has its own `@Mapper` interface (`CountryDtoMapper` in `countryConnector`, `CountryGraphMapper`
in `service`, `IPathMapper` in `api`) and Maven annotation processing is per-module.
`CountryGraphMapper` is the bean-to-bean mapper — follow that pattern (interface +
`@Mapper(componentModel = "spring")` + `@Mapping` for renamed fields) for future bean mappers rather than
hand-written stream/map code.

**Gotcha:** MapStruct classifies a mapping method (bean/iterable/map/enum) from its parameter and return
*types* before it ever looks at `@Mapping`. A method taking a bare `List<...>` and returning a
non-iterable type (e.g. wrapping a path into `RoutingResponse`) gets classified as an "iterable mapping"
and fails with "Can't generate mapping method from iterable type ... to non-iterable type" — no
`@Mapping` annotation fixes this, because `@Mapping` is only consulted for bean-mapping methods. For that
kind of trivial single-value wrapping, write a `default` method on the `@Mapper` interface instead of an
abstract one; MapStruct leaves default methods alone but *still* generates an `Impl` class and registers
it as a `@Component` bean for the whole interface (see `IPathMapper` — this is exactly why `api/pom.xml`
needs the mapstruct-processor too, even though `IPathMapper` has zero abstract mapping methods).

## Caching

`RoutingGraphProvider.getGraph()` is `@Cacheable("routingGraph")`, backed by Caffeine
(`spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine`), configured in
`application.yml` under `spring.cache.caffeine.spec` (`expireAfterWrite=1h,maximumSize=1` — one entry,
since the method takes no arguments). Caching lives on this method rather than on
`CountryDataConnector.fetchCountries()` directly: `getGraph()` also does the `CountryGraphMapper` mapping and
the `id -> GraphNode` indexing, so caching it means that work runs once per TTL window too, instead of
being redone by `ShortestPathFinder` on every request even when the fetch itself is a cache hit. A failed
call (any `CountryDataUnavailableException`, which `CountryDataConnector.fetchCountries()` always throws
rather than ever returning a partial/placeholder list) is not cached by Spring's cache abstraction, so the
next request retries the fetch rather than replaying the failure for the TTL.

Note that `@Cacheable` only intercepts calls that arrive through the Spring proxy — a class calling its
*own* `@Cacheable` method (`this.getGraph()`) bypasses the proxy and silently never caches. That's why
this lives on its own `RoutingGraphProvider` bean that `ShortestPathFinder` calls into, rather than as a
method on `ShortestPathFinder` itself.

`@EnableCaching` lives on its own `CacheConfig` class in `com.prorocketeers.lukas.routing.service` — the
`service` module, alongside `RoutingGraphProvider` itself, *not* the `api` module's `Application`. Two
reasons stack here: first, the original reason this was ever split out — `@WebMvcTest` slices (in `api`)
use `Application` as their root configuration but don't pull in `CacheAutoConfiguration`, and
`@EnableCaching` directly on `Application` would make Spring try to build the caching AOP infrastructure
in every slice test too, failing with "No qualifying bean of type CacheManager". Second, and specific to
the module split: putting it in `service` makes that module self-sufficient for caching — it doesn't need
anything from `api` to prove its own `@Cacheable` behavior works. `Application`'s default component scan
(`com.prorocketeers.lukas` and everything under it) still picks `CacheConfig` up transitively through the
`service` jar on its classpath — Spring's classpath component scanning doesn't care about jar/module
boundaries, only package names — so the full app still gets caching enabled with no change needed in
`Application` itself.

`RoutingGraphProviderCachingTest` (`service/src/test/java/.../routing/service/impl`) verifies the caching
behavior end-to-end through the real Spring proxy, with `CountryDataConnector` replaced by a
`@MockitoBean` rather than exercised over real HTTP: `service`'s tests trust that `fetchCountries()` is
already correct — that's `countryConnector`'s own responsibility to test — and only care here about *how
many times* it gets called. One test stubs a successful return and asserts the mock is hit once across two
`getGraph()` calls; another stubs the mock to throw on the first call and succeed on the second, to assert
a failed call isn't cached — `getGraph()` throws on the first call, then still calls the mock (and
succeeds) on the second, rather than replaying the failure. Both tests reset the `routingGraph` cache in
`@BeforeEach` since they share one Spring context.

This test is `@SpringBootTest` with no explicit `classes=`, so it relies on Spring's usual package-upward
search to find a `@SpringBootConfiguration` — but the real `Application` lives in `api`, a module `service`
cannot depend on without inverting `api -> service`. Fix:
`service/src/test/java/com/prorocketeers/lukas/routing/ServiceTestApplication.java` is a test-only
`@SpringBootApplication` sitting one package above both `.service` and `.countryConnector`, so its default
component scan covers this module's own beans (`RoutingGraphProvider`, `CacheConfig`, ...) plus
`countryConnector`'s (`CountryDataConnector`, pulled in transitively via the module dependency — again,
classpath scanning doesn't care that it's a separate jar) — the latter only matters here so `@MockitoBean`
has a real bean definition to replace. Because `service` has no web starter on its classpath at all, this
test boots as a plain non-web context — actually more correct than a hypothetical shared bootstrap would
be, since this test has nothing to do with HTTP.

`service/src/test/resources/application.yml` only configures `spring.cache.caffeine.spec` — the timeout
test that used to live here (real `RestClient` against a slow embedded server, to prove
`spring.http.clients.read-timeout` aborts the request) moved to
`countryConnector/src/test/java/.../CountryDataConnectorTimeoutTest.java`, since that's a property of
`CountryDataConnector` itself, not of `RoutingGraphProvider`'s caching — `service` has no reason to know
`CountryDataConnector` talks to `RestClient` at all. That test needs its own `@SpringBootTest` bootstrap
(`countryConnector/src/test/java/.../CountryConnectorTestApplication.java`) and its own
`countryConnector/src/test/resources/application.yml` (`spring.http.clients.connect-timeout` /
`read-timeout`) to get a real autoconfigured `RestClient.Builder` — `CountryDataConnectorTest`'s other two
tests don't need this, since a blank URL or refused connection fails before timeout config would matter,
so they stay plain `RestClient.builder()` unit tests without a Spring context.

## Logging

Plain SLF4J (`org.slf4j.Logger`/`LoggerFactory`, via the default Logback backend pulled in by
`spring-boot-starter-webmvc`) — no Lombok `@Slf4j`, no extra dependency. Current coverage:
- `RoutingController` — `DEBUG` on each incoming request (`origin -> destination`).
- `CountryDataConnector` — `INFO` immediately before the outbound `RestClient` call and again after a
  successful parse (count of countries fetched). Since `fetchCountries()` is only reached on a
  `RoutingGraphProvider.getGraph()` cache miss, these lines only appear on a real cache miss — they double
  as an "actually hit the network" signal, not just request-level noise. `ERROR` (with the exception)
  right before wrapping any failure into `CountryDataUnavailableException`.
- `RoutingExceptionHandler` — logs centrally at the point each exception is turned into a response,
  rather than at the throw site, so there's exactly one log line per failed request: `WARN` for
  `CountryNotFoundException`/`RouteNotFoundException` (expected client-input rejections, `400`), `ERROR`
  for `CountryDataUnavailableException` (infra-level failure, `503`).
- `RequestTimingInterceptor` (`HandlerInterceptor`, registered for `/routing/**` in `WebMvcConfig`) —
  `INFO` line per request: `{method} {uri} -> {status} ({duration} ms)`. This is automatic (every
  request under that path gets timed, no per-endpoint code needed) rather than timing added manually
  inside `RoutingController`. `@WebMvcTest` slices pick both classes up for free — `WebMvcConfigurer` and
  `HandlerInterceptor` beans are on the web slice's include list, unlike plain `@Component`s — so no test
  wiring changes were needed when this was added.

## Gotcha: plain unit tests vs. Spring test annotations

`@Autowired` and `@MockitoBean` only do anything inside a Spring-managed test (`@SpringBootTest`,
`@WebMvcTest`, or `@ExtendWith(SpringExtension.class)`). A plain `class FooTest` with no such annotation
silently leaves those fields `null` — this bit `ShortestPathFinderTest` once already. `ShortestPathFinder`,
`RoutingGraphProvider` and `CountryGraphMapper` are pure logic with no required Spring behavior at test
time, so their tests construct collaborators directly (plain `Mockito.mock(...)` or
`new CountryGraphMapperImpl()`) instead of pulling in a Spring test context. Note that constructing
`RoutingGraphProvider` this way (`new RoutingGraphProvider(...)`) bypasses the Spring proxy, so
`@Cacheable` has no effect in `ShortestPathFinderTest` — harmless there since each test calls `getGraph()`
at most once per mock setup; `RoutingGraphProviderCachingTest` is what actually exercises caching, via
`@SpringBootTest`.