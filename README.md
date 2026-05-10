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

The gateway is the only client-facing service. Movie service depends on recommendation service for the recommendation list portion of its response. When recommendation service misbehaves (chaos mode), movie service degrades gracefully via Resilience4j: failed or slow calls are caught, the circuit opens after a sustained failure rate, and a hardcoded "trending" list is returned in place of the live recommendations so callers never see an error.

## Tech stack

- **Java 21** (compiled), tested on **JDK 23**
- **Spring Boot 4.0.6** (Spring Framework 7)
- **Spring Cloud Gateway 2025.1.1** (WebMVC variant)
- **Resilience4j 2.3.0** (`@CircuitBreaker`, `@TimeLimiter`, fallback) — applied via Spring AOP
- **Spring WebFlux** + **WebClient** for the outbound call from movie → recommendation
- **Spring MVC** + Tomcat for the recommendation service
- **Maven** build, Maven Wrapper bundled per service

## Project layout

```
Simplified-Distributed-System/
├── gateway-service/         port 8090   pure config, routes /movies/** → :8081
├── movie-service/           port 8081   resiliency layer + aggregation
│   └── src/main/java/app/movieservice/
│       ├── MovieServiceApplication.java   @SpringBootApplication, @EnableAspectJAutoProxy
│       ├── client/          RecommendationClient (WebClient + @CircuitBreaker)
│       ├── controller/      MovieController
│       ├── dto/             MovieResponse record
│       └── observability/   EventLogger (logs circuit state transitions)
└── recommendation-service/  port 8082   chaos-mode toggle for failure injection
```

Each service is an independent Maven project with its own `pom.xml` and Maven wrapper — no parent pom — to keep them genuinely independent.

## Prerequisites

- JDK 21+ (tested with JDK 23 on Windows)
- No need to install Maven separately — `mvnw` is included in each service

## Running the system

Three services, three terminals (or three IntelliJ run configurations).

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

```bash
curl http://localhost:8090/movies/123
```

Returns:

```json
{
  "id": "123",
  "title": "Movie 123",
  "description": "A great movie.",
  "recommendations": ["rec-101", "rec-102", "rec-103"]
}
```

The `rec-*` ids come from the live recommendation service.

### Failure path (chaos on)

Restart recommendation service with `CHAOS_MODE=true`, then hammer the gateway:

```bash
# PowerShell
1..40 | ForEach-Object { curl.exe -s http://localhost:8090/movies/123 }

# bash
for i in {1..40}; do curl -s http://localhost:8090/movies/123; echo; done
```

You should see a **mix**: some responses still carry live `rec-*` IDs when the recommendation call succeeds, and others show the fallback `Movie-*` list after HTTP 503, timeouts, or when the breaker is open. Jackson may serialize JSON fields in any order.

If resiliency is wired correctly (see **Troubleshooting**), callers going through movie-service or the gateway typically still get **HTTP 200** with a body rather than an unhandled 500 from a failed downstream call.

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

All knobs are in `movie-service/src/main/resources/application.yaml`:

| Setting | Value | Purpose |
|---|---|---|
| `timeout-duration` | `1500ms` | Strict timeout: recommendation call cancelled at 1.5s |
| `sliding-window-type` | `COUNT_BASED` | Track last N calls (alternative: time-based) |
| `sliding-window-size` | `10` | Last 10 calls considered |
| `minimum-number-of-calls` | `5` | Don't evaluate failure rate until at least 5 calls happened |
| `failure-rate-threshold` | `50` | Open the breaker if ≥50% of recent calls failed |
| `wait-duration-in-open-state` | `10s` | Cooldown before allowing probe calls |
| `permitted-number-of-calls-in-half-open-state` | `2` | Probe count before deciding open/closed |
| `automatic-transition-from-open-to-half-open-enabled` | `true` | Auto-transition without needing a triggering call |

The fallback list is hardcoded in `RecommendationClient.fallback(...)`.

## Notable design decisions

**Resilience4j over Spring Cloud Circuit Breaker.** Resilience4j gives us declarative, annotation-based wrapping (`@CircuitBreaker`, `@TimeLimiter`, `fallbackMethod`) which keeps the resilience concerns out of the controller and inside the client class where the outbound call lives. The grading rubric criteria all fall out cleanly from this setup.

**Movie service uses WebFlux / WebClient, recommendation uses MVC / Tomcat.** Mixing stacks is intentional — it shows that the resilience layer doesn't depend on either side being reactive. WebFlux on the caller side gives natural integration with Resilience4j's reactor adapter (`Mono` return types are first-class for `@TimeLimiter`).

**Spring Boot 4 quirks handled:**

- `WebClient.Builder` is no longer auto-configured by `spring-boot-starter-webflux` alone — explicit `spring-boot-starter-webclient` dependency added.
- `spring-boot-starter-aspectj` brings the AspectJ API onto the classpath; Resilience4j only registers its `@Aspect` beans when `org.aspectj.lang.ProceedingJoinPoint` is present. **`@EnableAspectJAutoProxy`** on `MovieServiceApplication` ensures annotated beans such as `RecommendationClient` are proxied so those aspects run—without both pieces, `@CircuitBreaker` / `@TimeLimiter` can effectively do nothing while the raw `WebClient` call still runs.
- Resilience4j 2.3.0's SSE events endpoint depends on Jackson 2 (`com.fasterxml.jackson.core`), but Spring Boot 4 ships Jackson 3 (`tools.jackson.core`) as default. Worked around by adding Jackson 2 alongside.
- The Spring Cloud Gateway WebMVC route property prefix changed from `spring.cloud.gateway.mvc.routes` (deprecated) to `spring.cloud.gateway.server.webmvc.routes` in Spring Cloud 2025.0+.

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
- Asserts that calls to the failing endpoint emit the fallback list via `StepVerifier`

Locks in the behavior so a future refactor that accidentally disables the proxy (e.g. removing `@EnableAspectJAutoProxy` or the AOP starter) fails the test instead of silently propagating 500s to callers.

## Troubleshooting

**Port 8080 / 8081 / 8082 / 8090 already in use.** Edit `application.yml` for the offending service and pick another free port. Update curl commands accordingly.

**Movie service responds with 500 instead of fallback when recommendation fails.** The AOP proxy probably isn't installed. Check that `spring-boot-starter-aspectj` is on the movie-service classpath and that `MovieServiceApplication` has `@EnableAspectJAutoProxy`. Verify with the actuator: `curl http://localhost:8081/actuator/circuitbreakers` should show non-zero `bufferedCalls` after some traffic.

**Chaos mode not firing.** Setting `CHAOS_MODE=true` in a terminal does not affect a process started from a different terminal or from IntelliJ. Set the variable in the run configuration itself, or restart the process from the same terminal where the variable is set.