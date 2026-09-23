# LLM API Gateway

A Spring Boot service that sits between client apps and LLM providers: authenticates
API keys, rate-limits, caches responses, forwards to a primary provider with failover
to a backup, and records usage/cost.

Being built in phases — see progress below. Full architecture, setup instructions and
API docs will land in the README as later phases (observability, Docker Compose, CI,
load test results) are completed.

## Status: Phase 6 — observability

What exists so far:
- Maven project, Java 21, Spring Boot 3.2.5, Maven Wrapper (`mvnw` / `mvnw.cmd`) so a
  local Maven install isn't required
- `POST /v1/chat` forwards a prompt to Google Gemini (falling back to a local Ollama
  instance if Gemini is failing — see below) and returns the generated text —
  requires a valid `X-API-Key` header, is subject to a per-key rate limit, and
  identical requests are served from a Redis cache (`cached: true` in the response)
- `POST /admin/keys` creates an API key for a given name + tier (`FREE`/`PRO`) and
  returns the raw key **once**; only its SHA-256 hash is ever stored (Postgres, via
  Flyway migration `V1__create_api_keys_table.sql`)
- `GET /v1/usage` returns the calling key's total requests, tokens, estimated cost,
  and cache hits (Postgres `usage_logs`, Flyway `V2__create_usage_logs_table.sql`)
- Two Spring Security filter chains, each scoped to its own URL space: `/v1/**`
  requires a valid `X-API-Key`; `/admin/**` requires `X-Admin-Token` to match
  `ADMIN_TOKEN` (constant-time comparison, fails closed if unset) — either one
  missing/invalid → `401` with the same JSON error shape as every other error
- Token-bucket rate limiting per API key, in Redis, enforced by an atomic Lua script
  (`token_bucket.lua`) so the check-and-decrement can't race across concurrent
  requests or gateway instances. Limits are per tier (`rate-limit.free` /
  `rate-limit.pro` in `application.yml`). Over the limit → `429` with `Retry-After`;
  every `/v1/chat` response (allowed or not) carries `X-RateLimit-Remaining`
- Response cache key = SHA-256 of (model + normalized prompt + maxTokens), Redis with
  a TTL (`cache.ttl-seconds`) — deliberately shared across every caller, not scoped
  per API key (see the Phase 4 write-up for the trade-off)
- Usage is written **asynchronously** (`@Async`, on a virtual-thread executor) so
  logging never adds latency to the response the client is waiting on
- `FailoverLlmProvider` wraps Gemini with a Resilience4j circuit breaker, retry, and
  timeout (`resilience4j.*` in `application.yml`); when any of those trips, the same
  request transparently falls back to a local Ollama instance instead of failing —
  `ChatResponse.provider` reports whichever one actually answered
- Clean package layout: `controller / service / provider / repository / entity /
  security / ratelimit / cache / logging / metrics / util / config / dto / exception`
- Global exception handler → consistent JSON error body on any failure, and now also
  logs the real stack trace server-side for anything unexpected (the client still
  only ever sees the generic message)
- Actuator (`/actuator/health`, `/actuator/prometheus`) — an explicit allowlist, not
  `*`, since endpoints like `/actuator/env` can leak config values. Health includes
  the Gemini circuit breaker's own state, not just DB/Redis connectivity.
- Custom Micrometer metrics (`GatewayMetrics`): chat request count + latency
  histogram by provider, cache hit/miss, rate-limit rejections, provider fallback
  count — all exported in Prometheus format alongside the auto-instrumented JVM/HTTP
  metrics Spring Boot already provides for free
- Every log line is JSON (`logstash-logback-encoder`) and carries a per-request
  `requestId` (`RequestIdFilter`, via SLF4J's MDC) — generated fresh, or reused from
  an incoming `X-Request-Id` header for cross-service correlation, and echoed back
  as a response header
- Prometheus + Grafana in `docker-compose.yml`, with a provisioned dashboard
  (`monitoring/grafana/dashboards/llm-gateway.json`) — no manual setup after
  `docker compose up`

### Running locally

```bash
cp .env.example .env
# edit .env and set GEMINI_API_KEY and ADMIN_TOKEN (e.g. `openssl rand -hex 32`)
# Ollama fallback is optional for local dev — the app runs fine without it; Gemini
# failures without Ollama installed just surface as a 502 instead of failing over.

docker compose up -d postgres redis prometheus grafana

# load .env into your shell, then:
./mvnw spring-boot:run   # or .\mvnw.cmd spring-boot:run on Windows
```

Once running: Grafana at [http://localhost:3000](http://localhost:3000) (anonymous
viewer access is on — no login needed) has the "LLM API Gateway" dashboard already
provisioned. Prometheus itself is at [http://localhost:9090](http://localhost:9090).
Raw endpoints: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health),
[http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus).

```bash
# 1. Create a key (returns the raw key once) — requires the admin token
curl -X POST http://localhost:8080/admin/keys \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: <your ADMIN_TOKEN>" \
  -d '{"name": "my-test-app", "tier": "FREE"}'

# 2. Use it (FREE tier defaults to 10 requests/min — repeat 11 times to see a 429).
#    Run this exact command twice: the second response has "cached": true.
curl -i -X POST http://localhost:8080/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-<the key from step 1>" \
  -d '{"prompt": "Say hello in one sentence."}'

# 3. Check usage (requests, tokens, estimated cost, cache hits) for that key
curl http://localhost:8080/v1/usage \
  -H "X-API-Key: sk-<the key from step 1>"
```

### Running tests

```bash
./mvnw test   # or .\mvnw.cmd test on Windows
```

Integration tests (`*IntegrationTest`, and the app context test) spin up real
Postgres and Redis instances via Testcontainers — Docker must be running, but no
manual setup is needed.

**Windows + Docker Desktop:** if tests fail with `Could not find a valid Docker
environment`, Testcontainers is likely probing the wrong named pipe. Find the right
one with `docker context inspect $(docker context show)` (look for the `Host` field,
typically `npipe:////./pipe/dockerDesktopLinuxEngine`) and export it before running
tests:
```powershell
$env:DOCKER_HOST = "npipe:////./pipe/dockerDesktopLinuxEngine"
```

## Roadmap

- [x] Phase 1: Project skeleton and basic proxy
- [x] Phase 2: API-key authentication
- [x] Phase 3: Distributed rate limiting (Redis + Lua)
- [x] Phase 4: Response caching and usage metering
- [x] Phase 5: Failover and resilience (Ollama fallback, circuit breaker)
- [x] Phase 6: Observability (Prometheus + Grafana)
- [ ] Phase 7: Testing, load test, CI, docs
