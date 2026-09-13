# Resilience4j Circuit Breaker — Spring Boot

A working reference for the `resilience4j-spring-boot3` Circuit Breaker module, based on the attached `application.yaml`.

---

## 1. What is Resilience4j?

Resilience4j is a lightweight, functional fault-tolerance library for Java built around `java.util.function` and decorators. Instead of wrapping your whole service in a heavyweight framework (like the older Netflix Hystrix), it gives you small, composable modules that each solve one resilience problem:

| Module | Purpose |
|---|---|
| **CircuitBreaker** | Stop calling a service that's already failing |
| **RateLimiter** | Cap how many calls are allowed per time window |
| **Bulkhead** | Limit concurrent calls to isolate failures |
| **Retry** | Automatically retry failed calls |
| **TimeLimiter** | Cancel calls that take too long |

In Spring Boot, `resilience4j-spring-boot3` adds auto-configuration, YAML-based config, annotations (`@CircuitBreaker`, `@Retry`, etc.), and Actuator/Micrometer integration — so you get metrics and health indicators for free.

**Why it replaced Hystrix:** Hystrix went into maintenance mode in 2018. Resilience4j has no external dependencies (Hystrix depends on RxJava), works natively with Java 8+ functional interfaces, and lets you stack multiple resilience patterns (Retry + CircuitBreaker + TimeLimiter) on the same call.

---

## 2. What is a Circuit Breaker?

A Circuit Breaker prevents an application from repeatedly calling a service that is likely to fail — the same idea as an electrical circuit breaker tripping to stop current flow before it causes damage. Without one, a slow or failing downstream service can exhaust threads/connections in the caller and cause a cascading failure across the whole system.

### The three states

<img width="1197" height="880" alt="image" src="https://github.com/user-attachments/assets/1f6485f2-5bb6-4971-a8b4-a3f7753ecce7" />


- **CLOSED** — normal operation. Every call goes through; the breaker keeps a rolling window of results and calculates the failure/slow-call rate.
- **OPEN** — the failure/slow-call rate crossed the threshold. Calls fail immediately (`CallNotPermittedException`) without hitting the downstream service at all. This is what protects the caller.
- **HALF_OPEN** — after the wait duration, the breaker lets a small number of "probe" calls through to see if the dependency has recovered. If they succeed, it closes; if they fail, it reopens.

> **Senior-level nuance:** the breaker doesn't just count "did the call throw" — it can also count *slow* calls as failures (`slowCallRateThreshold` / `slowCallDurationThreshold`). This matters because a dependency that hangs (rather than errors immediately) is often more dangerous than one that fails fast, since it ties up threads while you wait.

---

## 3. Configuration walkthrough (your `application.yaml`)

```yaml
resilience4j.circuitbreaker.instances:
  testCircuitBreaker:
```
Each key under `instances` is a **named circuit breaker instance** — you reference `testCircuitBreaker` from your code via `@CircuitBreaker(name = "testCircuitBreaker", ...)`. You can define multiple instances, one per downstream dependency, each with its own thresholds.

| Property | Value | Meaning |
|---|---|---|
| `slidingWindowType` | `COUNT_BASED` | The window used to calculate failure rate is based on the *last N calls* (alternative: `TIME_BASED`, last N seconds) |
| `slidingWindowSize` | `10` | Evaluate the last 10 calls |
| `minimumNumberOfCalls` | `5` | Don't calculate a failure rate — and therefore never trip — until at least 5 calls have been recorded. Protects against opening the circuit on a tiny, statistically meaningless sample |
| `failureRateThreshold` | `50` | If ≥50% of the calls in the window failed, transition to OPEN |
| `slowCallRateThreshold` | `75` | If ≥75% of calls are "slow" (see below), also transition to OPEN, even if they technically succeeded |
| `slowCallDurationThreshold` | `5s` | A call is classified as "slow" if it takes longer than 5 seconds |
| `waitDurationInOpenState` | `5s` | Stay OPEN for 5 seconds before allowing a transition to HALF_OPEN |
| `permittedNumberOfCallsInHalfOpenState` | `3` | Allow 3 probe calls through in HALF_OPEN to decide whether to close or reopen |
| `automaticTransitionFromOpenToHalfOpenEnabled` | `true` | Move from OPEN → HALF_OPEN automatically once the wait duration passes, instead of waiting for the next call attempt to trigger the check |
| `registerHealthIndicator` | `true` | Expose this breaker's state via `/actuator/health` |
| `recordExceptions` / `ignoreExceptions` (commented out) | — | Fine-grained control over what counts as a "failure." Here they're commented out, so **all** exceptions count by default. In production you'd typically record `HttpServerErrorException`, `IOException`, `TimeoutException` as failures, and *ignore* `HttpClientErrorException` (4xx client errors, like a bad request, aren't the downstream service's fault and shouldn't trip the breaker) |

### RateLimiter & TimeLimiter placement (a common YAML trap)

`resilience4j.timelimiter` must sit at the **same indentation level** as `circuitbreaker`, `retry`, `bulkhead`, `threadpoolbulkhead`, and `ratelimiter` — i.e. directly under `resilience4j:`, not nested inside `ratelimiter:`. It's an easy mistake to make since they're often documented back-to-back:

```yaml
resilience4j:
  ratelimiter:
    configs: ...
    instances: ...

  # <- 2 spaces, a sibling of ratelimiter, NOT indented further
  timelimiter:
    configs:
      default:
        timeoutDuration: 2s
        cancelRunningFuture: true
    instances:
      timeLimiter:              # must exactly match @TimeLimiter(name = "...")
        baseConfig: default
        timeoutDuration: 1s
```

If `timelimiter` ends up nested one level too deep, Spring Boot simply never binds it — there's no error, no startup failure, nothing in the logs. Resilience4j just silently falls back to library defaults (1s timeout) for every `@TimeLimiter`, and your configured override does nothing. This is one of the harder Resilience4j bugs to spot precisely because it fails silently instead of throwing.

The second half of this trap is **instance naming**: the key under `instances:` must be the exact string passed to `name =` in the annotation. `@TimeLimiter(name = "timeLimiter", ...)` will not pick up an `instances.testTimeLimiter` block — it'll silently use `configs.default` instead, and your `testTimeLimiter` override becomes dead config. This applies to every Resilience4j module, not just TimeLimiter — always cross-check the YAML instance key against the annotation's `name` argument.

### Actuator section

```yaml
management:
  endpoints.web.exposure.include: "health, metrics, prometheus"
  endpoint.health.show-details: always
  endpoint.health.show-components: always
  health.circuitbreakers.enabled: true
```
This exposes:
- `/actuator/health` — shows `testCircuitBreaker`'s current state (CLOSED/OPEN/HALF_OPEN) as a health component, because `health.circuitbreakers.enabled: true` + `registerHealthIndicator: true` on the instance.
- `/actuator/metrics/resilience4j.circuitbreaker.*` and `/actuator/prometheus` — expose counters/gauges like `resilience4j_circuitbreaker_calls`, `resilience4j_circuitbreaker_state`, and buckets for slow calls, which you'd scrape into Prometheus/Grafana.

---

## 4. Implementing it in Spring Boot

### 4.1 Dependencies (Maven)

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```
`spring-boot-starter-aop` is required — Resilience4j's annotations work via a Spring AOP proxy wrapping your bean method.

### 4.2 Applying the breaker to a method

```java
@Service
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    // "testCircuitBreaker" must match the instance name in application.yaml
    @CircuitBreaker(name = "testCircuitBreaker", fallbackMethod = "fallbackStock")
    public StockResponse getStock(String sku) {
        return restClient.get()
                .uri("/inventory/{sku}", sku)
                .retrieve()
                .body(StockResponse.class);
    }

    // Fallback signature: same return type + same args, plus the Throwable
    private StockResponse fallbackStock(String sku, Throwable ex) {
        // Runs when the circuit is OPEN (CallNotPermittedException)
        // or when the real call throws
        return new StockResponse(sku, -1, "UNAVAILABLE - " + ex.getClass().getSimpleName());
    }
}
```

Key points a senior candidate should call out:
- The fallback method **must** be in the same class (proxy-based AOP — self-invocation doesn't trigger it, so calling `getStock()` from another method in the *same* class bypasses the breaker entirely).
- The fallback's parameter list must match the original method's parameters **exactly**, plus a trailing `Throwable`. You can also write multiple fallbacks overloaded for specific exception types.
  - **If the annotated method takes no arguments**, the fallback must be `fallback(Throwable ex)` — nothing else. Adding an unrelated extra parameter (e.g. a leftover `String id` copy-pasted from a different example) means Resilience4j can't find a matching fallback via reflection at all. It doesn't throw a compile error — it fails at runtime with `NoSuchMethodException: No fallback method match found`, and since that exception is unhandled, it surfaces as a generic `500 Internal Server Error` with no obvious link back to the real cause.
  - The fallback's **return type** must equal (or be assignable to) the original method's return type. A method returning `String` paired with a fallback returning `ResponseEntity<String>` fails to match for the same reason. If a fallback needs to signal a distinct HTTP status (e.g. `429 TOO_MANY_REQUESTS` for a rate limiter or bulkhead), the *annotated method itself* has to return `ResponseEntity<T>`, not the raw body type — the fallback can't unilaterally widen the return type.
- When the circuit is OPEN, the exception delivered to the fallback is `CallNotPermittedException`, not the original downstream exception — so if your fallback logic branches on exception type, handle that case explicitly.

### 4.3 Combining with Retry and TimeLimiter (production pattern)

```java
@Retry(name = "testCircuitBreaker")
@CircuitBreaker(name = "testCircuitBreaker", fallbackMethod = "fallbackStock")
@TimeLimiter(name = "testCircuitBreaker")
public CompletableFuture<StockResponse> getStockAsync(String sku) {
    return CompletableFuture.supplyAsync(() -> getStock(sku));
}
```
> **Senior-level nuance:** ordering matters. Annotations are applied outside-in as decorators; Resilience4j's own doc recommends the order Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead when stacking manually via `Decorators.ofSupplier(...)`, so that retries happen *inside* the circuit breaker's accounting (each retry attempt is what gets counted toward the failure rate), not around it.

### 4.4 Programmatic style (no annotations)

```java
CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
CircuitBreaker cb = registry.circuitBreaker("testCircuitBreaker");

Supplier<StockResponse> decorated = CircuitBreaker
        .decorateSupplier(cb, () -> inventoryClient.getStock(sku));

StockResponse result = Try.ofSupplier(decorated)
        .recover(throwable -> fallback(sku, throwable))
        .get();
```
Useful when you need dynamic instance names, or you're calling Resilience4j from non-Spring code.

### 4.5 Verifying it at runtime

```bash
curl http://localhost:8090/actuator/health
curl http://localhost:8090/actuator/metrics/resilience4j.circuitbreaker.calls
```
Watch `resilience4j_circuitbreaker_state` transition 0 (CLOSED) → 1 (OPEN) → 2 (HALF_OPEN) as you force failures (e.g., shut down the downstream dependency) past the `minimumNumberOfCalls` / `failureRateThreshold` combination configured above.

---

## 5. Common Pitfalls Found in This Project

These are real bugs hit while wiring up this project — worth knowing since none of them throw a compile-time error, and most don't produce an obvious message pointing at the real cause.

| Symptom | Root cause | Fix |
|---|---|---|
| Every protected endpoint returns a generic `500 Internal Server Error`, log shows `NoSuchMethodException: No fallback method match found` | Fallback method's parameter list or return type didn't match the annotated method (e.g. an extra unused `String id` parameter, or returning `ResponseEntity<String>` when the method returns `String`) | Fallback signature = original method's params, in order, plus a trailing `Throwable`/`Exception`; return type must equal or be assignable to the original's |
| `@TimeLimiter`'s configured `timeoutDuration` never takes effect, no error at startup | `resilience4j.timelimiter` was indented one level too deep, nested inside `resilience4j.ratelimiter` instead of being a sibling of it — Spring Boot never binds it, so Resilience4j quietly uses library defaults | Move `timelimiter:` to the same indentation as `ratelimiter:`, `bulkhead:`, etc., directly under `resilience4j:` |
| A rate limiter / time limiter override in YAML is defined but seemingly ignored | The `instances.<name>` key in YAML didn't match the string passed to `name =` in the annotation, so the instance fell back to `configs.default` | Keep the YAML instance key and the annotation's `name` argument identical, character-for-character |
| A `CompletableFuture`-returning endpoint (thread-pool bulkhead) returns something like `java.util.concurrent.CompletableFuture@1a2b3c[Not completed]` instead of the actual response body | Controller called `.toString()` on the `CompletableFuture` itself instead of returning it, so it never awaited the async result | Return the `CompletableFuture<ResponseEntity<...>>` directly from the `@GetMapping` method and let Spring MVC's built-in async request handling resolve it |
| Fallback methods work when annotations sit on a controller method calling itself, but the design still feels wrong | `@CircuitBreaker`/`@Retry`/etc. work via a Spring AOP proxy — putting them on controller methods mixes transport concerns (HTTP status codes, request mapping) with resilience concerns (retry/breaker state), and only works at all because the call comes in externally through the proxy | Keep resilience annotations on the **service layer**, next to the remote call they protect; let the controller stay a thin translation layer to `ResponseEntity` |

---

## 6. Quick-Fire Follow-Ups

- **Q: Why have both `failureRateThreshold` and `slowCallRateThreshold`?** A hung/slow dependency ties up caller threads even without throwing — treating "too slow" as a failure mode is what actually protects the caller's thread pool.
- **Q: Why `minimumNumberOfCalls` separate from `slidingWindowSize`?** So a brand-new instance (or one with low traffic) doesn't trip on 1-2 unlucky calls before there's a statistically meaningful sample.
- **Q: What happens to calls while OPEN?** They fail immediately with `CallNotPermittedException` — the downstream dependency is never actually invoked, which is the whole point (fail fast, don't pile up load).
- **Q: `COUNT_BASED` vs `TIME_BASED` sliding window?** Count-based is predictable regardless of traffic rate; time-based better reflects "recent" health under highly variable load but can trip on a burst of failures during a quiet period.
- **Q: Why comment out `recordExceptions`/`ignoreExceptions` here vs. production?** Leaving them unset means *every* exception counts as a failure, including 4xx client errors that aren't the dependency's fault — fine for a demo, but in production you'd exclude `HttpClientErrorException` so bad client requests don't unfairly trip the breaker for everyone.

---

## Top 3 Areas That Separate Senior Candidates

1. **Understanding failure accounting, not just states** — knowing *why* `minimumNumberOfCalls`, sliding window type, and slow-call thresholds exist, not just memorizing CLOSED/OPEN/HALF_OPEN.
2. **Annotation stacking order and self-invocation pitfalls** — knowing that Spring AOP proxies mean same-class method calls bypass `@CircuitBreaker`, and that Retry/CircuitBreaker/TimeLimiter ordering changes behavior.
3. **Exception classification** — distinguishing failures the breaker *should* count (server errors, timeouts) from ones it shouldn't (client errors), and articulating the blast-radius reasoning behind that split.
