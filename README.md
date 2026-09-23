# LLM API Gateway

A Spring Boot service that sits between client apps and LLM providers: authenticates
API keys, rate-limits, caches responses, forwards to a primary provider with failover
to a backup, and records usage/cost.

Being built in phases — see progress below. Full architecture, setup instructions and
API docs will land in the README as later phases (observability, Docker Compose, CI,
load test results) are completed.

## Status: Phase 3 — distributed rate limiting

What exists so far:
- Maven project, Java 21, Spring Boot 3.2.5, Maven Wrapper (`mvnw` / `mvnw.cmd`) so a
  local Maven install isn't required
- `POST /v1/chat` forwards a prompt to Google Gemini and returns the generated text —
  requires a valid `X-API-Key` header and is subject to a per-key rate limit
- `POST /admin/keys` creates an API key for a given name + tier (`FREE`/`PRO`) and
  returns the raw key **once**; only its SHA-256 hash is ever stored (Postgres, via
  Flyway migration `V1__create_api_keys_table.sql`)
- Two Spring Security filter chains, each scoped to its own URL space: `/v1/**`
  requires a valid `X-API-Key`; `/admin/**` requires `X-Admin-Token` to match
  `ADMIN_TOKEN` (constant-time comparison, fails closed if unset) — either one
  missing/invalid → `401` with the same JSON error shape as every other error
- Token-bucket rate limiting per API key, in Redis, enforced by an atomic Lua script
  (`token_bucket.lua`) so the check-and-decrement can't race across concurrent
  requests or gateway instances. Limits are per tier (`rate-limit.free` /
  `rate-limit.pro` in `application.yml`). Over the limit → `429` with `Retry-After`;
  every `/v1/chat` response (allowed or not) carries `X-RateLimit-Remaining`
- Clean package layout: `controller / service / provider / repository / entity /
  security / ratelimit / config / dto / exception`
- Global exception handler → consistent JSON error body on any failure

### Running locally

```bash
cp .env.example .env
# edit .env and set GEMINI_API_KEY and ADMIN_TOKEN (e.g. `openssl rand -hex 32`)

docker compose up -d postgres redis

# load .env into your shell, then:
./mvnw spring-boot:run   # or .\mvnw.cmd spring-boot:run on Windows
```

```bash
# 1. Create a key (returns the raw key once) — requires the admin token
curl -X POST http://localhost:8080/admin/keys \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: <your ADMIN_TOKEN>" \
  -d '{"name": "my-test-app", "tier": "FREE"}'

# 2. Use it (FREE tier defaults to 10 requests/min — repeat 11 times to see a 429)
curl -i -X POST http://localhost:8080/v1/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sk-<the key from step 1>" \
  -d '{"prompt": "Say hello in one sentence."}'
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
- [ ] Phase 4: Response caching and usage metering
- [ ] Phase 5: Failover and resilience (Ollama fallback, circuit breaker)
- [ ] Phase 6: Observability (Prometheus + Grafana)
- [ ] Phase 7: Testing, load test, CI, docs
