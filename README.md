# LLM API Gateway

A Spring Boot service that sits between client apps and LLM providers: authenticates
API keys, rate-limits, caches responses, forwards to a primary provider with failover
to a backup, and records usage/cost.

Being built in phases — see progress below. Full architecture, setup instructions and
API docs will land in the README as later phases (observability, Docker Compose, CI,
load test results) are completed.

## Status: Phase 1 — project skeleton and basic proxy

What exists so far:
- Maven project, Java 21, Spring Boot 3.2.5
- `POST /v1/chat` forwards a prompt to Google Gemini and returns the generated text
- Clean package layout: `controller / service / provider / config / dto / exception`
- Global exception handler → consistent JSON error body on any failure
- Config via `application.yml`, values overridden by environment variables (see `.env.example`)

### Running locally

```bash
cp .env.example .env
# edit .env and set GEMINI_API_KEY

# load .env into your shell, then:
mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Say hello in one sentence."}'
```

### Running tests

```bash
mvn test
```

## Roadmap

- [x] Phase 1: Project skeleton and basic proxy
- [ ] Phase 2: API-key authentication
- [ ] Phase 3: Distributed rate limiting (Redis + Lua)
- [ ] Phase 4: Response caching and usage metering
- [ ] Phase 5: Failover and resilience (Ollama fallback, circuit breaker)
- [ ] Phase 6: Observability (Prometheus + Grafana)
- [ ] Phase 7: Testing, load test, CI, docs
