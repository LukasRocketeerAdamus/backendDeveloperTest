# beDevTest

Spring Boot REST service that finds the shortest border-crossing route between two countries.

## What it does

`GET /routing/{origin}/{destination}` takes two [ISO 3166-1 alpha-3](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-3)
country codes (e.g. `CZE`, `PRT`) and returns the shortest path between them, counted in border
crossings — not physical distance.

- Country/border data is fetched from the [mledoze/countries](https://github.com/mledoze/countries)
  dataset (configurable via `routing.data-url`) and reduced to a graph of `{id, neighbors}`.
- The path is computed with a breadth-first search over that graph.
- Successful responses look like:
  ```json
  { "route": ["CZE", "DEU", "FRA", "ESP", "PRT"] }
  ```
- Error responses:
  - `400` — unknown country code, or no route exists between two valid countries (e.g. an island).
  - `503` — the country/border data source isn't configured or can't be reached/parsed.
- Every request is logged with method, path, status and duration, e.g.:
  ```
  GET /routing/CZE/PRT -> 200 (3 ms)
  ```

## Requirements

- JDK 25
- Maven 3.9+ (or use the included Docker setup, which needs no local JDK/Maven)

## Build & run

This is a 3-module Maven reactor (`countryConnector`, `service`, `api`); all commands run from the repo
root, and the reactor handles module build order automatically.

```bash
mvn package
mvn spring-boot:run -pl api  # starts on http://localhost:8080 — api is the only module with the
                              # Spring Boot plugin, so it must be targeted explicitly
```

```bash
curl http://localhost:8080/routing/CZE/PRT
```

## Test

```bash
mvn test                                          # full suite
mvn test -pl service -Dtest=ClassName#methodName  # a single test (-pl is required once there's more
                                                   # than one module — a bare class name is ambiguous)
```

## Docker

```bash
docker build -t bedevtest .
docker run -p 8080:8080 bedevtest
```

Override the data source URL at container run time if needed:

```bash
docker run -p 8080:8080 -e ROUTING_DATA_URL=https://example.com/countries.json bedevtest
```

## Configuration

See `api/src/main/resources/application.yml`:

- `routing.data-url` — URL of the country/border JSON dataset.
- `spring.cache.caffeine.spec` — TTL/size for the in-memory cache of the fetched dataset (default:
  1 hour, since the upstream data changes rarely).

For architecture notes and implementation details, see [CLAUDE.md](CLAUDE.md).
