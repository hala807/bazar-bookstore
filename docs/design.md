# Bazar.com Bookstore — Design Document

## Architecture Overview
Bazar.com is a two-tier distributed bookstore implemented as three microservices:
- **Frontend** (port 5000): Client-facing gateway. Handles all incoming requests, applies round-robin load balancing across catalog and order replicas, and manages an in-memory cache.
- **Catalog** (port 5001 / replica: 5003): Stores and serves book data (title, topic, price, quantity) persisted in a CSV file at /app/data/catalog.csv.
- **Order** (port 5002 / replica: 5004): Processes purchase requests, updates catalog stock, syncs replicas, and logs transactions to /app/data/orders.log.

## Lab 1: Two-Tier Design

### Components
| Service | Port | Responsibility |
|---|---|---|
| Frontend | 5000 | Request routing, client interface |
| Catalog | 5001 | Book search, info, stock updates |
| Order | 5002 | Purchase processing, order logging |

### REST API
| Method | Endpoint | Service | Description |
|---|---|---|---|
| GET | /search/:topic | Catalog | Search books by topic |
| GET | /info/:id | Catalog | Get book details |
| PUT | /update/:id | Catalog | Update price or quantity |
| POST | /purchase/:id | Order | Buy a book |

### Persistent Storage
- Catalog data is saved to `/app/data/catalog.csv` and loaded on startup.
- Order logs are appended to `/app/data/orders.log` on each purchase.
- Both paths are mounted as Docker named volumes to survive container restarts.

## Lab 2: Replication, Caching, and Load Balancing

### Replication
Two replicas are deployed for each backend service:
- `catalog` (primary) + `catalog-replica`
- `order` (primary) + `order-replica`

### Load Balancing
The frontend uses round-robin load balancing via an `AtomicInteger` counter. Each incoming request alternates between the primary and replica server.

### In-Memory Cache
The frontend maintains a `ConcurrentHashMap` cache keyed by request type:
- `info:<id>` for book info requests
- `search:<topic>` for search requests

On a cache hit, the result is returned immediately without contacting the catalog server, reducing latency by ~98% (740ms → 10ms measured).

### Cache Invalidation (Server-Push)
To maintain consistency, cache invalidation happens **before** any write:
1. Order server sends `DELETE /cache/invalidate/:id` to frontend before updating catalog.
2. Frontend removes the affected `info:<id>` entry and all `search:*` entries.
3. Order server then updates the primary catalog and syncs the replica.

### Consistency Model
This follows a **write-invalidate** consistency protocol:
- Reads are served from cache when available.
- Writes always invalidate cache first, then update primary, then sync replica.
- Replica sync failures are non-fatal (logged as warnings) to avoid blocking purchases.

## How to Run

### Prerequisites
- Docker and Docker Compose installed

### Steps
```bash
# Build and start all 5 services
docker-compose up --build

# Test search
curl http://localhost:5000/search/distributed%20systems

# Test info
curl http://localhost:5000/info/1

# Test purchase
curl -X POST http://localhost:5000/purchase/1
```

### Services Started
| Service | Port |
|---|---|
| frontend | 5000 |
| catalog (primary) | 5001 |
| catalog-replica | 5003 |
| order (primary) | 5002 |
| order-replica | 5004 |

## Design Tradeoffs
- **Cache consistency vs performance**: Invalidating before write adds one extra network call per purchase but guarantees no stale reads.
- **Replica sync is best-effort**: A failed replica sync logs a warning but does not fail the purchase. This prioritizes availability over strict consistency.
- **In-memory cache**: Simple and fast, but cache is lost on frontend restart. A persistent cache (e.g., Redis) would be more robust in production.
