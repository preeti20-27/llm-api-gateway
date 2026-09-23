// Load test for POST /v1/chat, measuring throughput and p95 latency under two
// conditions: cache cold (every request misses, so the full auth -> rate-limit ->
// cache-check -> provider-call -> cache-write path runs) vs. cache warm (every
// request after the first hits the cache, so the provider is never called at all).
//
// Requires an existing API key (create one via POST /admin/keys first — see the
// README's "Load testing" section for the full one-liner) with a high enough rate
// limit to sustain the VU count below without itself becoming the bottleneck being
// measured; the numbers recorded in the README used a PRO-tier key for exactly
// this reason.
//
// Run with the k6 Docker image (no local k6 install needed):
//   docker run --rm -i --network host \
//     -e BASE_URL=http://localhost:8080 -e API_KEY=<your key> \
//     grafana/k6 run - < k6/chat-load-test.js

import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY;

if (!API_KEY) {
  throw new Error('Set API_KEY to a real key from POST /admin/keys before running this script.');
}

// Custom trends so the summary reports cold-cache and warm-cache latency
// separately, instead of one number blending two very different code paths.
const coldCacheLatency = new Trend('cold_cache_latency_ms', true);
const warmCacheLatency = new Trend('warm_cache_latency_ms', true);

export const options = {
  scenarios: {
    cold_cache: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
      exec: 'coldCacheRequest',
    },
    warm_cache: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
      startTime: '35s', // runs after cold_cache finishes, not concurrently with it
      exec: 'warmCacheRequest',
    },
  },
  thresholds: {
    cold_cache_latency_ms: ['p(95)<5000'],
    warm_cache_latency_ms: ['p(95)<200'],
  },
};

const requestParams = {
  headers: {
    'Content-Type': 'application/json',
    'X-API-Key': API_KEY,
  },
};

export function coldCacheRequest() {
  // A unique prompt every call guarantees a cache miss every time, so this
  // measures the full request path, cache write included.
  const uniquePrompt = `Explain concept ${__VU}-${__ITER}-${Date.now()} in one short sentence.`;
  const res = http.post(
    `${BASE_URL}/v1/chat`,
    JSON.stringify({ prompt: uniquePrompt }),
    requestParams
  );

  check(res, {
    'cold: status is 200': (r) => r.status === 200,
    'cold: response not cached': (r) => r.json('cached') === false,
  });
  coldCacheLatency.add(res.timings.duration);
}

export function warmCacheRequest() {
  // The same prompt every call: after the very first one warms it, every
  // subsequent call hits the cache and never reaches the provider.
  const res = http.post(
    `${BASE_URL}/v1/chat`,
    JSON.stringify({ prompt: 'Say hello in one short sentence.' }),
    requestParams
  );

  check(res, {
    'warm: status is 200': (r) => r.status === 200,
  });
  warmCacheLatency.add(res.timings.duration);
}
