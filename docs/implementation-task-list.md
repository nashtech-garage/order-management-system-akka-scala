# Microservice Transactional Patterns - Implementation Task List

## Order Management System (OMS)

**Created:** January 13, 2026  
**Based On:** [microservice-transactional-patterns.md](microservice-transactional-patterns.md)  
**Implementation Guide:** [microservice-transaction-implement-guide.md](microservice-transaction-implement-guide.md)

---

## How to Use This Document

Each task includes a **Code** link to the corresponding implementation code in the [Implementation Guide](microservice-transaction-implement-guide.md). Click the link to see detailed code examples for that task.

**Navigation Pattern:**
- Task `1.1.1` → [Section 1.1](microservice-transaction-implement-guide.md#11-infrastructure-setup) → Anchor `task-1.1.1`
- Use the "Code" column in each task table to jump directly to the implementation

---

## Task Status Legend

| Symbol | Status |
|--------|--------|
| ⬜ | Not Started |
| 🔄 | In Progress |
| ✅ | Completed |
| ⏸️ | Blocked |
| ❌ | Cancelled |

---

## Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        IMPLEMENTATION PHASES                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Phase 1       Phase 2       Phase 3       Phase 4       Phase 5            │
│  ════════      ════════      ════════      ════════      ═════════          │
│  Foundation    Events        Saga          TCC           Testing            │
│                                                                              │
│  ┌────────┐   ┌────────┐   ┌────────┐   ┌────────┐   ┌────────┐            │
│  │Outbox  │──►│Domain  │──►│Order   │──►│Payment │──►│Integr- │            │
│  │Pattern │   │Events  │   │Saga    │   │TCC     │   │ation   │            │
│  │        │   │        │   │        │   │        │   │Tests   │            │
│  │Idempt- │   │Event   │   │Cancel  │   │Timeout │   │        │            │
│  │ency    │   │Publish │   │Saga    │   │Handler │   │Failure │            │
│  │        │   │        │   │        │   │        │   │Tests   │            │
│  │Kafka   │   │Event   │   │Compen- │   │Recovery│   │        │            │
│  │Setup   │   │Subscr  │   │sation  │   │Mechan- │   │Perf    │            │
│  └────────┘   └────────┘   └────────┘   └────────┘   └────────┘            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Foundation

### 1.1 Infrastructure Setup

> **Goal:** Set up the message broker infrastructure (Kafka, Zookeeper, Redis) required for event-driven communication between microservices.
> 
> **📖 Code:** [Section 1.1](microservice-transaction-implement-guide.md#11-infrastructure-setup)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 1.1.1 | Add Zookeeper to docker-compose.yml | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-111-add-zookeeper-to-docker-composeyml) | Add Zookeeper service configuration using `confluentinc/cp-zookeeper:7.5.0` image. Configure port 2181, set `ZOOKEEPER_CLIENT_PORT` and `ZOOKEEPER_TICK_TIME` environment variables. Zookeeper is required for Kafka cluster coordination. |
| 1.1.2 | Add Kafka to docker-compose.yml | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-112-add-kafka-to-docker-composeyml) | Add Kafka broker service using `confluentinc/cp-kafka:7.5.0` image. Configure internal port 9092 and external port 29092. Set broker ID, Zookeeper connection, listener configurations, and enable auto topic creation. Add dependency on Zookeeper service. |
| 1.1.3 | Add Kafka UI to docker-compose.yml | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-113-add-kafka-ui-to-docker-composeyml) | Add Kafka UI service using `provectuslabs/kafka-ui:latest` image on port 8090. Configure connection to Kafka broker. This provides a web interface to monitor topics, consumers, and messages for development and debugging. |
| 1.1.4 | Add Redis to docker-compose.yml | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-114-add-redis-to-docker-composeyml) | Add Redis service using `redis:7.2-alpine` image on port 6379. Configure persistence volume and memory limits. Redis will be used for fast idempotency key lookups and optional caching layer. |
| 1.1.5 | Create Kafka topics script | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-115-create-kafka-topics-script) | Create a shell script `scripts/create-kafka-topics.sh` that creates required topics: `oms.orders.events` (6 partitions), `oms.products.events` (6 partitions), `oms.payments.events` (6 partitions), `oms.saga.commands` (3 partitions), and `oms.dlq` (1 partition). Set appropriate retention policies. |
| 1.1.6 | Update network configuration | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-116-update-network-configuration) | Ensure all services (existing microservices + new infrastructure) are on the same Docker network `oms-network`. Update existing service configurations to connect to Kafka at `kafka:9092` for internal communication. |
| 1.1.7 | Test infrastructure startup | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-117-test-infrastructure-startup) | Create a verification script that: (1) starts all containers with `docker-compose up -d`, (2) waits for health checks, (3) verifies Kafka is accepting connections, (4) creates a test topic and produces/consumes a test message, (5) verifies Redis connectivity with PING command. |

### 1.2 Database Schema - Outbox Pattern

> **Goal:** Create the `outbox_events` table in each service database to enable reliable event publishing using the Transactional Outbox pattern.
> 
> **📖 Code:** [Section 1.2](microservice-transaction-implement-guide.md#12-database-schema---outbox-pattern)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 1.2.1 | Create `outbox_events` table | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-121---125-outbox-events-table) | Create table with columns: `id` (BIGSERIAL PRIMARY KEY), `aggregate_type` (VARCHAR(100) - e.g., 'ORDER'), `aggregate_id` (VARCHAR(100) - the order ID), `event_type` (VARCHAR(100) - e.g., 'ORDER_CREATED'), `payload` (JSONB - serialized event data), `created_at` (TIMESTAMP), `published_at` (TIMESTAMP NULL), `retry_count` (INT DEFAULT 0), `status` (VARCHAR(20) - 'PENDING', 'PUBLISHED', 'FAILED'). |
| 1.2.2 | Create `outbox_events` table | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-121---125-outbox-events-table) | Create identical `outbox_events` table in Product Service database. This service will publish StockReserved, StockReservationFailed, and StockReleased events through the outbox. |
| 1.2.3 | Create `outbox_events` table | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-121---125-outbox-events-table) | Create identical `outbox_events` table in Payment Service database. This service will publish PaymentCompleted, PaymentFailed, and PaymentRefunded events through the outbox. |
| 1.2.4 | Add index on (status, created_at) | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-121---125-outbox-events-table) | Create composite index `CREATE INDEX idx_outbox_pending ON outbox_events(status, created_at) WHERE status = 'PENDING'` on each service's outbox table. This index optimizes the polling query that fetches pending events ordered by creation time. |
| 1.2.5 | Create Flyway migration scripts | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-121---125-outbox-events-table) | Create migration file `V2__add_outbox_events.sql` in each service's `src/main/resources/db/migration` directory. Include table creation, index creation, and any necessary constraints. Ensure migrations are idempotent. |

### 1.3 Database Schema - Idempotency Pattern

> **Goal:** Create the `idempotency_records` table to prevent duplicate processing of requests that may be retried due to network issues or client retries.
> 
> **📖 Code:** [Section 1.3](microservice-transaction-implement-guide.md#13-database-schema---idempotency-pattern)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 1.3.1 | Create `idempotency_records` table | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-131---135-idempotency-records-table) | Create table with columns: `idempotency_key` (VARCHAR(100) PRIMARY KEY - client-provided unique key), `request_hash` (VARCHAR(64) - SHA-256 hash of request payload), `response_data` (JSONB - cached successful response), `status` (VARCHAR(20) - 'PROCESSING', 'COMPLETED', 'FAILED'), `created_at` (TIMESTAMP), `expires_at` (TIMESTAMP - default 24 hours from creation). |
| 1.3.2 | Create `idempotency_records` table | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-131---135-idempotency-records-table) | Create identical table for Product Service. Used for idempotent stock operations like reserve/release that may be retried. |
| 1.3.3 | Create `idempotency_records` table | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-131---135-idempotency-records-table) | Create identical table for Payment Service. Critical for payment operations to prevent double-charging customers on retry. |
| 1.3.4 | Add index on expires_at | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-131---135-idempotency-records-table) | Create index `CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at)` to optimize the cleanup job that removes expired records. Consider partial index for non-expired records if table grows large. |
| 1.3.5 | Create Flyway migration scripts | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-131---135-idempotency-records-table) | Create migration file `V3__add_idempotency.sql` in each service. Include table creation, indexes, and a comment explaining the 24-hour TTL policy for idempotency keys. |

### 1.4 Database Schema - TCC Pattern

> **Goal:** Create reservation tables to track temporary resource reservations during the TCC (Try-Confirm-Cancel) transaction phases.
> 
> **📖 Code:** [Section 1.4](microservice-transaction-implement-guide.md#14-database-schema---tcc-pattern)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 1.4.1 | Create `stock_reservations` table | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-141---144-tcc-reservation-tables) | Create table with columns: `id` (BIGSERIAL PRIMARY KEY), `order_id` (VARCHAR(100) - reference to order), `product_id` (BIGINT - reference to product), `quantity` (INT - reserved quantity), `status` (VARCHAR(20) - 'RESERVED', 'CONFIRMED', 'CANCELLED'), `created_at` (TIMESTAMP), `expires_at` (TIMESTAMP - default 15 minutes), `confirmed_at` (TIMESTAMP NULL), `cancelled_at` (TIMESTAMP NULL). Add UNIQUE constraint on (order_id, product_id) to prevent duplicate reservations. |
| 1.4.2 | Create `payment_reservations` table | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-141---144-tcc-reservation-tables) | Create table with columns: `id` (BIGSERIAL PRIMARY KEY), `order_id` (VARCHAR(100)), `customer_id` (BIGINT), `amount` (DECIMAL(15,2) - reserved amount), `currency` (VARCHAR(3) DEFAULT 'USD'), `status` (VARCHAR(20) - 'RESERVED', 'CONFIRMED', 'CANCELLED'), `created_at` (TIMESTAMP), `expires_at` (TIMESTAMP - default 15 minutes), `transaction_id` (VARCHAR(100) NULL - set on confirm). Add UNIQUE constraint on order_id. |
| 1.4.3 | Add indexes on (order_id) and (status, expires_at) | Product/Payment | ⬜ | [📄](microservice-transaction-implement-guide.md#task-141---144-tcc-reservation-tables) | Create indexes: (1) `idx_reservation_order ON table(order_id)` for lookups by order, (2) `idx_reservation_expiry ON table(status, expires_at) WHERE status = 'RESERVED'` for the expiry cleanup job that cancels stale reservations. |
| 1.4.4 | Create Flyway migration scripts | Product/Payment | ⬜ | [📄](microservice-transaction-implement-guide.md#task-141---144-tcc-reservation-tables) | Create migration file `V4__add_tcc_tables.sql` in Product and Payment services. Document the 15-minute default expiry time and the TCC lifecycle in comments. |

### 1.5 Database Schema - Saga Pattern

> **Goal:** Create the `saga_instances` table to persist saga state, enabling recovery after service restarts and tracking of long-running transactions.
> 
> **📖 Code:** [Section 1.5](microservice-transaction-implement-guide.md#15-database-schema---saga-pattern)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 1.5.1 | Create `saga_instances` table | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-151---153-saga-instances-table) | Create table with columns: `id` (VARCHAR(100) PRIMARY KEY - saga instance ID), `saga_type` (VARCHAR(50) - 'CREATE_ORDER', 'CANCEL_ORDER'), `state` (VARCHAR(30) - 'STARTED', 'STEP_COMPLETED', 'COMPENSATING', 'COMPLETED', 'FAILED'), `current_step` (INT - current step index), `payload` (JSONB - saga context data including order details), `created_at` (TIMESTAMP), `updated_at` (TIMESTAMP), `completed_at` (TIMESTAMP NULL), `error_message` (TEXT NULL - failure reason if any). |
| 1.5.2 | Add index on (saga_type, state) | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-151---153-saga-instances-table) | Create composite index `CREATE INDEX idx_saga_type_state ON saga_instances(saga_type, state)` to efficiently query active sagas by type (e.g., find all in-progress CREATE_ORDER sagas for monitoring or recovery). |
| 1.5.3 | Create Flyway migration script | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-151---153-saga-instances-table) | Create migration file `V5__add_saga_instances.sql`. Include table creation, indexes, and document the saga state machine transitions in comments for developer reference. |

### 1.6 SBT Dependencies

> **Goal:** Add all required library dependencies for implementing distributed transaction patterns using the Akka ecosystem.
> 
> **📖 Code:** [Section 1.6](microservice-transaction-implement-guide.md#16-sbt-dependencies)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 1.6.1 | Add akka-persistence-typed dependency | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-161---168-sbt-dependencies) | Add `"com.typesafe.akka" %% "akka-persistence-typed" % "2.8.5"` to common module's build.sbt. This provides typed persistent actors for event sourcing, used to persist saga state and enable recovery after restarts. |
| 1.6.2 | Add akka-persistence-query dependency | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-161---168-sbt-dependencies) | Add `"com.typesafe.akka" %% "akka-persistence-query" % "2.8.5"`. This enables querying the event journal, useful for reading saga events and building read-side projections. |
| 1.6.3 | Add akka-persistence-jdbc dependency | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-161---168-sbt-dependencies) | Add `"com.lightbend.akka" %% "akka-persistence-jdbc" % "5.2.1"`. This plugin stores Akka Persistence events and snapshots in PostgreSQL, integrating with our existing database infrastructure. |
| 1.6.4 | Add akka-stream-kafka (Alpakka) dependency | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-161---168-sbt-dependencies) | Add `"com.typesafe.akka" %% "akka-stream-kafka" % "5.0.0"`. Alpakka Kafka provides Akka Streams integration for producing and consuming Kafka messages with backpressure support. |
| 1.6.5 | Add akka-serialization-jackson dependency | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-161---168-sbt-dependencies) | Add `"com.typesafe.akka" %% "akka-serialization-jackson" % "2.8.5"`. This provides JSON serialization for Akka messages and persistent events using Jackson, with support for schema evolution. |
| 1.6.6 | Add Redis client dependency | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-161---168-sbt-dependencies) | Add `"net.debasishg" %% "redisclient" % "3.42"` OR `"dev.profunktor" %% "redis4cats-effects" % "1.5.2"` (if using Cats Effect). Redis client is used for fast idempotency key lookups with TTL support. |
| 1.6.7 | Add Flyway dependency | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-161---168-sbt-dependencies) | Add `"org.flywaydb" % "flyway-core" % "9.22.3"` and configure sbt-flyway plugin. Flyway manages database schema migrations, ensuring all environments have consistent schema versions. |
| 1.6.8 | Verify dependency compatibility | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-161---168-sbt-dependencies) | Run `sbt compile` on all services to verify no dependency conflicts. Check for version mismatches between Akka modules (all must use same version). Resolve any eviction warnings. Document final dependency tree. |

### 1.7 Outbox Pattern Implementation

> **Goal:** Implement the Outbox Pattern components to guarantee at-least-once event delivery by storing events in the database before publishing to Kafka.
> 
> **📖 Code:** [Section 1.7](microservice-transaction-implement-guide.md#17-outbox-pattern-implementation)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 1.7.1 | Create `OutboxEvent` model class | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-171---172-outboxevent-model) | Create case class `OutboxEvent(id: Long, aggregateType: String, aggregateId: String, eventType: String, payload: String, createdAt: Instant, publishedAt: Option[Instant], retryCount: Int, status: OutboxStatus)` in `com.oms.common.outbox` package. Include companion object with JSON codecs. |
| 1.7.2 | Create `OutboxRepository` trait | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-171---172-outboxevent-model) | Define trait with methods: `insert(event: OutboxEvent): Future[OutboxEvent]`, `findPendingEvents(limit: Int): Future[Seq[OutboxEvent]]`, `markAsPublished(id: Long): Future[Unit]`, `markAsFailed(id: Long): Future[Unit]`, `incrementRetryCount(id: Long): Future[Unit]`. |
| 1.7.3 | Implement `OutboxRepositoryImpl` | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-173-outboxrepositoryimpl) | Implement repository using Slick. Create `OutboxEventsTable` class mapping to database columns. Implement all trait methods with proper transaction handling. Use `forUpdate` lock when fetching pending events to prevent duplicate processing in clustered deployments. |
| 1.7.4 | Create `OutboxProcessor` actor | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-174-outboxprocessor-actor) | Create typed Akka actor that: (1) schedules periodic polling (default: every 1 second), (2) fetches pending events from repository, (3) publishes each event to Kafka via EventPublisher, (4) marks events as published on success or increments retry count on failure, (5) moves to DLQ after max retries (default: 5). |
| 1.7.5 | Create `KafkaEventPublisher` | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-175-kafkaeventpublisher) | Create class using Alpakka Kafka Producer. Implement `publish(topic: String, key: String, value: String): Future[Done]` method. Configure producer settings: acks=all, retries=3, idempotence=true. Route events to topics based on aggregate type (ORDER→oms.orders.events, etc.). |
| 1.7.6 | Add OutboxProcessor to Order Service | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-176---178-service-integration) | In Order Service's Main.scala, spawn OutboxProcessor actor on application startup. Configure polling interval and batch size from application.conf. Ensure graceful shutdown stops processor before database connection pool. |
| 1.7.7 | Add OutboxProcessor to Product Service | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-176---178-service-integration) | Same as 1.7.6 but for Product Service. Product Service publishes StockReserved, StockReservationFailed, and StockReleased events. |
| 1.7.8 | Add OutboxProcessor to Payment Service | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-176---178-service-integration) | Same as 1.7.6 but for Payment Service. Payment Service publishes PaymentCompleted, PaymentFailed, and PaymentRefunded events. |
| 1.7.9 | Write unit tests for OutboxProcessor | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-179-unit-tests) | Test scenarios: (1) successfully publishes pending events, (2) marks events as published after Kafka confirmation, (3) retries on transient Kafka failures, (4) moves to DLQ after max retries, (5) handles empty pending queue gracefully, (6) processes events in order by created_at. |

### 1.8 Idempotency Pattern Implementation

> **Goal:** Implement idempotency support to safely handle duplicate requests caused by client retries or network issues.
> 
> **📖 Code:** [Section 1.8](microservice-transaction-implement-guide.md#18-idempotency-pattern-implementation)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 1.8.1 | Create `IdempotencyRecord` model | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-181---182-idempotencyrecord-model) | Create case class `IdempotencyRecord(key: String, requestHash: String, responseData: Option[String], status: IdempotencyStatus, createdAt: Instant, expiresAt: Instant)` in `com.oms.common.idempotency`. Create sealed trait `IdempotencyStatus` with `Processing`, `Completed`, `Failed` case objects. |
| 1.8.2 | Create `IdempotencyRepository` trait | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-181---182-idempotencyrecord-model) | Define trait with methods: `find(key: String): Future[Option[IdempotencyRecord]]`, `insert(record: IdempotencyRecord): Future[IdempotencyRecord]`, `updateStatus(key: String, status: IdempotencyStatus, response: Option[String]): Future[Unit]`, `deleteExpired(): Future[Int]`. |
| 1.8.3 | Implement `IdempotencyRepositoryImpl` | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-183-idempotencyrepositoryimpl) | Implement using Slick. Use `insertOrUpdate` with conflict handling on primary key. Implement optimistic locking to handle concurrent requests with same idempotency key. |
| 1.8.4 | Create `IdempotencyService` | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-184-idempotencyservice) | Create service class with method `executeIdempotent[T](key: String, request: Any)(operation: => Future[T]): Future[T]`. Logic: (1) check if key exists, (2) if COMPLETED return cached response, (3) if PROCESSING return 409 Conflict, (4) if not exists insert with PROCESSING status, (5) execute operation, (6) update status to COMPLETED with response on success or FAILED on error. |
| 1.8.5 | Create cleanup scheduled job | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-185-cleanup-job) | Create scheduled actor or use Akka Scheduler to run `deleteExpired()` periodically (default: every hour). Log number of deleted records. Configure retention period in application.conf (default: 24 hours). |
| 1.8.6 | Add idempotencyKey to CreateOrderRequest | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-186---187-order-service-integration) | Add optional field `idempotencyKey: Option[String]` to `CreateOrderRequest` case class. Update JSON decoder to handle the new field. Document in API that clients should generate UUID for this field. |
| 1.8.7 | Integrate idempotency check in OrderActor | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-186---187-order-service-integration) | Modify `CreateOrder` command handling to wrap the operation with `IdempotencyService.executeIdempotent()` when idempotencyKey is provided. Return cached response for duplicate requests. |
| 1.8.8 | Write unit tests for IdempotencyService | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-188-unit-tests) | Test scenarios: (1) first request creates record and executes, (2) duplicate request with same key returns cached response, (3) concurrent requests with same key - one executes, other gets 409, (4) different key different request hash is rejected, (5) expired records are cleaned up, (6) failed operations are retryable with same key. |
| 1.8.4 | ⬜ Create `IdempotencyService` | Common | | | Create service class with method `executeIdempotent[T](key: String, request: Any)(operation: => Future[T]): Future[T]`. Logic: (1) check if key exists, (2) if COMPLETED return cached response, (3) if PROCESSING return 409 Conflict, (4) if not exists insert with PROCESSING status, (5) execute operation, (6) update status to COMPLETED with response on success or FAILED on error. |
| 1.8.5 | ⬜ Create cleanup scheduled job | Common | | | Create scheduled actor or use Akka Scheduler to run `deleteExpired()` periodically (default: every hour). Log number of deleted records. Configure retention period in application.conf (default: 24 hours). |
| 1.8.6 | ⬜ Add idempotencyKey to CreateOrderRequest | Order Service | | | Add optional field `idempotencyKey: Option[String]` to `CreateOrderRequest` case class. Update JSON decoder to handle the new field. Document in API that clients should generate UUID for this field. |
| 1.8.7 | ⬜ Integrate idempotency check in OrderActor | Order Service | | | Modify `CreateOrder` command handling to wrap the operation with `IdempotencyService.executeIdempotent()` when idempotencyKey is provided. Return cached response for duplicate requests. |
| 1.8.8 | ⬜ Write unit tests for IdempotencyService | Common | | | Test scenarios: (1) first request creates record and executes, (2) duplicate request with same key returns cached response, (3) concurrent requests with same key - one executes, other gets 409, (4) different key different request hash is rejected, (5) expired records are cleaned up, (6) failed operations are retryable with same key. |

---

## Phase 2: Event-Driven Communication

### 2.1 Domain Events - Common Module

> **Goal:** Define all domain events as immutable case classes in the common module, establishing a shared event schema for inter-service communication.
> 
> **📖 Code:** [Section 2.1](microservice-transaction-implement-guide.md#21-domain-events---common-module)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 2.1.1 | Create `DomainEvent` base trait | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-211-domainevent-base-trait) | Create sealed trait `DomainEvent` with common fields: `eventId: String` (UUID), `eventType: String`, `aggregateId: String`, `aggregateType: String`, `timestamp: Instant`, `version: Int` (for schema evolution). Place in `com.oms.common.events` package. All domain events must extend this trait. |
| 2.1.2 | Create `OrderEvents` sealed trait and case classes | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-212-orderevents) | Create: `OrderCreated(eventId, orderId, customerId, items: List[OrderItem], totalAmount, timestamp)`, `OrderConfirmed(eventId, orderId, timestamp)`, `OrderPaid(eventId, orderId, paymentId, amount, timestamp)`, `OrderCancelled(eventId, orderId, reason, cancelledBy, timestamp)`, `OrderCompleted(eventId, orderId, timestamp)`. Each extends DomainEvent. |
| 2.1.3 | Create `StockEvents` sealed trait and case classes | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-213-stockevents) | Create: `StockReserved(eventId, orderId, reservations: List[StockReservationItem], timestamp)`, `StockReservationFailed(eventId, orderId, productId, reason, availableQty, requestedQty, timestamp)`, `StockReleased(eventId, orderId, productId, quantity, reason, timestamp)`, `StockConfirmed(eventId, orderId, timestamp)`. |
| 2.1.4 | Create `PaymentEvents` sealed trait and case classes | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-214-paymentevents) | Create: `PaymentReserved(eventId, orderId, reservationId, amount, expiresAt, timestamp)`, `PaymentCompleted(eventId, orderId, paymentId, transactionId, amount, timestamp)`, `PaymentFailed(eventId, orderId, reason, errorCode, timestamp)`, `PaymentRefunded(eventId, orderId, paymentId, refundId, amount, timestamp)`. |
| 2.1.5 | Create JSON serializers for all events | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-215-json-serializers) | Create implicit JSON encoders/decoders for all event types using Jackson or Circe. Handle polymorphic serialization using a `type` discriminator field. Ensure backward compatibility by making new fields optional with defaults. Register serializers with Akka serialization. |
| 2.1.6 | Write unit tests for event serialization | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-216-unit-tests) | Test round-trip serialization (serialize → deserialize → compare) for all event types. Test backward compatibility by deserializing old event formats. Test that unknown fields are ignored (forward compatibility). Verify timestamp timezone handling (should use UTC). |

### 2.2 Event Publishers

> **Goal:** Implement event publishing infrastructure that routes domain events to appropriate Kafka topics through the Outbox pattern.
> 
> **📖 Code:** [Section 2.2](microservice-transaction-implement-guide.md#22-event-publishers)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 2.2.1 | Create `EventPublisher` trait | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-221---222-eventpublisher-trait) | Define trait with method `publish(event: DomainEvent): Future[Done]`. Add overload `publish(topic: String, key: String, event: DomainEvent): Future[Done]` for explicit topic routing. Include method `publishBatch(events: Seq[DomainEvent]): Future[Done]` for bulk publishing. |
| 2.2.2 | Implement `KafkaEventPublisher` | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-221---222-eventpublisher-trait) | Implement using Alpakka Kafka Producer API. Create `ProducerSettings` with serializers. Implement `publish()` to create `ProducerRecord` with event key (aggregateId) for partition assignment. Use `Producer.plainSink` for fire-and-forget or `Producer.flexiFlow` for acknowledgment. |
| 2.2.3 | Configure Kafka producer settings | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-223-kafka-producer-settings) | Add to application.conf: `akka.kafka.producer { kafka-clients { bootstrap.servers, acks=all, retries=3, enable.idempotence=true, max.in.flight.requests.per.connection=1 }}`. Configure serializers, compression (snappy), and batch settings for throughput. |
| 2.2.4 | Integrate publisher with OutboxProcessor | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-224-outbox-integration) | Modify OutboxProcessor to use KafkaEventPublisher for actual message delivery. Parse outbox event payload back to DomainEvent, then publish. On successful Kafka acknowledgment, mark outbox event as published. Handle Kafka failures with retry logic. |
| 2.2.5 | Add event routing by type | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-225-event-routing) | Create `EventRouter` that maps event types to topics: OrderEvents → `oms.orders.events`, StockEvents → `oms.products.events`, PaymentEvents → `oms.payments.events`. Use pattern matching on event type. Log routing decisions for debugging. |
| 2.2.6 | Write integration tests | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-226-integration-tests) | Use embedded Kafka (testcontainers) for integration tests. Test: (1) event published to correct topic, (2) event key matches aggregateId, (3) message headers contain correlation ID, (4) failed publish triggers retry, (5) batch publishing maintains order. |

### 2.3 Event Subscribers - Order Service

> **Goal:** Implement event consumers in Order Service to react to stock and payment events, enabling the choreography-based Saga pattern.
> 
> **📖 Code:** [Section 2.3](microservice-transaction-implement-guide.md#23-event-subscribers---order-service)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 2.3.1 | Create `EventSubscriber` trait | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-231---232-eventsubscriber) | Define trait with methods: `subscribe(topic: String, groupId: String)(handler: DomainEvent => Future[Done]): Control`, `subscribePartitioned(topic: String, groupId: String)(handler: (Int, DomainEvent) => Future[Done]): Control`. Return Alpakka Kafka `Control` for lifecycle management. |
| 2.3.2 | Implement `KafkaEventSubscriber` | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-231---232-eventsubscriber) | Implement using Alpakka Kafka Consumer API. Create `ConsumerSettings` with deserializers and group ID. Use `Consumer.committableSource` for at-least-once delivery. Implement manual offset commit after successful processing. Handle deserialization errors gracefully. |
| 2.3.3 | Create `StockEventHandler` | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-233-stockeventhandler) | Create handler class that processes: `StockReserved` → update saga state, trigger order confirmation step; `StockReservationFailed` → trigger saga compensation, publish OrderCancelled event. Inject SagaRepository and OrderRepository. Log all event handling with correlation ID. |
| 2.3.4 | Create `PaymentEventHandler` | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-234-paymenteventhandler) | Create handler class that processes: `PaymentCompleted` → update order status to 'paid', publish OrderPaid event; `PaymentFailed` → trigger compensation if needed, update order status. Handle idempotently by checking current order state before processing. |
| 2.3.5 | Subscribe to oms.products.events | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-235---236-subscriptions) | In application startup, call `subscriber.subscribe("oms.products.events", "order-service-group")(stockEventHandler.handle)`. Configure consumer: auto.offset.reset=earliest, enable.auto.commit=false. Store consumer Control for graceful shutdown. |
| 2.3.6 | Subscribe to oms.payments.events | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-235---236-subscriptions) | In application startup, call `subscriber.subscribe("oms.payments.events", "order-service-group")(paymentEventHandler.handle)`. Use same consumer group for all Order Service instances to ensure each event is processed once. |
| 2.3.7 | Write integration tests | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-237-integration-tests) | Test with embedded Kafka: (1) StockReserved event triggers order confirmation, (2) StockReservationFailed triggers compensation, (3) PaymentCompleted updates order status, (4) duplicate events are handled idempotently, (5) consumer recovers after restart. |

### 2.4 Event Subscribers - Product Service

> **Goal:** Implement event consumers in Product Service to automatically reserve or release stock in response to order events.
> 
> **📖 Code:** [Section 2.4](microservice-transaction-implement-guide.md#24-event-subscribers---product-service)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 2.4.1 | Create `OrderEventHandler` | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-241-ordereventhandler-product-service) | Create handler class that processes: `OrderCreated` → attempt to reserve stock for all items, publish StockReserved or StockReservationFailed; `OrderCancelled` → release any reserved stock, publish StockReleased. Inject StockReservationRepository and ProductRepository. |
| 2.4.2 | Subscribe to oms.orders.events | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-242-subscription) | In application startup, subscribe to order events topic with consumer group "product-service-group". Filter for relevant event types (OrderCreated, OrderCancelled, OrderPaid). Ignore other order events. |
| 2.4.3 | Implement stock reservation on OrderCreated | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-243-stock-reservation) | When OrderCreated received: (1) extract items list, (2) for each item check stock availability, (3) if all available create StockReservation records in RESERVED status, (4) publish StockReserved event. Use database transaction to ensure atomicity. |
| 2.4.4 | Implement stock release on OrderCancelled | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-244-stock-release) | When OrderCancelled received: (1) find all StockReservation records for orderId, (2) update status to CANCELLED, (3) restore stock quantities to products table, (4) publish StockReleased event for each product. Handle case where no reservations exist (idempotency). |
| 2.4.5 | Write integration tests | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-245-integration-tests) | Test: (1) OrderCreated with sufficient stock creates reservations, (2) OrderCreated with insufficient stock publishes failure event, (3) OrderCancelled releases reserved stock, (4) duplicate OrderCancelled is handled safely, (5) partial reservation failure rolls back all. |

### 2.5 Event Subscribers - Payment Service

> **Goal:** Implement event consumers in Payment Service to handle refunds when orders are cancelled.
> 
> **📖 Code:** [Section 2.5](microservice-transaction-implement-guide.md#25-event-subscribers---payment-service)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 2.5.1 | Create `OrderEventHandler` | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-251-ordereventhandler-payment-service) | Create handler class that processes `OrderCancelled` event. Check if order has associated payment with status 'completed'. If yes, initiate refund by creating refund record and publishing PaymentRefunded event. If no payment exists, ignore the event. |
| 2.5.2 | Subscribe to oms.orders.events | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-252-subscription) | In application startup, subscribe with consumer group "payment-service-group". Filter for OrderCancelled events only. Configure appropriate consumer timeouts for payment processing operations. |
| 2.5.3 | Implement refund on OrderCancelled (if paid) | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-253-refund-logic) | When OrderCancelled received: (1) query PaymentRepository for orderId, (2) if payment exists with status='completed', create Refund record, (3) simulate refund processing (80% success rate like payments), (4) publish PaymentRefunded on success. Handle already-refunded case. |
| 2.5.4 | Write integration tests | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-254-integration-tests) | Test: (1) OrderCancelled for paid order triggers refund, (2) OrderCancelled for unpaid order is ignored, (3) OrderCancelled for already-refunded order is idempotent, (4) refund failure is logged and can be retried, (5) PaymentRefunded event is published correctly. |

### 2.6 Event Logging & Auditing

> **Goal:** Implement comprehensive event logging for debugging, auditing, and distributed tracing across services.
> 
> **📖 Code:** [Section 2.6](microservice-transaction-implement-guide.md#26-event-logging--auditing)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 2.6.1 | Create `EventLogger` | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-261-eventlogger) | Create logging utility that logs events in structured JSON format including: eventId, eventType, aggregateId, timestamp, correlationId, service name, direction (PUBLISHED/RECEIVED). Use SLF4J MDC for correlation ID propagation. |
| 2.6.2 | Add correlation ID to events | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-262-correlation-id) | Add `correlationId: String` field to DomainEvent trait. Generate correlation ID at saga start, propagate through all related events. In HTTP requests, extract from X-Correlation-ID header or generate new one. Store in MDC for logging. |
| 2.6.3 | Implement event audit trail | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-263-audit-trail) | Create `EventAuditLog` table with columns: id, event_id, event_type, aggregate_id, correlation_id, payload, direction, service_name, timestamp. Insert record for every published and received event. Consider async insertion to avoid blocking main flow. |
| 2.6.4 | Add event logging middleware | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-264-logging-middleware) | Create wrapper for EventPublisher that logs before publishing. Create wrapper for event handlers that logs on receive. Include timing information for performance monitoring. Log errors with full stack trace for failed event processing. |

---

## Phase 3: Saga Implementation

### 3.1 Saga Framework

> **Goal:** Build a reusable Saga framework that manages saga state, step execution, and compensation logic for choreography-based distributed transactions.
> 
> **📖 Code:** [Section 3.1](microservice-transaction-implement-guide.md#31-saga-framework)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 3.1.1 | Create `SagaState` sealed trait | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-311-sagastate) | Define sealed trait with case objects: `Started` (saga initialized), `StepInProgress(stepIndex: Int)` (executing a step), `StepCompleted(stepIndex: Int)` (step finished successfully), `Compensating(fromStep: Int)` (rolling back), `Completed` (all steps done), `Failed(error: String)` (saga failed after compensation). |
| 3.1.2 | Create `SagaStep` model | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-312-sagastep) | Create case class `SagaStep[T](name: String, execute: T => Future[StepResult], compensate: T => Future[Unit], isCompensatable: Boolean = true)`. `StepResult` is sealed trait with `Success(data: Any)` and `Failure(error: String)`. Steps define forward action and rollback action. |
| 3.1.3 | Create `SagaInstance` model | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-313-sagainstance) | Create case class for database persistence: `SagaInstance(id: String, sagaType: String, state: SagaState, currentStep: Int, payload: JsValue, createdAt: Instant, updatedAt: Instant, completedAt: Option[Instant], errorMessage: Option[String])`. Include Slick table mapping. |
| 3.1.4 | Create `SagaRepository` | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-314-sagarepository) | Implement repository with methods: `create(instance: SagaInstance)`, `findById(id: String)`, `updateState(id: String, state: SagaState, step: Int)`, `markCompleted(id: String)`, `markFailed(id: String, error: String)`, `findByTypeAndState(sagaType: String, state: SagaState)`. |
| 3.1.5 | Create `SagaCoordinator` actor | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-315-sagacoordinator) | Create typed Akka actor that manages saga lifecycle. Commands: `StartSaga`, `StepCompleted`, `StepFailed`, `ExternalEventReceived`. Actor maintains saga state, persists to database, executes steps in order, triggers compensation on failure. Use behavior switching for state machine. |
| 3.1.6 | Write unit tests for SagaCoordinator | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-316-unit-tests) | Test: (1) happy path executes all steps in order, (2) step failure triggers compensation in reverse order, (3) state is persisted after each step, (4) external events are correlated to correct saga, (5) saga can be recovered from persisted state after restart. |

### 3.2 Order Creation Saga

> **Goal:** Implement the Create Order Saga that coordinates order creation, customer validation, stock reservation, and order confirmation across services.
> 
> **📖 Code:** [Section 3.2](microservice-transaction-implement-guide.md#32-order-creation-saga)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 3.2.1 | Define CreateOrderSaga steps | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-321-createordersaga-definition) | Define saga with 4 steps: (1) CreateDraftOrder - insert order with status='draft', (2) ValidateCustomer - verify customer exists and is active via Customer Service, (3) ReserveStock - publish OrderCreated event to trigger stock reservation, (4) ConfirmOrder - update order status to 'created' after receiving StockReserved event. |
| 3.2.2 | Implement Step 1: Create Draft Order | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-322-step-1) | Create function that inserts order record with status='draft' into orders table. Generate order ID (UUID). Store all order data in saga payload. Insert outbox event for audit. Return order ID on success. Compensation: delete the draft order record. |
| 3.2.3 | Implement Step 2: Validate Customer | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-323-step-2) | Create function that calls Customer Service GET /customers/{id} endpoint. Verify response status is 200 and customer is active. Store customer name in saga payload for enrichment. No compensation needed (read-only step). Fail saga if customer not found or inactive. |
| 3.2.4 | Implement Step 3: Reserve Stock (publish event) | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-324-step-3) | Create function that inserts OrderCreated event into outbox table. Event contains orderId, customerId, items list with productId and quantity. OutboxProcessor will publish to Kafka. Saga waits for StockReserved or StockReservationFailed event. Compensation: publish StockReleaseRequested event. |
| 3.2.5 | Implement Step 4: Confirm Order | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-325-step-4) | Create function that updates order status from 'draft' to 'created', sets confirmedAt timestamp. Insert OrderConfirmed event to outbox. This step executes only after receiving StockReserved event. Compensation: update order status to 'cancelled'. |
| 3.2.6 | Handle StockReserved event | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-326-stockreserved-handler) | In StockEventHandler, when StockReserved event received: (1) find saga instance by orderId, (2) verify saga is waiting for this event, (3) send StepCompleted message to SagaCoordinator actor, (4) coordinator advances to Step 4. Log correlation ID for tracing. |
| 3.2.7 | Implement compensation for Step 1 | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-327-compensation-step-1) | Create compensation function that deletes the draft order record from database. Log the compensation action. Handle case where order doesn't exist (already compensated). This is last compensation to execute in reverse order. |
| 3.2.8 | Implement compensation for Step 3 | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-328-compensation-step-3) | Create compensation function that publishes StockReleaseRequested event to outbox. Event contains orderId and list of items to release. Product Service will handle the event and release any reserved stock. Fire-and-forget, don't wait for confirmation. |
| 3.2.9 | Implement compensation for Step 4 | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-329-compensation-step-4) | Create compensation function that updates order status to 'cancelled', sets cancelledAt timestamp and reason='Saga compensation'. Insert OrderCancelled event to outbox. This executes first in compensation chain (reverse order). |
| 3.2.10 | Handle StockReservationFailed event | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-3210-failure-handler) | In StockEventHandler, when StockReservationFailed received: (1) find saga instance by orderId, (2) extract failure reason (e.g., "Insufficient stock for product X"), (3) send StepFailed message to SagaCoordinator, (4) coordinator triggers compensation chain starting from current step. |
| 3.2.11 | Update OrderActor to use saga | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-3211-orderactor-integration) | Modify CreateOrder command handling: instead of direct order creation, spawn CreateOrderSaga via SagaCoordinator. Return immediately with orderId and status='processing'. Client polls for order status. Remove old synchronous creation logic. |
| 3.2.12 | Write integration tests | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-3212-integration-tests) | Test happy path end-to-end: (1) send CreateOrder request, (2) verify order created with status='draft', (3) mock Customer Service validation, (4) simulate StockReserved event, (5) verify order status='created'. Use embedded Kafka for event testing. |
| 3.2.13 | Write compensation tests | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-3213-compensation-tests) | Test failure scenarios: (1) customer validation fails → order deleted, (2) stock reservation fails → order cancelled, stock release event published, (3) multiple items partial failure → all compensations executed, (4) compensation failure is logged and can be retried. |

### 3.3 Order Cancellation Saga

> **Goal:** Implement the Cancel Order Saga that coordinates stock release, payment refund, and order status update across services.
> 
> **📖 Code:** [Section 3.3](microservice-transaction-implement-guide.md#33-order-cancellation-saga)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 3.3.1 | Define CancelOrderSaga steps | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-331-cancelordersaga-definition) | Define saga with 4 steps: (1) ValidateCancellation - check order status allows cancellation, (2) RequestStockRelease - publish event to release reserved stock, (3) RequestRefund - publish event to refund payment if order was paid, (4) CompleteCancellation - update order status to 'cancelled'. |
| 3.3.2 | Implement Step 1: Validate cancellation allowed | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-332-step-1) | Create function that: (1) loads order by ID, (2) checks status is in ['draft', 'created', 'paid'], (3) rejects if status is 'shipping' or 'completed' with clear error message. Store current order state in saga payload. No compensation needed for validation. |
| 3.3.3 | Implement Step 2: Publish OrderCancellationRequested | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-333-step-2) | Create function that inserts OrderCancellationRequested event into outbox. Event contains orderId, items list, reason for cancellation. Product Service listens for this event to release stock. Saga waits for StockReleased event confirmation. |
| 3.3.4 | Implement Step 3: Trigger refund (if paid) | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-334-step-3) | Create conditional step that checks if order status was 'paid'. If yes, insert RefundRequested event into outbox with orderId and payment amount. Wait for PaymentRefunded event. If order was not paid, skip this step automatically. |
| 3.3.5 | Implement Step 4: Update status to 'cancelled' | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-335-step-4) | Create function that updates order status to 'cancelled', sets cancelledAt, cancelledBy, and cancellationReason fields. Insert OrderCancelled event into outbox. Mark saga as completed. This is the final step. |
| 3.3.6 | Handle StockReleased event | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-336-stockreleased-handler) | In StockEventHandler, when StockReleased received for cancellation saga: (1) find active CancelOrderSaga by orderId, (2) verify saga is waiting for stock release, (3) send StepCompleted to coordinator, (4) advance to refund step or completion. |
| 3.3.7 | Handle RefundInitiated event | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-337-refundinitiated-handler) | In PaymentEventHandler, when PaymentRefunded received: (1) find active CancelOrderSaga by orderId, (2) send StepCompleted to coordinator, (3) advance to final completion step. Log refund details for audit. |
| 3.3.8 | Update OrderActor cancel method | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-338-orderactor-integration) | Modify CancelOrder command handling: spawn CancelOrderSaga via SagaCoordinator instead of direct status update. Return immediately with confirmation. Previous synchronous approach had partial failure risk. |
| 3.3.9 | Write integration tests | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-339-integration-tests) | Test scenarios: (1) cancel draft order - only status update, (2) cancel created order - stock released then status update, (3) cancel paid order - stock released, payment refunded, then status update, (4) cancel shipping order - rejected with error. |

### 3.4 Stock Reservation (Product Service)

> **Goal:** Implement stock reservation logic in Product Service that responds to order events and publishes stock status events.
> 
> **📖 Code:** [Section 3.4](microservice-transaction-implement-guide.md#34-stock-reservation-product-service)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 3.4.1 | Create `StockReservation` model | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-341-stockreservation-model) | Create case class `StockReservation(id: Long, orderId: String, productId: Long, quantity: Int, status: ReservationStatus, createdAt: Instant, expiresAt: Instant, confirmedAt: Option[Instant], cancelledAt: Option[Instant])`. ReservationStatus: RESERVED, CONFIRMED, CANCELLED, EXPIRED. |
| 3.4.2 | Create `StockReservationRepository` | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-342-stockreservationrepository) | Implement repository with methods: `create(reservation)`, `findByOrderId(orderId)`, `findByProductIdAndStatus(productId, status)`, `updateStatus(id, status)`, `confirmAll(orderId)`, `cancelAll(orderId)`, `findExpired()`. Include Slick table mapping. |
| 3.4.3 | Implement reserve stock on OrderCreated | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-343-reserve-stock) | When OrderCreated event received: (1) for each item, check product exists and has sufficient stock, (2) if all items available, create StockReservation records and decrement available stock, (3) use database transaction for atomicity. If any item fails, rollback all and publish failure. |
| 3.4.4 | Publish StockReserved event | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-344-stockreserved-event) | After successful reservation, insert StockReserved event into outbox. Event contains: orderId, list of reserved items with productId, quantity, and reservationId. OutboxProcessor publishes to oms.products.events topic. Order Service receives and advances saga. |
| 3.4.5 | Publish StockReservationFailed event | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-345-stockreservationfailed-event) | If reservation fails (insufficient stock, product not found, product inactive), insert StockReservationFailed event. Include: orderId, failed productId, reason, availableQuantity, requestedQuantity. Order Service receives and triggers compensation. |
| 3.4.6 | Implement release stock on OrderCancelled | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-346-release-stock) | When OrderCancelled or StockReleaseRequested event received: (1) find StockReservation records by orderId, (2) update status to CANCELLED, (3) increment available stock for each product, (4) use database transaction. Handle case where no reservations exist (idempotency). |
| 3.4.7 | Publish StockReleased event | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-347-stockreleased-event) | After successful release, insert StockReleased event into outbox. Event contains: orderId, list of released items with productId and quantity, release reason. This confirms to Order Service that stock compensation completed. |
| 3.4.8 | Write unit tests | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-348-unit-tests) | Test: (1) sufficient stock creates reservations, (2) insufficient stock for one item fails entire reservation, (3) concurrent reservations for same product are handled correctly, (4) release restores stock quantities, (5) duplicate release request is idempotent. |

---

## Phase 4: TCC for Payments

### 4.1 TCC Framework

> **Goal:** Build a TCC (Try-Confirm-Cancel) framework that provides orchestration-based coordination for short-lived distributed transactions requiring strong consistency.
> 
> **📖 Code:** [Section 4.1](microservice-transaction-implement-guide.md#41-tcc-framework)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 4.1.1 | Create `TCCState` sealed trait | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-411-tccstate) | Define sealed trait with case objects: `Initial` (transaction not started), `Trying` (TRY phase in progress), `TrySucceeded` (all participants tried successfully), `Confirming` (CONFIRM phase in progress), `Confirmed` (transaction completed successfully), `Cancelling` (CANCEL phase in progress), `Cancelled` (transaction rolled back). |
| 4.1.2 | Create `TCCTransaction` model | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-412-tcctransaction) | Create case class `TCCTransaction(id: String, transactionType: String, state: TCCState, participants: List[TCCParticipant], payload: JsValue, createdAt: Instant, expiresAt: Instant, confirmedAt: Option[Instant], cancelledAt: Option[Instant])`. Include database table mapping for persistence. |
| 4.1.3 | Create `TCCParticipant` model | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-413-tccparticipant) | Create case class `TCCParticipant(serviceName: String, resourceId: String, tryEndpoint: String, confirmEndpoint: String, cancelEndpoint: String, status: ParticipantStatus, tryResponse: Option[JsValue], errorMessage: Option[String])`. ParticipantStatus: PENDING, TRY_SUCCESS, TRY_FAILED, CONFIRMED, CANCELLED. |
| 4.1.4 | Create `TCCCoordinator` actor | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-414-tcccoordinator) | Create typed Akka actor that orchestrates TCC lifecycle. Commands: `StartTCC(participants)`, `TryCompleted(participantId, result)`, `ConfirmAll`, `CancelAll`, `Timeout`. Actor calls participant endpoints, tracks responses, decides confirm/cancel based on results. Persists state for recovery. |
| 4.1.5 | Implement Try phase orchestration | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-415-try-phase) | Implement logic that: (1) calls TRY endpoint on all participants in parallel, (2) waits for all responses with timeout, (3) if all succeed transition to TrySucceeded, (4) if any fail or timeout transition to Cancelling. Use Akka HTTP client for REST calls. |
| 4.1.6 | Implement Confirm phase orchestration | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-416-confirm-phase) | Implement logic that: (1) calls CONFIRM endpoint on all participants, (2) retries on transient failures (network errors), (3) confirms are idempotent so safe to retry, (4) all confirms must eventually succeed (critical path). Transition to Confirmed when all done. |
| 4.1.7 | Implement Cancel phase orchestration | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-417-cancel-phase) | Implement logic that: (1) calls CANCEL endpoint on all participants that completed TRY, (2) cancels are idempotent, (3) best effort - log failures but continue, (4) release resources that were reserved. Transition to Cancelled when all attempted. |
| 4.1.8 | Write unit tests for TCCCoordinator | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-418-unit-tests) | Test: (1) all TRY succeed → CONFIRM all, (2) one TRY fails → CANCEL all, (3) timeout during TRY → CANCEL all, (4) CONFIRM retry on failure, (5) CANCEL best effort on failures, (6) state persisted for recovery, (7) recovery resumes from persisted state. |

### 4.2 Payment Service TCC Endpoints

> **Goal:** Implement TCC endpoints in Payment Service to support two-phase payment reservation and capture.
> 
> **📖 Code:** [Section 4.2](microservice-transaction-implement-guide.md#42-payment-service-tcc-endpoints)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 4.2.1 | Create `PaymentReservation` model | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-421---422-paymentreservation) | Create case class `PaymentReservation(id: Long, orderId: String, customerId: Long, amount: BigDecimal, currency: String, status: ReservationStatus, createdAt: Instant, expiresAt: Instant, transactionId: Option[String], confirmedAt: Option[Instant], cancelledAt: Option[Instant])`. Add Slick table mapping. |
| 4.2.2 | Create `PaymentReservationRepository` | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-421---422-paymentreservation) | Implement repository with methods: `create(reservation)`, `findByOrderId(orderId)`, `findById(id)`, `updateStatus(id, status)`, `setTransactionId(id, txnId)`, `findExpired()`, `confirm(id)`, `cancel(id)`. Handle concurrent access with optimistic locking. |
| 4.2.3 | Implement POST /payments/try | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-423---426-tcc-endpoints) | Create endpoint that: (1) validates request (orderId, customerId, amount), (2) checks no existing reservation for orderId, (3) creates PaymentReservation with status=RESERVED and expiresAt=now+15min, (4) does NOT charge customer yet, (5) returns reservationId. Idempotent: return existing reservation if already exists for orderId. |
| 4.2.4 | Implement POST /payments/confirm | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-423---426-tcc-endpoints) | Create endpoint that: (1) finds reservation by reservationId, (2) validates status=RESERVED and not expired, (3) simulates payment processing (80% success rate), (4) on success: create Payment record, update reservation status=CONFIRMED, set transactionId, (5) returns paymentId. This is when customer is charged. |
| 4.2.5 | Implement POST /payments/cancel | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-423---426-tcc-endpoints) | Create endpoint that: (1) finds reservation by reservationId, (2) if status=RESERVED: update to CANCELLED, (3) if already CANCELLED: return success (idempotent), (4) if CONFIRMED: return error (cannot cancel completed payment). No actual refund needed since payment wasn't captured. |
| 4.2.6 | Update PaymentRoutes | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-423---426-tcc-endpoints) | Add new routes to PaymentRoutes: `POST /payments/try` → TryPayment, `POST /payments/confirm/{reservationId}` → ConfirmPayment, `POST /payments/cancel/{reservationId}` → CancelPayment. Document request/response schemas. |
| 4.2.7 | Update PaymentActor | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-427-paymentactor) | Add new commands to PaymentActor: `TryPayment(orderId, customerId, amount)`, `ConfirmPayment(reservationId)`, `CancelPayment(reservationId)`. Implement command handlers using PaymentReservationRepository. Publish events through outbox. |
| 4.2.8 | Write unit tests | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-428-unit-tests) | Test: (1) TRY creates reservation, (2) TRY is idempotent for same orderId, (3) CONFIRM succeeds for valid reservation, (4) CONFIRM fails for expired reservation, (5) CONFIRM fails for cancelled reservation, (6) CANCEL releases reservation, (7) CANCEL is idempotent. |
| 4.2.9 | Write integration tests | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-429-integration-tests) | Test full HTTP flow: (1) POST /payments/try → 201 with reservationId, (2) POST /payments/confirm/{id} → 200 with paymentId, (3) POST /payments/cancel/{id} → 200. Test error scenarios: expired, already cancelled, invalid reservationId. |

### 4.3 Product Service TCC Endpoints (Stock)

> **Goal:** Implement TCC endpoints in Product Service for stock reservation during payment flow.
> 
> **📖 Code:** [Section 4.3](microservice-transaction-implement-guide.md#43-product-service-tcc-endpoints-stock)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 4.3.1 | Implement POST /products/stock/try | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-431-try-stock-endpoint) | Create endpoint that: (1) validates request (orderId, items list), (2) for each item checks stock availability, (3) if all available: create StockReservation records, decrement stock, (4) returns reservationId. Transaction ensures all-or-nothing. Fails if any item has insufficient stock. |
| 4.3.2 | Implement POST /products/stock/confirm | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-432-confirm-stock-endpoint) | Create endpoint that: (1) finds reservations by orderId, (2) validates status=RESERVED, (3) updates all to status=CONFIRMED, (4) stock already deducted during TRY so no additional deduction. Returns success. Idempotent for already-confirmed reservations. |
| 4.3.3 | Implement POST /products/stock/cancel | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-433-cancel-stock-endpoint) | Create endpoint that: (1) finds reservations by orderId, (2) if RESERVED: update to CANCELLED, restore stock quantities, (3) if already CANCELLED: return success (idempotent), (4) if CONFIRMED: cannot cancel, return error. Restore stock atomically in transaction. |
| 4.3.4 | Update ProductRoutes | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-434-productroutes) | Add new routes to ProductRoutes: `POST /products/stock/try` → TryReserveStock, `POST /products/stock/confirm/{orderId}` → ConfirmStock, `POST /products/stock/cancel/{orderId}` → CancelStock. Use orderId as correlation key. |
| 4.3.5 | Update ProductActor | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-435-productactor) | Add new commands to ProductActor: `TryReserveStock(orderId, items)`, `ConfirmStock(orderId)`, `CancelStock(orderId)`. Implement handlers using StockReservationRepository. Ensure thread-safe stock updates with database-level locking. |
| 4.3.6 | Write unit tests | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-436-unit-tests) | Test: (1) TRY reserves stock for all items, (2) TRY fails atomically if any item insufficient, (3) CONFIRM marks all confirmed, (4) CANCEL restores stock, (5) concurrent TRY for same product handles race condition, (6) operations are idempotent. |

### 4.4 Timeout Handling

> **Goal:** Implement timeout handling to automatically cancel TCC transactions that exceed the allowed reservation time.
> 
> **📖 Code:** [Section 4.4](microservice-transaction-implement-guide.md#44-timeout-handling)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 4.4.1 | Configure TCC timeout (15 minutes default) | Common | ⬜ | [📄](microservice-transaction-implement-guide.md#task-441-timeout-configuration) | Add configuration to application.conf: `oms.tcc.reservation-timeout = 15 minutes`, `oms.tcc.confirm-timeout = 30 seconds`, `oms.tcc.cancel-timeout = 30 seconds`. Document that 15 minutes allows user to complete checkout without losing reservation. |
| 4.4.2 | Implement timeout scheduler in TCCCoordinator | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-442-timeout-scheduler) | When TCC transaction starts, schedule timeout message using Akka Scheduler: `context.scheduleOnce(timeout, self, TCCTimeout(transactionId))`. On timeout: if state is Trying or TrySucceeded, trigger cancel phase. Cancel the scheduled timeout on successful confirm. |
| 4.4.3 | Auto-cancel on timeout | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-443-auto-cancel) | In TCCCoordinator, handle TCCTimeout message: (1) check current state, (2) if already Confirmed or Cancelled, ignore, (3) if Trying or TrySucceeded, transition to Cancelling and call cancel on all participants, (4) log timeout event with transaction details for debugging. |
| 4.4.4 | Create reservation expiry job | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-444-payment-expiry-job) | Create scheduled actor that runs every minute: (1) query `findExpired()` for reservations with status=RESERVED and expiresAt < now, (2) update status to EXPIRED, (3) log expired reservations. This is a safety net for orphaned reservations not cancelled by coordinator. |
| 4.4.5 | Create reservation expiry job | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-445-product-expiry-job) | Create scheduled actor similar to Payment Service: (1) find expired stock reservations, (2) update status to EXPIRED, (3) restore stock quantities, (4) log. Prevents stock from being locked indefinitely if coordinator crashes. |
| 4.4.6 | Write tests for timeout scenarios | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-446-timeout-tests) | Test: (1) TCC times out during TRY → all cancelled, (2) TCC times out after TRY success before confirm → all cancelled, (3) expired reservations are cleaned up by background job, (4) already-confirmed transaction ignores timeout. |

### 4.5 Recovery Mechanism

> **Goal:** Implement recovery mechanism to handle TCC coordinator failures and ensure transactions reach terminal state.
> 
> **📖 Code:** [Section 4.5](microservice-transaction-implement-guide.md#45-recovery-mechanism)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 4.5.1 | Persist TCC transaction state | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-451-persist-state) | Create `TCCTransactionRepository` with methods: `create`, `findById`, `updateState`, `findPending`, `findExpired`. Persist transaction state after each phase change. Store participant statuses and any error messages. Use PostgreSQL for durability. |
| 4.5.2 | Implement recovery on startup | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-452-startup-recovery) | On application startup, query `findPending()` for transactions in non-terminal states (Trying, TrySucceeded, Confirming, Cancelling). For each, spawn TCCCoordinator actor to resume handling. Load participant state from database. |
| 4.5.3 | Cancel expired pending transactions | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-453-cancel-expired) | During recovery, check `expiresAt` for each pending transaction. If expired: transition to Cancelling and execute cancel phase on all participants. This handles case where coordinator crashed after TRY but before CONFIRM timeout. |
| 4.5.4 | Verify confirmed transactions | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-454-verify-confirmed) | For transactions in Confirming state, verify each participant is confirmed by calling their status or confirm endpoint (idempotent). This handles case where coordinator crashed during confirm phase. Mark complete when all verified. |
| 4.5.5 | Write recovery tests | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-455-recovery-tests) | Test: (1) crash during TRY → recovery cancels all, (2) crash after TRY success → recovery confirms if not expired, cancels if expired, (3) crash during CONFIRM → recovery completes confirm, (4) crash during CANCEL → recovery completes cancel. |

### 4.6 Integration - Payment Flow

> **Goal:** Integrate TCC pattern into the order payment flow, replacing direct payment calls with coordinated two-phase commit.
> 
> **📖 Code:** [Section 4.6](microservice-transaction-implement-guide.md#46-integration---payment-flow)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 4.6.1 | Update OrderActor pay method | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-461-orderactor-pay) | Modify PayOrder command handling: (1) create TCCTransaction with Payment and Stock participants, (2) spawn TCCCoordinator to manage transaction, (3) return immediately with transactionId, (4) client polls for payment status. Replace old synchronous payment call. |
| 4.6.2 | Integrate TCCCoordinator with OrderActor | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-462-integration) | Define participant configuration for payment flow: Payment Service try/confirm/cancel endpoints, optionally Stock Service if stock needs re-verification. Pass orderId, customerId, amount to coordinator. Handle coordinator completion callback to update order status. |
| 4.6.3 | Update ServiceClient for TCC calls | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-463-serviceclient) | Add methods to ServiceClient: `tryPayment(orderId, customerId, amount)`, `confirmPayment(reservationId)`, `cancelPayment(reservationId)`. Similar methods for stock TCC. Configure HTTP timeouts appropriate for payment processing (30 seconds). |
| 4.6.4 | Write end-to-end payment tests | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-464-e2e-tests) | Test full payment flow: (1) create order, (2) initiate payment → TCC starts, (3) TRY phase reserves payment, (4) CONFIRM captures payment, (5) order status updated to 'paid'. Test failure: TRY fails → order remains 'created'. Test timeout → payment cancelled. |

---

## Phase 5: Testing & Hardening

### 5.1 Integration Testing

> **Goal:** Create comprehensive integration tests that verify end-to-end functionality across multiple services using real infrastructure.
> 
> **📖 Code:** [Section 5.1](microservice-transaction-implement-guide.md#51-integration-testing)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 5.1.1 | Create test docker-compose.yml | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-511-test-docker-compose) | Create `docker-compose.test.yml` that includes all services plus testcontainers for Kafka and PostgreSQL. Configure isolated network. Use separate database names for test isolation. Include health checks for startup ordering. Add script to start test environment. |
| 5.1.2 | Write Order Creation Saga integration test | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-512-saga-integration-test) | Create test class using ScalaTest + Akka TestKit. Test: (1) submit CreateOrder request, (2) verify order created with status='draft', (3) verify OrderCreated event published to Kafka, (4) simulate Product Service sending StockReserved event, (5) verify order status updated to 'created'. Assert all state transitions. |
| 5.1.3 | Write Order Cancellation Saga integration test | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-513-cancellation-test) | Test cancellation flow: (1) create order and advance to 'paid' status, (2) submit cancel request, (3) verify OrderCancellationRequested event published, (4) simulate StockReleased and PaymentRefunded events, (5) verify order status='cancelled'. Test conditional refund logic. |
| 5.1.4 | Write Payment TCC integration test | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-514-tcc-integration-test) | Test TCC payment flow: (1) create TCC transaction with payment participant, (2) call TRY endpoint verify reservation created, (3) call CONFIRM verify payment captured, (4) verify order status='paid'. Test failure: TRY fails → verify CANCEL called, order unchanged. |
| 5.1.5 | Write cross-service event flow test | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-515-cross-service-test) | Deploy all services in test environment. Test: (1) create order via API Gateway, (2) verify events flow through Kafka to Product Service, (3) verify Product Service processes and responds, (4) verify Order Service receives response, (5) verify final state consistent. Use real HTTP calls. |
| 5.1.6 | Write idempotency integration test | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-516-idempotency-test) | Test: (1) submit CreateOrder with idempotencyKey, (2) submit same request again, (3) verify second request returns cached response, (4) verify only one order created in database. Test concurrent submissions with same key. |

### 5.2 Failure Injection Testing

> **Goal:** Test system resilience by injecting failures at various points and verifying proper compensation and recovery.
> 
> **📖 Code:** [Section 5.2](microservice-transaction-implement-guide.md#52-failure-injection-testing)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 5.2.1 | Test: Stock reservation failure | Product Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-521-stock-failure-test) | Simulate insufficient stock scenario: (1) create order with quantity > available stock, (2) verify StockReservationFailed event published with correct reason, (3) verify Order Service receives event and triggers compensation, (4) verify order status='cancelled', (5) verify no stock was deducted. |
| 5.2.2 | Test: Payment failure | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-522-payment-failure-test) | Simulate payment failure (20% failure rate): (1) initiate TCC payment, (2) TRY succeeds, (3) CONFIRM fails (simulated), (4) verify TCC coordinator triggers CANCEL, (5) verify reservation released, (6) verify order remains in 'created' status, (7) verify PaymentFailed event published. |
| 5.2.3 | Test: Kafka unavailable | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-523-kafka-unavailable-test) | Stop Kafka container during operation: (1) create order, (2) stop Kafka before event published, (3) verify event stored in outbox with status='PENDING', (4) restart Kafka, (5) verify OutboxProcessor retries and publishes event, (6) verify saga completes successfully. |
| 5.2.4 | Test: Database unavailable | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-524-database-unavailable-test) | Simulate database failure: (1) start operation, (2) kill database connection mid-transaction, (3) verify operation fails with appropriate error, (4) verify no partial state committed, (5) restore database, (6) verify operation can be retried successfully. |
| 5.2.5 | Test: Service timeout | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-525-service-timeout-test) | Simulate slow service response: (1) add artificial delay to Product Service, (2) initiate order creation, (3) verify TCC timeout triggers after configured duration, (4) verify CANCEL phase executes, (5) verify resources released. Test Saga timeout similarly. |
| 5.2.6 | Test: Partial failure in multi-item order | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-526-partial-failure-test) | Test order with 3 items where middle item fails: (1) create order with items A, B, C, (2) A stock reservation succeeds, (3) B stock reservation fails (insufficient), (4) verify A reservation is rolled back, (5) verify C was never attempted, (6) verify order cancelled with clear error message. |
| 5.2.7 | Test: Network partition | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-527-network-partition-test) | Use Docker network manipulation: (1) start saga, (2) partition network between Order and Product services, (3) verify Order Service retries event publishing, (4) heal partition, (5) verify saga eventually completes. Test with various partition durations. |
| 5.2.8 | Test: Coordinator crash during TCC | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-528-coordinator-crash-test) | Test recovery mechanism: (1) start TCC transaction, (2) complete TRY phase, (3) kill Order Service process, (4) restart Order Service, (5) verify recovery loads pending transaction, (6) verify transaction completes (confirm if not expired, cancel if expired). |

### 5.3 Performance Testing

> **Goal:** Establish performance baselines and identify bottlenecks under load.
> 
> **📖 Code:** [Section 5.3](microservice-transaction-implement-guide.md#53-performance-testing)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 5.3.1 | Set up performance test environment | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-531-perf-environment) | Create dedicated performance environment with: production-like resources (4GB RAM per service), monitoring (Prometheus + Grafana), separate Kafka cluster with 3 partitions per topic, PostgreSQL with connection pooling (20 connections). Document environment specs. |
| 5.3.2 | Create load test scripts (Gatling/JMeter) | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-532-load-test-scripts) | Create Gatling simulation scripts for: (1) Order creation flow (CreateOrder → StockReserved → Confirmed), (2) Payment flow (TCC Try → Confirm), (3) Mixed workload (70% reads, 20% creates, 10% payments). Configure ramp-up, steady state, and cool-down phases. |
| 5.3.3 | Test: 100 concurrent order creations | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-533-concurrent-orders-test) | Execute load test: (1) ramp up to 100 concurrent users over 1 minute, (2) each user creates one order, (3) measure: p50/p95/p99 latency, throughput (orders/sec), error rate. Target: p95 < 2 seconds, error rate < 1%. |
| 5.3.4 | Test: 100 concurrent payments | Payment Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-534-concurrent-payments-test) | Execute payment load test: (1) pre-create 100 orders, (2) initiate 100 concurrent payments, (3) measure TCC phase latencies separately (TRY, CONFIRM), (4) measure end-to-end payment time. Target: TRY p95 < 500ms, CONFIRM p95 < 1s. |
| 5.3.5 | Test: Event publishing throughput | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-535-event-throughput-test) | Measure outbox processing: (1) generate 1000 events rapidly, (2) measure time to publish all to Kafka, (3) calculate events/second throughput, (4) measure Kafka producer latency. Tune batch size and polling interval based on results. |
| 5.3.6 | Test: Kafka consumer lag | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-536-consumer-lag-test) | Under sustained load: (1) publish events at 100/second rate, (2) monitor consumer lag in Kafka UI, (3) verify consumers keep up (lag < 100 messages), (4) identify if any consumer is bottleneck. Tune consumer parallelism if needed. |
| 5.3.7 | Identify and fix bottlenecks | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-537-bottleneck-analysis) | Analyze performance test results: (1) identify slowest operations from traces, (2) check database query plans for slow queries, (3) check connection pool utilization, (4) check Kafka producer/consumer metrics. Create tickets for optimizations. |
| 5.3.8 | Document performance baseline | Documentation | ⬜ | [📄](microservice-transaction-implement-guide.md#task-538-performance-baseline) | Create performance baseline document: (1) test environment specs, (2) test scenarios and parameters, (3) results with graphs, (4) identified bottlenecks and remediations, (5) recommended production sizing. Store results for regression comparison. |

### 5.4 Documentation

> **Goal:** Create comprehensive documentation for developers, operators, and API consumers.
> 
> **📖 Code:** [Section 5.4](microservice-transaction-implement-guide.md#54-documentation)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 5.4.1 | Update README.md with new architecture | Documentation | ⬜ | [📄](microservice-transaction-implement-guide.md#task-541-readme-update) | Update project README to include: (1) architecture diagram showing Kafka, (2) list of patterns implemented (Saga, TCC, Outbox, Idempotency), (3) quick start guide with new infrastructure, (4) link to detailed documentation. Keep concise, link to detailed docs. |
| 5.4.2 | Document Saga flows with diagrams | Documentation | ⬜ | [📄](microservice-transaction-implement-guide.md#task-542-saga-documentation) | Create `docs/saga-patterns.md` with: (1) Create Order Saga sequence diagram, (2) Cancel Order Saga sequence diagram, (3) state machine diagrams, (4) event schemas, (5) compensation logic explanation, (6) troubleshooting guide for stuck sagas. |
| 5.4.3 | Document TCC flows with diagrams | Documentation | ⬜ | [📄](microservice-transaction-implement-guide.md#task-543-tcc-documentation) | Create `docs/tcc-patterns.md` with: (1) Payment TCC sequence diagram showing all three phases, (2) timeout handling flow, (3) recovery scenarios, (4) participant endpoint contracts, (5) configuration options, (6) monitoring recommendations. |
| 5.4.4 | Create runbook for operations | Documentation | ⬜ | [📄](microservice-transaction-implement-guide.md#task-544-runbook) | Create `docs/runbook.md` for operations team: (1) how to monitor saga/TCC health, (2) how to identify stuck transactions, (3) manual intervention procedures, (4) how to replay failed events, (5) how to force-cancel stuck TCC, (6) alerting thresholds, (7) scaling guidelines. |
| 5.4.5 | Document Kafka topics and events | Documentation | ⬜ | [📄](microservice-transaction-implement-guide.md#task-545-event-catalog) | Create `docs/event-catalog.md` with: (1) list of all topics and their purpose, (2) event schemas for each event type (JSON schema), (3) producer/consumer mapping, (4) retention policies, (5) partitioning strategy, (6) sample events for testing. |
| 5.4.6 | Create API documentation for TCC endpoints | Documentation | ⬜ | [📄](microservice-transaction-implement-guide.md#task-546-api-documentation) | Update OpenAPI/Swagger spec with new TCC endpoints: (1) POST /payments/try with request/response schema, (2) POST /payments/confirm, (3) POST /payments/cancel, (4) error response schemas, (5) example requests. Generate and host Swagger UI. |
| 5.4.7 | Update postman collection | Documentation | ⬜ | [📄](microservice-transaction-implement-guide.md#task-547-postman-collection) | Update Postman collection with: (1) TCC payment flow folder with chained requests, (2) Saga test scenarios, (3) environment variables for different environments, (4) pre-request scripts for authentication, (5) test scripts to validate responses. Export and commit to repo. |

### 5.5 Monitoring & Alerting

> **Goal:** Implement observability to track system health, detect issues early, and enable debugging.
> 
> **📖 Code:** [Section 5.5](microservice-transaction-implement-guide.md#55-monitoring--alerting)

| ID | Task | Service | Status | Code | Description |
|----|------|---------|--------|------|-------------|
| 5.5.1 | Add metrics for Saga execution | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-551-saga-metrics) | Add Prometheus metrics: `oms_saga_started_total{type}` counter, `oms_saga_completed_total{type,result}` counter (result=success/failed/compensated), `oms_saga_duration_seconds{type}` histogram, `oms_saga_active` gauge. Instrument SagaCoordinator actor. |
| 5.5.2 | Add metrics for TCC transactions | Order Service | ⬜ | [📄](microservice-transaction-implement-guide.md#task-552-tcc-metrics) | Add Prometheus metrics: `oms_tcc_started_total` counter, `oms_tcc_phase_duration_seconds{phase}` histogram (phase=try/confirm/cancel), `oms_tcc_completed_total{result}` counter, `oms_tcc_timeout_total` counter. Track per-participant metrics. |
| 5.5.3 | Add metrics for Outbox processing | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-553-outbox-metrics) | Add metrics: `oms_outbox_pending_count` gauge (current pending events), `oms_outbox_published_total` counter, `oms_outbox_failed_total` counter, `oms_outbox_publish_duration_seconds` histogram. Monitor outbox health across services. |
| 5.5.4 | Add metrics for event consumption | All Services | ⬜ | [📄](microservice-transaction-implement-guide.md#task-554-event-metrics) | Add metrics: `oms_events_received_total{topic,type}` counter, `oms_events_processed_total{topic,type,result}` counter, `oms_event_processing_duration_seconds{type}` histogram, `oms_consumer_lag` gauge. Track processing success rate. |
| 5.5.5 | Set up Prometheus scraping | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-555-prometheus-setup) | Add Prometheus to docker-compose: (1) configure scrape targets for all services on /metrics endpoint, (2) set appropriate scrape interval (15s), (3) configure retention (15 days), (4) set up service discovery for dynamic targets. Verify metrics are being collected. |
| 5.5.6 | Create Grafana dashboards | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-556-grafana-dashboards) | Create Grafana dashboards: (1) Saga Overview - active sagas, completion rate, duration percentiles, (2) TCC Overview - transaction rate, phase durations, timeout rate, (3) Event Flow - publish rate, consumer lag, processing errors, (4) Service Health - request rate, error rate, latency. |
| 5.5.7 | Configure alerts for failures | Infrastructure | ⬜ | [📄](microservice-transaction-implement-guide.md#task-557-alerting) | Set up Alertmanager rules: (1) SagaHighFailureRate - >5% saga failures in 5 minutes, (2) TCCTimeoutSpike - >10 timeouts in 5 minutes, (3) OutboxBacklog - >100 pending events for >5 minutes, (4) ConsumerLagHigh - lag >1000 for >5 minutes, (5) ServiceDown - service unavailable >1 minute. Configure notification channels. |

---

## Task Summary

### By Phase

| Phase | Total Tasks | Not Started | In Progress | Completed |
|-------|-------------|-------------|-------------|-----------|
| Phase 1: Foundation | 41 | 41 | 0 | 0 |
| Phase 2: Event-Driven Communication | 27 | 27 | 0 | 0 |
| Phase 3: Saga Implementation | 30 | 30 | 0 | 0 |
| Phase 4: TCC for Payments | 31 | 31 | 0 | 0 |
| Phase 5: Testing & Hardening | 31 | 31 | 0 | 0 |
| **Total** | **160** | **160** | **0** | **0** |

### By Service

| Service | Tasks |
|---------|-------|
| Common Module | ~35 |
| Order Service | ~50 |
| Product Service | ~25 |
| Payment Service | ~25 |
| Infrastructure | ~15 |
| Documentation | ~10 |

---

## Dependencies

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           TASK DEPENDENCIES                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1.1 Infrastructure ─┬─► 1.2 Outbox Schema ─┬─► 1.7 Outbox Implementation   │
│                      │                      │                                │
│                      ├─► 1.3 Idempotency ───┴─► 1.8 Idempotency Impl        │
│                      │                                                       │
│                      └─► 1.4 TCC Schema ────────► 4.2-4.3 TCC Endpoints     │
│                                                                              │
│  1.6 Dependencies ───► 1.7 Outbox ──► 2.2 Event Publishers                  │
│                                                                              │
│  2.1 Domain Events ──► 2.2 Publishers ──► 2.3-2.5 Subscribers               │
│                                                                              │
│  2.3-2.5 Subscribers ──► 3.2 Order Creation Saga                            │
│                      ──► 3.3 Order Cancellation Saga                        │
│                                                                              │
│  3.1 Saga Framework ──► 3.2 Order Creation Saga                             │
│                     ──► 3.3 Order Cancellation Saga                         │
│                                                                              │
│  4.1 TCC Framework ───► 4.2 Payment TCC ───► 4.6 Integration                │
│                    ───► 4.3 Product TCC                                      │
│                                                                              │
│  All Phases 1-4 ─────► Phase 5 Testing                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Notes

### Critical Path
1. Infrastructure Setup (1.1) → SBT Dependencies (1.6) → Outbox Implementation (1.7)
2. Domain Events (2.1) → Event Publishers (2.2) → Event Subscribers (2.3-2.5)
3. Saga Framework (3.1) → Order Creation Saga (3.2)
4. TCC Framework (4.1) → Payment TCC (4.2) → Integration (4.6)

### Risk Items
- Kafka configuration complexity
- TCC timeout tuning
- Event serialization compatibility
- Performance under load

### Definition of Done
- [ ] Code implemented and compiles
- [ ] Unit tests written and passing
- [ ] Integration tests written and passing
- [ ] Code reviewed by at least one team member
- [ ] Documentation updated
- [ ] No critical or high-severity bugs

---

*Document maintained by: Architecture Team*  
*Last updated: January 13, 2026*
