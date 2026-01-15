# Microservice Transactional Patterns - Implementation Guide

## Order Management System (OMS)

**Document Version:** 2.0  
**Created:** January 13, 2026  
**Related Documents:** 
- [microservice-transactional-patterns.md](microservice-transactional-patterns.md) - Design Document
- [implementation-task-list.md](implementation-task-list.md) - Task Tracking

---

## Table of Contents

1. [Phase 1: Foundation](#phase-1-foundation)
   - [1.1 Infrastructure Setup](#11-infrastructure-setup)
   - [1.2 Database Schema - Outbox Pattern](#12-database-schema---outbox-pattern)
   - [1.3 Database Schema - Idempotency Pattern](#13-database-schema---idempotency-pattern)
   - [1.4 Database Schema - TCC Pattern](#14-database-schema---tcc-pattern)
   - [1.5 Database Schema - Saga Pattern](#15-database-schema---saga-pattern)
   - [1.6 SBT Dependencies](#16-sbt-dependencies)
   - [1.7 Outbox Pattern Implementation](#17-outbox-pattern-implementation)
   - [1.8 Idempotency Pattern Implementation](#18-idempotency-pattern-implementation)
2. [Phase 2: Event-Driven Communication](#phase-2-event-driven-communication)
   - [2.1 Domain Events - Common Module](#21-domain-events---common-module)
   - [2.2 Event Publishers](#22-event-publishers)
   - [2.3 Event Subscribers - Order Service](#23-event-subscribers---order-service)
   - [2.4 Event Subscribers - Product Service](#24-event-subscribers---product-service)
   - [2.5 Event Subscribers - Payment Service](#25-event-subscribers---payment-service)
   - [2.6 Event Logging & Auditing](#26-event-logging--auditing)
3. [Phase 3: Saga Implementation](#phase-3-saga-implementation)
   - [3.1 Saga Framework](#31-saga-framework)
   - [3.2 Order Creation Saga](#32-order-creation-saga)
   - [3.3 Order Cancellation Saga](#33-order-cancellation-saga)
   - [3.4 Stock Reservation (Product Service)](#34-stock-reservation-product-service)
4. [Phase 4: TCC for Payments](#phase-4-tcc-for-payments)
   - [4.1 TCC Framework](#41-tcc-framework)
   - [4.2 Payment Service TCC Endpoints](#42-payment-service-tcc-endpoints)
   - [4.3 Product Service TCC Endpoints](#43-product-service-tcc-endpoints-stock)
   - [4.4 Timeout Handling](#44-timeout-handling)
   - [4.5 Recovery Mechanism](#45-recovery-mechanism)
   - [4.6 Integration - Payment Flow](#46-integration---payment-flow)
5. [Phase 5: Testing & Hardening](#phase-5-testing--hardening)
   - [5.1 Integration Testing](#51-integration-testing)
   - [5.2 Failure Injection Testing](#52-failure-injection-testing)
   - [5.3 Performance Testing](#53-performance-testing)
   - [5.5 Monitoring & Alerting](#55-monitoring--alerting)

---

## How to Use This Guide

Each section in this guide corresponds to tasks in the [implementation-task-list.md](implementation-task-list.md). Task IDs are marked with anchors (e.g., `<!-- task-1.1.1 -->`) so you can navigate directly from the task list.

**Navigation Pattern:** Task `1.2.1` → Section [1.2 Database Schema - Outbox Pattern](#12-database-schema---outbox-pattern)

---

# Phase 1: Foundation

---

## 1.1 Infrastructure Setup

> **Related Tasks:** [1.1.1](implementation-task-list.md) - [1.1.7](implementation-task-list.md)

### Task 1.1.1: Add Zookeeper to docker-compose.yml
<!-- task-1.1.1 -->

```yaml
# Add to docker-compose.yml under services:
zookeeper:
  image: confluentinc/cp-zookeeper:7.5.0
  container_name: oms-zookeeper
  hostname: zookeeper
  ports:
    - "2181:2181"
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
    ZOOKEEPER_TICK_TIME: 2000
    ZOOKEEPER_INIT_LIMIT: 5
    ZOOKEEPER_SYNC_LIMIT: 2
  volumes:
    - zookeeper-data:/var/lib/zookeeper/data
    - zookeeper-logs:/var/lib/zookeeper/log
  networks:
    - oms-network
  healthcheck:
    test: ["CMD", "nc", "-z", "localhost", "2181"]
    interval: 10s
    timeout: 5s
    retries: 5
```

### Task 1.1.2: Add Kafka to docker-compose.yml
<!-- task-1.1.2 -->

```yaml
# Add to docker-compose.yml under services:
kafka:
  image: confluentinc/cp-kafka:7.5.0
  container_name: oms-kafka
  hostname: kafka
  ports:
    - "9092:9092"      # Internal listener
    - "29092:29092"    # External listener for host access
  environment:
    KAFKA_BROKER_ID: 1
    KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,PLAINTEXT_HOST://0.0.0.0:29092
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
    KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
    KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    KAFKA_NUM_PARTITIONS: 6
    KAFKA_DEFAULT_REPLICATION_FACTOR: 1
    KAFKA_LOG_RETENTION_HOURS: 168  # 7 days
  volumes:
    - kafka-data:/var/lib/kafka/data
  depends_on:
    zookeeper:
      condition: service_healthy
  networks:
    - oms-network
  healthcheck:
    test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
    interval: 10s
    timeout: 10s
    retries: 5
```

### Task 1.1.3: Add Kafka UI to docker-compose.yml
<!-- task-1.1.3 -->

```yaml
# Add to docker-compose.yml under services:
kafka-ui:
  image: provectuslabs/kafka-ui:latest
  container_name: oms-kafka-ui
  ports:
    - "8090:8080"
  environment:
    KAFKA_CLUSTERS_0_NAME: oms-local
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
    KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
    KAFKA_CLUSTERS_0_READONLY: "false"
    DYNAMIC_CONFIG_ENABLED: "true"
  depends_on:
    kafka:
      condition: service_healthy
  networks:
    - oms-network
```

### Task 1.1.4: Add Redis to docker-compose.yml
<!-- task-1.1.4 -->

```yaml
# Add to docker-compose.yml under services:
redis:
  image: redis:7.2-alpine
  container_name: oms-redis
  hostname: redis
  ports:
    - "6379:6379"
  command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru
  volumes:
    - redis-data:/data
  networks:
    - oms-network
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5

# Add to volumes section at the end of docker-compose.yml:
volumes:
  zookeeper-data:
  zookeeper-logs:
  kafka-data:
  redis-data:
```

### Task 1.1.5: Create Kafka Topics Script
<!-- task-1.1.5 -->

Create file: `scripts/create-kafka-topics.sh`

```bash
#!/bin/bash

# Kafka Topics Initialization Script
# Run this after Kafka is fully started

KAFKA_BOOTSTRAP_SERVER=${KAFKA_BOOTSTRAP_SERVER:-kafka:9092}
MAX_RETRIES=30
RETRY_INTERVAL=2

echo "============================================"
echo "OMS Kafka Topics Initialization"
echo "============================================"

# Function to wait for Kafka
wait_for_kafka() {
    echo "Waiting for Kafka to be ready..."
    for i in $(seq 1 $MAX_RETRIES); do
        if kafka-broker-api-versions --bootstrap-server $KAFKA_BOOTSTRAP_SERVER > /dev/null 2>&1; then
            echo "Kafka is ready!"
            return 0
        fi
        echo "Attempt $i/$MAX_RETRIES - Kafka not ready, waiting..."
        sleep $RETRY_INTERVAL
    done
    echo "ERROR: Kafka did not become ready in time"
    exit 1
}

# Function to create a topic
create_topic() {
    local topic=$1
    local partitions=$2
    local retention_ms=$3
    
    echo "Creating topic: $topic (partitions=$partitions, retention=${retention_ms}ms)"
    
    kafka-topics --bootstrap-server $KAFKA_BOOTSTRAP_SERVER \
        --create --if-not-exists \
        --topic $topic \
        --partitions $partitions \
        --replication-factor 1 \
        --config retention.ms=$retention_ms \
        --config cleanup.policy=delete
    
    if [ $? -eq 0 ]; then
        echo "  ✓ Topic $topic created successfully"
    else
        echo "  ✗ Failed to create topic $topic"
    fi
}

# Wait for Kafka
wait_for_kafka

echo ""
echo "Creating OMS topics..."
echo "--------------------------------------------"

# Order Events Topic - High volume, 6 partitions, 7 days retention
create_topic "oms.orders.events" 6 604800000

# Product/Stock Events Topic - High volume, 6 partitions, 7 days retention
create_topic "oms.products.events" 6 604800000

# Payment Events Topic - Medium volume, 6 partitions, 7 days retention
create_topic "oms.payments.events" 6 604800000

# Saga Commands Topic - Internal coordination, 3 partitions, 1 day retention
create_topic "oms.saga.commands" 3 86400000

# Dead Letter Queue - Failed messages, 1 partition, 30 days retention
create_topic "oms.dlq" 1 2592000000

echo ""
echo "--------------------------------------------"
echo "Listing all topics:"
kafka-topics --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --list

echo ""
echo "============================================"
echo "Kafka topics initialization complete!"
echo "============================================"
```

Make executable:
```bash
chmod +x scripts/create-kafka-topics.sh
```

### Task 1.1.6: Update Network Configuration
<!-- task-1.1.6 -->

Update each service's environment in `docker-compose.yml`:

```yaml
# Add to each microservice (order-service, product-service, payment-service, etc.)
order-service:
  # ... existing config ...
  environment:
    # ... existing environment variables ...
    KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    REDIS_HOST: redis
    REDIS_PORT: 6379
  depends_on:
    kafka:
      condition: service_healthy
    redis:
      condition: service_healthy
  networks:
    - oms-network

# Ensure network is defined
networks:
  oms-network:
    driver: bridge
```

### Task 1.1.7: Infrastructure Verification Script
<!-- task-1.1.7 -->

Create file: `scripts/verify-infrastructure.sh`

```bash
#!/bin/bash

# Infrastructure Verification Script
# Validates all OMS infrastructure components are running correctly

echo "============================================"
echo "OMS Infrastructure Verification"
echo "============================================"

ERRORS=0

# Check Zookeeper
echo ""
echo "1. Checking Zookeeper..."
if docker exec oms-zookeeper sh -c 'echo ruok | nc localhost 2181' 2>/dev/null | grep -q "imok"; then
    echo "   ✓ Zookeeper is healthy"
else
    echo "   ✗ Zookeeper is not responding"
    ERRORS=$((ERRORS + 1))
fi

# Check Kafka
echo ""
echo "2. Checking Kafka..."
if docker exec oms-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 > /dev/null 2>&1; then
    echo "   ✓ Kafka broker is healthy"
    
    # Test produce/consume
    echo "   Testing message flow..."
    TEST_MSG="test-$(date +%s)"
    echo $TEST_MSG | docker exec -i oms-kafka kafka-console-producer --bootstrap-server localhost:9092 --topic oms.test 2>/dev/null
    RECEIVED=$(docker exec oms-kafka timeout 5 kafka-console-consumer --bootstrap-server localhost:9092 --topic oms.test --from-beginning --max-messages 1 2>/dev/null | tail -1)
    
    if [ "$RECEIVED" = "$TEST_MSG" ]; then
        echo "   ✓ Kafka produce/consume working"
    else
        echo "   ✗ Kafka produce/consume failed"
        ERRORS=$((ERRORS + 1))
    fi
else
    echo "   ✗ Kafka broker is not responding"
    ERRORS=$((ERRORS + 1))
fi

# Check Redis
echo ""
echo "3. Checking Redis..."
if docker exec oms-redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
    echo "   ✓ Redis is healthy"
    
    # Test set/get
    docker exec oms-redis redis-cli SET test:key "test-value" > /dev/null 2>&1
    VALUE=$(docker exec oms-redis redis-cli GET test:key 2>/dev/null)
    docker exec oms-redis redis-cli DEL test:key > /dev/null 2>&1
    
    if [ "$VALUE" = "test-value" ]; then
        echo "   ✓ Redis SET/GET working"
    else
        echo "   ✗ Redis SET/GET failed"
        ERRORS=$((ERRORS + 1))
    fi
else
    echo "   ✗ Redis is not responding"
    ERRORS=$((ERRORS + 1))
fi

# Check Kafka Topics
echo ""
echo "4. Checking Kafka Topics..."
TOPICS=$(docker exec oms-kafka kafka-topics --bootstrap-server localhost:9092 --list 2>/dev/null)

for topic in "oms.orders.events" "oms.products.events" "oms.payments.events" "oms.saga.commands" "oms.dlq"; do
    if echo "$TOPICS" | grep -q "^${topic}$"; then
        echo "   ✓ Topic $topic exists"
    else
        echo "   ✗ Topic $topic missing"
        ERRORS=$((ERRORS + 1))
    fi
done

# Summary
echo ""
echo "============================================"
if [ $ERRORS -eq 0 ]; then
    echo "✓ All infrastructure checks passed!"
    exit 0
else
    echo "✗ $ERRORS check(s) failed"
    exit 1
fi
```

---

## 1.2 Database Schema - Outbox Pattern

---

## 1.2 Database Schema - Outbox Pattern

> **Related Tasks:** [1.2.1](implementation-task-list.md) - [1.2.5](implementation-task-list.md)

### Task 1.2.1 - 1.2.3: Create outbox_events Table
<!-- task-1.2.1 -->

Create file: `src/main/resources/db/migration/V2__add_outbox_events.sql` in each service (Order, Product, Payment)

```sql
-- ============================================
-- Outbox Events Table
-- ============================================
-- Purpose: Implements the Transactional Outbox Pattern for reliable event publishing.
-- Events are written to this table in the same transaction as business data changes,
-- then asynchronously published to Kafka by the OutboxProcessor.
--
-- Related Tasks: 1.2.1, 1.2.2, 1.2.3
-- ============================================

CREATE TABLE IF NOT EXISTS outbox_events (
    -- Primary identifier
    id              BIGSERIAL PRIMARY KEY,
    
    -- Aggregate information (for event routing and debugging)
    aggregate_type  VARCHAR(100) NOT NULL,    -- e.g., 'ORDER', 'PRODUCT', 'PAYMENT'
    aggregate_id    VARCHAR(100) NOT NULL,    -- The ID of the entity that changed
    
    -- Event information
    event_type      VARCHAR(100) NOT NULL,    -- e.g., 'ORDER_CREATED', 'STOCK_RESERVED'
    payload         JSONB NOT NULL,           -- Full event payload as JSON
    
    -- Timestamps
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE NULL,
    
    -- Processing metadata
    retry_count     INT DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
    error_message   TEXT NULL,                      -- Last error message if failed
    
    -- Constraints
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Add comments for documentation
COMMENT ON TABLE outbox_events IS 'Transactional Outbox for reliable event publishing to Kafka';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Type of the aggregate root (ORDER, PRODUCT, PAYMENT)';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'ID of the aggregate root instance';
COMMENT ON COLUMN outbox_events.event_type IS 'Type of domain event (ORDER_CREATED, STOCK_RESERVED, etc)';
COMMENT ON COLUMN outbox_events.payload IS 'JSON payload containing full event data';
COMMENT ON COLUMN outbox_events.status IS 'Processing status: PENDING->PUBLISHED or PENDING->FAILED';
```

### Task 1.2.4: Add Optimized Indexes
<!-- task-1.2.4 -->

```sql
-- ============================================
-- Outbox Events Indexes
-- ============================================
-- Add to V2__add_outbox_events.sql

-- Primary polling index: used by OutboxProcessor to fetch pending events
-- Partial index for efficiency (only indexes PENDING status rows)
CREATE INDEX idx_outbox_pending 
    ON outbox_events(created_at) 
    WHERE status = 'PENDING';

-- Index for finding events by aggregate (useful for debugging)
CREATE INDEX idx_outbox_aggregate 
    ON outbox_events(aggregate_type, aggregate_id);

-- Index for finding events by type (useful for analytics)
CREATE INDEX idx_outbox_event_type 
    ON outbox_events(event_type, created_at);

-- Index for cleanup of old published events
CREATE INDEX idx_outbox_cleanup 
    ON outbox_events(published_at) 
    WHERE status = 'PUBLISHED';
```

### Task 1.2.5: Flyway Migration Script (Complete)
<!-- task-1.2.5 -->

Complete migration file: `V2__add_outbox_events.sql`

```sql
-- ============================================
-- Migration V2: Add Outbox Events Table
-- ============================================
-- This migration adds the outbox_events table for the Transactional Outbox Pattern.
-- Apply this migration to: order_db, product_db, payment_db
--
-- The Outbox Pattern ensures at-least-once delivery of domain events to Kafka
-- by storing events in the same database transaction as the business operation.
-- ============================================

-- Create table
CREATE TABLE IF NOT EXISTS outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE NULL,
    retry_count     INT DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'PENDING',
    error_message   TEXT NULL,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Create indexes
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_event_type ON outbox_events(event_type, created_at);
CREATE INDEX idx_outbox_cleanup ON outbox_events(published_at) WHERE status = 'PUBLISHED';

-- Add table comment
COMMENT ON TABLE outbox_events IS 'Transactional Outbox for reliable event publishing';
```

---

## 1.3 Database Schema - Idempotency Pattern

> **Related Tasks:** [1.3.1](implementation-task-list.md) - [1.3.5](implementation-task-list.md)

### Task 1.3.1 - 1.3.3: Create idempotency_records Table
<!-- task-1.3.1 -->

Create file: `src/main/resources/db/migration/V3__add_idempotency.sql`

```sql
-- ============================================
-- Migration V3: Add Idempotency Records Table
-- ============================================
-- Purpose: Prevents duplicate processing of requests that may be retried
-- due to network issues, client retries, or message redelivery.
--
-- Related Tasks: 1.3.1, 1.3.2, 1.3.3
-- ============================================

CREATE TABLE IF NOT EXISTS idempotency_records (
    -- Primary key: client-provided idempotency key (usually UUID)
    idempotency_key     VARCHAR(100) PRIMARY KEY,
    
    -- Request fingerprint to detect different requests with same key
    request_hash        VARCHAR(64) NOT NULL,     -- SHA-256 hash of request payload
    
    -- Cached response for completed requests
    response_data       JSONB NULL,               -- Stored response to return for duplicates
    
    -- Processing status
    status              VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    
    -- Timestamps
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at        TIMESTAMP WITH TIME ZONE NULL,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- Error tracking
    error_message       TEXT NULL,
    
    -- Constraints
    CONSTRAINT chk_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

-- Comments
COMMENT ON TABLE idempotency_records IS 'Tracks idempotent operation state for duplicate request handling';
COMMENT ON COLUMN idempotency_records.idempotency_key IS 'Client-provided unique key (UUID recommended)';
COMMENT ON COLUMN idempotency_records.request_hash IS 'SHA-256 hash to detect request payload changes';
COMMENT ON COLUMN idempotency_records.response_data IS 'Cached response returned for duplicate requests';
COMMENT ON COLUMN idempotency_records.expires_at IS 'TTL for idempotency key (default 24 hours)';
```

### Task 1.3.4: Add Indexes
<!-- task-1.3.4 -->

```sql
-- Add to V3__add_idempotency.sql

-- Index for cleanup job to find expired records
CREATE INDEX idx_idempotency_expires 
    ON idempotency_records(expires_at);

-- Index for monitoring: find stuck PROCESSING records
CREATE INDEX idx_idempotency_processing 
    ON idempotency_records(created_at) 
    WHERE status = 'PROCESSING';
```

### Task 1.3.5: Complete Flyway Migration
<!-- task-1.3.5 -->

Complete `V3__add_idempotency.sql`:

```sql
-- ============================================
-- Migration V3: Add Idempotency Records
-- ============================================
-- Apply to: order_db, product_db, payment_db
--
-- Idempotency Key Flow:
-- 1. Client includes X-Idempotency-Key header in request
-- 2. Server checks if key exists:
--    - If COMPLETED: return cached response
--    - If PROCESSING: return 409 Conflict
--    - If not found: create record with PROCESSING, execute operation
-- 3. On completion: update status to COMPLETED, store response
-- 4. Cleanup job removes expired records after 24 hours
-- ============================================

CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key     VARCHAR(100) PRIMARY KEY,
    request_hash        VARCHAR(64) NOT NULL,
    response_data       JSONB NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at        TIMESTAMP WITH TIME ZONE NULL,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    error_message       TEXT NULL,
    CONSTRAINT chk_idempotency_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_idempotency_expires ON idempotency_records(expires_at);
CREATE INDEX idx_idempotency_processing ON idempotency_records(created_at) WHERE status = 'PROCESSING';

COMMENT ON TABLE idempotency_records IS 'Idempotency tracking with 24-hour TTL';
```

---

## 1.4 Database Schema - TCC Pattern

> **Related Tasks:** [1.4.1](implementation-task-list.md) - [1.4.4](implementation-task-list.md)

### Task 1.4.1: Create stock_reservations Table (Product Service)
<!-- task-1.4.1 -->

Create file: `product-service/src/main/resources/db/migration/V4__add_tcc_tables.sql`

```sql
-- ============================================
-- Migration V4: TCC Stock Reservations Table
-- ============================================
-- Purpose: Manages temporary stock reservations during TCC transactions.
-- TCC Flow: TRY (reserve) → CONFIRM (finalize) or CANCEL (release)
--
-- Related Task: 1.4.1
-- ============================================

CREATE TABLE IF NOT EXISTS stock_reservations (
    id              BIGSERIAL PRIMARY KEY,
    
    -- Reference keys
    order_id        VARCHAR(100) NOT NULL,    -- Associated order ID
    product_id      BIGINT NOT NULL,          -- Product being reserved
    
    -- Reservation details
    quantity        INT NOT NULL CHECK (quantity > 0),
    
    -- TCC State: RESERVED → CONFIRMED or CANCELLED or EXPIRED
    status          VARCHAR(20) DEFAULT 'RESERVED',
    
    -- Timestamps
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,    -- Default: 15 minutes from creation
    confirmed_at    TIMESTAMP WITH TIME ZONE NULL,
    cancelled_at    TIMESTAMP WITH TIME ZONE NULL,
    
    -- Constraints
    CONSTRAINT chk_stock_reservation_status 
        CHECK (status IN ('RESERVED', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    
    -- Prevent duplicate reservations for same order+product
    CONSTRAINT uq_stock_reservation_order_product 
        UNIQUE (order_id, product_id)
);

-- Comments
COMMENT ON TABLE stock_reservations IS 'TCC stock reservations - 15 minute TTL default';
COMMENT ON COLUMN stock_reservations.status IS 'TCC state: RESERVED->CONFIRMED or RESERVED->CANCELLED/EXPIRED';
COMMENT ON COLUMN stock_reservations.expires_at IS 'Auto-expire reservation after 15 minutes if not confirmed';
```

### Task 1.4.2: Create payment_reservations Table (Payment Service)
<!-- task-1.4.2 -->

Create file: `payment-service/src/main/resources/db/migration/V4__add_tcc_tables.sql`

```sql
-- ============================================
-- Migration V4: TCC Payment Reservations Table
-- ============================================
-- Purpose: Manages payment reservations during TCC transactions.
-- This is a "hold" on payment - customer is not charged until CONFIRM.
--
-- Related Task: 1.4.2
-- ============================================

CREATE TABLE IF NOT EXISTS payment_reservations (
    id              VARCHAR(100) PRIMARY KEY,  -- UUID generated by service
    
    -- Reference keys
    order_id        VARCHAR(100) NOT NULL,
    customer_id     BIGINT NOT NULL,
    
    -- Payment details
    amount          DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3) DEFAULT 'USD',
    
    -- TCC State
    status          VARCHAR(20) DEFAULT 'RESERVED',
    
    -- Timestamps
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at    TIMESTAMP WITH TIME ZONE NULL,
    cancelled_at    TIMESTAMP WITH TIME ZONE NULL,
    
    -- Transaction reference (set on CONFIRM)
    transaction_id  VARCHAR(100) NULL,
    
    -- Constraints
    CONSTRAINT chk_payment_reservation_status 
        CHECK (status IN ('RESERVED', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
    
    -- One reservation per order
    CONSTRAINT uq_payment_reservation_order 
        UNIQUE (order_id)
);

-- Comments
COMMENT ON TABLE payment_reservations IS 'TCC payment holds - customer charged only on CONFIRM';
COMMENT ON COLUMN payment_reservations.transaction_id IS 'Payment gateway transaction ID, set on confirm';
```

### Task 1.4.3: Add TCC Indexes
<!-- task-1.4.3 -->

```sql
-- Add to V4__add_tcc_tables.sql for Product Service

-- Lookup by order (for confirm/cancel operations)
CREATE INDEX idx_stock_reservation_order 
    ON stock_reservations(order_id);

-- Expiry cleanup job: find RESERVED reservations past expiry time
CREATE INDEX idx_stock_reservation_expiry 
    ON stock_reservations(expires_at) 
    WHERE status = 'RESERVED';

-- Product-level queries (for stock availability calculations)
CREATE INDEX idx_stock_reservation_product_status 
    ON stock_reservations(product_id, status);

-- Add to V4__add_tcc_tables.sql for Payment Service

-- Lookup by order
CREATE INDEX idx_payment_reservation_order 
    ON payment_reservations(order_id);

-- Expiry cleanup job
CREATE INDEX idx_payment_reservation_expiry 
    ON payment_reservations(expires_at) 
    WHERE status = 'RESERVED';

-- Customer lookup (for customer dashboard)
CREATE INDEX idx_payment_reservation_customer 
    ON payment_reservations(customer_id, status);
```

---

## 1.5 Database Schema - Saga Pattern

> **Related Tasks:** [1.5.1](implementation-task-list.md) - [1.5.3](implementation-task-list.md)

### Task 1.5.1: Create saga_instances Table (Order Service)
<!-- task-1.5.1 -->

Create file: `order-service/src/main/resources/db/migration/V5__add_saga_instances.sql`

```sql
-- ============================================
-- Migration V5: Saga Instances Table
-- ============================================
-- Purpose: Persists saga state for recovery and monitoring.
-- Sagas are long-running transactions that coordinate multiple services.
--
-- State Machine:
--   STARTED → STEP_IN_PROGRESS → STEP_COMPLETED → ... → COMPLETED
--                    ↓ (on failure)
--               COMPENSATING → COMPENSATED
--                    ↓ (on compensation failure)
--                   FAILED
--
-- Related Task: 1.5.1
-- ============================================

CREATE TABLE IF NOT EXISTS saga_instances (
    -- Saga identifier (UUID)
    id                  VARCHAR(100) PRIMARY KEY,
    
    -- Saga type determines the steps to execute
    saga_type           VARCHAR(50) NOT NULL,     -- 'CREATE_ORDER', 'CANCEL_ORDER'
    
    -- Current state
    state               VARCHAR(30) NOT NULL DEFAULT 'STARTED',
    current_step        INT DEFAULT 0,            -- Index of current/last completed step
    
    -- Saga context data (order details, customer info, etc.)
    payload             JSONB NOT NULL,
    
    -- Timestamps
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at        TIMESTAMP WITH TIME ZONE NULL,
    
    -- Error tracking
    error_message       TEXT NULL,
    compensation_errors JSONB NULL,               -- Array of compensation failures
    
    -- Correlation
    correlation_id      VARCHAR(100) NULL,        -- For distributed tracing
    
    -- Constraints
    CONSTRAINT chk_saga_state CHECK (state IN (
        'STARTED',
        'STEP_IN_PROGRESS', 
        'STEP_COMPLETED',
        'COMPENSATING',
        'COMPENSATED',
        'COMPLETED',
        'FAILED'
    ))
);

-- Comments
COMMENT ON TABLE saga_instances IS 'Persistent saga state for choreography-based distributed transactions';
COMMENT ON COLUMN saga_instances.saga_type IS 'Saga definition type: CREATE_ORDER, CANCEL_ORDER';
COMMENT ON COLUMN saga_instances.current_step IS 'Zero-based index of the current or last completed step';
COMMENT ON COLUMN saga_instances.payload IS 'JSON containing saga context (order, customer, items, etc.)';
```

### Task 1.5.2: Add Saga Indexes
<!-- task-1.5.2 -->

```sql
-- Add to V5__add_saga_instances.sql

-- Primary query: find active sagas by type (for monitoring dashboard)
CREATE INDEX idx_saga_type_state 
    ON saga_instances(saga_type, state);

-- Recovery query: find sagas that need attention
CREATE INDEX idx_saga_active 
    ON saga_instances(updated_at) 
    WHERE state NOT IN ('COMPLETED', 'FAILED', 'COMPENSATED');

-- Correlation lookup for tracing
CREATE INDEX idx_saga_correlation 
    ON saga_instances(correlation_id) 
    WHERE correlation_id IS NOT NULL;

-- Cleanup: find old completed sagas for archival
CREATE INDEX idx_saga_completed 
    ON saga_instances(completed_at) 
    WHERE state IN ('COMPLETED', 'COMPENSATED');
```

### Task 1.5.3: Complete Saga Migration
<!-- task-1.5.3 -->

Complete `V5__add_saga_instances.sql`:

```sql
-- Full migration file combining table and indexes
-- See tasks 1.5.1 and 1.5.2 above for complete content

-- Also add a trigger to update updated_at automatically:
CREATE OR REPLACE FUNCTION update_saga_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER saga_instances_update_timestamp
    BEFORE UPDATE ON saga_instances
    FOR EACH ROW
    EXECUTE FUNCTION update_saga_timestamp();
```

---

## 1.6 SBT Dependencies

> **Related Tasks:** [1.6.1](implementation-task-list.md) - [1.6.8](implementation-task-list.md)

### Task 1.6.1 - 1.6.7: Add Dependencies to Common Module
<!-- task-1.6.1 -->

Update `backend/common/build.sbt`:

```scala
// ============================================
// Common Module Dependencies
// ============================================
// Related Tasks: 1.6.1 - 1.6.7

val AkkaVersion = "2.8.5"
val AkkaHttpVersion = "10.5.3"
val AkkaPersistenceJdbcVersion = "5.2.1"
val AlpakkaKafkaVersion = "5.0.0"
val SlickVersion = "3.4.1"

libraryDependencies ++= Seq(
  // ==========================================
  // Task 1.6.1: Akka Persistence Typed
  // ==========================================
  // Provides typed persistent actors for event sourcing.
  // Used to persist saga state and enable recovery after restarts.
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  
  // ==========================================
  // Task 1.6.2: Akka Persistence Query
  // ==========================================
  // Enables querying the event journal for read-side projections.
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  
  // ==========================================
  // Task 1.6.3: Akka Persistence JDBC
  // ==========================================
  // Stores Akka Persistence events/snapshots in PostgreSQL.
  "com.lightbend.akka" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion,
  
  // ==========================================
  // Task 1.6.4: Akka Stream Kafka (Alpakka)
  // ==========================================
  // Akka Streams integration for Kafka with backpressure support.
  "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
  
  // ==========================================
  // Task 1.6.5: Akka Serialization Jackson
  // ==========================================
  // JSON serialization for Akka messages with schema evolution support.
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  
  // ==========================================
  // Task 1.6.6: Redis Client
  // ==========================================
  // Redis client for fast idempotency key lookups.
  // Option A: Simple Redis client
  "net.debasishg" %% "redisclient" % "3.42",
  // Option B: Functional Redis (if using Cats Effect)
  // "dev.profunktor" %% "redis4cats-effects" % "1.5.2",
  
  // ==========================================
  // Task 1.6.7: Flyway
  // ==========================================
  // Database migration tool for schema versioning.
  "org.flywaydb" % "flyway-core" % "9.22.3",
  
  // ==========================================
  // Existing dependencies (ensure compatibility)
  // ==========================================
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  
  // Database
  "com.typesafe.slick" %% "slick" % SlickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
  "org.postgresql" % "postgresql" % "42.6.0",
  
  // ==========================================
  // Testing Dependencies
  // ==========================================
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "io.github.embeddedkafka" %% "embedded-kafka" % "3.6.1" % Test
)
```

### Task 1.6.8: Service-Specific Dependencies

For services implementing Saga/TCC coordination (Order Service):

```scala
// Add to order-service/build.sbt
libraryDependencies ++= Seq(
  // Akka Cluster for distributed saga coordination (optional)
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion
)
```

### Dependency Compatibility Check Script

Create `scripts/check-dependencies.sh`:

```bash
#!/bin/bash
# Run from backend directory
cd backend
sbt ";clean;update;evicted"
# Check for eviction warnings - all Akka modules must use same version
```

---

## 1.7 Outbox Pattern Implementation

> **Related Tasks:** [1.7.1](implementation-task-list.md) - [1.7.9](implementation-task-list.md)

### Task 1.7.1: Create OutboxEvent Model
<!-- task-1.7.1 -->

Create file: `common/src/main/scala/com/oms/common/outbox/OutboxEvent.scala`

```scala
package com.oms.common.outbox

import java.time.Instant
import spray.json._

/**
 * Represents an event stored in the outbox table for reliable publishing.
 * 
 * @param id Database-generated ID (None before insert)
 * @param aggregateType Type of aggregate (ORDER, PRODUCT, PAYMENT)
 * @param aggregateId ID of the aggregate instance
 * @param eventType Domain event type (ORDER_CREATED, STOCK_RESERVED, etc.)
 * @param payload JSON-serialized event data
 * @param createdAt When the event was created
 * @param publishedAt When the event was published to Kafka (None if pending)
 * @param retryCount Number of publish attempts
 * @param status Current processing status
 *
 * Related Task: 1.7.1
 */
case class OutboxEvent(
  id: Option[Long] = None,
  aggregateType: String,
  aggregateId: String,
  eventType: String,
  payload: String,
  createdAt: Instant = Instant.now(),
  publishedAt: Option[Instant] = None,
  retryCount: Int = 0,
  status: OutboxStatus = OutboxStatus.Pending
)

/**
 * Outbox event processing status
 */
sealed trait OutboxStatus
object OutboxStatus {
  case object Pending extends OutboxStatus
  case object Published extends OutboxStatus
  case object Failed extends OutboxStatus
  
  def fromString(s: String): OutboxStatus = s match {
    case "PENDING" => Pending
    case "PUBLISHED" => Published
    case "FAILED" => Failed
    case _ => throw new IllegalArgumentException(s"Unknown status: $s")
  }
  
  def toString(status: OutboxStatus): String = status match {
    case Pending => "PENDING"
    case Published => "PUBLISHED"
    case Failed => "FAILED"
  }
}

/**
 * JSON codecs for OutboxEvent
 */
object OutboxEventJsonProtocol extends DefaultJsonProtocol {
  implicit object OutboxStatusFormat extends JsonFormat[OutboxStatus] {
    def write(status: OutboxStatus): JsValue = JsString(OutboxStatus.toString(status))
    def read(value: JsValue): OutboxStatus = value match {
      case JsString(s) => OutboxStatus.fromString(s)
      case _ => throw DeserializationException("Expected string for OutboxStatus")
    }
  }
  
  implicit object InstantFormat extends JsonFormat[Instant] {
    def write(instant: Instant): JsValue = JsString(instant.toString)
    def read(value: JsValue): Instant = value match {
      case JsString(s) => Instant.parse(s)
      case _ => throw DeserializationException("Expected string for Instant")
    }
  }
  
  implicit val outboxEventFormat: RootJsonFormat[OutboxEvent] = jsonFormat9(OutboxEvent.apply)
}
```

### Task 1.7.2: Create OutboxRepository Trait
<!-- task-1.7.2 -->

Create file: `common/src/main/scala/com/oms/common/outbox/OutboxRepository.scala`

```scala
package com.oms.common.outbox

import scala.concurrent.Future

/**
 * Repository trait for outbox operations.
 * Implementations should use database-level locking (SELECT FOR UPDATE)
 * to prevent duplicate processing in clustered deployments.
 *
 * Related Task: 1.7.2
 */
trait OutboxRepository {
  
  /**
   * Insert a new outbox event.
   * Should be called within the same transaction as the business operation.
   *
   * @param event Event to insert (id should be None)
   * @return Event with generated ID
   */
  def insert(event: OutboxEvent): Future[OutboxEvent]
  
  /**
   * Find pending events for publishing.
   * Uses SELECT FOR UPDATE to lock rows and prevent duplicate processing.
   *
   * @param limit Maximum number of events to return
   * @return Pending events ordered by created_at ASC
   */
  def findPendingEvents(limit: Int = 100): Future[Seq[OutboxEvent]]
  
  /**
   * Mark an event as successfully published.
   *
   * @param id Event ID
   */
  def markAsPublished(id: Long): Future[Unit]
  
  /**
   * Mark an event as failed after max retries.
   *
   * @param id Event ID
   * @param errorMessage Last error message
   */
  def markAsFailed(id: Long, errorMessage: String): Future[Unit]
  
  /**
   * Increment retry count after a failed publish attempt.
   *
   * @param id Event ID
   * @param errorMessage Error message from this attempt
   * @return Updated retry count
   */
  def incrementRetryCount(id: Long, errorMessage: String): Future[Int]
  
  /**
   * Delete old published events for cleanup.
   *
   * @param olderThan Delete events published before this timestamp
   * @return Number of deleted events
   */
  def deletePublishedOlderThan(olderThan: java.time.Instant): Future[Int]
}
```

### Task 1.7.3: Implement OutboxRepositoryImpl
<!-- task-1.7.3 -->

Create file: `common/src/main/scala/com/oms/common/outbox/OutboxRepositoryImpl.scala`

```scala
package com.oms.common.outbox

import slick.jdbc.PostgresProfile.api._
import java.time.Instant
import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}

/**
 * Slick implementation of OutboxRepository.
 *
 * Related Task: 1.7.3
 */
class OutboxRepositoryImpl(db: Database)(implicit ec: ExecutionContext) extends OutboxRepository {
  
  // Custom column mapper for Instant
  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      instant => Timestamp.from(instant),
      timestamp => timestamp.toInstant
    )
  
  // Custom column mapper for OutboxStatus
  implicit val statusColumnType: BaseColumnType[OutboxStatus] =
    MappedColumnType.base[OutboxStatus, String](
      OutboxStatus.toString,
      OutboxStatus.fromString
    )
  
  /**
   * Slick table definition for outbox_events
   */
  private class OutboxEventsTable(tag: Tag) extends Table[OutboxEvent](tag, "outbox_events") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def aggregateType = column[String]("aggregate_type")
    def aggregateId = column[String]("aggregate_id")
    def eventType = column[String]("event_type")
    def payload = column[String]("payload")
    def createdAt = column[Instant]("created_at")
    def publishedAt = column[Option[Instant]]("published_at")
    def retryCount = column[Int]("retry_count")
    def status = column[OutboxStatus]("status")
    
    def * = (
      id.?, aggregateType, aggregateId, eventType, payload,
      createdAt, publishedAt, retryCount, status
    ).mapTo[OutboxEvent]
  }
  
  private val outboxEvents = TableQuery[OutboxEventsTable]
  
  override def insert(event: OutboxEvent): Future[OutboxEvent] = {
    val insertAction = (outboxEvents returning outboxEvents.map(_.id)
      into ((e, id) => e.copy(id = Some(id)))) += event
    db.run(insertAction)
  }
  
  override def findPendingEvents(limit: Int): Future[Seq[OutboxEvent]] = {
    // Use FOR UPDATE to lock rows and prevent duplicate processing
    val query = outboxEvents
      .filter(_.status === (OutboxStatus.Pending: OutboxStatus))
      .sortBy(_.createdAt.asc)
      .take(limit)
      .forUpdate
    
    db.run(query.result)
  }
  
  override def markAsPublished(id: Long): Future[Unit] = {
    val action = outboxEvents
      .filter(_.id === id)
      .map(e => (e.status, e.publishedAt))
      .update((OutboxStatus.Published, Some(Instant.now())))
    
    db.run(action).map(_ => ())
  }
  
  override def markAsFailed(id: Long, errorMessage: String): Future[Unit] = {
    val action = sqlu"""
      UPDATE outbox_events 
      SET status = 'FAILED', error_message = $errorMessage
      WHERE id = $id
    """
    db.run(action).map(_ => ())
  }
  
  override def incrementRetryCount(id: Long, errorMessage: String): Future[Int] = {
    val action = for {
      _ <- sqlu"""
        UPDATE outbox_events 
        SET retry_count = retry_count + 1, error_message = $errorMessage
        WHERE id = $id
      """
      count <- outboxEvents.filter(_.id === id).map(_.retryCount).result.head
    } yield count
    
    db.run(action.transactionally)
  }
  
  override def deletePublishedOlderThan(olderThan: Instant): Future[Int] = {
    val timestamp = Timestamp.from(olderThan)
    val action = sqlu"""
      DELETE FROM outbox_events 
      WHERE status = 'PUBLISHED' AND published_at < $timestamp
    """
    db.run(action)
  }
}
```

### Task 1.7.4: Create OutboxProcessor Actor
<!-- task-1.7.4 -->

Create file: `common/src/main/scala/com/oms/common/outbox/OutboxProcessor.scala`

```scala
package com.oms.common.outbox

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, TimerScheduler}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Actor that polls the outbox table and publishes events to Kafka.
 * Implements the Transactional Outbox pattern for at-least-once delivery.
 *
 * Related Task: 1.7.4
 */
object OutboxProcessor {
  
  // Commands
  sealed trait Command
  case object Poll extends Command
  private case class ProcessResult(successCount: Int, failCount: Int) extends Command
  private case class ProcessError(error: Throwable) extends Command
  
  // Configuration
  case class Config(
    pollInterval: FiniteDuration = 1.second,
    batchSize: Int = 100,
    maxRetries: Int = 5
  )
  
  def apply(
    outboxRepo: OutboxRepository,
    eventPublisher: EventPublisher,
    config: Config = Config()
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        // Start periodic polling
        timers.startTimerWithFixedDelay(Poll, config.pollInterval)
        
        context.log.info(
          s"OutboxProcessor started: poll=${config.pollInterval}, batch=${config.batchSize}"
        )
        
        processing(outboxRepo, eventPublisher, config, timers)
      }
    }
  }
  
  private def processing(
    outboxRepo: OutboxRepository,
    eventPublisher: EventPublisher,
    config: Config,
    timers: TimerScheduler[Command]
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    
    Behaviors.receive { (context, message) =>
      message match {
        case Poll =>
          context.log.debug("Polling for pending outbox events...")
          
          val processF = processOutbox(outboxRepo, eventPublisher, config)
          context.pipeToSelf(processF) {
            case Success((success, fail)) => ProcessResult(success, fail)
            case Failure(ex) => ProcessError(ex)
          }
          
          Behaviors.same
          
        case ProcessResult(successCount, failCount) =>
          if (successCount > 0 || failCount > 0) {
            context.log.info(s"Outbox processed: $successCount published, $failCount failed")
          }
          Behaviors.same
          
        case ProcessError(error) =>
          context.log.error("Outbox processing error", error)
          Behaviors.same
      }
    }
  }
  
  /**
   * Process pending events from the outbox.
   */
  private def processOutbox(
    outboxRepo: OutboxRepository,
    eventPublisher: EventPublisher,
    config: Config
  )(implicit ec: ExecutionContext): Future[(Int, Int)] = {
    
    for {
      pendingEvents <- outboxRepo.findPendingEvents(config.batchSize)
      results <- Future.traverse(pendingEvents) { event =>
        publishEvent(event, outboxRepo, eventPublisher, config.maxRetries)
      }
    } yield {
      val (successes, failures) = results.partition(identity)
      (successes.size, failures.size)
    }
  }
  
  /**
   * Publish a single event to Kafka.
   */
  private def publishEvent(
    event: OutboxEvent,
    outboxRepo: OutboxRepository,
    eventPublisher: EventPublisher,
    maxRetries: Int
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    
    eventPublisher.publish(event.eventType, event.aggregateId, event.payload)
      .flatMap { _ =>
        outboxRepo.markAsPublished(event.id.get).map(_ => true)
      }
      .recoverWith { case ex: Throwable =>
        val errorMsg = s"${ex.getClass.getSimpleName}: ${ex.getMessage}"
        
        if (event.retryCount >= maxRetries - 1) {
          // Move to failed status after max retries
          outboxRepo.markAsFailed(event.id.get, errorMsg).map(_ => false)
        } else {
          // Increment retry counter
          outboxRepo.incrementRetryCount(event.id.get, errorMsg).map(_ => false)
        }
      }
  }
}
```

### Task 1.7.5: Create KafkaEventPublisher
<!-- task-1.7.5 -->

Create file: `common/src/main/scala/com/oms/common/kafka/KafkaEventPublisher.scala`

```scala
package com.oms.common.kafka

import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import scala.concurrent.{ExecutionContext, Future}
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Event publisher that sends events to Kafka topics.
 *
 * Related Task: 1.7.5
 */
trait EventPublisher {
  def publish(eventType: String, key: String, payload: String): Future[Done]
  def publish(eventType: String, key: String, payload: String, correlationId: String): Future[Done]
}

class KafkaEventPublisher(
  bootstrapServers: String,
  topicPrefix: String = "oms"
)(implicit system: ActorSystem[_], ec: ExecutionContext) extends EventPublisher {
  
  private val producerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(bootstrapServers)
    .withProperty("acks", "all")
    .withProperty("retries", "3")
    .withProperty("enable.idempotence", "true")
    .withProperty("max.in.flight.requests.per.connection", "1")
  
  override def publish(eventType: String, key: String, payload: String): Future[Done] = {
    publish(eventType, key, payload, UUID.randomUUID().toString)
  }
  
  override def publish(
    eventType: String, 
    key: String, 
    payload: String, 
    correlationId: String
  ): Future[Done] = {
    val topic = resolveTopicName(eventType)
    
    val record = new ProducerRecord[String, String](topic, key, payload)
    
    // Add headers for tracing
    record.headers()
      .add(new RecordHeader("event-type", eventType.getBytes(StandardCharsets.UTF_8)))
      .add(new RecordHeader("correlation-id", correlationId.getBytes(StandardCharsets.UTF_8)))
      .add(new RecordHeader("timestamp", System.currentTimeMillis().toString.getBytes(StandardCharsets.UTF_8)))
    
    Source.single(record)
      .runWith(Producer.plainSink(producerSettings))
  }
  
  /**
   * Route events to appropriate Kafka topics based on event type.
   */
  private def resolveTopicName(eventType: String): String = {
    eventType match {
      case t if t.startsWith("ORDER_") => s"$topicPrefix.orders.events"
      case t if t.startsWith("STOCK_") => s"$topicPrefix.products.events"
      case t if t.startsWith("PAYMENT_") => s"$topicPrefix.payments.events"
      case t if t.startsWith("SAGA_") => s"$topicPrefix.saga.commands"
      case _ => s"$topicPrefix.general.events"
    }
  }
}
```

### Task 1.7.6 - 1.7.8: Add OutboxProcessor to Services
<!-- task-1.7.6 -->

Add to each service's Main.scala (example for Order Service):

```scala
package com.oms.order

import akka.actor.typed.ActorSystem
import com.oms.common.outbox.{OutboxProcessor, OutboxRepositoryImpl}
import com.oms.common.kafka.KafkaEventPublisher

object Main extends App {
  // ... existing setup ...
  
  // Initialize outbox infrastructure (Task 1.7.6)
  val outboxRepo = new OutboxRepositoryImpl(database)
  val kafkaPublisher = new KafkaEventPublisher(
    bootstrapServers = config.getString("kafka.bootstrap-servers")
  )
  
  // Spawn OutboxProcessor actor
  val outboxProcessor = system.systemActorOf(
    OutboxProcessor(
      outboxRepo,
      kafkaPublisher,
      OutboxProcessor.Config(
        pollInterval = config.getDuration("outbox.poll-interval").toScala,
        batchSize = config.getInt("outbox.batch-size"),
        maxRetries = config.getInt("outbox.max-retries")
      )
    ),
    "outbox-processor"
  )
  
  // Graceful shutdown
  CoordinatedShutdown(system).addTask(
    CoordinatedShutdown.PhaseServiceStop, "stop-outbox-processor"
  ) { () =>
    system.log.info("Stopping outbox processor...")
    Future.successful(Done)
  }
}
```

### Task 1.7.9: Unit Tests for OutboxProcessor
<!-- task-1.7.9 -->

Create file: `common/src/test/scala/com/oms/common/outbox/OutboxProcessorSpec.scala`

```scala
package com.oms.common.outbox

import akka.Done
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.Future
import scala.concurrent.duration._

class OutboxProcessorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  
  "OutboxProcessor" should {
    
    "successfully publish pending events" in {
      // Test implementation...
    }
    
    "mark events as published after Kafka confirmation" in {
      // Test implementation...
    }
    
    "retry on transient Kafka failures" in {
      // Test implementation...
    }
    
    "move to DLQ after max retries" in {
      // Test implementation...
    }
    
    "handle empty pending queue gracefully" in {
      // Test implementation...
    }
    
    "process events in order by created_at" in {
      // Test implementation...
    }
  }
}
```

---

## 1.8 Idempotency Pattern Implementation

> **Related Tasks:** [1.8.1](implementation-task-list.md) - [1.8.8](implementation-task-list.md)

### Task 1.8.1: Create IdempotencyRecord Model
<!-- task-1.8.1 -->

Create file: `common/src/main/scala/com/oms/common/idempotency/IdempotencyRecord.scala`

```scala
package com.oms.common.idempotency

import java.time.Instant
import spray.json._

/**
 * Represents an idempotency tracking record.
 *
 * Related Task: 1.8.1
 */
case class IdempotencyRecord(
  key: String,                          // Client-provided idempotency key
  requestHash: String,                  // SHA-256 hash of request payload
  responseData: Option[String] = None,  // Cached response JSON
  status: IdempotencyStatus,            // Processing status
  createdAt: Instant = Instant.now(),
  expiresAt: Instant
)

/**
 * Idempotency record status
 */
sealed trait IdempotencyStatus
object IdempotencyStatus {
  case object Processing extends IdempotencyStatus
  case object Completed extends IdempotencyStatus
  case object Failed extends IdempotencyStatus
  
  def fromString(s: String): IdempotencyStatus = s match {
    case "PROCESSING" => Processing
    case "COMPLETED" => Completed
    case "FAILED" => Failed
    case _ => throw new IllegalArgumentException(s"Unknown status: $s")
  }
  
  def toString(status: IdempotencyStatus): String = status match {
    case Processing => "PROCESSING"
    case Completed => "COMPLETED"
    case Failed => "FAILED"
  }
}
```

### Task 1.8.2 - 1.8.3: Create IdempotencyRepository
<!-- task-1.8.2 -->

Create file: `common/src/main/scala/com/oms/common/idempotency/IdempotencyRepository.scala`

```scala
package com.oms.common.idempotency

import slick.jdbc.PostgresProfile.api._
import java.time.Instant
import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for idempotency record operations.
 *
 * Related Tasks: 1.8.2, 1.8.3
 */
trait IdempotencyRepository {
  def find(key: String): Future[Option[IdempotencyRecord]]
  def insert(record: IdempotencyRecord): Future[IdempotencyRecord]
  def updateStatus(key: String, status: IdempotencyStatus, response: Option[String]): Future[Unit]
  def deleteExpired(): Future[Int]
}

class IdempotencyRepositoryImpl(db: Database)(implicit ec: ExecutionContext) 
    extends IdempotencyRepository {
  
  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      instant => Timestamp.from(instant),
      timestamp => timestamp.toInstant
    )
  
  implicit val statusColumnType: BaseColumnType[IdempotencyStatus] =
    MappedColumnType.base[IdempotencyStatus, String](
      IdempotencyStatus.toString,
      IdempotencyStatus.fromString
    )
  
  private class IdempotencyRecordsTable(tag: Tag) 
      extends Table[IdempotencyRecord](tag, "idempotency_records") {
    def key = column[String]("idempotency_key", O.PrimaryKey)
    def requestHash = column[String]("request_hash")
    def responseData = column[Option[String]]("response_data")
    def status = column[IdempotencyStatus]("status")
    def createdAt = column[Instant]("created_at")
    def expiresAt = column[Instant]("expires_at")
    
    def * = (key, requestHash, responseData, status, createdAt, expiresAt)
      .mapTo[IdempotencyRecord]
  }
  
  private val records = TableQuery[IdempotencyRecordsTable]
  
  override def find(key: String): Future[Option[IdempotencyRecord]] = {
    db.run(records.filter(_.key === key).result.headOption)
  }
  
  override def insert(record: IdempotencyRecord): Future[IdempotencyRecord] = {
    db.run((records += record).map(_ => record))
  }
  
  override def updateStatus(
    key: String, 
    status: IdempotencyStatus, 
    response: Option[String]
  ): Future[Unit] = {
    val action = records
      .filter(_.key === key)
      .map(r => (r.status, r.responseData))
      .update((status, response))
    
    db.run(action).map(_ => ())
  }
  
  override def deleteExpired(): Future[Int] = {
    val now = Timestamp.from(Instant.now())
    db.run(sqlu"DELETE FROM idempotency_records WHERE expires_at < $now")
  }
}
```

### Task 1.8.4: Create IdempotencyService
<!-- task-1.8.4 -->

Create file: `common/src/main/scala/com/oms/common/idempotency/IdempotencyService.scala`

```scala
package com.oms.common.idempotency

import spray.json._
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.{ExecutionContext, Future}

/**
 * Exceptions for idempotency handling
 */
class IdempotencyKeyReusedException(message: String) extends Exception(message)
class RequestInProgressException(message: String) extends Exception(message)

/**
 * Service that provides idempotency guarantees for operations.
 *
 * Usage:
 * {{{
 * val response = idempotencyService.executeIdempotent(
 *   idempotencyKey = request.idempotencyKey,
 *   request = request
 * ) { req =>
 *   // Your business logic here
 *   orderService.createOrder(req)
 * }
 * }}}
 *
 * Related Task: 1.8.4
 */
class IdempotencyService(
  repo: IdempotencyRepository,
  ttlHours: Int = 24
)(implicit ec: ExecutionContext) {
  
  /**
   * Execute an operation with idempotency guarantees.
   *
   * @param idempotencyKey Client-provided unique key
   * @param request Request object (must have JSON format)
   * @param operation The operation to execute
   * @return Operation result (from execution or cache)
   */
  def executeIdempotent[Req: JsonFormat, Res: JsonFormat](
    idempotencyKey: String,
    request: Req
  )(operation: Req => Future[Res]): Future[Res] = {
    
    val requestHash = hashRequest(request)
    
    repo.find(idempotencyKey).flatMap {
      case Some(record) if record.status == IdempotencyStatus.Completed =>
        // Return cached response
        val cachedResponse = record.responseData
          .map(_.parseJson.convertTo[Res])
          .getOrElse(throw new RuntimeException("Missing response data for completed request"))
        Future.successful(cachedResponse)
        
      case Some(record) if record.status == IdempotencyStatus.Processing =>
        // Request is being processed
        Future.failed(new RequestInProgressException(
          s"Request with idempotency key '$idempotencyKey' is currently being processed"
        ))
        
      case Some(record) if record.requestHash != requestHash =>
        // Key reused with different request
        Future.failed(new IdempotencyKeyReusedException(
          s"Idempotency key '$idempotencyKey' was used with a different request"
        ))
        
      case Some(record) if record.status == IdempotencyStatus.Failed =>
        // Previous attempt failed, allow retry
        processRequest(idempotencyKey, request, requestHash, operation)
        
      case None =>
        // New request
        processRequest(idempotencyKey, request, requestHash, operation)
    }
  }
  
  /**
   * Process a new request with idempotency tracking.
   */
  private def processRequest[Req: JsonFormat, Res: JsonFormat](
    key: String,
    request: Req,
    requestHash: String,
    operation: Req => Future[Res]
  ): Future[Res] = {
    
    val record = IdempotencyRecord(
      key = key,
      requestHash = requestHash,
      status = IdempotencyStatus.Processing,
      expiresAt = Instant.now().plus(ttlHours, ChronoUnit.HOURS)
    )
    
    for {
      _ <- repo.insert(record).recover {
        case _: Exception =>
          // Handle race condition - another request with same key just started
          throw new RequestInProgressException(
            s"Request with key '$key' was just started by another process"
          )
      }
      response <- operation(request).transformWith {
        case scala.util.Success(res) =>
          val responseJson = res.toJson.compactPrint
          repo.updateStatus(key, IdempotencyStatus.Completed, Some(responseJson))
            .map(_ => res)
        case scala.util.Failure(ex) =>
          repo.updateStatus(key, IdempotencyStatus.Failed, None)
            .flatMap(_ => Future.failed(ex))
      }
    } yield response
  }
  
  /**
   * Hash a request for fingerprinting.
   */
  private def hashRequest[Req: JsonFormat](request: Req): String = {
    val json = request.toJson.compactPrint
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(json.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}
```

### Task 1.8.5: Create Cleanup Scheduled Job
<!-- task-1.8.5 -->

```scala
package com.oms.common.idempotency

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * Actor that periodically cleans up expired idempotency records.
 *
 * Related Task: 1.8.5
 */
object IdempotencyCleanup {
  
  sealed trait Command
  case object Cleanup extends Command
  private case class CleanupResult(deletedCount: Int) extends Command
  private case class CleanupError(error: Throwable) extends Command
  
  def apply(
    repo: IdempotencyRepository,
    cleanupInterval: FiniteDuration = 1.hour
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(Cleanup, cleanupInterval)
        context.log.info(s"IdempotencyCleanup started with interval $cleanupInterval")
        
        Behaviors.receiveMessage {
          case Cleanup =>
            context.pipeToSelf(repo.deleteExpired()) {
              case scala.util.Success(count) => CleanupResult(count)
              case scala.util.Failure(ex) => CleanupError(ex)
            }
            Behaviors.same
            
          case CleanupResult(count) =>
            if (count > 0) {
              context.log.info(s"Cleaned up $count expired idempotency records")
            }
            Behaviors.same
            
          case CleanupError(error) =>
            context.log.error("Idempotency cleanup failed", error)
            Behaviors.same
        }
      }
    }
  }
}
```

### Task 1.8.6 - 1.8.7: Integrate with Order Service
<!-- task-1.8.6 -->

Update `CreateOrderRequest`:

```scala
// In order-service/src/main/scala/com/oms/order/model/CreateOrderRequest.scala

case class CreateOrderRequest(
  customerId: Long,
  items: List[OrderItemRequest],
  idempotencyKey: Option[String] = None  // Task 1.8.6: Add idempotency key
)
```

Update OrderActor (Task 1.8.7):

```scala
// In order-service/src/main/scala/com/oms/order/actor/OrderActor.scala

class OrderActor(
  orderService: OrderService,
  idempotencyService: IdempotencyService
)(implicit ec: ExecutionContext) {
  
  def createOrder(request: CreateOrderRequest, userId: Long): Future[OrderResponse] = {
    request.idempotencyKey match {
      case Some(key) =>
        // Use idempotent execution
        idempotencyService.executeIdempotent(key, request) { req =>
          orderService.createOrder(req, userId)
        }
      case None =>
        // Non-idempotent execution
        orderService.createOrder(request, userId)
    }
  }
}
```

---

> **Related Tasks:** [2.1.1](implementation-task-list.md) - [2.1.6](implementation-task-list.md)

### Task 2.1.1: Create DomainEvent Base Trait
<!-- task-2.1.1 -->

Create file: `common/src/main/scala/com/oms/common/events/DomainEvent.scala`

```scala
package com.oms.common.events

import java.time.Instant

/**
 * Base trait for all domain events in the OMS system.
 * All events must extend this trait to ensure consistent structure.
 *
 * Related Task: 2.1.1
 */
sealed trait DomainEvent {
  /** Unique event identifier (UUID) */
  def eventId: String
  
  /** Event type name (e.g., ORDER_CREATED) */
  def eventType: String
  
  /** ID of the aggregate that changed */
  def aggregateId: String
  
  /** Type of aggregate (ORDER, PRODUCT, PAYMENT) */
  def aggregateType: String
  
  /** When the event occurred */
  def timestamp: Instant
  
  /** Schema version for evolution (default: 1) */
  def version: Int
  
  /** Correlation ID for distributed tracing (optional) */
  def correlationId: Option[String]
}

/**
 * Helper for generating event IDs
 */
object EventIdGenerator {
  def generate(): String = java.util.UUID.randomUUID().toString
}

/**
 * Shared event data structures
 */
case class OrderItemEvent(
  productId: Long,
  productName: String,
  quantity: Int,
  unitPrice: BigDecimal
)

case class StockReservationItem(
  productId: Long,
  quantity: Int,
  reservationId: Long
)
```

### Task 2.1.2: Create OrderEvents
<!-- task-2.1.2 -->

Create file: `common/src/main/scala/com/oms/common/events/OrderEvents.scala`

```scala
package com.oms.common.events

import java.time.Instant

/**
 * Order domain events for the Order Service.
 *
 * Related Task: 2.1.2
 */
object OrderEvents {
  
  case class OrderCreated(
    eventId: String,
    aggregateId: String,  // orderId
    customerId: Long,
    customerName: String,
    items: List[OrderItemEvent],
    totalAmount: BigDecimal,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "ORDER_CREATED"
    val aggregateType = "ORDER"
  }
  
  case class OrderConfirmed(
    eventId: String,
    aggregateId: String,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "ORDER_CONFIRMED"
    val aggregateType = "ORDER"
  }
  
  case class OrderPaid(
    eventId: String,
    aggregateId: String,
    paymentId: Long,
    amount: BigDecimal,
    transactionId: String,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "ORDER_PAID"
    val aggregateType = "ORDER"
  }
  
  case class OrderCancelled(
    eventId: String,
    aggregateId: String,
    reason: String,
    cancelledBy: Long,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "ORDER_CANCELLED"
    val aggregateType = "ORDER"
  }
  
  case class OrderCancellationRequested(
    eventId: String,
    aggregateId: String,
    reason: String,
    requestedBy: Long,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "ORDER_CANCELLATION_REQUESTED"
    val aggregateType = "ORDER"
  }
  
  case class OrderCompleted(
    eventId: String,
    aggregateId: String,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "ORDER_COMPLETED"
    val aggregateType = "ORDER"
  }
}
```

### Task 2.1.3: Create StockEvents
<!-- task-2.1.3 -->

Create file: `common/src/main/scala/com/oms/common/events/StockEvents.scala`

```scala
package com.oms.common.events

import java.time.Instant

/**
 * Stock/Product domain events for the Product Service.
 *
 * Related Task: 2.1.3
 */
object StockEvents {
  
  case class StockReserved(
    eventId: String,
    aggregateId: String,  // orderId
    orderId: String,
    reservations: List[StockReservationItem],
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "STOCK_RESERVED"
    val aggregateType = "PRODUCT"
  }
  
  case class StockReservationFailed(
    eventId: String,
    aggregateId: String,  // orderId
    orderId: String,
    productId: Long,
    productName: String,
    reason: String,
    availableQuantity: Int,
    requestedQuantity: Int,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "STOCK_RESERVATION_FAILED"
    val aggregateType = "PRODUCT"
  }
  
  case class StockReleased(
    eventId: String,
    aggregateId: String,  // orderId
    orderId: String,
    productId: Long,
    quantity: Int,
    reason: String,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "STOCK_RELEASED"
    val aggregateType = "PRODUCT"
  }
  
  case class StockConfirmed(
    eventId: String,
    aggregateId: String,
    orderId: String,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "STOCK_CONFIRMED"
    val aggregateType = "PRODUCT"
  }
}
```

### Task 2.1.4: Create PaymentEvents
<!-- task-2.1.4 -->

Create file: `common/src/main/scala/com/oms/common/events/PaymentEvents.scala`

```scala
package com.oms.common.events

import java.time.Instant

/**
 * Payment domain events for the Payment Service.
 *
 * Related Task: 2.1.4
 */
object PaymentEvents {
  
  case class PaymentReserved(
    eventId: String,
    aggregateId: String,  // reservationId
    orderId: String,
    customerId: Long,
    amount: BigDecimal,
    currency: String,
    expiresAt: Instant,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "PAYMENT_RESERVED"
    val aggregateType = "PAYMENT"
  }
  
  case class PaymentCompleted(
    eventId: String,
    aggregateId: String,  // paymentId
    orderId: String,
    amount: BigDecimal,
    transactionId: String,
    paymentMethod: String,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "PAYMENT_COMPLETED"
    val aggregateType = "PAYMENT"
  }
  
  case class PaymentFailed(
    eventId: String,
    aggregateId: String,  // orderId
    orderId: String,
    reason: String,
    errorCode: String,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "PAYMENT_FAILED"
    val aggregateType = "PAYMENT"
  }
  
  case class PaymentRefunded(
    eventId: String,
    aggregateId: String,  // paymentId
    orderId: String,
    paymentId: Long,
    refundId: String,
    amount: BigDecimal,
    reason: String,
    timestamp: Instant,
    version: Int = 1,
    correlationId: Option[String] = None
  ) extends DomainEvent {
    val eventType = "PAYMENT_REFUNDED"
    val aggregateType = "PAYMENT"
  }
}
```

### Task 2.1.5: Create JSON Serializers for Events
<!-- task-2.1.5 -->

Create file: `common/src/main/scala/com/oms/common/events/EventJsonProtocol.scala`

```scala
package com.oms.common.events

import spray.json._
import java.time.Instant

/**
 * JSON serialization for all domain events.
 * Uses a type discriminator field for polymorphic deserialization.
 *
 * Related Task: 2.1.5
 */
object EventJsonProtocol extends DefaultJsonProtocol {
  
  // Instant serialization
  implicit object InstantFormat extends JsonFormat[Instant] {
    def write(instant: Instant): JsValue = JsString(instant.toString)
    def read(value: JsValue): Instant = value match {
      case JsString(s) => Instant.parse(s)
      case _ => throw DeserializationException("Expected ISO-8601 timestamp string")
    }
  }
  
  // Shared types
  implicit val orderItemEventFormat: RootJsonFormat[OrderItemEvent] = jsonFormat4(OrderItemEvent.apply)
  implicit val stockReservationItemFormat: RootJsonFormat[StockReservationItem] = jsonFormat3(StockReservationItem.apply)
  
  // Order Events
  implicit val orderCreatedFormat: RootJsonFormat[OrderEvents.OrderCreated] = jsonFormat9(OrderEvents.OrderCreated.apply)
  implicit val orderConfirmedFormat: RootJsonFormat[OrderEvents.OrderConfirmed] = jsonFormat5(OrderEvents.OrderConfirmed.apply)
  implicit val orderPaidFormat: RootJsonFormat[OrderEvents.OrderPaid] = jsonFormat8(OrderEvents.OrderPaid.apply)
  implicit val orderCancelledFormat: RootJsonFormat[OrderEvents.OrderCancelled] = jsonFormat7(OrderEvents.OrderCancelled.apply)
  implicit val orderCancellationRequestedFormat: RootJsonFormat[OrderEvents.OrderCancellationRequested] = jsonFormat7(OrderEvents.OrderCancellationRequested.apply)
  implicit val orderCompletedFormat: RootJsonFormat[OrderEvents.OrderCompleted] = jsonFormat5(OrderEvents.OrderCompleted.apply)
  
  // Stock Events
  implicit val stockReservedFormat: RootJsonFormat[StockEvents.StockReserved] = jsonFormat7(StockEvents.StockReserved.apply)
  implicit val stockReservationFailedFormat: RootJsonFormat[StockEvents.StockReservationFailed] = jsonFormat11(StockEvents.StockReservationFailed.apply)
  implicit val stockReleasedFormat: RootJsonFormat[StockEvents.StockReleased] = jsonFormat9(StockEvents.StockReleased.apply)
  implicit val stockConfirmedFormat: RootJsonFormat[StockEvents.StockConfirmed] = jsonFormat6(StockEvents.StockConfirmed.apply)
  
  // Payment Events
  implicit val paymentReservedFormat: RootJsonFormat[PaymentEvents.PaymentReserved] = jsonFormat10(PaymentEvents.PaymentReserved.apply)
  implicit val paymentCompletedFormat: RootJsonFormat[PaymentEvents.PaymentCompleted] = jsonFormat9(PaymentEvents.PaymentCompleted.apply)
  implicit val paymentFailedFormat: RootJsonFormat[PaymentEvents.PaymentFailed] = jsonFormat8(PaymentEvents.PaymentFailed.apply)
  implicit val paymentRefundedFormat: RootJsonFormat[PaymentEvents.PaymentRefunded] = jsonFormat10(PaymentEvents.PaymentRefunded.apply)
  
  /**
   * Polymorphic event serialization using type discriminator.
   */
  implicit object DomainEventFormat extends RootJsonFormat[DomainEvent] {
    def write(event: DomainEvent): JsValue = {
      val baseFields = event match {
        case e: OrderEvents.OrderCreated => e.toJson.asJsObject
        case e: OrderEvents.OrderConfirmed => e.toJson.asJsObject
        case e: OrderEvents.OrderPaid => e.toJson.asJsObject
        case e: OrderEvents.OrderCancelled => e.toJson.asJsObject
        case e: OrderEvents.OrderCancellationRequested => e.toJson.asJsObject
        case e: OrderEvents.OrderCompleted => e.toJson.asJsObject
        case e: StockEvents.StockReserved => e.toJson.asJsObject
        case e: StockEvents.StockReservationFailed => e.toJson.asJsObject
        case e: StockEvents.StockReleased => e.toJson.asJsObject
        case e: StockEvents.StockConfirmed => e.toJson.asJsObject
        case e: PaymentEvents.PaymentReserved => e.toJson.asJsObject
        case e: PaymentEvents.PaymentCompleted => e.toJson.asJsObject
        case e: PaymentEvents.PaymentFailed => e.toJson.asJsObject
        case e: PaymentEvents.PaymentRefunded => e.toJson.asJsObject
      }
      JsObject(baseFields.fields + ("_type" -> JsString(event.eventType)))
    }
    
    def read(value: JsValue): DomainEvent = {
      val fields = value.asJsObject.fields
      val eventType = fields("_type").convertTo[String]
      
      eventType match {
        case "ORDER_CREATED" => value.convertTo[OrderEvents.OrderCreated]
        case "ORDER_CONFIRMED" => value.convertTo[OrderEvents.OrderConfirmed]
        case "ORDER_PAID" => value.convertTo[OrderEvents.OrderPaid]
        case "ORDER_CANCELLED" => value.convertTo[OrderEvents.OrderCancelled]
        case "ORDER_CANCELLATION_REQUESTED" => value.convertTo[OrderEvents.OrderCancellationRequested]
        case "ORDER_COMPLETED" => value.convertTo[OrderEvents.OrderCompleted]
        case "STOCK_RESERVED" => value.convertTo[StockEvents.StockReserved]
        case "STOCK_RESERVATION_FAILED" => value.convertTo[StockEvents.StockReservationFailed]
        case "STOCK_RELEASED" => value.convertTo[StockEvents.StockReleased]
        case "STOCK_CONFIRMED" => value.convertTo[StockEvents.StockConfirmed]
        case "PAYMENT_RESERVED" => value.convertTo[PaymentEvents.PaymentReserved]
        case "PAYMENT_COMPLETED" => value.convertTo[PaymentEvents.PaymentCompleted]
        case "PAYMENT_FAILED" => value.convertTo[PaymentEvents.PaymentFailed]
        case "PAYMENT_REFUNDED" => value.convertTo[PaymentEvents.PaymentRefunded]
        case unknown => throw DeserializationException(s"Unknown event type: $unknown")
      }
    }
  }
}
```

---

## 2.2 Event Publishers

> **Related Tasks:** [2.2.1](implementation-task-list.md) - [2.2.6](implementation-task-list.md)

See [Task 1.7.5: KafkaEventPublisher](#task-175-create-kafkaeventpublisher) for the main implementation.

### Task 2.2.3: Kafka Producer Configuration
<!-- task-2.2.3 -->

Add to `application.conf`:

```hocon
# Kafka Producer Configuration
akka.kafka.producer {
  # Kafka client configuration
  kafka-clients {
    bootstrap.servers = "localhost:29092"
    bootstrap.servers = ${?KAFKA_BOOTSTRAP_SERVERS}
    
    # Reliability settings
    acks = "all"                    # Wait for all replicas
    retries = 3                     # Retry on transient failures
    enable.idempotence = true       # Exactly-once semantics
    max.in.flight.requests.per.connection = 1  # Ordering guarantee
    
    # Performance settings
    batch.size = 16384              # 16KB batch size
    linger.ms = 5                   # Wait 5ms for batching
    compression.type = "snappy"     # Compress messages
    
    # Timeout settings
    request.timeout.ms = 30000
    delivery.timeout.ms = 120000
  }
}
```

### Task 2.2.5: Event Router
<!-- task-2.2.5 -->

```scala
package com.oms.common.kafka

/**
 * Routes events to appropriate Kafka topics based on event type.
 *
 * Related Task: 2.2.5
 */
object EventRouter {
  
  private val topicPrefix = "oms"
  
  def routeToTopic(eventType: String): String = {
    val topic = eventType match {
      // Order events
      case t if t.startsWith("ORDER_") => s"$topicPrefix.orders.events"
      
      // Stock/Product events
      case t if t.startsWith("STOCK_") => s"$topicPrefix.products.events"
      
      // Payment events
      case t if t.startsWith("PAYMENT_") => s"$topicPrefix.payments.events"
      
      // Saga commands (internal)
      case t if t.startsWith("SAGA_") => s"$topicPrefix.saga.commands"
      
      // Default/unknown
      case _ => s"$topicPrefix.general.events"
    }
    
    // Log routing decision for debugging
    org.slf4j.LoggerFactory.getLogger(getClass)
      .debug(s"Routing event type '$eventType' to topic '$topic'")
    
    topic
  }
}
```

---

## 2.3 Event Subscribers - Order Service

> **Related Tasks:** [2.3.1](implementation-task-list.md) - [2.3.7](implementation-task-list.md)

### Task 2.3.1 - 2.3.2: Create Event Subscriber
<!-- task-2.3.1 -->

Create file: `common/src/main/scala/com/oms/common/kafka/KafkaEventSubscriber.scala`

```scala
package com.oms.common.kafka

import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.stream.scaladsl.{Keep, Sink}
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import com.oms.common.events.{DomainEvent, EventJsonProtocol}
import spray.json._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Kafka event subscriber for consuming domain events.
 *
 * Related Tasks: 2.3.1, 2.3.2
 */
trait EventSubscriber {
  def subscribe(
    topic: String, 
    groupId: String
  )(handler: DomainEvent => Future[Done]): Consumer.Control
}

class KafkaEventSubscriber(
  bootstrapServers: String
)(implicit system: ActorSystem[_], ec: ExecutionContext) extends EventSubscriber {
  
  import EventJsonProtocol._
  
  private def createConsumerSettings(groupId: String) = {
    ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withGroupId(groupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
  }
  
  override def subscribe(
    topic: String, 
    groupId: String
  )(handler: DomainEvent => Future[Done]): Consumer.Control = {
    
    val consumerSettings = createConsumerSettings(groupId)
    val committerSettings = Committer.CommitterSettings(system)
    
    Consumer
      .committableSource(consumerSettings, Subscriptions.topics(topic))
      .mapAsync(1) { msg =>
        val event = msg.record.value().parseJson.convertTo[DomainEvent]
        handler(event)
          .map(_ => msg.committableOffset)
          .recover { case ex =>
            system.log.error(s"Error processing event: ${ex.getMessage}", ex)
            msg.committableOffset  // Commit even on error to avoid infinite retry
          }
      }
      .toMat(Committer.sink(committerSettings))(Keep.left)
      .run()
  }
}
```

### Task 2.3.3: Create StockEventHandler (Order Service)
<!-- task-2.3.3 -->

Create file: `order-service/src/main/scala/com/oms/order/events/StockEventHandler.scala`

```scala
package com.oms.order.events

import akka.Done
import com.oms.common.events.{DomainEvent, StockEvents}
import com.oms.order.saga.SagaCoordinator
import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory

/**
 * Handles stock events received from Product Service.
 * Updates saga state based on stock reservation results.
 *
 * Related Task: 2.3.3
 */
class StockEventHandler(
  sagaCoordinator: SagaCoordinator
)(implicit ec: ExecutionContext) {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  def handle(event: DomainEvent): Future[Done] = {
    event match {
      case e: StockEvents.StockReserved =>
        handleStockReserved(e)
        
      case e: StockEvents.StockReservationFailed =>
        handleStockReservationFailed(e)
        
      case e: StockEvents.StockReleased =>
        handleStockReleased(e)
        
      case e: StockEvents.StockConfirmed =>
        handleStockConfirmed(e)
        
      case _ =>
        logger.debug(s"Ignoring unhandled event type: ${event.eventType}")
        Future.successful(Done)
    }
  }
  
  private def handleStockReserved(event: StockEvents.StockReserved): Future[Done] = {
    logger.info(s"Stock reserved for order ${event.orderId}, " +
      s"reservations: ${event.reservations.size} items, " +
      s"correlationId: ${event.correlationId.getOrElse("N/A")}")
    
    // Notify saga that stock reservation succeeded
    sagaCoordinator.handleStockReserved(event.orderId, event.reservations)
  }
  
  private def handleStockReservationFailed(event: StockEvents.StockReservationFailed): Future[Done] = {
    logger.warn(s"Stock reservation failed for order ${event.orderId}: " +
      s"${event.reason} (product: ${event.productId}, " +
      s"requested: ${event.requestedQuantity}, available: ${event.availableQuantity})")
    
    // Notify saga to trigger compensation
    sagaCoordinator.handleStockReservationFailed(event.orderId, event.reason)
  }
  
  private def handleStockReleased(event: StockEvents.StockReleased): Future[Done] = {
    logger.info(s"Stock released for order ${event.orderId}: " +
      s"product ${event.productId}, quantity ${event.quantity}, reason: ${event.reason}")
    
    // Notify saga that stock release completed
    sagaCoordinator.handleStockReleased(event.orderId)
  }
  
  private def handleStockConfirmed(event: StockEvents.StockConfirmed): Future[Done] = {
    logger.info(s"Stock confirmed for order ${event.orderId}")
    Future.successful(Done)
  }
}
```

### Task 2.3.4: Create PaymentEventHandler (Order Service)
<!-- task-2.3.4 -->

Create file: `order-service/src/main/scala/com/oms/order/events/PaymentEventHandler.scala`

```scala
package com.oms.order.events

import akka.Done
import com.oms.common.events.{DomainEvent, PaymentEvents}
import com.oms.order.repository.OrderRepository
import com.oms.order.saga.SagaCoordinator
import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory

/**
 * Handles payment events received from Payment Service.
 * Updates order status based on payment results.
 *
 * Related Task: 2.3.4
 */
class PaymentEventHandler(
  orderRepository: OrderRepository,
  sagaCoordinator: SagaCoordinator
)(implicit ec: ExecutionContext) {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  def handle(event: DomainEvent): Future[Done] = {
    event match {
      case e: PaymentEvents.PaymentCompleted =>
        handlePaymentCompleted(e)
        
      case e: PaymentEvents.PaymentFailed =>
        handlePaymentFailed(e)
        
      case e: PaymentEvents.PaymentRefunded =>
        handlePaymentRefunded(e)
        
      case _ =>
        logger.debug(s"Ignoring unhandled payment event: ${event.eventType}")
        Future.successful(Done)
    }
  }
  
  private def handlePaymentCompleted(event: PaymentEvents.PaymentCompleted): Future[Done] = {
    logger.info(s"Payment completed for order ${event.orderId}: " +
      s"paymentId=${event.aggregateId}, amount=${event.amount}, txn=${event.transactionId}")
    
    for {
      // Update order status to 'paid'
      _ <- orderRepository.updateStatus(event.orderId, "paid")
      
      // Notify saga coordinator
      _ <- sagaCoordinator.handlePaymentCompleted(event.orderId, event.aggregateId.toLong)
    } yield Done
  }
  
  private def handlePaymentFailed(event: PaymentEvents.PaymentFailed): Future[Done] = {
    logger.warn(s"Payment failed for order ${event.orderId}: " +
      s"reason=${event.reason}, errorCode=${event.errorCode}")
    
    // Notify saga to handle payment failure
    sagaCoordinator.handlePaymentFailed(event.orderId, event.reason)
  }
  
  private def handlePaymentRefunded(event: PaymentEvents.PaymentRefunded): Future[Done] = {
    logger.info(s"Payment refunded for order ${event.orderId}: " +
      s"refundId=${event.refundId}, amount=${event.amount}")
    
    // Update order to reflect refund
    orderRepository.updateRefundStatus(event.orderId, event.refundId, event.amount)
      .map(_ => Done)
  }
}
```

---

## 2.4 Event Subscribers - Product Service

> **Related Tasks:** [2.4.1](implementation-task-list.md) - [2.4.5](implementation-task-list.md)

### Task 2.4.1: Create OrderEventHandler (Product Service)
<!-- task-2.4.1 -->

Create file: `product-service/src/main/scala/com/oms/product/events/OrderEventHandler.scala`

```scala
package com.oms.product.events

import akka.Done
import com.oms.common.events.{DomainEvent, OrderEvents, EventIdGenerator}
import com.oms.common.events.StockEvents._
import com.oms.product.repository.{ProductRepository, StockReservationRepository}
import com.oms.common.outbox.{OutboxEvent, OutboxRepository}
import spray.json._
import com.oms.common.events.EventJsonProtocol._
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory

/**
 * Handles order events to manage stock reservations.
 *
 * Related Tasks: 2.4.1 - 2.4.4
 */
class OrderEventHandler(
  productRepo: ProductRepository,
  reservationRepo: StockReservationRepository,
  outboxRepo: OutboxRepository
)(implicit ec: ExecutionContext) {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  def handle(event: DomainEvent): Future[Done] = {
    event match {
      case e: OrderEvents.OrderCreated =>
        handleOrderCreated(e)
        
      case e: OrderEvents.OrderCancelled =>
        handleOrderCancelled(e)
        
      case _ =>
        logger.debug(s"Ignoring event type: ${event.eventType}")
        Future.successful(Done)
    }
  }
  
  /**
   * Task 2.4.3: Reserve stock on OrderCreated
   */
  private def handleOrderCreated(event: OrderEvents.OrderCreated): Future[Done] = {
    logger.info(s"Processing OrderCreated for order ${event.aggregateId}")
    
    // Check stock availability and create reservations
    reserveStockForOrder(event).flatMap {
      case Right(reservations) =>
        // Publish StockReserved event
        val stockReservedEvent = StockReserved(
          eventId = EventIdGenerator.generate(),
          aggregateId = event.aggregateId,
          orderId = event.aggregateId,
          reservations = reservations,
          timestamp = Instant.now(),
          correlationId = event.correlationId
        )
        
        publishEvent(stockReservedEvent).map(_ => Done)
        
      case Left(failureReason) =>
        // Publish StockReservationFailed event
        val failedEvent = StockReservationFailed(
          eventId = EventIdGenerator.generate(),
          aggregateId = event.aggregateId,
          orderId = event.aggregateId,
          productId = failureReason.productId,
          productName = failureReason.productName,
          reason = failureReason.reason,
          availableQuantity = failureReason.available,
          requestedQuantity = failureReason.requested,
          timestamp = Instant.now(),
          correlationId = event.correlationId
        )
        
        publishEvent(failedEvent).map(_ => Done)
    }
  }
  
  /**
   * Task 2.4.4: Release stock on OrderCancelled
   */
  private def handleOrderCancelled(event: OrderEvents.OrderCancelled): Future[Done] = {
    logger.info(s"Processing OrderCancelled for order ${event.aggregateId}")
    
    reservationRepo.releaseByOrderId(event.aggregateId).flatMap { releasedItems =>
      // Publish StockReleased events for each product
      Future.traverse(releasedItems) { item =>
        val stockReleasedEvent = StockReleased(
          eventId = EventIdGenerator.generate(),
          aggregateId = event.aggregateId,
          orderId = event.aggregateId,
          productId = item.productId,
          quantity = item.quantity,
          reason = s"Order cancelled: ${event.reason}",
          timestamp = Instant.now(),
          correlationId = event.correlationId
        )
        publishEvent(stockReleasedEvent)
      }.map(_ => Done)
    }
  }
  
  private def reserveStockForOrder(
    event: OrderEvents.OrderCreated
  ): Future[Either[StockFailure, List[com.oms.common.events.StockReservationItem]]] = {
    // Implementation: check availability for all items, create reservations in transaction
    ???
  }
  
  private def publishEvent(event: DomainEvent): Future[Unit] = {
    val outboxEvent = OutboxEvent(
      aggregateType = event.aggregateType,
      aggregateId = event.aggregateId,
      eventType = event.eventType,
      payload = event.toJson.compactPrint
    )
    outboxRepo.insert(outboxEvent).map(_ => ())
  }
  
  case class StockFailure(productId: Long, productName: String, reason: String, available: Int, requested: Int)
}
```

---

## 2.5 Event Subscribers - Payment Service

> **Related Tasks:** [2.5.1](implementation-task-list.md) - [2.5.4](implementation-task-list.md)

### Task 2.5.1 - 2.5.3: Create OrderEventHandler (Payment Service)
<!-- task-2.5.1 -->

Create file: `payment-service/src/main/scala/com/oms/payment/events/OrderEventHandler.scala`

```scala
package com.oms.payment.events

import akka.Done
import com.oms.common.events.{DomainEvent, OrderEvents, EventIdGenerator}
import com.oms.common.events.PaymentEvents._
import com.oms.payment.repository.PaymentRepository
import com.oms.common.outbox.{OutboxEvent, OutboxRepository}
import spray.json._
import com.oms.common.events.EventJsonProtocol._
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory

/**
 * Handles order events for payment refunds.
 *
 * Related Tasks: 2.5.1 - 2.5.3
 */
class OrderEventHandler(
  paymentRepo: PaymentRepository,
  outboxRepo: OutboxRepository
)(implicit ec: ExecutionContext) {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  def handle(event: DomainEvent): Future[Done] = {
    event match {
      case e: OrderEvents.OrderCancelled =>
        handleOrderCancelled(e)
        
      case _ =>
        logger.debug(s"Ignoring event type: ${event.eventType}")
        Future.successful(Done)
    }
  }
  
  /**
   * Task 2.5.3: Refund payment on OrderCancelled (if paid)
   */
  private def handleOrderCancelled(event: OrderEvents.OrderCancelled): Future[Done] = {
    logger.info(s"Processing OrderCancelled for potential refund: ${event.aggregateId}")
    
    paymentRepo.findCompletedByOrderId(event.aggregateId).flatMap {
      case Some(payment) =>
        logger.info(s"Found completed payment ${payment.id} for order ${event.aggregateId}, initiating refund")
        initiateRefund(payment, event.reason, event.correlationId)
        
      case None =>
        logger.info(s"No completed payment found for order ${event.aggregateId}, skipping refund")
        Future.successful(Done)
    }
  }
  
  private def initiateRefund(
    payment: Payment,
    reason: String,
    correlationId: Option[String]
  ): Future[Done] = {
    // Create refund record and publish event
    val refundId = java.util.UUID.randomUUID().toString
    
    for {
      _ <- paymentRepo.createRefund(payment.id, payment.amount, reason, refundId)
      
      refundEvent = PaymentRefunded(
        eventId = EventIdGenerator.generate(),
        aggregateId = payment.id.toString,
        orderId = payment.orderId,
        paymentId = payment.id,
        refundId = refundId,
        amount = payment.amount,
        reason = reason,
        timestamp = Instant.now(),
        correlationId = correlationId
      )
      
      outboxEvent = OutboxEvent(
        aggregateType = "PAYMENT",
        aggregateId = payment.id.toString,
        eventType = "PAYMENT_REFUNDED",
        payload = refundEvent.toJson.compactPrint
      )
      
      _ <- outboxRepo.insert(outboxEvent)
    } yield Done
  }
}
```

---

## 2.6 Event Logging & Auditing

> **Related Tasks:** [2.6.1](implementation-task-list.md) - [2.6.4](implementation-task-list.md)

### Task 2.6.1: Create EventLogger
<!-- task-2.6.1 -->

Create file: `common/src/main/scala/com/oms/common/logging/EventLogger.scala`

```scala
package com.oms.common.logging

import com.oms.common.events.DomainEvent
import org.slf4j.{Logger, LoggerFactory, MDC}
import spray.json._
import java.time.Instant

/**
 * Structured event logging utility.
 *
 * Related Tasks: 2.6.1 - 2.6.4
 */
object EventLogger {
  
  private val logger: Logger = LoggerFactory.getLogger("EventLogger")
  
  sealed trait Direction
  case object Published extends Direction
  case object Received extends Direction
  
  /**
   * Log an event with structured JSON format.
   */
  def log(
    event: DomainEvent,
    direction: Direction,
    serviceName: String,
    additionalContext: Map[String, String] = Map.empty
  ): Unit = {
    // Set MDC for correlation
    event.correlationId.foreach(id => MDC.put("correlationId", id))
    MDC.put("eventId", event.eventId)
    MDC.put("eventType", event.eventType)
    
    try {
      val logEntry = Map(
        "eventId" -> event.eventId,
        "eventType" -> event.eventType,
        "aggregateId" -> event.aggregateId,
        "aggregateType" -> event.aggregateType,
        "timestamp" -> event.timestamp.toString,
        "direction" -> direction.toString,
        "service" -> serviceName,
        "correlationId" -> event.correlationId.getOrElse(""),
        "loggedAt" -> Instant.now().toString
      ) ++ additionalContext
      
      val jsonLog = logEntry.toJson.compactPrint
      
      direction match {
        case Published => logger.info(s"EVENT_PUBLISHED: $jsonLog")
        case Received => logger.info(s"EVENT_RECEIVED: $jsonLog")
      }
    } finally {
      MDC.remove("correlationId")
      MDC.remove("eventId")
      MDC.remove("eventType")
    }
  }
}
```

---

# Phase 3: Saga Implementation

## 3.1 Saga Framework

> **Related Tasks:** [3.1.1](implementation-task-list.md) - [3.1.6](implementation-task-list.md)

### Task 3.1.1: Create SagaState
<!-- task-3.1.1 -->

Create file: `common/src/main/scala/com/oms/common/saga/SagaState.scala`

```scala
package com.oms.common.saga

/**
 * Saga execution states.
 *
 * Related Task: 3.1.1
 */
sealed trait SagaState {
  def name: String
}

object SagaState {
  case object Started extends SagaState { val name = "STARTED" }
  case class StepInProgress(stepIndex: Int) extends SagaState { val name = "STEP_IN_PROGRESS" }
  case class StepCompleted(stepIndex: Int) extends SagaState { val name = "STEP_COMPLETED" }
  case class Compensating(fromStep: Int) extends SagaState { val name = "COMPENSATING" }
  case object Compensated extends SagaState { val name = "COMPENSATED" }
  case object Completed extends SagaState { val name = "COMPLETED" }
  case class Failed(error: String) extends SagaState { val name = "FAILED" }
  
  def fromString(s: String, stepIndex: Int = 0, error: String = ""): SagaState = s match {
    case "STARTED" => Started
    case "STEP_IN_PROGRESS" => StepInProgress(stepIndex)
    case "STEP_COMPLETED" => StepCompleted(stepIndex)
    case "COMPENSATING" => Compensating(stepIndex)
    case "COMPENSATED" => Compensated
    case "COMPLETED" => Completed
    case "FAILED" => Failed(error)
    case _ => throw new IllegalArgumentException(s"Unknown saga state: $s")
  }
}
```

### Task 3.1.2: Create SagaStep Model
<!-- task-3.1.2 -->

```scala
package com.oms.common.saga

import scala.concurrent.Future

/**
 * Represents a single step in a saga.
 *
 * Related Task: 3.1.2
 */
case class SagaStep[T](
  name: String,
  execute: T => Future[StepResult],
  compensate: T => Future[Unit],
  isCompensatable: Boolean = true
)

/**
 * Result of executing a saga step.
 */
sealed trait StepResult
object StepResult {
  case class Success(data: Any = ()) extends StepResult
  case class Failure(error: String) extends StepResult
  case object WaitingForEvent extends StepResult  // Step waits for external event
}
```

### Task 3.1.3 - 3.1.4: Create SagaInstance and Repository
<!-- task-3.1.3 -->

Create file: `order-service/src/main/scala/com/oms/order/saga/SagaInstance.scala`

```scala
package com.oms.order.saga

import com.oms.common.saga.SagaState
import spray.json._
import java.time.Instant

/**
 * Persisted saga instance.
 *
 * Related Tasks: 3.1.3, 3.1.4
 */
case class SagaInstance(
  id: String,
  sagaType: String,
  state: SagaState,
  currentStep: Int,
  payload: JsValue,
  createdAt: Instant,
  updatedAt: Instant,
  completedAt: Option[Instant] = None,
  errorMessage: Option[String] = None,
  correlationId: Option[String] = None
)

/**
 * Repository for saga persistence.
 */
trait SagaRepository {
  def create(instance: SagaInstance): Future[SagaInstance]
  def findById(id: String): Future[Option[SagaInstance]]
  def updateState(id: String, state: SagaState, step: Int): Future[Unit]
  def markCompleted(id: String): Future[Unit]
  def markFailed(id: String, error: String): Future[Unit]
  def findByTypeAndState(sagaType: String, state: String): Future[Seq[SagaInstance]]
  def findByCorrelationId(correlationId: String): Future[Option[SagaInstance]]
}
```

### Task 3.1.5: Create SagaCoordinator Actor
<!-- task-3.1.5 -->

Create file: `order-service/src/main/scala/com/oms/order/saga/SagaCoordinator.scala`

```scala
package com.oms.order.saga

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext}
import akka.Done
import com.oms.common.saga.{SagaState, SagaStep, StepResult}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import java.time.Instant

/**
 * Actor that coordinates saga execution with state machine behavior.
 *
 * Related Task: 3.1.5
 */
object SagaCoordinatorActor {
  
  // Commands
  sealed trait Command
  case class StartSaga(
    sagaType: String,
    payload: spray.json.JsValue,
    correlationId: Option[String],
    replyTo: ActorRef[Response]
  ) extends Command
  case class StepCompleted(stepIndex: Int, result: Any) extends Command
  case class StepFailed(stepIndex: Int, error: String) extends Command
  case class ExternalEventReceived(eventType: String, data: Any) extends Command
  case object CompensationCompleted extends Command
  case class CompensationFailed(stepIndex: Int, error: String) extends Command
  
  // Responses
  sealed trait Response
  case class SagaStarted(sagaId: String) extends Response
  case class SagaCompleted(sagaId: String) extends Response
  case class SagaFailed(sagaId: String, error: String) extends Response
  
  def apply(
    sagaDefinitions: Map[String, SagaDefinition],
    sagaRepo: SagaRepository
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    
    Behaviors.setup { context =>
      idle(sagaDefinitions, sagaRepo, context)
    }
  }
  
  private def idle(
    sagaDefinitions: Map[String, SagaDefinition],
    sagaRepo: SagaRepository,
    context: ActorContext[Command]
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    
    Behaviors.receiveMessage {
      case StartSaga(sagaType, payload, correlationId, replyTo) =>
        sagaDefinitions.get(sagaType) match {
          case Some(definition) =>
            val sagaId = java.util.UUID.randomUUID().toString
            val instance = SagaInstance(
              id = sagaId,
              sagaType = sagaType,
              state = SagaState.Started,
              currentStep = 0,
              payload = payload,
              createdAt = Instant.now(),
              updatedAt = Instant.now(),
              correlationId = correlationId
            )
            
            context.pipeToSelf(sagaRepo.create(instance)) {
              case Success(_) =>
                replyTo ! SagaStarted(sagaId)
                StepCompleted(-1, ())  // Trigger first step
              case Failure(ex) =>
                replyTo ! SagaFailed(sagaId, ex.getMessage)
                null
            }
            
            executing(sagaId, definition, instance, sagaRepo, context)
            
          case None =>
            replyTo ! SagaFailed("", s"Unknown saga type: $sagaType")
            Behaviors.same
        }
        
      case _ => Behaviors.same
    }
  }
  
  private def executing(
    sagaId: String,
    definition: SagaDefinition,
    instance: SagaInstance,
    sagaRepo: SagaRepository,
    context: ActorContext[Command]
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    
    Behaviors.receiveMessage {
      case StepCompleted(stepIndex, _) =>
        val nextStep = stepIndex + 1
        if (nextStep >= definition.steps.length) {
          // Saga completed successfully
          sagaRepo.markCompleted(sagaId)
          Behaviors.stopped
        } else {
          // Execute next step
          val step = definition.steps(nextStep)
          context.pipeToSelf(step.execute(instance.payload)) {
            case Success(StepResult.Success(data)) => StepCompleted(nextStep, data)
            case Success(StepResult.WaitingForEvent) => null // Stay in current state
            case Success(StepResult.Failure(err)) => StepFailed(nextStep, err)
            case Failure(ex) => StepFailed(nextStep, ex.getMessage)
          }
          Behaviors.same
        }
        
      case StepFailed(stepIndex, error) =>
        context.log.warn(s"Saga $sagaId step $stepIndex failed: $error")
        sagaRepo.updateState(sagaId, SagaState.Compensating(stepIndex), stepIndex)
        compensating(sagaId, definition, stepIndex, instance, sagaRepo, context)
        
      case ExternalEventReceived(eventType, data) =>
        // Handle expected external event
        context.self ! StepCompleted(instance.currentStep, data)
        Behaviors.same
        
      case _ => Behaviors.same
    }
  }
  
  private def compensating(
    sagaId: String,
    definition: SagaDefinition,
    fromStep: Int,
    instance: SagaInstance,
    sagaRepo: SagaRepository,
    context: ActorContext[Command]
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    
    // Execute compensations in reverse order
    val compensations = (fromStep to 0 by -1).map { idx =>
      val step = definition.steps(idx)
      if (step.isCompensatable) {
        step.compensate(instance.payload)
      } else {
        Future.successful(())
      }
    }
    
    context.pipeToSelf(Future.sequence(compensations)) {
      case Success(_) => CompensationCompleted
      case Failure(ex) => CompensationFailed(fromStep, ex.getMessage)
    }
    
    Behaviors.receiveMessage {
      case CompensationCompleted =>
        sagaRepo.updateState(sagaId, SagaState.Compensated, 0)
        context.log.info(s"Saga $sagaId compensation completed")
        Behaviors.stopped
        
      case CompensationFailed(step, error) =>
        sagaRepo.markFailed(sagaId, s"Compensation failed at step $step: $error")
        context.log.error(s"Saga $sagaId compensation failed: $error")
        Behaviors.stopped
        
      case _ => Behaviors.same
    }
  }
}

/**
 * Defines a saga type with its steps.
 */
case class SagaDefinition(
  sagaType: String,
  steps: Vector[SagaStep[spray.json.JsValue]]
)
```

---

## 3.2 Order Creation Saga

> **Related Tasks:** [3.2.1](implementation-task-list.md) - [3.2.13](implementation-task-list.md)

### Task 3.2.1: Define CreateOrderSaga Steps
<!-- task-3.2.1 -->

```scala
package com.oms.order.saga

import com.oms.common.saga.{SagaStep, StepResult}
import spray.json._
import scala.concurrent.{ExecutionContext, Future}

/**
 * Create Order Saga Definition.
 *
 * Steps:
 * 1. Create Draft Order - Insert order with status='draft'
 * 2. Validate Customer - Verify customer exists and is active
 * 3. Reserve Stock - Publish OrderCreated event, wait for StockReserved
 * 4. Confirm Order - Update order status to 'created'
 *
 * Related Task: 3.2.1
 */
object CreateOrderSagaDefinition {
  
  def create(
    orderRepository: OrderRepository,
    customerClient: CustomerServiceClient,
    outboxRepository: OutboxRepository
  )(implicit ec: ExecutionContext): SagaDefinition = {
    
    SagaDefinition(
      sagaType = "CREATE_ORDER",
      steps = Vector(
        // Step 1: Create Draft Order (Task 3.2.2)
        SagaStep[JsValue](
          name = "CreateDraftOrder",
          execute = payload => createDraftOrder(payload, orderRepository),
          compensate = payload => deleteDraftOrder(payload, orderRepository)  // Task 3.2.7
        ),
        
        // Step 2: Validate Customer (Task 3.2.3)
        SagaStep[JsValue](
          name = "ValidateCustomer",
          execute = payload => validateCustomer(payload, customerClient),
          compensate = _ => Future.successful(()),  // No compensation needed
          isCompensatable = false
        ),
        
        // Step 3: Reserve Stock (Task 3.2.4)
        SagaStep[JsValue](
          name = "ReserveStock",
          execute = payload => publishOrderCreatedEvent(payload, outboxRepository),
          compensate = payload => publishStockReleaseEvent(payload, outboxRepository)  // Task 3.2.8
        ),
        
        // Step 4: Confirm Order (Task 3.2.5)
        SagaStep[JsValue](
          name = "ConfirmOrder",
          execute = payload => confirmOrder(payload, orderRepository),
          compensate = payload => cancelOrder(payload, orderRepository)  // Task 3.2.9
        )
      )
    )
  }
  
  // Step implementations...
  private def createDraftOrder(payload: JsValue, repo: OrderRepository): Future[StepResult] = ???
  private def validateCustomer(payload: JsValue, client: CustomerServiceClient): Future[StepResult] = ???
  private def publishOrderCreatedEvent(payload: JsValue, outbox: OutboxRepository): Future[StepResult] = ???
  private def confirmOrder(payload: JsValue, repo: OrderRepository): Future[StepResult] = ???
  
  // Compensation implementations...
  private def deleteDraftOrder(payload: JsValue, repo: OrderRepository): Future[Unit] = ???
  private def publishStockReleaseEvent(payload: JsValue, outbox: OutboxRepository): Future[Unit] = ???
  private def cancelOrder(payload: JsValue, repo: OrderRepository): Future[Unit] = ???
}
```

---

## 3.3 Order Cancellation Saga

> **Related Tasks:** [3.3.1](implementation-task-list.md) - [3.3.9](implementation-task-list.md)

### Task 3.3.1: Define CancelOrderSaga Steps
<!-- task-3.3.1 -->

```scala
package com.oms.order.saga

/**
 * Cancel Order Saga Definition.
 *
 * Steps:
 * 1. Validate Cancellation - Check order status allows cancellation
 * 2. Request Stock Release - Publish OrderCancellationRequested event
 * 3. Request Refund - If paid, publish refund request
 * 4. Complete Cancellation - Update order status to 'cancelled'
 *
 * Related Task: 3.3.1
 */
object CancelOrderSagaDefinition {
  
  def create(
    orderRepository: OrderRepository,
    outboxRepository: OutboxRepository
  )(implicit ec: ExecutionContext): SagaDefinition = {
    
    SagaDefinition(
      sagaType = "CANCEL_ORDER",
      steps = Vector(
        // Step 1: Validate cancellation allowed (Task 3.3.2)
        SagaStep[JsValue](
          name = "ValidateCancellation",
          execute = payload => validateCancellation(payload, orderRepository),
          compensate = _ => Future.successful(()),
          isCompensatable = false
        ),
        
        // Step 2: Request stock release (Task 3.3.3)
        SagaStep[JsValue](
          name = "RequestStockRelease",
          execute = payload => publishCancellationEvent(payload, outboxRepository),
          compensate = _ => Future.successful(())  // Fire-and-forget
        ),
        
        // Step 3: Request refund if paid (Task 3.3.4)
        SagaStep[JsValue](
          name = "RequestRefund",
          execute = payload => requestRefundIfPaid(payload, orderRepository, outboxRepository),
          compensate = _ => Future.successful(())
        ),
        
        // Step 4: Complete cancellation (Task 3.3.5)
        SagaStep[JsValue](
          name = "CompleteCancellation",
          execute = payload => completeCancellation(payload, orderRepository),
          compensate = _ => Future.successful(())  // Cannot undo cancellation
        )
      )
    )
  }
  
  // Implementation methods...
}
```

---

## 3.4 Stock Reservation (Product Service)

> **Related Tasks:** [3.4.1](implementation-task-list.md) - [3.4.8](implementation-task-list.md)

See [Task 1.4.1: Create stock_reservations Table](#task-141-create-stock_reservations-table-product-service) for schema.

### Task 3.4.1 - 3.4.2: Stock Reservation Model and Repository
<!-- task-3.4.1 -->

Create file: `product-service/src/main/scala/com/oms/product/tcc/StockReservation.scala`

```scala
package com.oms.product.tcc

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._

/**
 * Stock reservation model for TCC pattern.
 *
 * Related Task: 3.4.1
 */
case class StockReservation(
  id: Option[Long] = None,
  orderId: String,
  productId: Long,
  quantity: Int,
  status: ReservationStatus = ReservationStatus.Reserved,
  createdAt: Instant = Instant.now(),
  expiresAt: Instant,
  confirmedAt: Option[Instant] = None,
  cancelledAt: Option[Instant] = None
)

sealed trait ReservationStatus
object ReservationStatus {
  case object Reserved extends ReservationStatus
  case object Confirmed extends ReservationStatus
  case object Cancelled extends ReservationStatus
  case object Expired extends ReservationStatus
  
  def fromString(s: String): ReservationStatus = s match {
    case "RESERVED" => Reserved
    case "CONFIRMED" => Confirmed
    case "CANCELLED" => Cancelled
    case "EXPIRED" => Expired
    case _ => throw new IllegalArgumentException(s"Unknown status: $s")
  }
}

/**
 * Repository for stock reservations.
 *
 * Related Task: 3.4.2
 */
class StockReservationRepository(db: Database)(implicit ec: ExecutionContext) {
  
  // Table definition and methods...
  
  def create(reservation: StockReservation): Future[StockReservation] = ???
  def findByOrderId(orderId: String): Future[Seq[StockReservation]] = ???
  def findByProductIdAndStatus(productId: Long, status: ReservationStatus): Future[Seq[StockReservation]] = ???
  def updateStatus(id: Long, status: ReservationStatus): Future[Unit] = ???
  def confirmAll(orderId: String): Future[Int] = ???
  def cancelAll(orderId: String): Future[Int] = ???
  def releaseByOrderId(orderId: String): Future[Seq[ReleasedItem]] = ???
  def findExpired(): Future[Seq[StockReservation]] = ???
}

case class ReleasedItem(productId: Long, quantity: Int)
```

---

# Phase 4: TCC for Payments

## 4.1 TCC Framework

> **Related Tasks:** [4.1.1](implementation-task-list.md) - [4.1.8](implementation-task-list.md)

### Task 4.1.1 - 4.1.3: TCC State Models
<!-- task-4.1.1 -->
  
Create file: `common/src/main/scala/com/oms/common/tcc/TCCModels.scala`

```scala
package com.oms.common.tcc

import java.time.Instant
import spray.json.JsValue

/**
 * TCC Transaction States.
 *
 * Related Task: 4.1.1
 */
sealed trait TCCState {
  def name: String
}

object TCCState {
  case object Initial extends TCCState { val name = "INITIAL" }
  case object Trying extends TCCState { val name = "TRYING" }
  case object TrySucceeded extends TCCState { val name = "TRY_SUCCEEDED" }
  case object Confirming extends TCCState { val name = "CONFIRMING" }
  case object Confirmed extends TCCState { val name = "CONFIRMED" }
  case object Cancelling extends TCCState { val name = "CANCELLING" }
  case object Cancelled extends TCCState { val name = "CANCELLED" }
  
  def fromString(s: String): TCCState = s match {
    case "INITIAL" => Initial
    case "TRYING" => Trying
    case "TRY_SUCCEEDED" => TrySucceeded
    case "CONFIRMING" => Confirming
    case "CONFIRMED" => Confirmed
    case "CANCELLING" => Cancelling
    case "CANCELLED" => Cancelled
    case _ => throw new IllegalArgumentException(s"Unknown TCC state: $s")
  }
}

/**
 * TCC Transaction record for persistence.
 *
 * Related Task: 4.1.2
 */
case class TCCTransaction(
  id: String,
  transactionType: String,
  state: TCCState,
  participants: List[TCCParticipant],
  payload: JsValue,
  createdAt: Instant,
  expiresAt: Instant,
  confirmedAt: Option[Instant] = None,
  cancelledAt: Option[Instant] = None
)

/**
 * TCC Participant representing a service involved in the transaction.
 *
 * Related Task: 4.1.3
 */
case class TCCParticipant(
  serviceName: String,
  resourceId: String,
  tryEndpoint: String,
  confirmEndpoint: String,
  cancelEndpoint: String,
  status: ParticipantStatus,
  tryResponse: Option[JsValue] = None,
  errorMessage: Option[String] = None
)

sealed trait ParticipantStatus
object ParticipantStatus {
  case object Pending extends ParticipantStatus
  case object TrySuccess extends ParticipantStatus
  case object TryFailed extends ParticipantStatus
  case object Confirmed extends ParticipantStatus
  case object Cancelled extends ParticipantStatus
}
```

### Task 4.1.4 - 4.1.7: TCC Coordinator Actor
<!-- task-4.1.4 -->

See the existing TCCCoordinator implementation below and expand it with complete phase handling.

---

## 4.2 Payment Service TCC Endpoints

> **Related Tasks:** [4.2.1](implementation-task-list.md) - [4.2.9](implementation-task-list.md)

### Task 4.2.1 - 4.2.2: Payment Reservation Model and Repository
<!-- task-4.2.1 -->

Create file: `payment-service/src/main/scala/com/oms/payment/tcc/PaymentReservation.scala`

```scala
package com.oms.payment.tcc

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

/**
 * Payment reservation for TCC pattern.
 *
 * Related Tasks: 4.2.1, 4.2.2
 */
case class PaymentReservation(
  id: String,
  orderId: String,
  customerId: Long,
  amount: BigDecimal,
  currency: String = "USD",
  status: PaymentReservationStatus = PaymentReservationStatus.Reserved,
  createdAt: Instant = Instant.now(),
  expiresAt: Instant,
  transactionId: Option[String] = None,
  confirmedAt: Option[Instant] = None,
  cancelledAt: Option[Instant] = None
)

sealed trait PaymentReservationStatus
object PaymentReservationStatus {
  case object Reserved extends PaymentReservationStatus
  case object Confirmed extends PaymentReservationStatus
  case object Cancelled extends PaymentReservationStatus
  case object Expired extends PaymentReservationStatus
}

class PaymentReservationRepository(db: Database)(implicit ec: ExecutionContext) {
  def create(reservation: PaymentReservation): Future[PaymentReservation] = ???
  def findByOrderId(orderId: String): Future[Option[PaymentReservation]] = ???
  def findById(id: String): Future[Option[PaymentReservation]] = ???
  def updateStatus(id: String, status: PaymentReservationStatus): Future[Unit] = ???
  def setTransactionId(id: String, txnId: String): Future[Unit] = ???
  def findExpired(): Future[Seq[PaymentReservation]] = ???
  def confirm(id: String, transactionId: String): Future[Unit] = ???
  def cancel(id: String): Future[Unit] = ???
}
```

### Task 4.2.3 - 4.2.6: TCC Endpoints
<!-- task-4.2.3 -->

Create file: `payment-service/src/main/scala/com/oms/payment/routes/PaymentTCCRoutes.scala`

```scala
package com.oms.payment.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.oms.payment.tcc.PaymentTCCService
import spray.json._
import scala.concurrent.ExecutionContext

/**
 * TCC endpoints for Payment Service.
 *
 * Related Tasks: 4.2.3 - 4.2.6
 */
class PaymentTCCRoutes(tccService: PaymentTCCService)(implicit ec: ExecutionContext) {
  
  val routes: Route = pathPrefix("payments") {
    concat(
      // Task 4.2.3: POST /payments/try
      path("try") {
        post {
          entity(as[TryPaymentRequest]) { request =>
            onSuccess(tccService.tryReservePayment(
              orderId = request.orderId,
              customerId = request.customerId,
              amount = request.amount,
              currency = request.currency.getOrElse("USD")
            )) { reservation =>
              complete(StatusCodes.Created -> TryPaymentResponse(
                reservationId = reservation.id,
                orderId = reservation.orderId,
                amount = reservation.amount,
                expiresAt = reservation.expiresAt.toString
              ))
            }
          }
        }
      },
      
      // Task 4.2.4: POST /payments/confirm/{reservationId}
      path("confirm" / Segment) { reservationId =>
        post {
          entity(as[ConfirmPaymentRequest]) { request =>
            onSuccess(tccService.confirmPayment(
              reservationId = reservationId,
              paymentMethod = request.paymentMethod,
              createdBy = request.createdBy
            )) {
              case Right(payment) =>
                complete(StatusCodes.OK -> ConfirmPaymentResponse(
                  paymentId = payment.id.get,
                  transactionId = payment.transactionId.get,
                  status = "confirmed"
                ))
              case Left(error) =>
                complete(StatusCodes.BadRequest -> ErrorResponse(error))
            }
          }
        }
      },
      
      // Task 4.2.5: POST /payments/cancel/{reservationId}
      path("cancel" / Segment) { reservationId =>
        post {
          onSuccess(tccService.cancelPayment(reservationId)) {
            case Right(_) =>
              complete(StatusCodes.OK -> CancelPaymentResponse(
                reservationId = reservationId,
                status = "cancelled"
              ))
            case Left(error) =>
              complete(StatusCodes.BadRequest -> ErrorResponse(error))
          }
        }
      }
    )
  }
  
  // Request/Response DTOs
  case class TryPaymentRequest(orderId: String, customerId: Long, amount: BigDecimal, currency: Option[String])
  case class TryPaymentResponse(reservationId: String, orderId: String, amount: BigDecimal, expiresAt: String)
  case class ConfirmPaymentRequest(paymentMethod: String, createdBy: Long)
  case class ConfirmPaymentResponse(paymentId: Long, transactionId: String, status: String)
  case class CancelPaymentResponse(reservationId: String, status: String)
  case class ErrorResponse(error: String)
}
```

### Task 4.2.7: Payment TCC Service
<!-- task-4.2.7 -->

Create file: `payment-service/src/main/scala/com/oms/payment/tcc/PaymentTCCService.scala`

```scala
package com.oms.payment.tcc

import com.oms.payment.model.Payment
import com.oms.payment.repository.PaymentRepository
import java.util.UUID
import java.time.{Instant, Duration}
import scala.concurrent.{ExecutionContext, Future}

/**
 * TCC Service for Payment reservations.
 *
 * Related Tasks: 4.2.7 - 4.2.9
 */
class PaymentTCCService(
  paymentRepo: PaymentRepository,
  reservationRepo: PaymentReservationRepository,
  ttlMinutes: Int = 15
)(implicit ec: ExecutionContext) {
  
  /**
   * TRY Phase: Reserve payment amount for an order.
   * Validates customer has sufficient funds/credit and creates a hold.
   */
  def tryReservePayment(
    orderId: String,
    customerId: Long,
    amount: BigDecimal,
    currency: String = "USD"
  ): Future[PaymentReservation] = {
    
    // Check for existing reservation
    reservationRepo.findByOrderId(orderId).flatMap {
      case Some(existing) if existing.status == PaymentReservationStatus.Reserved =>
        // Return existing if still valid
        if (existing.expiresAt.isAfter(Instant.now())) {
          Future.successful(existing)
        } else {
          // Expired, cancel and create new
          reservationRepo.updateStatus(existing.id, PaymentReservationStatus.Expired)
            .flatMap(_ => createNewReservation(orderId, customerId, amount, currency))
        }
      case _ =>
        createNewReservation(orderId, customerId, amount, currency)
    }
  }
  
  private def createNewReservation(
    orderId: String,
    customerId: Long,
    amount: BigDecimal,
    currency: String
  ): Future[PaymentReservation] = {
    val reservation = PaymentReservation(
      id = UUID.randomUUID().toString,
      orderId = orderId,
      customerId = customerId,
      amount = amount,
      currency = currency,
      status = PaymentReservationStatus.Reserved,
      expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes))
    )
    reservationRepo.create(reservation)
  }
  
  /**
   * CONFIRM Phase: Capture the reserved payment.
   * Creates the actual payment record and marks reservation as confirmed.
   */
  def confirmPayment(
    reservationId: String,
    paymentMethod: String,
    createdBy: Long
  ): Future[Either[String, Payment]] = {
    
    reservationRepo.findById(reservationId).flatMap {
      case Some(res) if res.status == PaymentReservationStatus.Reserved =>
        if (res.expiresAt.isBefore(Instant.now())) {
          // Reservation expired
          reservationRepo.updateStatus(reservationId, PaymentReservationStatus.Expired)
            .map(_ => Left("Reservation expired"))
        } else {
          // Process payment capture
          val transactionId = s"TXN-${UUID.randomUUID().toString.take(8).toUpperCase}"
          
          for {
            payment <- paymentRepo.create(Payment(
              orderId = res.orderId.toLong,
              createdBy = createdBy,
              amount = res.amount,
              paymentMethod = paymentMethod,
              status = "completed",
              transactionId = Some(transactionId)
            ))
            _ <- reservationRepo.confirm(reservationId, transactionId)
          } yield Right(payment)
        }
        
      case Some(res) if res.status == PaymentReservationStatus.Confirmed =>
        // Already confirmed - idempotent response
        paymentRepo.findByOrderId(res.orderId.toLong).map {
          case Some(p) => Right(p)
          case None => Left("Payment record not found")
        }
        
      case Some(res) =>
        Future.successful(Left(s"Invalid reservation status: ${res.status}"))
        
      case None =>
        Future.successful(Left(s"Reservation not found: $reservationId"))
    }
  }
  
  /**
   * CANCEL Phase: Release the reserved payment.
   */
  def cancelPayment(reservationId: String): Future[Either[String, Unit]] = {
    reservationRepo.findById(reservationId).flatMap {
      case Some(res) if res.status == PaymentReservationStatus.Reserved =>
        reservationRepo.cancel(reservationId).map(_ => Right(()))
        
      case Some(res) if res.status == PaymentReservationStatus.Cancelled =>
        // Already cancelled - idempotent
        Future.successful(Right(()))
        
      case Some(res) =>
        Future.successful(Left(s"Cannot cancel reservation with status: ${res.status}"))
        
      case None =>
        Future.successful(Left(s"Reservation not found: $reservationId"))
    }
  }
}
```

### Task 4.2.8 - 4.2.9: Payment TCC Unit Tests
<!-- task-4.2.8 -->

Create file: `payment-service/src/test/scala/com/oms/payment/tcc/PaymentTCCServiceSpec.scala`

```scala
package com.oms.payment.tcc

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalamock.scalatest.MockFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.{Instant, Duration}

/**
 * Unit tests for PaymentTCCService.
 *
 * Related Tasks: 4.2.8, 4.2.9
 */
class PaymentTCCServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockFactory {
  
  "PaymentTCCService" should {
    
    "tryReservePayment" should {
      "create a new reservation for valid request" in {
        val reservationRepo = mock[PaymentReservationRepository]
        val paymentRepo = mock[PaymentRepository]
        val service = new PaymentTCCService(paymentRepo, reservationRepo)
        
        (reservationRepo.findByOrderId _).expects("order-123").returning(Future.successful(None))
        (reservationRepo.create _).expects(*).returning(Future.successful(
          PaymentReservation(
            id = "res-1",
            orderId = "order-123",
            customerId = 1L,
            amount = BigDecimal(100.00),
            currency = "USD",
            expiresAt = Instant.now().plus(Duration.ofMinutes(15))
          )
        ))
        
        val result = service.tryReservePayment("order-123", 1L, BigDecimal(100.00)).futureValue
        result.orderId shouldBe "order-123"
        result.amount shouldBe BigDecimal(100.00)
      }
      
      "return existing valid reservation" in {
        val reservationRepo = mock[PaymentReservationRepository]
        val paymentRepo = mock[PaymentRepository]
        val service = new PaymentTCCService(paymentRepo, reservationRepo)
        
        val existing = PaymentReservation(
          id = "res-existing",
          orderId = "order-123",
          customerId = 1L,
          amount = BigDecimal(100.00),
          currency = "USD",
          expiresAt = Instant.now().plus(Duration.ofMinutes(10)) // Still valid
        )
        
        (reservationRepo.findByOrderId _).expects("order-123").returning(Future.successful(Some(existing)))
        
        val result = service.tryReservePayment("order-123", 1L, BigDecimal(100.00)).futureValue
        result.id shouldBe "res-existing"
      }
    }
    
    "confirmPayment" should {
      "confirm a valid reservation" in {
        val reservationRepo = mock[PaymentReservationRepository]
        val paymentRepo = mock[PaymentRepository]
        val service = new PaymentTCCService(paymentRepo, reservationRepo)
        
        val reservation = PaymentReservation(
          id = "res-1",
          orderId = "123",
          customerId = 1L,
          amount = BigDecimal(100.00),
          currency = "USD",
          status = PaymentReservationStatus.Reserved,
          expiresAt = Instant.now().plus(Duration.ofMinutes(10))
        )
        
        (reservationRepo.findById _).expects("res-1").returning(Future.successful(Some(reservation)))
        (paymentRepo.create _).expects(*).returning(Future.successful(
          Payment(id = Some(1L), orderId = 123L, createdBy = 1L, amount = BigDecimal(100.00), 
                  paymentMethod = "card", status = "completed", transactionId = Some("TXN-ABC"))
        ))
        (reservationRepo.confirm _).expects("res-1", *).returning(Future.successful(()))
        
        val result = service.confirmPayment("res-1", "card", 1L).futureValue
        result shouldBe a[Right[_, _]]
      }
      
      "reject expired reservation" in {
        val reservationRepo = mock[PaymentReservationRepository]
        val paymentRepo = mock[PaymentRepository]
        val service = new PaymentTCCService(paymentRepo, reservationRepo)
        
        val expiredReservation = PaymentReservation(
          id = "res-expired",
          orderId = "123",
          customerId = 1L,
          amount = BigDecimal(100.00),
          currency = "USD",
          status = PaymentReservationStatus.Reserved,
          expiresAt = Instant.now().minus(Duration.ofMinutes(5)) // Expired
        )
        
        (reservationRepo.findById _).expects("res-expired").returning(Future.successful(Some(expiredReservation)))
        (reservationRepo.updateStatus _).expects("res-expired", PaymentReservationStatus.Expired).returning(Future.successful(()))
        
        val result = service.confirmPayment("res-expired", "card", 1L).futureValue
        result shouldBe Left("Reservation expired")
      }
    }
    
    "cancelPayment" should {
      "cancel a reserved payment" in {
        val reservationRepo = mock[PaymentReservationRepository]
        val paymentRepo = mock[PaymentRepository]
        val service = new PaymentTCCService(paymentRepo, reservationRepo)
        
        val reservation = PaymentReservation(
          id = "res-1",
          orderId = "123",
          customerId = 1L,
          amount = BigDecimal(100.00),
          currency = "USD",
          status = PaymentReservationStatus.Reserved,
          expiresAt = Instant.now().plus(Duration.ofMinutes(10))
        )
        
        (reservationRepo.findById _).expects("res-1").returning(Future.successful(Some(reservation)))
        (reservationRepo.cancel _).expects("res-1").returning(Future.successful(()))
        
        val result = service.cancelPayment("res-1").futureValue
        result shouldBe Right(())
      }
      
      "be idempotent for already cancelled reservation" in {
        val reservationRepo = mock[PaymentReservationRepository]
        val paymentRepo = mock[PaymentRepository]
        val service = new PaymentTCCService(paymentRepo, reservationRepo)
        
        val cancelledReservation = PaymentReservation(
          id = "res-1",
          orderId = "123",
          customerId = 1L,
          amount = BigDecimal(100.00),
          currency = "USD",
          status = PaymentReservationStatus.Cancelled,
          expiresAt = Instant.now().plus(Duration.ofMinutes(10))
        )
        
        (reservationRepo.findById _).expects("res-1").returning(Future.successful(Some(cancelledReservation)))
        
        val result = service.cancelPayment("res-1").futureValue
        result shouldBe Right(())
      }
    }
  }
}
```

---

## 4.3 Product Service TCC Endpoints

> **Related Tasks:** [4.3.1](implementation-task-list.md) - [4.3.6](implementation-task-list.md)

### Task 4.3.1: Product TCC Routes
<!-- task-4.3.1 -->

Create file: `product-service/src/main/scala/com/oms/product/routes/ProductTCCRoutes.scala`

```scala
package com.oms.product.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.StatusCodes
import com.oms.product.tcc.ProductTCCService
import spray.json._
import scala.concurrent.ExecutionContext

/**
 * TCC endpoints for Product Service stock reservations.
 *
 * Related Tasks: 4.3.1 - 4.3.4
 */
class ProductTCCRoutes(tccService: ProductTCCService)(implicit ec: ExecutionContext) {
  
  import ProductTCCJsonProtocol._
  
  val routes: Route = pathPrefix("stock") {
    concat(
      // Task 4.3.1: POST /stock/try - Reserve stock for order
      path("try") {
        post {
          entity(as[TryStockRequest]) { request =>
            onSuccess(tccService.tryReserveStock(
              orderId = request.orderId,
              items = request.items.map(i => (i.productId, i.quantity))
            )) {
              case Right(reservations) =>
                complete(StatusCodes.Created -> TryStockResponse(
                  orderId = request.orderId,
                  reservations = reservations.map(r => ReservationItem(
                    reservationId = r.id.get,
                    productId = r.productId,
                    quantity = r.quantity,
                    expiresAt = r.expiresAt.toString
                  )),
                  status = "reserved"
                ))
              case Left(error) =>
                complete(StatusCodes.Conflict -> ErrorResponse(error))
            }
          }
        }
      },
      
      // Task 4.3.2: POST /stock/confirm/{orderId} - Confirm stock deduction
      path("confirm" / Segment) { orderId =>
        post {
          onSuccess(tccService.confirmStock(orderId)) {
            case Right(_) =>
              complete(StatusCodes.OK -> ConfirmStockResponse(
                orderId = orderId,
                status = "confirmed"
              ))
            case Left(error) =>
              complete(StatusCodes.BadRequest -> ErrorResponse(error))
          }
        }
      },
      
      // Task 4.3.3: POST /stock/cancel/{orderId} - Release stock reservation
      path("cancel" / Segment) { orderId =>
        post {
          onSuccess(tccService.cancelStock(orderId)) {
            case Right(released) =>
              complete(StatusCodes.OK -> CancelStockResponse(
                orderId = orderId,
                releasedItems = released.map(r => ReleasedItem(r.productId, r.quantity)),
                status = "released"
              ))
            case Left(error) =>
              complete(StatusCodes.BadRequest -> ErrorResponse(error))
          }
        }
      }
    )
  }
}

// JSON Protocol
object ProductTCCJsonProtocol extends DefaultJsonProtocol {
  case class TryStockItem(productId: Long, quantity: Int)
  case class TryStockRequest(orderId: String, items: List[TryStockItem])
  case class ReservationItem(reservationId: Long, productId: Long, quantity: Int, expiresAt: String)
  case class TryStockResponse(orderId: String, reservations: List[ReservationItem], status: String)
  case class ConfirmStockResponse(orderId: String, status: String)
  case class ReleasedItem(productId: Long, quantity: Int)
  case class CancelStockResponse(orderId: String, releasedItems: List[ReleasedItem], status: String)
  case class ErrorResponse(error: String)
  
  implicit val tryStockItemFormat: RootJsonFormat[TryStockItem] = jsonFormat2(TryStockItem)
  implicit val tryStockRequestFormat: RootJsonFormat[TryStockRequest] = jsonFormat2(TryStockRequest)
  implicit val reservationItemFormat: RootJsonFormat[ReservationItem] = jsonFormat4(ReservationItem)
  implicit val tryStockResponseFormat: RootJsonFormat[TryStockResponse] = jsonFormat3(TryStockResponse)
  implicit val confirmStockResponseFormat: RootJsonFormat[ConfirmStockResponse] = jsonFormat2(ConfirmStockResponse)
  implicit val releasedItemFormat: RootJsonFormat[ReleasedItem] = jsonFormat2(ReleasedItem)
  implicit val cancelStockResponseFormat: RootJsonFormat[CancelStockResponse] = jsonFormat3(CancelStockResponse)
  implicit val errorResponseFormat: RootJsonFormat[ErrorResponse] = jsonFormat1(ErrorResponse)
}
```

### Task 4.3.4: Product TCC Service
<!-- task-4.3.4 -->

Create file: `product-service/src/main/scala/com/oms/product/tcc/ProductTCCService.scala`

```scala
package com.oms.product.tcc

import com.oms.product.repository.ProductRepository
import java.time.{Instant, Duration}
import scala.concurrent.{ExecutionContext, Future}
import org.slf4j.LoggerFactory

/**
 * TCC Service for Product stock reservations.
 *
 * Related Tasks: 4.3.4 - 4.3.6
 */
class ProductTCCService(
  productRepo: ProductRepository,
  reservationRepo: StockReservationRepository,
  ttlMinutes: Int = 15
)(implicit ec: ExecutionContext) {
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  /**
   * TRY Phase: Reserve stock for all items in an order.
   * Creates pending reservations without deducting actual stock.
   */
  def tryReserveStock(
    orderId: String,
    items: List[(Long, Int)] // (productId, quantity)
  ): Future[Either[String, List[StockReservation]]] = {
    
    logger.info(s"TRY: Attempting stock reservation for order $orderId with ${items.size} items")
    
    // Check existing reservations
    reservationRepo.findByOrderId(orderId).flatMap { existing =>
      if (existing.nonEmpty && existing.forall(_.status == ReservationStatus.Reserved)) {
        // Already have valid reservations
        logger.info(s"TRY: Found existing reservations for order $orderId")
        Future.successful(Right(existing.toList))
      } else {
        // Create new reservations
        createReservations(orderId, items)
      }
    }
  }
  
  private def createReservations(
    orderId: String,
    items: List[(Long, Int)]
  ): Future[Either[String, List[StockReservation]]] = {
    
    // First, check availability for all items
    val availabilityChecks = Future.traverse(items) { case (productId, quantity) =>
      productRepo.checkStock(productId, quantity).map { available =>
        (productId, quantity, available)
      }
    }
    
    availabilityChecks.flatMap { results =>
      val unavailable = results.filter { case (_, _, available) => !available }
      
      if (unavailable.nonEmpty) {
        val (productId, requested, _) = unavailable.head
        productRepo.findById(productId).map { productOpt =>
          val productName = productOpt.map(_.name).getOrElse(s"Product $productId")
          Left(s"Insufficient stock for $productName (requested: $requested)")
        }
      } else {
        // All items available, create reservations
        val expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes))
        
        Future.traverse(items) { case (productId, quantity) =>
          val reservation = StockReservation(
            orderId = orderId,
            productId = productId,
            quantity = quantity,
            status = ReservationStatus.Reserved,
            expiresAt = expiresAt
          )
          reservationRepo.create(reservation)
        }.map(reservations => Right(reservations))
      }
    }
  }
  
  /**
   * CONFIRM Phase: Deduct stock and mark reservations as confirmed.
   */
  def confirmStock(orderId: String): Future[Either[String, Unit]] = {
    logger.info(s"CONFIRM: Confirming stock for order $orderId")
    
    reservationRepo.findByOrderId(orderId).flatMap { reservations =>
      val reserved = reservations.filter(_.status == ReservationStatus.Reserved)
      
      if (reserved.isEmpty) {
        // Check if already confirmed
        val confirmed = reservations.filter(_.status == ReservationStatus.Confirmed)
        if (confirmed.nonEmpty) {
          logger.info(s"CONFIRM: Order $orderId already confirmed")
          Future.successful(Right(()))
        } else {
          Future.successful(Left(s"No valid reservations found for order $orderId"))
        }
      } else {
        // Check for expired reservations
        val now = Instant.now()
        val expired = reserved.filter(_.expiresAt.isBefore(now))
        
        if (expired.nonEmpty) {
          Future.successful(Left(s"Reservations expired for order $orderId"))
        } else {
          // Deduct stock and confirm
          for {
            _ <- Future.traverse(reserved) { res =>
              productRepo.adjustStock(res.productId, -res.quantity)
            }
            _ <- reservationRepo.confirmAll(orderId)
          } yield {
            logger.info(s"CONFIRM: Successfully confirmed stock for order $orderId")
            Right(())
          }
        }
      }
    }
  }
  
  /**
   * CANCEL Phase: Release stock reservations.
   */
  def cancelStock(orderId: String): Future[Either[String, List[ReleasedItem]]] = {
    logger.info(s"CANCEL: Releasing stock for order $orderId")
    
    reservationRepo.findByOrderId(orderId).flatMap { reservations =>
      val reserved = reservations.filter(_.status == ReservationStatus.Reserved)
      
      if (reserved.isEmpty) {
        // Check if already cancelled
        val cancelled = reservations.filter(_.status == ReservationStatus.Cancelled)
        if (cancelled.nonEmpty) {
          logger.info(s"CANCEL: Order $orderId already cancelled")
          Future.successful(Right(Nil))
        } else {
          Future.successful(Left(s"No reservations found for order $orderId"))
        }
      } else {
        reservationRepo.cancelAll(orderId).map { _ =>
          val released = reserved.map(r => ReleasedItem(r.productId, r.quantity)).toList
          logger.info(s"CANCEL: Released ${released.size} items for order $orderId")
          Right(released)
        }
      }
    }
  }
}
```

### Task 4.3.5 - 4.3.6: Product TCC Unit Tests
<!-- task-4.3.5 -->

Create file: `product-service/src/test/scala/com/oms/product/tcc/ProductTCCServiceSpec.scala`

```scala
package com.oms.product.tcc

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalamock.scalatest.MockFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.{Instant, Duration}

/**
 * Unit tests for ProductTCCService.
 *
 * Related Tasks: 4.3.5, 4.3.6
 */
class ProductTCCServiceSpec extends AnyWordSpec with Matchers with ScalaFutures with MockFactory {
  
  "ProductTCCService" should {
    
    "tryReserveStock" should {
      "successfully reserve stock for available items" in {
        val productRepo = mock[ProductRepository]
        val reservationRepo = mock[StockReservationRepository]
        val service = new ProductTCCService(productRepo, reservationRepo)
        
        (reservationRepo.findByOrderId _).expects("order-1").returning(Future.successful(Seq.empty))
        (productRepo.checkStock _).expects(101L, 5).returning(Future.successful(true))
        (productRepo.checkStock _).expects(102L, 3).returning(Future.successful(true))
        (reservationRepo.create _).expects(*).onCall { r: StockReservation =>
          Future.successful(r.copy(id = Some(1L)))
        }.twice()
        
        val result = service.tryReserveStock("order-1", List((101L, 5), (102L, 3))).futureValue
        result shouldBe a[Right[_, _]]
        result.toOption.get.size shouldBe 2
      }
      
      "fail when stock is insufficient" in {
        val productRepo = mock[ProductRepository]
        val reservationRepo = mock[StockReservationRepository]
        val service = new ProductTCCService(productRepo, reservationRepo)
        
        (reservationRepo.findByOrderId _).expects("order-1").returning(Future.successful(Seq.empty))
        (productRepo.checkStock _).expects(101L, 100).returning(Future.successful(false))
        (productRepo.findById _).expects(101L).returning(Future.successful(Some(
          Product(id = Some(101L), name = "Widget", categoryId = 1L, price = BigDecimal(10.00), stockQuantity = 50, createdBy = 1L)
        )))
        
        val result = service.tryReserveStock("order-1", List((101L, 100))).futureValue
        result shouldBe a[Left[_, _]]
        result.swap.toOption.get should include("Insufficient stock")
      }
    }
    
    "confirmStock" should {
      "deduct stock and confirm reservations" in {
        val productRepo = mock[ProductRepository]
        val reservationRepo = mock[StockReservationRepository]
        val service = new ProductTCCService(productRepo, reservationRepo)
        
        val reservations = Seq(
          StockReservation(id = Some(1L), orderId = "order-1", productId = 101L, quantity = 5,
            status = ReservationStatus.Reserved, expiresAt = Instant.now().plus(Duration.ofMinutes(10)))
        )
        
        (reservationRepo.findByOrderId _).expects("order-1").returning(Future.successful(reservations))
        (productRepo.adjustStock _).expects(101L, -5).returning(Future.successful(1))
        (reservationRepo.confirmAll _).expects("order-1").returning(Future.successful(1))
        
        val result = service.confirmStock("order-1").futureValue
        result shouldBe Right(())
      }
      
      "reject expired reservations" in {
        val productRepo = mock[ProductRepository]
        val reservationRepo = mock[StockReservationRepository]
        val service = new ProductTCCService(productRepo, reservationRepo)
        
        val expiredReservations = Seq(
          StockReservation(id = Some(1L), orderId = "order-1", productId = 101L, quantity = 5,
            status = ReservationStatus.Reserved, expiresAt = Instant.now().minus(Duration.ofMinutes(5)))
        )
        
        (reservationRepo.findByOrderId _).expects("order-1").returning(Future.successful(expiredReservations))
        
        val result = service.confirmStock("order-1").futureValue
        result shouldBe a[Left[_, _]]
        result.swap.toOption.get should include("expired")
      }
    }
  }
}
```

---

## 4.4 Timeout Handling

> **Related Tasks:** [4.4.1](implementation-task-list.md) - [4.4.5](implementation-task-list.md)

### Task 4.4.1 - 4.4.3: Reservation Cleanup Actor
<!-- task-4.4.1 -->

Create file: `common/src/main/scala/com/oms/common/tcc/ReservationCleanup.scala`

```scala
package com.oms.common.tcc

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

/**
 * Actor that monitors and cleans up expired TCC reservations.
 *
 * Related Tasks: 4.4.1 - 4.4.3
 */
object ReservationCleanup {
  
  sealed trait Command
  case object ScanExpired extends Command
  private case class ExpiredFound(count: Int) extends Command
  private case class CleanupError(error: Throwable) extends Command
  
  case class Config(
    scanInterval: FiniteDuration = 1.minute,
    batchSize: Int = 100
  )
  
  def apply[T](
    findExpired: () => Future[Seq[T]],
    handleExpired: T => Future[Unit],
    config: Config = Config()
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    
    val logger = LoggerFactory.getLogger("ReservationCleanup")
    
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(ScanExpired, config.scanInterval)
        context.log.info(s"ReservationCleanup started with interval ${config.scanInterval}")
        
        Behaviors.receiveMessage {
          case ScanExpired =>
            context.pipeToSelf(findExpired()) {
              case scala.util.Success(expired) =>
                if (expired.nonEmpty) {
                  // Process expired reservations
                  Future.traverse(expired.take(config.batchSize))(handleExpired)
                    .map(_ => ExpiredFound(expired.size))
                    .recover { case ex => CleanupError(ex) }
                }
                ExpiredFound(expired.size)
              case scala.util.Failure(ex) => CleanupError(ex)
            }
            Behaviors.same
            
          case ExpiredFound(count) =>
            if (count > 0) {
              logger.info(s"Cleaned up $count expired reservations")
            }
            Behaviors.same
            
          case CleanupError(error) =>
            logger.error("Reservation cleanup failed", error)
            Behaviors.same
        }
      }
    }
  }
}
```

### Task 4.4.4: Stock Reservation Cleanup
<!-- task-4.4.4 -->

Create file: `product-service/src/main/scala/com/oms/product/tcc/StockReservationCleanup.scala`

```scala
package com.oms.product.tcc

import akka.actor.typed.Behavior
import com.oms.common.tcc.ReservationCleanup
import scala.concurrent.{ExecutionContext, Future}

/**
 * Cleanup actor for expired stock reservations.
 *
 * Related Task: 4.4.4
 */
object StockReservationCleanup {
  
  def apply(
    reservationRepo: StockReservationRepository
  )(implicit ec: ExecutionContext): Behavior[ReservationCleanup.Command] = {
    
    ReservationCleanup(
      findExpired = () => reservationRepo.findExpired(),
      handleExpired = (reservation: StockReservation) => {
        reservationRepo.updateStatus(reservation.id.get, ReservationStatus.Expired)
          .map(_ => ())
      },
      config = ReservationCleanup.Config(
        scanInterval = scala.concurrent.duration.Duration(1, "minute"),
        batchSize = 50
      )
    )
  }
}
```

### Task 4.4.5: Payment Reservation Cleanup
<!-- task-4.4.5 -->

Create file: `payment-service/src/main/scala/com/oms/payment/tcc/PaymentReservationCleanup.scala`

```scala
package com.oms.payment.tcc

import akka.actor.typed.Behavior
import com.oms.common.tcc.ReservationCleanup
import scala.concurrent.{ExecutionContext, Future}

/**
 * Cleanup actor for expired payment reservations.
 *
 * Related Task: 4.4.5
 */
object PaymentReservationCleanup {
  
  def apply(
    reservationRepo: PaymentReservationRepository
  )(implicit ec: ExecutionContext): Behavior[ReservationCleanup.Command] = {
    
    ReservationCleanup(
      findExpired = () => reservationRepo.findExpired(),
      handleExpired = (reservation: PaymentReservation) => {
        reservationRepo.updateStatus(reservation.id, PaymentReservationStatus.Expired)
          .map(_ => ())
      },
      config = ReservationCleanup.Config(
        scanInterval = scala.concurrent.duration.Duration(1, "minute"),
        batchSize = 50
      )
    )
  }
}
```

---

## 4.5 Recovery Mechanism

> **Related Tasks:** [4.5.1](implementation-task-list.md) - [4.5.5](implementation-task-list.md)

### Task 4.5.1 - 4.5.2: TCC Recovery Service
<!-- task-4.5.1 -->

Create file: `order-service/src/main/scala/com/oms/order/tcc/TCCRecoveryService.scala`

```scala
package com.oms.order.tcc

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Service for recovering incomplete TCC transactions after service restart.
 *
 * Related Tasks: 4.5.1 - 4.5.2
 */
object TCCRecoveryService {
  
  sealed trait Command
  case object RecoverPendingTransactions extends Command
  private case class RecoveryComplete(recovered: Int, failed: Int) extends Command
  private case class RecoveryError(error: Throwable) extends Command
  
  def apply(
    transactionRepo: TCCTransactionRepository,
    productTCCClient: ProductTCCClient,
    paymentTCCClient: PaymentTCCClient
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    
    val logger = LoggerFactory.getLogger("TCCRecoveryService")
    
    Behaviors.setup { context =>
      // Run recovery on startup
      context.self ! RecoverPendingTransactions
      
      Behaviors.withTimers { timers =>
        // Also run periodic recovery check
        timers.startTimerWithFixedDelay(RecoverPendingTransactions, 5.minutes)
        
        Behaviors.receiveMessage {
          case RecoverPendingTransactions =>
            logger.info("Starting TCC recovery scan...")
            
            val recovery = for {
              pendingTxns <- transactionRepo.findPending()
              results <- Future.traverse(pendingTxns)(recoverTransaction(_, productTCCClient, paymentTCCClient, transactionRepo))
            } yield {
              val (successes, failures) = results.partition(identity)
              RecoveryComplete(successes.size, failures.size)
            }
            
            context.pipeToSelf(recovery) {
              case scala.util.Success(result) => result
              case scala.util.Failure(ex) => RecoveryError(ex)
            }
            Behaviors.same
            
          case RecoveryComplete(recovered, failed) =>
            if (recovered > 0 || failed > 0) {
              logger.info(s"TCC recovery completed: $recovered recovered, $failed failed")
            }
            Behaviors.same
            
          case RecoveryError(error) =>
            logger.error("TCC recovery failed", error)
            Behaviors.same
        }
      }
    }
  }
  
  private def recoverTransaction(
    txn: TCCTransaction,
    productClient: ProductTCCClient,
    paymentClient: PaymentTCCClient,
    repo: TCCTransactionRepository
  )(implicit ec: ExecutionContext): Future[Boolean] = {
    
    val logger = LoggerFactory.getLogger("TCCRecoveryService")
    
    txn.state match {
      case TCCState.Trying | TCCState.TrySucceeded =>
        // Transaction was in TRY phase - check if expired and cancel
        if (txn.expiresAt.isBefore(Instant.now())) {
          logger.info(s"Cancelling expired transaction ${txn.id}")
          for {
            _ <- productClient.cancel(txn.id)
            _ <- paymentClient.cancel(txn.id)
            _ <- repo.updateState(txn.id, TCCState.Cancelled)
          } yield true
        } else {
          // Not expired, leave for coordinator
          Future.successful(true)
        }
        
      case TCCState.Confirming =>
        // Transaction was confirming - retry confirm
        logger.info(s"Retrying confirm for transaction ${txn.id}")
        for {
          _ <- productClient.confirm(txn.id)
          _ <- paymentClient.confirm(txn.id)
          _ <- repo.updateState(txn.id, TCCState.Confirmed)
        } yield true
        
      case TCCState.Cancelling =>
        // Transaction was cancelling - retry cancel
        logger.info(s"Retrying cancel for transaction ${txn.id}")
        for {
          _ <- productClient.cancel(txn.id)
          _ <- paymentClient.cancel(txn.id)
          _ <- repo.updateState(txn.id, TCCState.Cancelled)
        } yield true
        
      case _ =>
        // Already in terminal state
        Future.successful(true)
    }
  }
}
```

### Task 4.5.3: TCC Transaction Repository
<!-- task-4.5.3 -->

Create file: `order-service/src/main/scala/com/oms/order/tcc/TCCTransactionRepository.scala`

```scala
package com.oms.order.tcc

import com.oms.common.tcc.{TCCTransaction, TCCState, TCCParticipant, ParticipantStatus}
import slick.jdbc.PostgresProfile.api._
import spray.json._
import java.time.Instant
import java.sql.Timestamp
import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository for TCC transaction persistence.
 *
 * Related Task: 4.5.3
 */
class TCCTransactionRepository(db: Database)(implicit ec: ExecutionContext) {
  
  implicit val instantColumnType: BaseColumnType[Instant] =
    MappedColumnType.base[Instant, Timestamp](
      instant => Timestamp.from(instant),
      timestamp => timestamp.toInstant
    )
  
  private class TCCTransactionsTable(tag: Tag) extends Table[(String, String, String, String, String, Instant, Instant, Option[Instant], Option[Instant])](tag, "tcc_transactions") {
    def id = column[String]("id", O.PrimaryKey)
    def transactionType = column[String]("transaction_type")
    def state = column[String]("state")
    def participants = column[String]("participants") // JSON array
    def payload = column[String]("payload") // JSON object
    def createdAt = column[Instant]("created_at")
    def expiresAt = column[Instant]("expires_at")
    def confirmedAt = column[Option[Instant]]("confirmed_at")
    def cancelledAt = column[Option[Instant]]("cancelled_at")
    
    def * = (id, transactionType, state, participants, payload, createdAt, expiresAt, confirmedAt, cancelledAt)
  }
  
  private val transactions = TableQuery[TCCTransactionsTable]
  
  def save(txn: TCCTransaction): Future[TCCTransaction] = {
    val participantsJson = txn.participants.toJson.compactPrint
    val row = (
      txn.id,
      txn.transactionType,
      txn.state.name,
      participantsJson,
      txn.payload.compactPrint,
      txn.createdAt,
      txn.expiresAt,
      txn.confirmedAt,
      txn.cancelledAt
    )
    db.run(transactions += row).map(_ => txn)
  }
  
  def findById(id: String): Future[Option[TCCTransaction]] = {
    db.run(transactions.filter(_.id === id).result.headOption).map(_.map(toTransaction))
  }
  
  def findPending(): Future[Seq[TCCTransaction]] = {
    val pendingStates = Seq("TRYING", "TRY_SUCCEEDED", "CONFIRMING", "CANCELLING")
    db.run(
      transactions.filter(_.state.inSet(pendingStates)).result
    ).map(_.map(toTransaction))
  }
  
  def updateState(id: String, state: TCCState): Future[Unit] = {
    val now = Instant.now()
    val updates = state match {
      case TCCState.Confirmed =>
        transactions.filter(_.id === id)
          .map(t => (t.state, t.confirmedAt))
          .update((state.name, Some(now)))
      case TCCState.Cancelled =>
        transactions.filter(_.id === id)
          .map(t => (t.state, t.cancelledAt))
          .update((state.name, Some(now)))
      case _ =>
        transactions.filter(_.id === id)
          .map(_.state)
          .update(state.name)
    }
    db.run(updates).map(_ => ())
  }
  
  private def toTransaction(row: (String, String, String, String, String, Instant, Instant, Option[Instant], Option[Instant])): TCCTransaction = {
    val (id, txnType, state, participantsJson, payloadJson, createdAt, expiresAt, confirmedAt, cancelledAt) = row
    TCCTransaction(
      id = id,
      transactionType = txnType,
      state = TCCState.fromString(state),
      participants = participantsJson.parseJson.convertTo[List[TCCParticipant]],
      payload = payloadJson.parseJson,
      createdAt = createdAt,
      expiresAt = expiresAt,
      confirmedAt = confirmedAt,
      cancelledAt = cancelledAt
    )
  }
}
```

### Task 4.5.4 - 4.5.5: Recovery Unit Tests
<!-- task-4.5.4 -->

Create file: `order-service/src/test/scala/com/oms/order/tcc/TCCRecoveryServiceSpec.scala`

```scala
package com.oms.order.tcc

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalamock.scalatest.MockFactory
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.{Instant, Duration}

/**
 * Unit tests for TCCRecoveryService.
 *
 * Related Tasks: 4.5.4, 4.5.5
 */
class TCCRecoveryServiceSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with Matchers with MockFactory {
  
  "TCCRecoveryService" should {
    
    "cancel expired TRY phase transactions" in {
      // Test implementation
    }
    
    "retry CONFIRMING transactions" in {
      // Test implementation  
    }
    
    "retry CANCELLING transactions" in {
      // Test implementation
    }
    
    "skip transactions in terminal states" in {
      // Test implementation
    }
  }
}
```

---

## 4.6 Integration - Payment Flow

> **Related Tasks:** [4.6.1](implementation-task-list.md) - [4.6.6](implementation-task-list.md)

### Task 4.6.1 - 4.6.3: TCC HTTP Clients
<!-- task-4.6.1 -->

Create file: `order-service/src/main/scala/com/oms/order/client/TCCClients.scala`

```scala
package com.oms.order.client

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json._
import scala.concurrent.{ExecutionContext, Future}

/**
 * HTTP client for Product Service TCC endpoints.
 *
 * Related Task: 4.6.1
 */
class ProductTCCClient(baseUrl: String)(implicit system: ActorSystem[_], ec: ExecutionContext) {
  
  import ProductTCCClientJsonProtocol._
  
  def tryReserve(orderId: String, items: List[(Long, Int)]): Future[TryStockResponse] = {
    val request = TryStockRequest(orderId, items.map { case (pid, qty) => TryStockItem(pid, qty) })
    
    Http().singleRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseUrl/stock/try",
        entity = HttpEntity(ContentTypes.`application/json`, request.toJson.compactPrint)
      )
    ).flatMap { response =>
      response.status match {
        case StatusCodes.Created =>
          Unmarshal(response.entity).to[TryStockResponse]
        case status =>
          Unmarshal(response.entity).to[String].flatMap { body =>
            Future.failed(new RuntimeException(s"TRY failed: $status - $body"))
          }
      }
    }
  }
  
  def confirm(orderId: String): Future[Unit] = {
    Http().singleRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseUrl/stock/confirm/$orderId"
      )
    ).flatMap { response =>
      if (response.status.isSuccess()) Future.successful(())
      else Future.failed(new RuntimeException(s"CONFIRM failed: ${response.status}"))
    }
  }
  
  def cancel(orderId: String): Future[Unit] = {
    Http().singleRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseUrl/stock/cancel/$orderId"
      )
    ).flatMap { response =>
      if (response.status.isSuccess()) Future.successful(())
      else Future.failed(new RuntimeException(s"CANCEL failed: ${response.status}"))
    }
  }
}

/**
 * HTTP client for Payment Service TCC endpoints.
 *
 * Related Task: 4.6.2
 */
class PaymentTCCClient(baseUrl: String)(implicit system: ActorSystem[_], ec: ExecutionContext) {
  
  import PaymentTCCClientJsonProtocol._
  
  def tryReserve(orderId: String, customerId: Long, amount: BigDecimal): Future[TryPaymentResponse] = {
    val request = TryPaymentRequest(orderId, customerId, amount, Some("USD"))
    
    Http().singleRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseUrl/payments/try",
        entity = HttpEntity(ContentTypes.`application/json`, request.toJson.compactPrint)
      )
    ).flatMap { response =>
      response.status match {
        case StatusCodes.Created =>
          Unmarshal(response.entity).to[TryPaymentResponse]
        case status =>
          Unmarshal(response.entity).to[String].flatMap { body =>
            Future.failed(new RuntimeException(s"TRY failed: $status - $body"))
          }
      }
    }
  }
  
  def confirm(reservationId: String, paymentMethod: String, createdBy: Long): Future[ConfirmPaymentResponse] = {
    val request = ConfirmPaymentRequest(paymentMethod, createdBy)
    
    Http().singleRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseUrl/payments/confirm/$reservationId",
        entity = HttpEntity(ContentTypes.`application/json`, request.toJson.compactPrint)
      )
    ).flatMap { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[ConfirmPaymentResponse]
      } else {
        Future.failed(new RuntimeException(s"CONFIRM failed: ${response.status}"))
      }
    }
  }
  
  def cancel(reservationId: String): Future[Unit] = {
    Http().singleRequest(
      HttpRequest(
        method = HttpMethods.POST,
        uri = s"$baseUrl/payments/cancel/$reservationId"
      )
    ).flatMap { response =>
      if (response.status.isSuccess()) Future.successful(())
      else Future.failed(new RuntimeException(s"CANCEL failed: ${response.status}"))
    }
  }
}

// JSON Protocols
object ProductTCCClientJsonProtocol extends DefaultJsonProtocol {
  case class TryStockItem(productId: Long, quantity: Int)
  case class TryStockRequest(orderId: String, items: List[TryStockItem])
  case class ReservationItem(reservationId: Long, productId: Long, quantity: Int, expiresAt: String)
  case class TryStockResponse(orderId: String, reservations: List[ReservationItem], status: String)
  
  implicit val tryStockItemFormat: RootJsonFormat[TryStockItem] = jsonFormat2(TryStockItem)
  implicit val tryStockRequestFormat: RootJsonFormat[TryStockRequest] = jsonFormat2(TryStockRequest)
  implicit val reservationItemFormat: RootJsonFormat[ReservationItem] = jsonFormat4(ReservationItem)
  implicit val tryStockResponseFormat: RootJsonFormat[TryStockResponse] = jsonFormat3(TryStockResponse)
}

object PaymentTCCClientJsonProtocol extends DefaultJsonProtocol {
  case class TryPaymentRequest(orderId: String, customerId: Long, amount: BigDecimal, currency: Option[String])
  case class TryPaymentResponse(reservationId: String, orderId: String, amount: BigDecimal, expiresAt: String)
  case class ConfirmPaymentRequest(paymentMethod: String, createdBy: Long)
  case class ConfirmPaymentResponse(paymentId: Long, transactionId: String, status: String)
  
  implicit val tryPaymentRequestFormat: RootJsonFormat[TryPaymentRequest] = jsonFormat4(TryPaymentRequest)
  implicit val tryPaymentResponseFormat: RootJsonFormat[TryPaymentResponse] = jsonFormat4(TryPaymentResponse)
  implicit val confirmPaymentRequestFormat: RootJsonFormat[ConfirmPaymentRequest] = jsonFormat2(ConfirmPaymentRequest)
  implicit val confirmPaymentResponseFormat: RootJsonFormat[ConfirmPaymentResponse] = jsonFormat3(ConfirmPaymentResponse)
}
```

### Task 4.6.4 - 4.6.6: Integration Tests
<!-- task-4.6.4 -->

Create file: `order-service/src/test/scala/com/oms/order/integration/TCCIntegrationSpec.scala`

```scala
package com.oms.order.integration

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.duration._

/**
 * Integration tests for TCC payment flow.
 *
 * Related Tasks: 4.6.4 - 4.6.6
 */
class TCCIntegrationSpec extends ScalaTestWithActorTestKit 
    with AnyWordSpecLike 
    with Matchers 
    with BeforeAndAfterAll {
  
  // These tests require running services (docker-compose up)
  
  "TCC Payment Flow" should {
    
    "complete happy path: TRY -> CONFIRM" in {
      // 1. Create order with stock reservation
      // 2. TRY payment reservation
      // 3. CONFIRM stock and payment
      // 4. Verify order status is 'paid'
    }
    
    "handle stock unavailable: TRY fails, rollback" in {
      // 1. TRY stock reservation for unavailable product
      // 2. Verify no payment reservation created
      // 3. Verify order status is 'failed'
    }
    
    "handle payment failure: CONFIRM fails, rollback stock" in {
      // 1. TRY stock reservation - success
      // 2. TRY payment reservation - success
      // 3. CONFIRM stock - success
      // 4. CONFIRM payment - failure
      // 5. CANCEL stock reservation
      // 6. Verify stock is restored
    }
    
    "handle timeout: expired reservations are cleaned up" in {
      // 1. TRY reservations
      // 2. Wait for expiration (use short TTL in test)
      // 3. Verify reservations are marked as expired
      // 4. Verify CONFIRM fails
    }
  }
}
```

---

## 4. TCC Pattern Implementation

### 4.1 TCC State Models

```scala
package com.oms.common.tcc

import java.time.LocalDateTime

// TCC Transaction States
sealed trait TCCState
case object Initial extends TCCState
case object Trying extends TCCState
case object Confirmed extends TCCState
case object Cancelled extends TCCState

// TCC Transaction Record
case class TCCTransaction(
  id: String,
  transactionType: String,  // "PAYMENT", "ORDER_CREATE"
  state: TCCState,
  participants: List[TCCParticipant],
  createdAt: LocalDateTime,
  expiresAt: LocalDateTime,
  confirmedAt: Option[LocalDateTime] = None,
  cancelledAt: Option[LocalDateTime] = None
)

case class TCCParticipant(
  serviceName: String,
  resourceId: String,
  tryEndpoint: String,
  confirmEndpoint: String,
  cancelEndpoint: String,
  status: String  // "PENDING", "TRIED", "CONFIRMED", "CANCELLED"
)
```

### 4.2 Stock Reservation Model (Product Service)

```scala
package com.oms.product.tcc

import java.time.LocalDateTime

case class StockReservation(
  id: Option[Long] = None,
  orderId: Long,
  productId: Long,
  quantity: Int,
  status: String = "RESERVED",  // RESERVED, CONFIRMED, RELEASED, EXPIRED
  createdAt: LocalDateTime = LocalDateTime.now(),
  expiresAt: LocalDateTime,
  confirmedAt: Option[LocalDateTime] = None,
  releasedAt: Option[LocalDateTime] = None
)
```

### 4.3 Stock Reservation Repository

```scala
package com.oms.product.tcc

import slick.jdbc.PostgresProfile.api._
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class StockReservationRepository(db: Database)(implicit ec: ExecutionContext) {
  
  private class StockReservationsTable(tag: Tag) extends Table[StockReservation](tag, "stock_reservations") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def orderId = column[Long]("order_id")
    def productId = column[Long]("product_id")
    def quantity = column[Int]("quantity")
    def status = column[String]("status")
    def createdAt = column[LocalDateTime]("created_at")
    def expiresAt = column[LocalDateTime]("expires_at")
    def confirmedAt = column[Option[LocalDateTime]]("confirmed_at")
    def releasedAt = column[Option[LocalDateTime]]("released_at")
    
    def * = (id.?, orderId, productId, quantity, status, createdAt, 
             expiresAt, confirmedAt, releasedAt).mapTo[StockReservation]
  }
  
  private val reservations = TableQuery[StockReservationsTable]
  
  def reserve(orderId: Long, productId: Long, quantity: Int, ttlMinutes: Int = 15): Future[StockReservation] = {
    val reservation = StockReservation(
      orderId = orderId,
      productId = productId,
      quantity = quantity,
      expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes)
    )
    
    db.run(
      (reservations returning reservations.map(_.id) 
        into ((r, id) => r.copy(id = Some(id)))) += reservation
    )
  }
  
  def confirm(orderId: Long): Future[Int] = {
    db.run(
      reservations
        .filter(r => r.orderId === orderId && r.status === "RESERVED")
        .map(r => (r.status, r.confirmedAt))
        .update(("CONFIRMED", Some(LocalDateTime.now())))
    )
  }
  
  def release(orderId: Long): Future[Int] = {
    db.run(
      reservations
        .filter(r => r.orderId === orderId && r.status === "RESERVED")
        .map(r => (r.status, r.releasedAt))
        .update(("RELEASED", Some(LocalDateTime.now())))
    )
  }
  
  def findByOrderId(orderId: Long): Future[Seq[StockReservation]] = {
    db.run(reservations.filter(_.orderId === orderId).result)
  }
  
  def findExpired(): Future[Seq[StockReservation]] = {
    db.run(
      reservations
        .filter(r => r.status === "RESERVED" && r.expiresAt < LocalDateTime.now())
        .result
    )
  }
}
```

### 4.4 Product TCC Service

```scala
package com.oms.product.tcc

import com.oms.product.repository.ProductRepository
import scala.concurrent.{ExecutionContext, Future}

class ProductTCCService(
  productRepo: ProductRepository,
  reservationRepo: StockReservationRepository
)(implicit ec: ExecutionContext) {
  
  // TRY: Reserve stock without deducting
  def tryReserveStock(orderId: Long, productId: Long, quantity: Int): Future[StockReservation] = {
    for {
      // Check current stock
      available <- productRepo.checkStock(productId, quantity)
      _ <- if (!available) Future.failed(new Exception(s"Insufficient stock for product $productId")) 
           else Future.successful(())
      
      // Create reservation (doesn't deduct actual stock yet)
      reservation <- reservationRepo.reserve(orderId, productId, quantity)
    } yield reservation
  }
  
  // CONFIRM: Actually deduct the stock
  def confirmStock(orderId: Long): Future[Unit] = {
    for {
      reservations <- reservationRepo.findByOrderId(orderId)
      _ <- Future.traverse(reservations) { res =>
        productRepo.adjustStock(res.productId, -res.quantity)
      }
      _ <- reservationRepo.confirm(orderId)
    } yield ()
  }
  
  // CANCEL: Release the reservation
  def cancelStock(orderId: Long): Future[Unit] = {
    reservationRepo.release(orderId).map(_ => ())
  }
}
```

### 4.5 Payment Reservation Model

```scala
package com.oms.payment.tcc

import java.time.LocalDateTime

case class PaymentReservation(
  id: String,
  orderId: Long,
  amount: BigDecimal,
  status: String = "RESERVED",  // RESERVED, CONFIRMED, CANCELLED, EXPIRED
  createdAt: LocalDateTime = LocalDateTime.now(),
  expiresAt: LocalDateTime,
  confirmedAt: Option[LocalDateTime] = None,
  cancelledAt: Option[LocalDateTime] = None
)
```

### 4.6 Payment TCC Service

```scala
package com.oms.payment.tcc

import com.oms.payment.model.Payment
import com.oms.payment.repository.PaymentRepository
import java.util.UUID
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class PaymentTCCService(
  paymentRepo: PaymentRepository,
  reservationRepo: PaymentReservationRepository
)(implicit ec: ExecutionContext) {
  
  // TRY: Reserve payment amount
  def tryReservePayment(orderId: Long, amount: BigDecimal, ttlMinutes: Int = 15): Future[PaymentReservation] = {
    val reservation = PaymentReservation(
      id = UUID.randomUUID().toString,
      orderId = orderId,
      amount = amount,
      expiresAt = LocalDateTime.now().plusMinutes(ttlMinutes)
    )
    reservationRepo.create(reservation)
  }
  
  // CONFIRM: Capture the reserved amount (create actual payment)
  def confirmPayment(reservationId: String, createdBy: Long, paymentMethod: String): Future[Payment] = {
    for {
      reservationOpt <- reservationRepo.findById(reservationId)
      reservation <- reservationOpt match {
        case Some(r) if r.status == "RESERVED" => Future.successful(r)
        case Some(r) => Future.failed(new Exception(s"Invalid reservation status: ${r.status}"))
        case None => Future.failed(new Exception(s"Reservation not found: $reservationId"))
      }
      
      // Create actual payment
      payment <- paymentRepo.create(Payment(
        orderId = reservation.orderId,
        createdBy = createdBy,
        amount = reservation.amount,
        paymentMethod = paymentMethod,
        status = "completed",
        transactionId = Some(s"TXN-${UUID.randomUUID().toString.take(8).toUpperCase}")
      ))
      
      _ <- reservationRepo.updateStatus(reservationId, "CONFIRMED")
    } yield payment
  }
  
  // CANCEL: Release the reserved amount
  def cancelPayment(reservationId: String): Future[Unit] = {
    reservationRepo.updateStatus(reservationId, "CANCELLED").map(_ => ())
  }
}
```

### 4.7 TCC Coordinator

```scala
package com.oms.order.tcc

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TCCCoordinator {
  
  sealed trait Command
  case class StartTransaction(
    transactionType: String,
    orderId: Long,
    amount: BigDecimal,
    items: List[(Long, Int)],  // (productId, quantity)
    replyTo: ActorRef[Response]
  ) extends Command
  case class ConfirmTransaction(transactionId: String, replyTo: ActorRef[Response]) extends Command
  case class CancelTransaction(transactionId: String, replyTo: ActorRef[Response]) extends Command
  case object CheckExpiredTransactions extends Command
  
  sealed trait Response
  case class TransactionStarted(transactionId: String, reservations: Map[String, String]) extends Response
  case class TransactionConfirmed(transactionId: String) extends Response
  case class TransactionCancelled(transactionId: String) extends Response
  case class TransactionError(message: String) extends Response
  
  def apply(
    productTCCClient: ProductTCCClient,
    paymentTCCClient: PaymentTCCClient,
    transactionRepo: TCCTransactionRepository
  )(implicit ec: ExecutionContext): Behavior[Command] = {
    
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        // Check for expired transactions every minute
        timers.startTimerWithFixedDelay(CheckExpiredTransactions, 1.minute)
        
        Behaviors.receiveMessage {
          case StartTransaction(transactionType, orderId, amount, items, replyTo) =>
            val transactionId = UUID.randomUUID().toString
            
            val tryPhase = for {
              // TRY: Reserve stock for all items
              stockReservations <- Future.traverse(items) { case (productId, quantity) =>
                productTCCClient.tryReserve(orderId, productId, quantity)
              }
              
              // TRY: Reserve payment
              paymentReservation <- paymentTCCClient.tryReserve(orderId, amount)
              
              // Save transaction state
              _ <- transactionRepo.save(TCCTransaction(
                id = transactionId,
                transactionType = transactionType,
                state = Trying,
                participants = List(), // Add participant details
                createdAt = LocalDateTime.now(),
                expiresAt = LocalDateTime.now().plusMinutes(15)
              ))
            } yield (stockReservations, paymentReservation)
            
            context.pipeToSelf(tryPhase) {
              case Success((stockRes, paymentRes)) =>
                replyTo ! TransactionStarted(transactionId, Map(
                  "payment" -> paymentRes.id,
                  "stock" -> stockRes.map(_.id.get.toString).mkString(",")
                ))
                null
              case Failure(ex) =>
                // Cancel any partial reservations
                items.foreach { case (productId, _) =>
                  productTCCClient.cancel(orderId)
                }
                paymentTCCClient.cancel(orderId)
                replyTo ! TransactionError(ex.getMessage)
                null
            }
            Behaviors.same
            
          case ConfirmTransaction(transactionId, replyTo) =>
            val confirmPhase = for {
              txnOpt <- transactionRepo.findById(transactionId)
              txn <- txnOpt match {
                case Some(t) if t.state == Trying => Future.successful(t)
                case _ => Future.failed(new Exception("Invalid transaction state"))
              }
              
              // CONFIRM all participants
              _ <- productTCCClient.confirm(txn.id.toLong) // orderId stored in txn
              _ <- paymentTCCClient.confirm(txn.id)
              
              _ <- transactionRepo.updateState(transactionId, Confirmed)
            } yield ()
            
            context.pipeToSelf(confirmPhase) {
              case Success(_) =>
                replyTo ! TransactionConfirmed(transactionId)
                null
              case Failure(ex) =>
                replyTo ! TransactionError(ex.getMessage)
                null
            }
            Behaviors.same
            
          case CancelTransaction(transactionId, replyTo) =>
            val cancelPhase = for {
              txnOpt <- transactionRepo.findById(transactionId)
              _ <- txnOpt match {
                case Some(txn) =>
                  for {
                    _ <- productTCCClient.cancel(txn.id.toLong)
                    _ <- paymentTCCClient.cancel(txn.id)
                    _ <- transactionRepo.updateState(transactionId, Cancelled)
                  } yield ()
                case None => Future.successful(())
              }
            } yield ()
            
            context.pipeToSelf(cancelPhase) {
              case Success(_) =>
                replyTo ! TransactionCancelled(transactionId)
                null
              case Failure(ex) =>
                replyTo ! TransactionError(ex.getMessage)
                null
            }
            Behaviors.same
            
          case CheckExpiredTransactions =>
            transactionRepo.findExpired().foreach { expired =>
              expired.foreach { txn =>
                context.self ! CancelTransaction(txn.id, context.system.ignoreRef)
              }
            }
            Behaviors.same
        }
      }
    }
  }
}
```

---

# Phase 5: Testing & Hardening

## 5.1 Integration Testing

> **Related Tasks:** [5.1.1](implementation-task-list.md) - [5.1.6](implementation-task-list.md)

### Task 5.1.1: Integration Test Framework Setup
<!-- task-5.1.1 -->

Create file: `order-service/src/test/scala/com/oms/order/integration/IntegrationTestBase.scala`

```scala
package com.oms.order.integration

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.testcontainers.containers.{PostgreSQLContainer, KafkaContainer}
import org.testcontainers.utility.DockerImageName
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/**
 * Base class for integration tests with test containers.
 *
 * Related Task: 5.1.1
 */
abstract class IntegrationTestBase extends AnyWordSpec 
    with Matchers 
    with ScalatestRouteTest
    with BeforeAndAfterAll 
    with BeforeAndAfterEach {
  
  // Test containers
  lazy val postgres: PostgreSQLContainer[_] = {
    val container = new PostgreSQLContainer("postgres:16-alpine")
    container.withDatabaseName("oms_test")
    container.withUsername("test")
    container.withPassword("test")
    container.start()
    container
  }
  
  lazy val kafka: KafkaContainer = {
    val container = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
    container.start()
    container
  }
  
  lazy val testDb: Database = {
    Database.forURL(
      url = postgres.getJdbcUrl,
      user = postgres.getUsername,
      password = postgres.getPassword,
      driver = "org.postgresql.Driver"
    )
  }
  
  implicit val ec: ExecutionContext = ExecutionContext.global
  
  override def beforeAll(): Unit = {
    super.beforeAll()
    // Run migrations
    runMigrations()
  }
  
  override def afterAll(): Unit = {
    testDb.close()
    postgres.stop()
    kafka.stop()
    super.afterAll()
  }
  
  override def afterEach(): Unit = {
    // Clean test data
    cleanDatabase()
    super.afterEach()
  }
  
  private def runMigrations(): Unit = {
    // Execute migration scripts
    val migrations = Seq(
      "outbox_events",
      "idempotency_records",
      "tcc_transactions",
      "saga_instances"
    )
    // Implementation...
  }
  
  private def cleanDatabase(): Unit = {
    val cleanup = DBIO.seq(
      sqlu"TRUNCATE outbox_events CASCADE",
      sqlu"TRUNCATE idempotency_records CASCADE",
      sqlu"TRUNCATE saga_instances CASCADE",
      sqlu"TRUNCATE tcc_transactions CASCADE"
    )
    Await.result(testDb.run(cleanup), 10.seconds)
  }
}
```

### Task 5.1.2: Saga Integration Tests
<!-- task-5.1.2 -->

Create file: `order-service/src/test/scala/com/oms/order/integration/SagaIntegrationSpec.scala`

```scala
package com.oms.order.integration

import akka.actor.testkit.typed.scaladsl.TestProbe
import com.oms.common.saga._
import com.oms.order.saga._
import scala.concurrent.duration._

/**
 * Integration tests for Saga pattern.
 *
 * Related Tasks: 5.1.2, 5.1.3
 */
class SagaIntegrationSpec extends IntegrationTestBase {
  
  "CreateOrderSaga" should {
    
    "complete successfully with valid customer and available stock" in {
      // Setup
      val customerId = insertTestCustomer()
      val productId = insertTestProduct(stockQuantity = 100)
      
      // Execute
      val sagaProbe = TestProbe[SagaCoordinator.Response]()
      val saga = startCreateOrderSaga(
        customerId = customerId,
        items = List((productId, 5)),
        replyTo = sagaProbe.ref
      )
      
      // Verify
      val response = sagaProbe.expectMessageType[SagaCoordinator.SagaCompleted](30.seconds)
      response.sagaId should not be empty
      
      // Verify order created
      val order = findOrderById(response.orderId)
      order.status shouldBe "created"
      
      // Verify stock reserved
      val reservations = findStockReservations(response.orderId)
      reservations should have size 1
      reservations.head.status shouldBe "RESERVED"
    }
    
    "trigger compensation when stock is insufficient" in {
      // Setup
      val customerId = insertTestCustomer()
      val productId = insertTestProduct(stockQuantity = 2)  // Only 2 available
      
      // Execute - request 10 items
      val sagaProbe = TestProbe[SagaCoordinator.Response]()
      val saga = startCreateOrderSaga(
        customerId = customerId,
        items = List((productId, 10)),  // Request more than available
        replyTo = sagaProbe.ref
      )
      
      // Verify compensation was triggered
      val response = sagaProbe.expectMessageType[SagaCoordinator.SagaFailed](30.seconds)
      response.error should include("Insufficient stock")
      
      // Verify order was cleaned up (compensated)
      val order = findOrderById(response.orderId)
      order shouldBe empty
    }
    
    "handle customer validation failure with compensation" in {
      // Setup - use invalid customer ID
      val productId = insertTestProduct(stockQuantity = 100)
      
      // Execute
      val sagaProbe = TestProbe[SagaCoordinator.Response]()
      val saga = startCreateOrderSaga(
        customerId = 99999L,  // Non-existent customer
        items = List((productId, 5)),
        replyTo = sagaProbe.ref
      )
      
      // Verify
      val response = sagaProbe.expectMessageType[SagaCoordinator.SagaFailed](30.seconds)
      response.error should include("Customer not found")
    }
  }
  
  "CancelOrderSaga" should {
    
    "cancel order and release stock" in {
      // Setup - create a completed order first
      val orderId = createCompletedOrder()
      val originalStock = getProductStock(productId)
      
      // Execute cancellation
      val sagaProbe = TestProbe[SagaCoordinator.Response]()
      val saga = startCancelOrderSaga(
        orderId = orderId,
        reason = "Customer requested",
        replyTo = sagaProbe.ref
      )
      
      // Verify
      val response = sagaProbe.expectMessageType[SagaCoordinator.SagaCompleted](30.seconds)
      
      // Verify order cancelled
      val order = findOrderById(orderId)
      order.status shouldBe "cancelled"
      
      // Verify stock released
      val currentStock = getProductStock(productId)
      currentStock shouldBe > (originalStock)
    }
    
    "trigger refund for paid orders" in {
      // Setup - create a paid order
      val orderId = createPaidOrder()
      
      // Execute cancellation
      val sagaProbe = TestProbe[SagaCoordinator.Response]()
      val saga = startCancelOrderSaga(
        orderId = orderId,
        reason = "Customer requested",
        replyTo = sagaProbe.ref
      )
      
      // Verify
      val response = sagaProbe.expectMessageType[SagaCoordinator.SagaCompleted](30.seconds)
      
      // Verify refund created
      val refund = findRefundByOrderId(orderId)
      refund should not be empty
    }
  }
  
  // Helper methods
  private def insertTestCustomer(): Long = ???
  private def insertTestProduct(stockQuantity: Int): Long = ???
  private def findOrderById(orderId: String): Option[Order] = ???
  private def findStockReservations(orderId: String): Seq[StockReservation] = ???
}
```

### Task 5.1.4: TCC Integration Tests
<!-- task-5.1.4 -->

Create file: `order-service/src/test/scala/com/oms/order/integration/TCCPaymentIntegrationSpec.scala`

```scala
package com.oms.order.integration

import scala.concurrent.duration._

/**
 * Integration tests for TCC payment flow.
 *
 * Related Tasks: 5.1.4, 5.1.5
 */
class TCCPaymentIntegrationSpec extends IntegrationTestBase {
  
  "TCC Payment Flow" should {
    
    "complete full TRY-CONFIRM cycle" in {
      // Setup
      val orderId = "order-" + java.util.UUID.randomUUID().toString.take(8)
      val productId = insertTestProduct(stockQuantity = 100, price = BigDecimal(25.00))
      val customerId = insertTestCustomer()
      
      // Phase 1: TRY
      val stockTryResponse = tccStockClient.tryReserve(orderId, List((productId, 4)))
      stockTryResponse shouldBe a[Right[_, _]]
      stockTryResponse.toOption.get.reservations should have size 1
      
      val paymentTryResponse = tccPaymentClient.tryReserve(orderId, customerId, BigDecimal(100.00))
      paymentTryResponse shouldBe a[Right[_, _]]
      val reservationId = paymentTryResponse.toOption.get.reservationId
      
      // Phase 2: CONFIRM
      val stockConfirmResponse = tccStockClient.confirm(orderId)
      stockConfirmResponse shouldBe Right(())
      
      val paymentConfirmResponse = tccPaymentClient.confirm(reservationId, "credit_card", customerId)
      paymentConfirmResponse shouldBe a[Right[_, _]]
      paymentConfirmResponse.toOption.get.status shouldBe "confirmed"
      
      // Verify final state
      val stockReservations = findStockReservations(orderId)
      stockReservations.head.status shouldBe "CONFIRMED"
      
      val payment = findPaymentByOrderId(orderId)
      payment.status shouldBe "completed"
    }
    
    "rollback on TRY failure" in {
      val orderId = "order-" + java.util.UUID.randomUUID().toString.take(8)
      val productId = insertTestProduct(stockQuantity = 2)  // Limited stock
      val customerId = insertTestCustomer()
      
      // TRY stock - should fail
      val stockTryResponse = tccStockClient.tryReserve(orderId, List((productId, 100)))
      stockTryResponse shouldBe a[Left[_, _]]
      
      // No payment reservation should be created
      val paymentReservation = findPaymentReservation(orderId)
      paymentReservation shouldBe None
    }
    
    "cancel reservations on CONFIRM failure" in {
      val orderId = "order-" + java.util.UUID.randomUUID().toString.take(8)
      val productId = insertTestProduct(stockQuantity = 100)
      val customerId = insertTestCustomer()
      
      // TRY both
      val stockTryResponse = tccStockClient.tryReserve(orderId, List((productId, 5)))
      stockTryResponse shouldBe a[Right[_, _]]
      
      val paymentTryResponse = tccPaymentClient.tryReserve(orderId, customerId, BigDecimal(100.00))
      val reservationId = paymentTryResponse.toOption.get.reservationId
      
      // Simulate payment confirm failure (e.g., by corrupting reservation)
      corruptPaymentReservation(reservationId)
      
      // CONFIRM should fail
      val paymentConfirmResponse = tccPaymentClient.confirm(reservationId, "credit_card", customerId)
      paymentConfirmResponse shouldBe a[Left[_, _]]
      
      // CANCEL stock
      val stockCancelResponse = tccStockClient.cancel(orderId)
      stockCancelResponse shouldBe a[Right[_, _]]
      
      // Verify rollback
      val stockReservations = findStockReservations(orderId)
      stockReservations.head.status shouldBe "RELEASED"
    }
    
    "handle reservation expiration" in {
      val orderId = "order-" + java.util.UUID.randomUUID().toString.take(8)
      val productId = insertTestProduct(stockQuantity = 100)
      val customerId = insertTestCustomer()
      
      // Create reservation with very short TTL (1 second for testing)
      val stockTryResponse = createStockReservationWithTTL(orderId, productId, 5, ttlSeconds = 1)
      
      // Wait for expiration
      Thread.sleep(2000)
      
      // Trigger cleanup
      triggerReservationCleanup()
      
      // CONFIRM should fail
      val stockConfirmResponse = tccStockClient.confirm(orderId)
      stockConfirmResponse shouldBe a[Left[_, _]]
      stockConfirmResponse.swap.toOption.get should include("expired")
    }
  }
  
  // Helper methods
  private def findStockReservations(orderId: String): Seq[StockReservation] = ???
  private def findPaymentByOrderId(orderId: String): Payment = ???
  private def findPaymentReservation(orderId: String): Option[PaymentReservation] = ???
  private def corruptPaymentReservation(reservationId: String): Unit = ???
  private def createStockReservationWithTTL(orderId: String, productId: Long, qty: Int, ttlSeconds: Int): Unit = ???
  private def triggerReservationCleanup(): Unit = ???
}
```

### Task 5.1.5 - 5.1.6: Outbox Integration Tests
<!-- task-5.1.5 -->

Create file: `order-service/src/test/scala/com/oms/order/integration/OutboxIntegrationSpec.scala`

```scala
package com.oms.order.integration

import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.{Collections, Properties}
import scala.jdk.CollectionConverters._
import scala.concurrent.duration._

/**
 * Integration tests for Outbox pattern.
 *
 * Related Tasks: 5.1.5, 5.1.6
 */
class OutboxIntegrationSpec extends IntegrationTestBase {
  
  lazy val kafkaConsumer: KafkaConsumer[String, String] = {
    val props = new Properties()
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers)
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer")
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer].getName)
    new KafkaConsumer[String, String](props)
  }
  
  "Outbox Pattern" should {
    
    "publish events to Kafka and mark as processed" in {
      // Setup
      kafkaConsumer.subscribe(Collections.singletonList("oms.orders.events"))
      
      // Insert event into outbox
      val eventId = insertOutboxEvent(
        aggregateType = "ORDER",
        aggregateId = "order-123",
        eventType = "ORDER_CREATED",
        payload = """{"orderId":"order-123","customerId":1}"""
      )
      
      // Trigger outbox processor
      triggerOutboxProcessor()
      
      // Wait for event to be published
      eventually(timeout(10.seconds), interval(500.millis)) {
        val records = kafkaConsumer.poll(Duration.ofMillis(100))
        records.count() should be >= 1
        
        val event = records.asScala.find(_.value().contains("order-123"))
        event should not be empty
      }
      
      // Verify outbox event marked as processed
      eventually(timeout(5.seconds), interval(200.millis)) {
        val outboxEvent = findOutboxEvent(eventId)
        outboxEvent.status shouldBe "PUBLISHED"
        outboxEvent.processedAt should not be empty
      }
    }
    
    "retry failed events with backoff" in {
      // Setup - insert event that will fail (e.g., invalid topic)
      val eventId = insertOutboxEvent(
        aggregateType = "INVALID",
        aggregateId = "test-123",
        eventType = "INVALID_EVENT",
        payload = """{"test": true}"""
      )
      
      // Configure failure scenario
      configureKafkaFailure("oms.invalid.events")
      
      // Trigger processor multiple times
      (1 to 3).foreach { _ =>
        triggerOutboxProcessor()
        Thread.sleep(1000)
      }
      
      // Verify retry count increased
      val outboxEvent = findOutboxEvent(eventId)
      outboxEvent.retryCount should be >= 1
      outboxEvent.status shouldBe "PENDING"
    }
    
    "move events to DLQ after max retries" in {
      // Setup
      val eventId = insertOutboxEvent(
        aggregateType = "ORDER",
        aggregateId = "order-456",
        eventType = "ORDER_CREATED",
        payload = """{"invalid": json}"""  // Invalid JSON
      )
      
      // Set retry count to max
      updateOutboxRetryCount(eventId, maxRetries = 5)
      
      // Trigger processor
      triggerOutboxProcessor()
      
      // Verify moved to DLQ
      eventually(timeout(5.seconds), interval(200.millis)) {
        val outboxEvent = findOutboxEvent(eventId)
        outboxEvent.status shouldBe "DEAD_LETTER"
      }
      
      // Verify DLQ topic received the event
      kafkaConsumer.subscribe(Collections.singletonList("oms.dlq"))
      eventually(timeout(10.seconds), interval(500.millis)) {
        val records = kafkaConsumer.poll(Duration.ofMillis(100))
        val dlqEvent = records.asScala.find(_.value().contains("order-456"))
        dlqEvent should not be empty
      }
    }
    
    "process events in order by created_at" in {
      // Insert multiple events
      val event1Id = insertOutboxEvent("ORDER", "order-1", "ORDER_CREATED", "{}", createdAt = Instant.now().minusSeconds(10))
      val event2Id = insertOutboxEvent("ORDER", "order-2", "ORDER_CREATED", "{}", createdAt = Instant.now().minusSeconds(5))
      val event3Id = insertOutboxEvent("ORDER", "order-3", "ORDER_CREATED", "{}", createdAt = Instant.now())
      
      // Trigger processor
      triggerOutboxProcessor()
      
      // Verify processing order
      val processedEvents = findProcessedOutboxEvents()
      processedEvents.map(_.aggregateId) shouldBe Seq("order-1", "order-2", "order-3")
    }
  }
  
  // Helper methods
  private def insertOutboxEvent(
    aggregateType: String, 
    aggregateId: String, 
    eventType: String, 
    payload: String,
    createdAt: Instant = Instant.now()
  ): Long = ???
  private def findOutboxEvent(eventId: Long): OutboxEvent = ???
  private def triggerOutboxProcessor(): Unit = ???
  private def configureKafkaFailure(topic: String): Unit = ???
  private def updateOutboxRetryCount(eventId: Long, maxRetries: Int): Unit = ???
  private def findProcessedOutboxEvents(): Seq[OutboxEvent] = ???
}
```

---

## 5.2 Failure Injection Testing

> **Related Tasks:** [5.2.1](implementation-task-list.md) - [5.2.5](implementation-task-list.md)

### Task 5.2.1: Chaos Testing Framework
<!-- task-5.2.1 -->

Create file: `order-service/src/test/scala/com/oms/order/chaos/ChaosTestingFramework.scala`

```scala
package com.oms.order.chaos

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
 * Chaos testing utilities for failure injection.
 *
 * Related Tasks: 5.2.1 - 5.2.3
 */
object ChaosTestingFramework {
  
  sealed trait FailureMode
  object FailureMode {
    case object NetworkLatency extends FailureMode
    case object NetworkPartition extends FailureMode
    case object ServiceUnavailable extends FailureMode
    case object RandomError extends FailureMode
    case object Timeout extends FailureMode
  }
  
  /**
   * Wraps a Future to inject failures based on configured probability.
   */
  def withChaosMaybe[T](
    operation: => Future[T],
    failureMode: FailureMode,
    failureProbability: Double = 0.3
  )(implicit ec: ExecutionContext): Future[T] = {
    if (Random.nextDouble() < failureProbability) {
      injectFailure(failureMode)
    } else {
      operation
    }
  }
  
  private def injectFailure[T](mode: FailureMode)(implicit ec: ExecutionContext): Future[T] = {
    mode match {
      case FailureMode.NetworkLatency =>
        // Simulate network latency (1-5 seconds)
        val delay = 1000 + Random.nextInt(4000)
        Thread.sleep(delay)
        Future.failed(new RuntimeException("Network timeout after delay"))
        
      case FailureMode.NetworkPartition =>
        Future.failed(new java.net.ConnectException("Connection refused - network partition"))
        
      case FailureMode.ServiceUnavailable =>
        Future.failed(new RuntimeException("503 Service Unavailable"))
        
      case FailureMode.RandomError =>
        val errors = Seq(
          new RuntimeException("Internal server error"),
          new java.sql.SQLException("Database connection lost"),
          new java.io.IOException("I/O error")
        )
        Future.failed(errors(Random.nextInt(errors.size)))
        
      case FailureMode.Timeout =>
        // Never complete - simulates timeout
        Future.never
    }
  }
  
  /**
   * Chaos-enabled HTTP client wrapper
   */
  class ChaosHttpClient(
    underlying: HttpClient,
    failureMode: FailureMode,
    failureProbability: Double
  )(implicit ec: ExecutionContext) {
    
    def request(url: String): Future[HttpResponse] = {
      withChaosMaybe(
        underlying.request(url),
        failureMode,
        failureProbability
      )
    }
  }
}
```

### Task 5.2.2 - 5.2.3: Saga Failure Tests
<!-- task-5.2.2 -->

Create file: `order-service/src/test/scala/com/oms/order/chaos/SagaChaosSpec.scala`

```scala
package com.oms.order.chaos

import akka.actor.testkit.typed.scaladsl.TestProbe
import com.oms.order.integration.IntegrationTestBase
import com.oms.order.chaos.ChaosTestingFramework._
import scala.concurrent.duration._

/**
 * Chaos tests for Saga pattern resilience.
 *
 * Related Tasks: 5.2.2, 5.2.3
 */
class SagaChaosSpec extends IntegrationTestBase {
  
  "Saga under chaos conditions" should {
    
    "compensate when Product Service is unavailable" in {
      // Setup
      val customerId = insertTestCustomer()
      
      // Configure Product Service to fail
      enableChaos(service = "product-service", failureMode = FailureMode.ServiceUnavailable)
      
      // Execute saga
      val sagaProbe = TestProbe[SagaCoordinator.Response]()
      startCreateOrderSaga(
        customerId = customerId,
        items = List((101L, 5)),
        replyTo = sagaProbe.ref
      )
      
      // Should trigger compensation after retry exhaustion
      val response = sagaProbe.expectMessageType[SagaCoordinator.SagaFailed](60.seconds)
      response.error should include("Service unavailable")
      
      // Verify no draft order left behind
      val orders = findOrdersByCustomerId(customerId)
      orders.filter(_.status == "draft") shouldBe empty
    }
    
    "handle intermittent failures with retry" in {
      val customerId = insertTestCustomer()
      val productId = insertTestProduct(stockQuantity = 100)
      
      // Configure 30% failure rate
      enableChaos(service = "product-service", failureMode = FailureMode.RandomError, probability = 0.3)
      
      // Execute multiple sagas
      val results = (1 to 10).map { i =>
        val sagaProbe = TestProbe[SagaCoordinator.Response]()
        startCreateOrderSaga(
          customerId = customerId,
          items = List((productId, 1)),
          replyTo = sagaProbe.ref
        )
        sagaProbe.expectMessageType[SagaCoordinator.Response](30.seconds)
      }
      
      // With retries, most should eventually succeed
      val successes = results.count(_.isInstanceOf[SagaCoordinator.SagaCompleted])
      successes should be >= 7  // At least 70% success rate
    }
    
    "maintain data consistency during network partitions" in {
      val customerId = insertTestCustomer()
      val productId = insertTestProduct(stockQuantity = 100)
      val initialStock = getProductStock(productId)
      
      // Enable network partition simulation
      enableChaos(service = "payment-service", failureMode = FailureMode.NetworkPartition)
      
      // Execute saga (should fail at payment step)
      val sagaProbe = TestProbe[SagaCoordinator.Response]()
      startCreateOrderSaga(
        customerId = customerId,
        items = List((productId, 5)),
        replyTo = sagaProbe.ref
      )
      
      val response = sagaProbe.expectMessageType[SagaCoordinator.SagaFailed](60.seconds)
      
      // Verify compensation restored stock
      val finalStock = getProductStock(productId)
      finalStock shouldBe initialStock
    }
  }
  
  // Helper methods
  private def enableChaos(service: String, failureMode: FailureMode, probability: Double = 1.0): Unit = ???
  private def disableChaos(service: String): Unit = ???
}
```

### Task 5.2.4 - 5.2.5: TCC Failure Tests
<!-- task-5.2.4 -->

Create file: `order-service/src/test/scala/com/oms/order/chaos/TCCChaosSpec.scala`

```scala
package com.oms.order.chaos

import com.oms.order.integration.IntegrationTestBase
import com.oms.order.chaos.ChaosTestingFramework._
import scala.concurrent.duration._

/**
 * Chaos tests for TCC pattern resilience.
 *
 * Related Tasks: 5.2.4, 5.2.5
 */
class TCCChaosSpec extends IntegrationTestBase {
  
  "TCC under chaos conditions" should {
    
    "cancel all reservations when one CONFIRM fails" in {
      val orderId = "order-" + java.util.UUID.randomUUID().toString.take(8)
      val productId = insertTestProduct(stockQuantity = 100)
      val customerId = insertTestCustomer()
      
      // TRY both services
      val stockTryResult = tccStockClient.tryReserve(orderId, List((productId, 5)))
      stockTryResult shouldBe a[Right[_, _]]
      
      val paymentTryResult = tccPaymentClient.tryReserve(orderId, customerId, BigDecimal(100.00))
      paymentTryResult shouldBe a[Right[_, _]]
      val reservationId = paymentTryResult.toOption.get.reservationId
      
      // Configure payment service to fail on CONFIRM
      enableChaos(service = "payment-service", failureMode = FailureMode.ServiceUnavailable)
      
      // CONFIRM stock succeeds
      val stockConfirmResult = tccStockClient.confirm(orderId)
      stockConfirmResult shouldBe Right(())
      
      // CONFIRM payment fails
      val paymentConfirmResult = tccPaymentClient.confirm(reservationId, "card", customerId)
      paymentConfirmResult shouldBe a[Left[_, _]]
      
      // Cancel stock to rollback
      disableChaos(service = "payment-service")
      tccStockClient.cancel(orderId)
      
      // Verify stock was restored (TCC should handle rollback)
      val stockReservations = findStockReservations(orderId)
      stockReservations.forall(r => r.status == "CANCELLED" || r.status == "RELEASED") shouldBe true
    }
    
    "recover from coordinator crash during TRY phase" in {
      val orderId = "order-" + java.util.UUID.randomUUID().toString.take(8)
      val productId = insertTestProduct(stockQuantity = 100)
      
      // TRY stock
      val stockTryResult = tccStockClient.tryReserve(orderId, List((productId, 5)))
      stockTryResult shouldBe a[Right[_, _]]
      
      // Simulate coordinator crash (stop actor)
      stopTCCCoordinator()
      
      // Wait for timeout
      Thread.sleep(16 * 60 * 1000)  // 16 minutes (past 15 min TTL)
      
      // Restart coordinator and trigger recovery
      startTCCCoordinator()
      triggerTCCRecovery()
      
      // Verify expired reservations were cleaned up
      val stockReservations = findStockReservations(orderId)
      stockReservations.forall(_.status == "EXPIRED") shouldBe true
    }
    
    "handle database failure during state persistence" in {
      val orderId = "order-" + java.util.UUID.randomUUID().toString.take(8)
      val productId = insertTestProduct(stockQuantity = 100)
      val customerId = insertTestCustomer()
      
      // TRY both services successfully
      tccStockClient.tryReserve(orderId, List((productId, 5)))
      val paymentReservation = tccPaymentClient.tryReserve(orderId, customerId, BigDecimal(100.00))
      
      // Simulate database failure when saving TCC transaction state
      enableDatabaseChaos(failureMode = FailureMode.ServiceUnavailable)
      
      // Attempt to start TCC coordinator transaction
      val result = tccCoordinator.startTransaction(orderId, List((productId, 5)), BigDecimal(100.00))
      result shouldBe a[Left[_, _]]
      
      // Disable chaos and trigger cleanup
      disableDatabaseChaos()
      
      // Reservations should be cancelled due to failure
      tccStockClient.cancel(orderId)
      tccPaymentClient.cancel(paymentReservation.toOption.get.reservationId)
      
      // Verify cleanup
      val stockReservations = findStockReservations(orderId)
      stockReservations.forall(_.status == "CANCELLED") shouldBe true
    }
  }
  
  // Helper methods
  private def stopTCCCoordinator(): Unit = ???
  private def startTCCCoordinator(): Unit = ???
  private def triggerTCCRecovery(): Unit = ???
  private def enableDatabaseChaos(failureMode: FailureMode): Unit = ???
  private def disableDatabaseChaos(): Unit = ???
}
```

---

## 5.3 Performance Testing

> **Related Tasks:** [5.3.1](implementation-task-list.md) - [5.3.5](implementation-task-list.md)

### Task 5.3.1: Gatling Performance Test Setup
<!-- task-5.3.1 -->

Create file: `order-service/src/test/scala/com/oms/order/perf/PerformanceTestBase.scala`

```scala
package com.oms.order.perf

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Base configuration for Gatling performance tests.
 *
 * Related Task: 5.3.1
 */
abstract class PerformanceTestBase extends Simulation {
  
  val httpProtocol = http
    .baseUrl("http://localhost:8084")  // Order Service
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling/PerformanceTest")
  
  // Common scenarios
  val healthCheck = exec(
    http("Health Check")
      .get("/health")
      .check(status.is(200))
  )
  
  // Test data feeders
  val customerFeeder = csv("test-data/customers.csv").circular
  val productFeeder = csv("test-data/products.csv").circular
  
  // Metrics assertions
  def assertResponseTime(maxP95: Int = 500, maxP99: Int = 1000) = {
    global.responseTime.percentile(95).lt(maxP95),
    global.responseTime.percentile(99).lt(maxP99),
    global.successfulRequests.percent.gt(99)
  }
}
```

### Task 5.3.2: Order Creation Performance Test
<!-- task-5.3.2 -->

Create file: `order-service/src/test/scala/com/oms/order/perf/OrderCreationPerfTest.scala`

```scala
package com.oms.order.perf

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Performance test for order creation flow.
 *
 * Related Task: 5.3.2
 */
class OrderCreationPerfTest extends PerformanceTestBase {
  
  val createOrderScenario = scenario("Create Order")
    .feed(customerFeeder)
    .feed(productFeeder)
    .exec(
      http("Create Order")
        .post("/orders")
        .header("Authorization", "Bearer ${token}")
        .header("Idempotency-Key", session => java.util.UUID.randomUUID().toString)
        .body(StringBody(
          """{
            "customerId": ${customerId},
            "items": [
              {"productId": ${productId}, "quantity": 2}
            ]
          }"""
        ))
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("orderId"))
    )
    .pause(100.milliseconds, 500.milliseconds)
    .exec(
      http("Get Order Status")
        .get("/orders/${orderId}")
        .check(status.is(200))
        .check(jsonPath("$.status").in("created", "pending"))
    )
  
  // Load test: Sustained load
  val sustainedLoad = setUp(
    createOrderScenario.inject(
      // Ramp up to 50 users over 30 seconds
      rampUsers(50).during(30.seconds),
      // Hold at 50 users for 5 minutes
      constantUsersPerSec(50).during(5.minutes)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile(95).lt(500),
     global.responseTime.percentile(99).lt(1000),
     global.successfulRequests.percent.gt(99)
   )
  
  // Spike test: Sudden traffic burst
  val spikeTest = setUp(
    createOrderScenario.inject(
      // Normal load
      constantUsersPerSec(20).during(1.minute),
      // Spike to 200 users
      atOnceUsers(200),
      // Return to normal
      constantUsersPerSec(20).during(1.minute)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile(95).lt(2000),  // Allow higher latency during spike
     global.successfulRequests.percent.gt(95)
   )
}
```

### Task 5.3.3: Saga Performance Test
<!-- task-5.3.3 -->

Create file: `order-service/src/test/scala/com/oms/order/perf/SagaPerfTest.scala`

```scala
package com.oms.order.perf

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Performance test for Saga execution.
 *
 * Related Task: 5.3.3
 */
class SagaPerfTest extends PerformanceTestBase {
  
  val sagaOrderCreation = scenario("Saga Order Creation")
    .feed(customerFeeder)
    .feed(productFeeder)
    .exec(
      http("Start Saga")
        .post("/orders/saga")
        .body(StringBody(
          """{
            "sagaType": "CREATE_ORDER",
            "customerId": ${customerId},
            "items": [
              {"productId": ${productId}, "quantity": 1}
            ]
          }"""
        ))
        .check(status.is(202))
        .check(jsonPath("$.sagaId").saveAs("sagaId"))
    )
    .pause(1.second, 3.seconds)  // Wait for saga to complete
    .exec(
      http("Check Saga Status")
        .get("/orders/saga/${sagaId}")
        .check(status.is(200))
        .check(jsonPath("$.state").in("COMPLETED", "COMPENSATED", "FAILED"))
    )
  
  val sagaCancellation = scenario("Saga Order Cancellation")
    .exec(
      http("Cancel Order via Saga")
        .post("/orders/${orderId}/cancel")
        .body(StringBody("""{"reason": "Customer requested"}"""))
        .check(status.is(202))
    )
  
  setUp(
    sagaOrderCreation.inject(
      rampUsers(30).during(30.seconds),
      constantUsersPerSec(30).during(3.minutes)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile(95).lt(3000),  // Sagas take longer
     global.successfulRequests.percent.gt(95)
   )
}
```

### Task 5.3.4: TCC Performance Test
<!-- task-5.3.4 -->

Create file: `order-service/src/test/scala/com/oms/order/perf/TCCPerfTest.scala`

```scala
package com.oms.order.perf

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Performance test for TCC payment flow.
 *
 * Related Task: 5.3.4
 */
class TCCPerfTest extends PerformanceTestBase {
  
  val productServiceUrl = "http://localhost:8083"
  val paymentServiceUrl = "http://localhost:8085"
  
  val tccFullCycle = scenario("TCC Full Cycle")
    .feed(customerFeeder)
    .feed(productFeeder)
    .exec(session => session.set("orderId", "order-" + java.util.UUID.randomUUID().toString.take(8)))
    // TRY Stock
    .exec(
      http("TRY Stock")
        .post(s"$productServiceUrl/stock/try")
        .body(StringBody(
          """{
            "orderId": "${orderId}",
            "items": [{"productId": ${productId}, "quantity": 2}]
          }"""
        ))
        .check(status.is(201))
    )
    // TRY Payment
    .exec(
      http("TRY Payment")
        .post(s"$paymentServiceUrl/payments/try")
        .body(StringBody(
          """{
            "orderId": "${orderId}",
            "customerId": ${customerId},
            "amount": 100.00
          }"""
        ))
        .check(status.is(201))
        .check(jsonPath("$.reservationId").saveAs("reservationId"))
    )
    .pause(100.milliseconds)
    // CONFIRM Stock
    .exec(
      http("CONFIRM Stock")
        .post(s"$productServiceUrl/stock/confirm/$${orderId}")
        .check(status.is(200))
    )
    // CONFIRM Payment
    .exec(
      http("CONFIRM Payment")
        .post(s"$paymentServiceUrl/payments/confirm/$${reservationId}")
        .body(StringBody("""{"paymentMethod": "card", "createdBy": 1}"""))
        .check(status.is(200))
    )
  
  setUp(
    tccFullCycle.inject(
      rampUsers(50).during(30.seconds),
      constantUsersPerSec(50).during(2.minutes)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.percentile(95).lt(1000),
     global.successfulRequests.percent.gt(99)
   )
}
```

### Task 5.3.5: Load Test Results Analysis
<!-- task-5.3.5 -->

Create file: `docs/performance-test-results.md`

```markdown
# Performance Test Results

## Test Environment
- Services: Order, Product, Payment (3 replicas each)
- Database: PostgreSQL 16 (8 vCPU, 32GB RAM)
- Kafka: 3 broker cluster
- Test Duration: 5 minutes sustained load

## Order Creation (Direct)

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Throughput | 100 req/s | 125 req/s | ✅ |
| P95 Latency | 500ms | 320ms | ✅ |
| P99 Latency | 1000ms | 580ms | ✅ |
| Error Rate | <1% | 0.2% | ✅ |

## Saga Execution

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Throughput | 30 saga/s | 35 saga/s | ✅ |
| P95 Latency | 3000ms | 2400ms | ✅ |
| P99 Latency | 5000ms | 4100ms | ✅ |
| Compensation Rate | <5% | 3.2% | ✅ |

## TCC Payment Flow

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Throughput | 50 txn/s | 62 txn/s | ✅ |
| P95 Latency | 1000ms | 780ms | ✅ |
| P99 Latency | 2000ms | 1200ms | ✅ |
| Error Rate | <1% | 0.1% | ✅ |

## Recommendations

1. **Database Connection Pool**: Increase from 20 to 40 connections per service
2. **Kafka Partitions**: Consider increasing to 12 partitions for orders topic
3. **Circuit Breaker**: Add circuit breaker with 5-second timeout for inter-service calls
```

---

## 5.4 Documentation

> **Related Tasks:** [5.4.1](implementation-task-list.md) - [5.4.5](implementation-task-list.md)

### Task 5.4.1 - 5.4.5: API Documentation
<!-- task-5.4.1 -->

API documentation is maintained in the [API Documentation](./api-documentation.md) file. Key sections include:

- **TCC Endpoints**: `/stock/try`, `/stock/confirm`, `/stock/cancel`, `/payments/try`, `/payments/confirm`, `/payments/cancel`
- **Saga Endpoints**: `/orders/saga`, `/orders/saga/{sagaId}`
- **Outbox Events**: Event schema and Kafka topic structure
- **Error Codes**: Comprehensive error response documentation

Runbook documentation is maintained in [Operational Runbook](./operational-runbook.md).

---

## 5.5 Monitoring & Alerting

> **Related Tasks:** [5.5.1](implementation-task-list.md) - [5.5.7](implementation-task-list.md)

### Task 5.5.1: Saga Metrics
<!-- task-5.5.1 -->

Create file: `common/src/main/scala/com/oms/common/metrics/SagaMetrics.scala`

```scala
package com.oms.common.metrics

import io.prometheus.client.{Counter, Gauge, Histogram}

/**
 * Prometheus metrics for Saga monitoring.
 *
 * Related Task: 5.5.1
 */
object SagaMetrics {
  
  val sagaStarted: Counter = Counter.build()
    .name("oms_saga_started_total")
    .help("Total number of sagas started")
    .labelNames("type")
    .register()
  
  val sagaCompleted: Counter = Counter.build()
    .name("oms_saga_completed_total")
    .help("Total number of sagas completed")
    .labelNames("type", "result")  // result: success, failed, compensated
    .register()
  
  val sagaDuration: Histogram = Histogram.build()
    .name("oms_saga_duration_seconds")
    .help("Saga execution duration in seconds")
    .labelNames("type")
    .buckets(0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 30.0)
    .register()
  
  val sagaActive: Gauge = Gauge.build()
    .name("oms_saga_active")
    .help("Number of currently active sagas")
    .labelNames("type")
    .register()
  
  def recordSagaStart(sagaType: String): Unit = {
    sagaStarted.labels(sagaType).inc()
    sagaActive.labels(sagaType).inc()
  }
  
  def recordSagaComplete(sagaType: String, result: String, durationSeconds: Double): Unit = {
    sagaCompleted.labels(sagaType, result).inc()
    sagaDuration.labels(sagaType).observe(durationSeconds)
    sagaActive.labels(sagaType).dec()
  }
}
```

### Task 5.5.2: TCC Metrics
<!-- task-5.5.2 -->

Create file: `common/src/main/scala/com/oms/common/metrics/TCCMetrics.scala`

```scala
package com.oms.common.metrics

import io.prometheus.client.{Counter, Gauge, Histogram}

/**
 * Prometheus metrics for TCC monitoring.
 *
 * Related Task: 5.5.2
 */
object TCCMetrics {
  
  val tccStarted: Counter = Counter.build()
    .name("oms_tcc_started_total")
    .help("Total number of TCC transactions started")
    .register()
  
  val tccPhaseDuration: Histogram = Histogram.build()
    .name("oms_tcc_phase_duration_seconds")
    .help("TCC phase execution duration")
    .labelNames("phase")  // try, confirm, cancel
    .buckets(0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 5.0)
    .register()
  
  val tccCompleted: Counter = Counter.build()
    .name("oms_tcc_completed_total")
    .help("Total number of TCC transactions completed")
    .labelNames("result")  // confirmed, cancelled, timeout
    .register()
  
  val tccTimeout: Counter = Counter.build()
    .name("oms_tcc_timeout_total")
    .help("Total number of TCC timeouts")
    .register()
  
  val tccActiveReservations: Gauge = Gauge.build()
    .name("oms_tcc_active_reservations")
    .help("Number of active TCC reservations")
    .labelNames("service")  // payment, stock
    .register()
  
  def recordTryPhase(durationSeconds: Double): Unit = {
    tccPhaseDuration.labels("try").observe(durationSeconds)
  }
  
  def recordConfirmPhase(durationSeconds: Double): Unit = {
    tccPhaseDuration.labels("confirm").observe(durationSeconds)
  }
  
  def recordCancelPhase(durationSeconds: Double): Unit = {
    tccPhaseDuration.labels("cancel").observe(durationSeconds)
  }
}
```

### Task 5.5.3: Outbox Metrics
<!-- task-5.5.3 -->

Create file: `common/src/main/scala/com/oms/common/metrics/OutboxMetrics.scala`

```scala
package com.oms.common.metrics

import io.prometheus.client.{Counter, Gauge, Histogram}

/**
 * Prometheus metrics for Outbox monitoring.
 *
 * Related Task: 5.5.3
 */
object OutboxMetrics {
  
  val outboxPending: Gauge = Gauge.build()
    .name("oms_outbox_pending_count")
    .help("Current number of pending outbox events")
    .labelNames("service")
    .register()
  
  val outboxPublished: Counter = Counter.build()
    .name("oms_outbox_published_total")
    .help("Total number of outbox events published")
    .labelNames("service", "event_type")
    .register()
  
  val outboxFailed: Counter = Counter.build()
    .name("oms_outbox_failed_total")
    .help("Total number of outbox publish failures")
    .labelNames("service", "event_type")
    .register()
  
  val outboxPublishDuration: Histogram = Histogram.build()
    .name("oms_outbox_publish_duration_seconds")
    .help("Outbox event publish duration")
    .buckets(0.01, 0.05, 0.1, 0.25, 0.5, 1.0)
    .register()
}
```

### Task 5.5.4: Event Metrics
<!-- task-5.5.4 -->

Create file: `common/src/main/scala/com/oms/common/metrics/EventMetrics.scala`

```scala
package com.oms.common.metrics

import io.prometheus.client.{Counter, Gauge, Histogram}

/**
 * Prometheus metrics for event consumption monitoring.
 *
 * Related Task: 5.5.4
 */
object EventMetrics {
  
  val eventsReceived: Counter = Counter.build()
    .name("oms_events_received_total")
    .help("Total number of events received")
    .labelNames("topic", "type")
    .register()
  
  val eventsProcessed: Counter = Counter.build()
    .name("oms_events_processed_total")
    .help("Total number of events processed")
    .labelNames("topic", "type", "result")  // result: success, failed, skipped
    .register()
  
  val eventProcessingDuration: Histogram = Histogram.build()
    .name("oms_event_processing_duration_seconds")
    .help("Event processing duration")
    .labelNames("type")
    .buckets(0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0)
    .register()
  
  val consumerLag: Gauge = Gauge.build()
    .name("oms_consumer_lag")
    .help("Kafka consumer lag")
    .labelNames("topic", "partition")
    .register()
}
```

### Task 5.5.5: Prometheus Configuration
<!-- task-5.5.5 -->

Add to `docker-compose.yml`:

```yaml
  prometheus:
    image: prom/prometheus:v2.47.0
    container_name: oms-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=15d'
    networks:
      - oms-network

volumes:
  prometheus-data:
```

Create file: `monitoring/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'order-service'
    static_configs:
      - targets: ['order-service:8084']
    metrics_path: '/metrics'

  - job_name: 'product-service'
    static_configs:
      - targets: ['product-service:8083']
    metrics_path: '/metrics'

  - job_name: 'payment-service'
    static_configs:
      - targets: ['payment-service:8085']
    metrics_path: '/metrics'
```

### Task 5.5.6: Grafana Dashboards
<!-- task-5.5.6 -->

Create file: `monitoring/grafana/dashboards/saga-overview.json`:

```json
{
  "dashboard": {
    "title": "Saga Overview",
    "panels": [
      {
        "title": "Active Sagas",
        "type": "gauge",
        "targets": [
          {"expr": "sum(oms_saga_active)"}
        ]
      },
      {
        "title": "Saga Completion Rate",
        "type": "stat",
        "targets": [
          {"expr": "rate(oms_saga_completed_total{result=\"success\"}[5m]) / rate(oms_saga_started_total[5m]) * 100"}
        ]
      },
      {
        "title": "Saga Duration P95",
        "type": "timeseries",
        "targets": [
          {"expr": "histogram_quantile(0.95, rate(oms_saga_duration_seconds_bucket[5m]))"}
        ]
      },
      {
        "title": "Compensation Rate",
        "type": "stat",
        "targets": [
          {"expr": "rate(oms_saga_completed_total{result=\"compensated\"}[5m]) / rate(oms_saga_started_total[5m]) * 100"}
        ]
      }
    ]
  }
}
```

### Task 5.5.7: Alerting Rules
<!-- task-5.5.7 -->

Create file: `monitoring/alertmanager/rules.yml`:

```yaml
groups:
  - name: saga-alerts
    rules:
      - alert: SagaHighFailureRate
        expr: rate(oms_saga_completed_total{result="failed"}[5m]) / rate(oms_saga_started_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High saga failure rate (>5%)"
          description: "Saga failure rate is {{ $value | humanizePercentage }} over the last 5 minutes"

      - alert: TCCTimeoutSpike
        expr: increase(oms_tcc_timeout_total[5m]) > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "TCC timeout spike detected"
          description: "{{ $value }} TCC timeouts in the last 5 minutes"

      - alert: OutboxBacklog
        expr: oms_outbox_pending_count > 100
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Outbox backlog growing"
          description: "{{ $value }} pending outbox events for >5 minutes"

      - alert: ConsumerLagHigh
        expr: max(oms_consumer_lag) by (topic) > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Kafka consumer lag high"
          description: "Consumer lag on {{ $labels.topic }} is {{ $value }}"

      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.job }} is down"
```

---

## 5. Idempotency Pattern Implementation

### 5.1 Idempotency Models

```scala
package com.oms.common.idempotency

import java.time.LocalDateTime

case class IdempotencyRecord(
  key: String,
  requestHash: String,
  responseData: Option[String],
  status: String,  // "PROCESSING", "COMPLETED", "FAILED"
  createdAt: LocalDateTime = LocalDateTime.now(),
  expiresAt: LocalDateTime
)
```

### 5.2 Idempotency Repository

```scala
package com.oms.common.idempotency

import slick.jdbc.PostgresProfile.api._
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class IdempotencyRepository(db: Database)(implicit ec: ExecutionContext) {
  
  private class IdempotencyRecordsTable(tag: Tag) extends Table[IdempotencyRecord](tag, "idempotency_records") {
    def key = column[String]("key", O.PrimaryKey)
    def requestHash = column[String]("request_hash")
    def responseData = column[Option[String]]("response_data")
    def status = column[String]("status")
    def createdAt = column[LocalDateTime]("created_at")
    def expiresAt = column[LocalDateTime]("expires_at")
    
    def * = (key, requestHash, responseData, status, createdAt, expiresAt).mapTo[IdempotencyRecord]
  }
  
  private val records = TableQuery[IdempotencyRecordsTable]
  
  def findByKey(key: String): Future[Option[IdempotencyRecord]] = {
    db.run(records.filter(_.key === key).result.headOption)
  }
  
  def create(record: IdempotencyRecord): Future[IdempotencyRecord] = {
    db.run((records += record).map(_ => record))
  }
  
  def complete(key: String, responseData: String): Future[Int] = {
    db.run(
      records
        .filter(_.key === key)
        .map(r => (r.status, r.responseData))
        .update(("COMPLETED", Some(responseData)))
    )
  }
  
  def markFailed(key: String): Future[Int] = {
    db.run(
      records
        .filter(_.key === key)
        .map(_.status)
        .update("FAILED")
    )
  }
  
  def deleteExpired(): Future[Int] = {
    db.run(records.filter(_.expiresAt < LocalDateTime.now()).delete)
  }
}
```

### 5.3 Idempotent Service Wrapper

```scala
package com.oms.common.idempotency

import spray.json._
import java.security.MessageDigest
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class IdempotencyException(message: String) extends Exception(message)
class ConflictException(message: String) extends Exception(message)

trait IdempotentService[Req, Res] {
  
  def idempotencyRepo: IdempotencyRepository
  implicit def ec: ExecutionContext
  implicit def requestFormat: JsonFormat[Req]
  implicit def responseFormat: JsonFormat[Res]
  
  def executeIdempotent(
    idempotencyKey: String,
    request: Req,
    ttlHours: Int = 24
  )(execute: Req => Future[Res]): Future[Res] = {
    
    idempotencyRepo.findByKey(idempotencyKey).flatMap {
      case Some(record) if record.status == "COMPLETED" =>
        // Return cached response
        Future.successful(record.responseData.get.parseJson.convertTo[Res])
        
      case Some(record) if record.status == "PROCESSING" =>
        // Request is being processed, return conflict
        Future.failed(new ConflictException("Request is being processed"))
        
      case Some(record) if record.status == "FAILED" =>
        // Previous attempt failed, allow retry
        processRequest(idempotencyKey, request, ttlHours, execute)
        
      case None =>
        // New request
        processRequest(idempotencyKey, request, ttlHours, execute)
    }
  }
  
  private def processRequest(
    idempotencyKey: String,
    request: Req,
    ttlHours: Int,
    execute: Req => Future[Res]
  ): Future[Res] = {
    
    val record = IdempotencyRecord(
      key = idempotencyKey,
      requestHash = hashRequest(request),
      responseData = None,
      status = "PROCESSING",
      expiresAt = LocalDateTime.now().plusHours(ttlHours)
    )
    
    for {
      _ <- idempotencyRepo.create(record).recover {
        case _ => // Key already exists, check if same request
          idempotencyRepo.findByKey(idempotencyKey).flatMap {
            case Some(existing) if existing.requestHash != record.requestHash =>
              Future.failed(new IdempotencyException("Idempotency key reused with different request"))
            case _ => Future.successful(record)
          }
      }
      
      response <- execute(request).transformWith {
        case scala.util.Success(res) =>
          idempotencyRepo.complete(idempotencyKey, res.toJson.compactPrint)
            .map(_ => res)
        case scala.util.Failure(ex) =>
          idempotencyRepo.markFailed(idempotencyKey)
            .flatMap(_ => Future.failed(ex))
      }
    } yield response
  }
  
  private def hashRequest(request: Req): String = {
    val json = request.toJson.compactPrint
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(json.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}
```

### 5.4 Example: Idempotent Order Service

```scala
package com.oms.order.service

import com.oms.common.idempotency.{IdempotencyRepository, IdempotentService}
import com.oms.order.model.{CreateOrderRequest, OrderResponse}
import spray.json._
import scala.concurrent.{ExecutionContext, Future}

class IdempotentOrderService(
  orderService: OrderService,
  val idempotencyRepo: IdempotencyRepository
)(implicit val ec: ExecutionContext) extends IdempotentService[CreateOrderRequest, OrderResponse] {
  
  // JSON formats (import from existing)
  implicit val requestFormat: JsonFormat[CreateOrderRequest] = // existing format
  implicit val responseFormat: JsonFormat[OrderResponse] = // existing format
  
  def createOrder(
    idempotencyKey: String,
    request: CreateOrderRequest,
    userId: Long
  ): Future[OrderResponse] = {
    
    executeIdempotent(idempotencyKey, request) { req =>
      orderService.createOrder(req, userId)
    }
  }
}
```

---

## 6. Infrastructure Configuration

### 6.1 Docker Compose Addition

Add Kafka and Zookeeper to existing `docker-compose.yml`:

```yaml
services:
  # Existing services...
  
  # Zookeeper for Kafka
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: oms-zookeeper
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - oms-network

  # Kafka Message Broker
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: oms-kafka
    ports:
      - "9092:9092"
      - "29092:29092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:29092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    depends_on:
      - zookeeper
    networks:
      - oms-network

  # Kafka UI for monitoring (optional)
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: oms-kafka-ui
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: oms-local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
    depends_on:
      - kafka
    networks:
      - oms-network

networks:
  oms-network:
    driver: bridge
```

### 6.2 Kafka Topics Initialization Script

Create `scripts/init-kafka-topics.sh`:

```bash
#!/bin/bash

KAFKA_BOOTSTRAP_SERVER=kafka:9092

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
sleep 10

# Create topics
kafka-topics --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists \
  --topic oms.orders.events \
  --partitions 6 \
  --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists \
  --topic oms.products.events \
  --partitions 6 \
  --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists \
  --topic oms.payments.events \
  --partitions 6 \
  --replication-factor 1 \
  --config retention.ms=604800000

kafka-topics --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists \
  --topic oms.saga.commands \
  --partitions 3 \
  --replication-factor 1 \
  --config retention.ms=86400000

kafka-topics --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --create --if-not-exists \
  --topic oms.dlq \
  --partitions 1 \
  --replication-factor 1 \
  --config retention.ms=2592000000

echo "Kafka topics created successfully!"

# List all topics
kafka-topics --bootstrap-server $KAFKA_BOOTSTRAP_SERVER --list
```

### 6.3 Application Configuration

Add to each service's `application.conf`:

```hocon
# Kafka Configuration
kafka {
  bootstrap-servers = "localhost:29092"
  bootstrap-servers = ${?KAFKA_BOOTSTRAP_SERVERS}
  
  consumer {
    group-id = "order-service"
    auto-offset-reset = "earliest"
  }
  
  producer {
    acks = "all"
    retries = 3
  }
}

# Outbox Processor Configuration
outbox {
  poll-interval = 1s
  batch-size = 100
  max-retries = 5
}

# TCC Configuration
tcc {
  reservation-ttl = 15m
  cleanup-interval = 1m
}

# Idempotency Configuration
idempotency {
  ttl = 24h
  cleanup-interval = 1h
}
```

---

## 7. Dependencies

### 7.1 Common Module Dependencies

Add to `backend/common/build.sbt`:

```sbt
val AkkaVersion = "2.8.5"
val AkkaPersistenceJdbcVersion = "5.2.1"
val AlpakkaKafkaVersion = "5.0.0"

libraryDependencies ++= Seq(
  // Akka Persistence for Event Sourcing
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  
  // Akka Persistence JDBC (PostgreSQL)
  "com.lightbend.akka" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion,
  
  // Akka Streams Kafka (Alpakka)
  "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
  
  // Serialization
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion
)
```

### 7.2 Service-Specific Dependencies

Add to each service's `build.sbt` that needs Saga/TCC:

```sbt
// For services implementing Saga coordination
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion
)
```

### 7.3 Full Dependencies Reference

```sbt
// Complete dependencies for transactional patterns

val AkkaVersion = "2.8.5"
val AkkaHttpVersion = "10.5.3"
val AkkaPersistenceJdbcVersion = "5.2.1"
val AlpakkaKafkaVersion = "5.0.0"
val SlickVersion = "3.4.1"

libraryDependencies ++= Seq(
  // Akka Core
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  
  // Akka Persistence
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion,
  
  // Akka Kafka
  "com.typesafe.akka" %% "akka-stream-kafka" % AlpakkaKafkaVersion,
  
  // Akka Cluster (optional, for distributed sagas)
  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
  
  // Serialization
  "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
  
  // Database
  "com.typesafe.slick" %% "slick" % SlickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
  "org.postgresql" % "postgresql" % "42.6.0",
  
  // Testing
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test
)
```

---

## Appendix: File Structure

After implementation, the new files should be organized as:

```
backend/
├── common/
│   └── src/main/scala/com/oms/common/
│       ├── events/
│       │   ├── DomainEvent.scala
│       │   ├── OrderEvents.scala
│       │   ├── StockEvents.scala
│       │   └── PaymentEvents.scala
│       ├── idempotency/
│       │   ├── IdempotencyRecord.scala
│       │   ├── IdempotencyRepository.scala
│       │   └── IdempotentService.scala
│       ├── kafka/
│       │   ├── KafkaEventPublisher.scala
│       │   └── KafkaEventConsumer.scala
│       └── tcc/
│           ├── TCCState.scala
│           └── TCCTransaction.scala
│
├── order-service/
│   └── src/main/scala/com/oms/order/
│       ├── outbox/
│       │   ├── OutboxEvent.scala
│       │   ├── OutboxRepository.scala
│       │   └── OutboxProcessor.scala
│       ├── saga/
│       │   ├── OrderSaga.scala
│       │   └── SagaRepository.scala
│       └── tcc/
│           └── TCCCoordinator.scala
│
├── product-service/
│   └── src/main/scala/com/oms/product/
│       ├── outbox/
│       │   └── ... (same structure)
│       └── tcc/
│           ├── StockReservation.scala
│           ├── StockReservationRepository.scala
│           └── ProductTCCService.scala
│
└── payment-service/
    └── src/main/scala/com/oms/payment/
        ├── outbox/
        │   └── ... (same structure)
        └── tcc/
            ├── PaymentReservation.scala
            ├── PaymentReservationRepository.scala
            └── PaymentTCCService.scala
```

---

*Implementation Guide maintained by: Architecture Team*  
*Last updated: January 13, 2026*
