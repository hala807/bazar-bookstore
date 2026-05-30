# Performance Results — Bazar Bookstore (Lab 2)

## Response Time: With vs Without Cache

| Request | Without Cache (first hit) | With Cache (second hit) |
|---|---|---|
| GET /info/1 | 740 ms | 10 ms |
| GET /search/distributed systems | 16 ms | 10 ms |

## Cache Invalidation Test
- Sent POST /purchase/1 → triggered cache invalidation
- Next GET /info/1 was a cache MISS (cold request)
- Latency after invalidation: 9 ms

## Conclusion
Caching reduced response time by approximately 98.6% for /info/1 (740 ms → 10 ms).
The first /search hit was already fast (16 ms) because the JVM and catalog service were warm after the prior /info call.
