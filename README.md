# Financial API Gateway

A microservices-based financial transaction platform demonstrating distributed systems patterns: API gateway routing, event-driven processing with Kafka, JWT authentication, distributed rate limiting, and rule-based fraud detection.

Built as a portfolio project to demonstrate backend architecture and distributed systems design. 
Took inspiration after completing J.P. Morgan simulation and decided to expand on it. This project mimics person-to-person transfers (e.g Cash App) compared to full traditional banking

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Testing](#testing)
- [Logging](#logging)
- [Performance & Metrics](#performance--metrics)
- [Current Limitations](#current-limitations)

## Overview

This project simulates a financial transaction system split across four independently deployable services, communicating both synchronously (REST via Feign) and asynchronously (events via Kafka). A client interacts only with a single API Gateway, which handles authentication, rate limiting, and request routing to the appropriate downstream service.

## Architecture

![Gateway and Service Architecture](./docs/architecture-diagram.png)

![Data Model](./docs/data-model-diagram.png)

![Kafka Diagram](./docs/kafka-diagram.png)

**Request flow (synchronous):**
```
Client → Gateway (rate limit filter → JWT auth filter → routing) → downstream service
```

**Transaction flow (asynchronous, event-driven):**
```
POST /transactions → Transaction Service checks both accounts (Feign) → saves PENDING
    → publishes TransactionEvent to "transactions"
        → Account Service (account-group) → moves the money, at most once per transaction
            → publishes TransactionResultEvent to "transaction-results"
                → Transaction Service (transaction-group) → sets COMPLETE or FAILED
        → Fraud Service (fraud-group) → checks fraud rules (calls Account and Transaction services)
            → saves a FraudAlert if flagged
Failed events are retried, then sent to "transactions-dlt" / "transaction-results-dlt".
```

Each consumer group reads the same topic independently (Account Service and Fraud Service both receive every transaction event, but are responsible for different things)

## Tech Stack

- **Backend:** Spring Boot, Spring Cloud Gateway, Spring WebFlux
- **Messaging:** Apache Kafka
- **Caching / Rate Limiting:** Redis, Bucket4j
- **Database:** PostgreSQL 
- **Inter-service Communication:** OpenFeign
- **Authentication:** JWT (JJWT)
- **Testing:** JUnit 5, Mockito, k6
- **Infrastructure:** Docker

## Getting Started

### Prerequisites
- Docker & Docker Compose
- (Optional, for local development outside Docker) Java 21, Maven

### Running locally

```bash
git clone <repo-url>
cd financial-api-gateway
```

Each service requires its own `.env.local` file (not committed to source control). See `.env.example` in the project root for the required variables (database connection, Kafka bootstrap servers, JWT secret, Redis host).

Start the full stack from the project root:

```bash
docker compose up --build
```

This starts all four services, four PostgreSQL instances, Redis, Kafka, and Zookeeper on a shared Docker network.

### Verifying it's running

```bash
# Register a user
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"password123"}'
```

Use the returned JWT as a `Bearer` token for all subsequent requests.

**Note:** For testing/demo purposes MUST fund account via direct SQL. (see [Current Limitations](#current-limitations)).

## Testing

Unit tests focus on business logic with real branching/decision paths, rather than pass-through CRUD.

```bash
cd <service>   # accountservice | fraudservice | transactionservice | gatewayservice
./mvnw test
```

Tests don't need Docker: the `test` profile uses in-memory H2, embedded Kafka and a mocked Redis.

## Logging

Services log through SLF4J (`@Slf4j`) with async Log4j2, so request threads don't wait on log I/O.

- **Log the start:** each entry point logs a `DEBUG` line with its IDs.
- **Log failures once, where they happen:** `WARN` for expected failures, `ERROR` for faults that need attention.
- **No "done" logs:** a start line with no `WARN`/`ERROR` after it means success.
- **Business state changes** (transaction processed, fraud alert created, user registered...) log one `INFO` line.
- **Logs carry IDs only:** never passwords, tokens, emails, account numbers, amounts or whole DTOs.

| Level | Used for |
|---|---|
| `DEBUG` | Start of an operation |
| `INFO` | Business state changes |
| `WARN` | Expected failure, handled |
| `ERROR` | Failed, needs attention |

Default level is `INFO`. For a step-by-step trace, set `LOGGING_LEVEL_COM_FINANCIALAPIGATEWAY=DEBUG` in a service's `.env.local`.

## Performance & Metrics

Load tested `POST /api/v1/transactions` through the gateway with k6, at fixed request rates. How to run it: [`loadtests/README.md`](loadtests/README.md).

| Test | Requests | p50 | p95 | p99 | Errors |
|---|---|---|---|---|---|
| Steady 200 req/s, 2 min | 24,001 | 4.2 ms | 25.8 ms | 67.6 ms | 0% |
| Stress 25 → 400 req/s, 5 min | 57,741 | 4.4 ms | 29.8 ms | 88.7 ms | 0% |

- **Hardware:** Apple M3, 8 GB RAM; full stack in Docker (8 CPUs, 4 GB); clean database for each run.
- **At 400 req/s,** 8 requests (0.01%) were dropped, and the Fraud Service falls behind on events. The API stays fast because fraud checks run asynchronously.
- **Key fixes:** batched rate-limit updates in Redis, security filters run once per request, role read from the JWT (no DB lookup), and an indexed time-window count for the fraud frequency rule.

**Kafka end-to-end** (publish → balance updated, single transactions): ~492 ms after warm-up, ~1.8 s cold start.

## Current Limitations

- **Ownership not enforced:** services trust the IDs the client sends; the gateway doesn't yet pass the JWT's user to check ownership.
- **No deposits or withdrawals:** by design (person-to-person transfers); test accounts are funded with SQL.
- **Rule-based fraud detection:** three simple rules, not ML.
- **One Kafka broker, one partition per topic:** fine locally, but it limits throughput (the Fraud Service can't scale out) and has no failover.
- **No dead-letter consumer:** failed events are retried, then parked in a DLT for manual replay.
