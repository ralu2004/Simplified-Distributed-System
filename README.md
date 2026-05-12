# Simplified Distributed System

A three-service distributed system demonstrating fault-tolerant inter-service communication via the **Circuit Breaker**, **Strict Timeout**, and **Graceful Degradation** patterns.

## Architecture

```
                           ┌──────────────┐
   client  ─── HTTP ───►   │   Gateway    │   port 8090
                           │  (Spring     │
                           │   Cloud      │
                           │   Gateway)   │
                           └──────┬───────┘
                                  │  /movies/**
                                  ▼
                           ┌──────────────┐
                           │ Movie        │   port 8081
                           │ Service      │
                           │              │
                           │ ┌──────────┐ │
                           │ │ Circuit  │ │
                           │ │ Breaker  │ │   1.5s timeout
                           │ │ Fallback │ │   50% failure threshold
                           │ └────┬─────┘ │   10s open-state cooldown
                           └──────┼───────┘
                                  │  /recommendations/{id}
                                  ▼
                           ┌──────────────┐
                           │ Recommend.   │   port 8082
                           │ Service      │
                           │              │   chaos mode (env-toggled):
                           │              │   - 30% chance: HTTP 503
                           │              │   - 40% chance: 3-10s sleep
                           │              │   (two independent rolls per request)
                           └──────────────┘
```

The gateway is the only client-facing service. Movie service depends on recommendation service for the recommendation list portion of its response. Both services read MovieLens CSV data at startup. When recommendation service misbehaves (chaos mode), movie service degrades gracefully via Resilience4j: failed or slow calls are caught, the circuit opens after a sustained failure rate, and a fallback list of movie IDs from the local catalog is returned in place of live recommendations so callers still receive a valid response.

## Dataset attribution

This project uses the MovieLens *ml-latest-small* dataset (~9,700 movies, 100k ratings) for realistic catalog data and popularity-based recommendations.

> F. Maxwell Harper and Joseph A. Konstan. 2015. *The MovieLens Datasets: History and Context.* ACM Transactions on Interactive Intelligent Systems (TiiS) 5, 4: 19:1–19:19. [https://doi.org/10.1145/2827872](https://doi.org/10.1145/2827872)

## Tech stack

- **Java 21** (compiled), tested on **JDK 23**
- **Spring Boot 4.0.6** (Spring Framework 7)
- **Spring Cloud Gateway 2025.1.1** (WebMVC variant)
- **Resilience4j 2.3.0** (`@CircuitBreaker`, `@TimeLimiter`, fallback) — applied via Spring AOP
- **MovieLens latest-small dataset** (`movies.csv`, `ratings.csv`)
- **Spring WebFlux** + **WebClient** for the outbound call from movie → recommendation
- **Spring MVC** + Tomcat for the recommendation service
- **Apache Commons CSV 1.11.0** for CSV parsing
- **Maven** build, Maven Wrapper bundled per service
- **Docker** + **Docker Compose** (optional): multi-stage builds, `eclipse-temurin:21-jre` runtime

## Project layout

```
Simplified-Distributed-System/
├── docker-compose.yml       three-service stack, health-gated startup, env wiring
├── scripts/                 demo.ps1 (evidence capture), demo-stop.ps1 (teardown)
├── gateway-service/         port 8090   routes /movies/** → movie (see env below)
├── movie-service/           port 8081   resiliency layer + aggregation
│   └── src/main/java/app/movieservice/
│       ├── MovieServiceApplication.java   @SpringBootApplication, @EnableAspectJAutoProxy
│       ├── catalog/         MovieCatalog (loads movies.csv)
│       ├── client/          RecommendationClient (WebClient + @CircuitBreaker)
│       ├── controller/      MovieController (movie metadata + description from dataset)
│       ├── dto/             MovieResponse, MovieData records
│       └── observability/   EventLogger (logs circuit state transitions)
└── recommendation-service/  port 8082   popularity-based recommendations + chaos toggle
```

Each service is an independent Maven project with its own `pom.xml` and Maven wrapper — no parent pom — to keep them independent.

## Prerequisites

- JDK 21+ (tested with JDK 23 on Windows)
- No need to install Maven separately — `mvnw` is included in each service
- **Docker Desktop** (optional) if you use Compose — Linux containers mode

## Running the system

You can run the stack **locally with Maven** (three terminals) or **with Docker Compose** (single command). Both paths use the same ports on the host: `8090` (gateway), `8081` (movie), `8082` (recommendation).

### Cross-environment URLs (important)

- **On your machine (browser / curl):** always use `http://localhost:…` for published ports.
- **Between containers (Compose):** services call each other by **Compose service name**, not `localhost`:
  - `movie-service` → `http://recommendation-service:8082` (set in `docker-compose.yml` as `RECOMMENDATION_URL`)
  - `gateway-service` → `http://movie-service:8081` (set as `MOVIE_SERVICE_URL`; see `gateway-service/src/main/resources/application.yaml`, default `http://localhost:8081` when not in Docker)

### Run with Docker Compose

From the repo root:

```powershell
docker compose up --build
```

Chaos mode for recommendation (Compose reads `CHAOS_MODE` from your shell):

```powershell
$env:CHAOS_MODE = "true"
docker compose up -d --build recommendation-service
```

Stop everything:

```powershell
docker compose down
```

### Demo script (optional)

`scripts/demo.ps1` automates happy path → chaos traffic → chaos off → recovery checks and writes timestamped evidence under `demo-artifacts/` (gitignored). `scripts/demo-stop.ps1` runs `docker compose down`.

```powershell
.\scripts\demo.ps1
.\scripts\demo-stop.ps1
```

Default demo movie id is `123` (Chungking Express); override with `-MovieId`.

## Running the system (Maven, three terminals)

Three terminals (or three IntelliJ run configurations).

### Dataset files

The MovieLens CSV files are included in the repo at:

- `movie-service/src/main/resources/movies.csv`
- `recommendation-service/src/main/resources/movies.csv`
- `recommendation-service/src/main/resources/ratings.csv`

Movie service uses `movies.csv` for catalog lookups (titles, genres) and the static fallback list. Recommendation service uses both files — `movies.csv` for id validity, `ratings.csv` to compute the popularity ranking that drives recommendations.

### 1. Recommendation service

Normal mode:

```bash
cd recommendation-service
./mvnw spring-boot:run
```

Chaos mode (Linux / macOS):

```bash
cd recommendation-service
CHAOS_MODE=true ./mvnw spring-boot:run
```

Chaos mode (Windows **cmd**):

```cmd
cd recommendation-service
set CHAOS_MODE=true && mvnw.cmd spring-boot:run
```

Chaos mode (Windows **PowerShell**):

```powershell
cd recommendation-service
$env:CHAOS_MODE = "true"
.\mvnw.cmd spring-boot:run
```

On Windows, use `mvnw.cmd` (not `mvnw`) unless you are in Git Bash, where `./mvnw` works like on Linux/macOS.

In IntelliJ, set the `CHAOS_MODE=true` environment variable on the run configuration (Run → Edit Configurations → Environment variables).

### 2. Movie service

```bash
cd movie-service
./mvnw spring-boot:run
```

### 3. Gateway

```bash
cd gateway-service
./mvnw spring-boot:run
```

Order matters only loosely — recommendation service should be up before you start hammering, but movie service and gateway can start in any order.

## Demo

All requests should go through the gateway (port 8090). The movie service is reachable directly on 8081 for inspection but a real client wouldn't see it.

### Happy path (chaos off)

Use any valid MovieLens id from `movies.csv`. Id `123` is a good demo target (and matches `scripts/demo.ps1` defaults):

```bash
curl http://localhost:8090/movies/123
```

Returns (shape; IDs vary because recommendations are sampled from the popularity pool):

```json
{
  "id": "123",
  "title": "Chungking Express (Chung Hing sam lam) (1994)",
  "description": "A drama, mystery, romance film.",
  "recommendations": ["356", "2762", "1210"]
}
```

The recommendation IDs come from the live recommendation service's popularity pool built from `ratings.csv`.

### Failure path (chaos on)

Restart recommendation service with `CHAOS_MODE=true`, then hammer the gateway:

```bash
# PowerShell
1..40 | ForEach-Object { curl.exe -s http://localhost:8090/movies/123 }

# bash
for i in {1..40}; do curl -s http://localhost:8090/movies/123; echo; done
```

You will see a **mix**: some responses still carry live numeric movie IDs from the popularity pool when the recommendation call succeeds, and others show the fallback list (the first 5 movie IDs from `movies.csv`) after HTTP 503, timeouts, or when the breaker is open.

Callers going through movie-service or the gateway typically receive **HTTP 200** with a body during downstream recommendation failures — those failures are caught at the resilience layer and replaced with fallback data before the response leaves movie-service.

### What to watch in the logs

**Recommendation service** (chaos firing):

```
Chaos: sleeping 9370ms
Chaos: injecting 503
Chaos: sleeping 7482ms
```

**Movie service** (circuit transitioning):

```
>>> Circuit state changed: State transition from CLOSED to OPEN
>>> Circuit state changed: State transition from OPEN to HALF_OPEN
>>> Circuit state changed: State transition from HALF_OPEN to CLOSED
```

The 10-second gap between `OPEN` and `HALF_OPEN` is the `wait-duration-in-open-state` cooldown. The half-open state then either closes (successful probe) or re-opens (probe failed).

### Verifying full recovery (`OPEN → HALF_OPEN → CLOSED`)

To witness the breaker closing again — not just opening — you need to turn chaos off mid-demo so the probes can succeed:

1. Start with `CHAOS_MODE=true` and hammer the gateway until you see `CLOSED → OPEN` in movie-service logs.
2. **Maven:** stop recommendation, restart without `CHAOS_MODE`. **Docker:** `Remove-Item Env:CHAOS_MODE` (or set `CHAOS_MODE=false`) then `docker compose up -d --build --force-recreate recommendation-service` so the container picks up the new env.
3. After the open-state cooldown (~10s), the breaker auto-transitions to `HALF_OPEN`. Keep sending requests while recommendation is healthy.
4. Successful probes log `HALF_OPEN → CLOSED`. Subsequent calls go through normally with live recommendations.

For a scripted, artifact-based walkthrough, use `scripts/demo.ps1` (see above).

### Actuator endpoints

Movie service exposes Resilience4j state via Spring Boot Actuator:

```bash
curl http://localhost:8081/actuator/circuitbreakers
```

Returns live state of the breaker — `CLOSED` / `OPEN` / `HALF_OPEN`, buffered call count, current failure rate, etc.

```bash
curl http://localhost:8081/actuator/circuitbreakerevents
```

Returns the rolling event log — every state transition, success, error, or call-not-permitted.

## Resiliency configuration

Set in `movie-service/src/main/resources/application.yaml`:


| Setting                                               | Value         | Purpose                                                     |
| ----------------------------------------------------- | ------------- | ----------------------------------------------------------- |
| `timeout-duration`                                    | `1500ms`      | Strict timeout: recommendation call cancelled at 1.5s       |
| `sliding-window-type`                                 | `COUNT_BASED` | Track last N calls (alternative: time-based)                |
| `sliding-window-size`                                 | `10`          | Last 10 calls considered                                    |
| `minimum-number-of-calls`                             | `5`           | Don't evaluate failure rate until at least 5 calls happened |
| `failure-rate-threshold`                              | `50`          | Open the breaker if ≥50% of recent calls failed             |
| `wait-duration-in-open-state`                         | `10s`         | Cooldown before allowing probe calls                        |
| `permitted-number-of-calls-in-half-open-state`        | `2`           | Probe count before deciding open/closed                     |
| `automatic-transition-from-open-to-half-open-enabled` | `true`        | Auto-transition without needing a triggering call           |


The fallback list is produced in `RecommendationClient.fallback(...)` from the local movie catalog (`firstNIds(5)`), so it stays valid with the dataset.

## Architecture notes

**Where resilience lives.** The gateway is a thin layer (`spring-cloud-starter-gateway-server-webmvc`): it forwards `/movies/`** to the movie service and does not host circuit breakers or fallbacks. The movie service owns aggregation and outbound calls to recommendation; that is where **Resilience4j** (`@CircuitBreaker`, `@TimeLimiter`, fallback) wraps the `WebClient` call. When recommendation is slow or failing, callers through the gateway still usually get **HTTP 200** with a complete JSON body because the movie service degrades to catalog-backed IDs before returning.

**Resilience4j with annotations.** Declarative `@CircuitBreaker`, `@TimeLimiter`, and `fallbackMethod` keep failure handling next to the client that performs the remote call instead of spreading it across controllers.

**Mixed runtimes on purpose.** Movie service uses **WebFlux / WebClient**; recommendation service uses **Spring MVC / Tomcat**. The caller side stays reactive-friendly so `@TimeLimiter` integrates cleanly with `Mono`; the downstream API stays a straightforward blocking service.

**Spring Boot 4 / Spring Cloud 2025 details:**

- `WebClient.Builder` is not auto-configured by `spring-boot-starter-webflux` alone — `spring-boot-starter-webclient` is added explicitly.
- `spring-boot-starter-aspectj` supplies AspectJ on the classpath; Resilience4j registers `@Aspect` beans only when `org.aspectj.lang.ProceedingJoinPoint` is present. `@EnableAspectJAutoProxy` on `MovieServiceApplication` ensures beans like `RecommendationClient` are proxied so the aspects actually run — without that, annotations can appear to do nothing while the raw `WebClient` still executes.
- Resilience4j 2.3.0’s SSE events path still expects Jackson 2 (`com.fasterxml.jackson.core`), while Spring Boot 4 defaults to Jackson 3 (`tools.jackson.core`); Jackson 2 is included alongside for compatibility.
- Gateway routes use `spring.cloud.gateway.server.webmvc.routes`.
- Gateway upstream URI is `${MOVIE_SERVICE_URL:http://localhost:8081}` for local dev; Docker Compose sets `http://movie-service:8081`.

## Tests

`movie-service` includes an integration test that proves the resiliency wiring is live:

```bash
cd movie-service
./mvnw test
```

`RecommendationClientResilienceTest`:

- Spins up an in-process Reactor Netty stub returning HTTP 503
- Points `recommendation.url` at it via `@DynamicPropertySource`
- Asserts `RecommendationClient` is wrapped as a CGLIB proxy (proves AOP is active)
- Asserts that calls to the failing endpoint emit numeric fallback IDs from the dataset via `StepVerifier`

Locks in the behavior so a future refactor that accidentally disables the proxy (e.g. removing `@EnableAspectJAutoProxy` or the AOP starter) fails the test instead of silently propagating 500s to callers.

## Troubleshooting

**Port 8080 / 8081 / 8082 / 8090 already in use.** Edit `application.yaml` / `application.properties` for the offending service and pick another free port. Update curl commands accordingly.

**Service fails at startup with CSV/read errors.** Ensure dataset files exist in the resource paths listed in **Dataset files** above and that they are valid MovieLens CSVs with headers (`movieId,title,genres` and `userId,movieId,rating,timestamp`).

**Movie service responds with 500 instead of fallback when recommendation fails.** The AOP proxy probably isn't installed. Check that `spring-boot-starter-aspectj` is on the movie-service classpath and that `MovieServiceApplication` has `@EnableAspectJAutoProxy`. Verify with the actuator: `curl http://localhost:8081/actuator/circuitbreakers` should show non-zero `bufferedCalls` after some traffic.

**Chaos mode not firing.** Setting `CHAOS_MODE=true` in a terminal does not affect a process started from a different terminal or from IntelliJ. Set the variable in the run configuration itself, or restart the process from the same terminal where the variable is set.

**Gateway returns 500 / connection refused in Docker.** Inside a container, `localhost` refers to that container. Ensure `MOVIE_SERVICE_URL` points at `http://movie-service:8081` in Compose (already set in `docker-compose.yml`) and that `recommendation.url` / `RECOMMENDATION_URL` points at `http://recommendation-service:8082` for movie-service.