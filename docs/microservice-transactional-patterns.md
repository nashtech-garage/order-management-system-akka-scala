# Microservice Transactional Patterns Analysis

## Order Management System (OMS)

**Document Version:** 1.0  
**Created:** January 13, 2026  
**Author:** Architecture Team  
**Implementation Guide:** [microservice-transaction-implement-guide.md](microservice-transaction-implement-guide.md)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current Architecture Analysis](#2-current-architecture-analysis)
3. [Business Scope Review](#3-business-scope-review)
4. [Problem Areas Identified](#4-problem-areas-identified)
5. [Recommended Transactional Patterns](#5-recommended-transactional-patterns)
6. [Pattern Mapping to Use Cases](#6-pattern-mapping-to-use-cases)
7. [Implementation Strategy](#7-implementation-strategy)
8. [Technology Recommendations](#8-technology-recommendations)
   - [8.1 Akka-Based Implementation](#81-akka-based-implementation)
   - [8.2 Recommended Dependencies](#82-recommended-dependencies)
   - [8.3 Infrastructure Requirements](#83-infrastructure-requirements)
   - [8.4 Kafka Topics Structure](#84-kafka-topics-structure)
   - [8.5 Complete Tool Stack](#85-complete-tool-stack)
9. [Conclusion](#9-conclusion)

---

## 1. Executive Summary

This document analyzes the Order Management System's microservice architecture and identifies suitable **Distributed Transaction Patterns** to ensure data consistency across services. The analysis is based on the business requirements defined in `oms_requirements.md` and the current source code implementation.

### Key Findings

| Aspect | Current State | Recommendation |
|--------|---------------|----------------|
| Transaction Model | Synchronous HTTP calls | Event-driven Saga Pattern |
| Failure Handling | No compensation logic | Implement rollback/compensation |
| Event Publishing | Not implemented | Add Outbox Pattern |
| Payment Processing | Direct calls | TCC (Try-Confirm-Cancel) Pattern |

### Pattern Coordination Approach Summary

This document recommends two different coordination approaches based on the use case:

| Pattern | Coordination | Used For | Reason |
|---------|--------------|----------|--------|
| **Saga** | **Choreography** (Decentralized) | Order Creation, Order Cancellation | Long-running workflows, loose coupling, high scalability |
| **TCC** | **Orchestration** (Centralized) | Payment Processing | Short-lived transactions, strong consistency, easier timeout handling |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    COORDINATION APPROACH COMPARISON                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   CHOREOGRAPHY (Saga Pattern)         ORCHESTRATION (TCC Pattern)           │
│   ═══════════════════════════         ═══════════════════════════           │
│                                                                              │
│   ┌───────┐    ┌───────┐              ┌─────────────────────┐               │
│   │Service│───►│Service│              │    Coordinator      │               │
│   │   A   │    │   B   │              └──────────┬──────────┘               │
│   └───────┘    └───┬───┘                         │                          │
│        ▲           │                    ┌────────┼────────┐                 │
│        │           ▼                    ▼        ▼        ▼                 │
│   ┌────┴───┐   ┌───────┐           ┌──────┐ ┌──────┐ ┌──────┐              │
│   │Service │◄──│Service│           │Svc A │ │Svc B │ │Svc C │              │
│   │   D    │   │   C   │           └──────┘ └──────┘ └──────┘              │
│   └────────┘   └───────┘                                                    │
│                                                                              │
│   • Events flow between services      • Coordinator calls services          │
│   • No central control                • Central transaction control         │
│   • Each service reacts to events     • Services expose TRY/CONFIRM/CANCEL  │
│   • Compensation via events           • Coordinator handles rollback        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Current Architecture Analysis

### 2.1 Service Overview

The OMS consists of **6 microservices** communicating via **synchronous HTTP calls**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              API Gateway (:8080)                             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
          ┌───────────────────────────┼───────────────────────────┐
          │                           │                           │
          ▼                           ▼                           ▼
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  User Service   │       │ Customer Service│       │ Product Service │
│     (:8081)     │       │     (:8082)     │       │     (:8083)     │
└─────────────────┘       └─────────────────┘       └─────────────────┘
                                      │                     │
                                      │                     │
                                      ▼                     ▼
                          ┌─────────────────┐       ┌─────────────────┐
                          │  Order Service  │◄─────►│ Payment Service │
                          │     (:8084)     │       │     (:8085)     │
                          └─────────────────┘       └─────────────────┘
                                      │
                                      ▼
                          ┌─────────────────┐
                          │ Report Service  │
                          │     (:8086)     │
                          └─────────────────┘
```

### 2.2 Service Responsibilities

| Service | Port | Responsibilities |
|---------|------|------------------|
| API Gateway | 8080 | Request routing, authentication |
| User Service | 8081 | User registration, authentication, authorization |
| Customer Service | 8082 | Customer profiles, contact information |
| Product Service | 8083 | Product catalog, inventory/stock management |
| Order Service | 8084 | Order lifecycle, orchestrates payment & stock |
| Payment Service | 8085 | Payment processing, transaction records |
| Report Service | 8086 | Analytics, EOD reports, dashboard data |

### 2.3 Technology Stack

- **Language:** Scala 2.13
- **Framework:** Akka HTTP, Akka Actor Typed
- **Database:** PostgreSQL 16 with Slick ORM
- **Frontend:** Angular 20 with SSR

---

## 3. Business Scope Review

Based on the requirements document, the following business flows require distributed transaction handling:

### 3.1 Order Lifecycle States

```
┌─────────┐     ┌──────────┐     ┌──────┐     ┌───────────┐     ┌────────────┐
│  Draft  │────►│ Created  │────►│ Paid │────►│ Shipping  │────►│ Completed  │
└─────────┘     └──────────┘     └──────┘     └───────────┘     └────────────┘
     │               │              │               │
     │               │              │               └──────────────► Cancelled
     │               │              └──────────────────────────────► Cancelled
     │               └─────────────────────────────────────────────► Cancelled
     └─────────────────────────────────────────────────────────────► Cancelled
```

### 3.2 Critical Business Rules

| Rule | Description | Services Involved |
|------|-------------|-------------------|
| BR-01 | Customer must exist before order creation | Order, Customer |
| BR-02 | Products must be ACTIVE and have sufficient stock | Order, Product |
| BR-03 | Stock must be deducted upon order confirmation | Order, Product |
| BR-04 | Payment success triggers status change to Paid | Order, Payment |
| BR-05 | Cancellation must restore reserved stock | Order, Product |
| BR-06 | Cannot cancel orders in Shipping/Completed status | Order |

### 3.3 Transaction Boundaries Identified

| Transaction | Services | Data Modified |
|-------------|----------|---------------|
| Create Order | Order, Customer, Product | Order record, Stock quantity |
| Process Payment | Order, Payment | Payment record, Order status |
| Cancel Order | Order, Product | Order status, Stock quantity |
| Ship Order | Order | Order status |
| Complete Order | Order | Order status |

---

## 4. Problem Areas Identified

### 4.1 Order Creation - Partial Failure Risk

**Current Implementation Flow:**
```
1. Validate customer exists         ✓
2. Get product information          ✓
3. Check stock availability         ✓
4. Create order in database         ✓
5. Deduct stock from products       ❌ (Can fail after order created)
```

**Problem:** If step 5 fails, the order exists but stock is not deducted, leading to:
- Overselling products
- Inventory inconsistency
- No automatic rollback mechanism

**Code Location:** `OrderActor.scala` lines 45-82

```scala
// Current problematic flow
val orderCreation = for {
  customerOpt <- serviceClient.getCustomer(request.customerId)
  // ... validations ...
  (createdOrder, createdItems) <- repository.createOrder(order, orderItems)
  // ⚠️ Stock adjustment happens AFTER order creation
  _ <- Future.sequence(request.items.map { item =>
    serviceClient.adjustProductStock(item.productId, -item.quantity)
  })
} yield OrderResponse.fromOrder(createdOrder, enrichedItems, customerName)
```

### 4.2 Payment Flow - Inconsistency Risk

**Current Implementation Flow:**
```
1. Validate order status (pending)  ✓
2. Call payment service             ✓
3. Update order status              ❌ (Can fail after payment charged)
```

**Problem:** If step 3 fails after successful payment:
- Customer is charged
- Order status remains "pending"
- Manual intervention required

**Code Location:** `OrderActor.scala` lines 221-248

```scala
// Current problematic flow
val paymentProcessing = for {
  orderOpt <- repository.findById(id)
  result <- orderOpt match {
    case Some(order) if order.status == "pending" =>
      for {
        paymentInfoOpt <- serviceClient.processPayment(id, order.totalAmount, paymentMethod, token)
        result <- paymentInfoOpt match {
          case Some(info) =>
            // ⚠️ Status update can fail after payment success
            repository.updateStatus(id, "processing").map(_ => info)
          case None =>
            Future.failed(new Exception("Payment failed or rejected"))
        }
      } yield result
    // ...
  }
} yield result
```

### 4.3 Order Cancellation - Partial Recovery Risk

**Current Implementation Flow:**
```
1. Get order details                ✓
2. Validate status allows cancel    ✓
3. Restore stock for Product A      ✓
4. Restore stock for Product B      ❌ (Can fail mid-way)
5. Update order status              ? (May or may not execute)
```

**Problem:** Partial stock restoration with no compensation:
- Some products have stock restored
- Other products remain with incorrect stock
- Inconsistent inventory state

**Code Location:** `OrderActor.scala` lines 193-218

```scala
// Current problematic flow
val cancellation = for {
  orderOpt <- repository.findById(id)
  result <- orderOpt match {
    case Some(order) if order.status == "pending" || order.status == "confirmed" =>
      for {
        items <- repository.getOrderItems(id)
        // ⚠️ Partial failure possible - no transaction boundary
        _ <- Future.sequence(items.map { item =>
          serviceClient.adjustProductStock(item.productId, item.quantity)
        })
        _ <- repository.updateStatus(id, "cancelled")
      } yield true
    // ...
  }
} yield result
```

### 4.4 Summary of Issues

| Issue | Impact | Severity |
|-------|--------|----------|
| No distributed transaction coordination | Data inconsistency | High |
| Missing compensation/rollback logic | Manual intervention needed | High |
| Synchronous HTTP calls without retry | Transient failures cause permanent issues | Medium |
| No idempotency handling | Duplicate operations possible | Medium |
| No event auditing | Difficult to debug/trace issues | Low |

---

## 5. Recommended Transactional Patterns

### 5.1 Saga Pattern (Choreography-Based) ⭐ Primary Recommendation

The Saga pattern is ideal for the Order Lifecycle flow where multiple services participate in a long-running transaction.

#### 5.1.0 Why Choreography for Saga?

We chose **Choreography** over Orchestration for the Saga pattern because:

| Aspect | Choreography (Chosen) | Orchestration (Alternative) |
|--------|----------------------|-----------------------------|
| **Coupling** | Loose - services only know about events | Tight - orchestrator knows all services |
| **Single Point of Failure** | None - fully distributed | Yes - orchestrator is critical |
| **Scalability** | High - each service scales independently | Limited by orchestrator capacity |
| **Complexity** | In event routing | In orchestrator logic |
| **Best For** | Simple, linear workflows | Complex, conditional workflows |

**How Choreography Works:**
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CHOREOGRAPHY: No Central Controller                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   Service A                Service B                Service C                │
│      │                        │                        │                    │
│      │── publishes Event1 ───►│                        │                    │
│      │                        │── publishes Event2 ───►│                    │
│      │◄─────────────────────────── publishes Event3 ───│                    │
│      │                        │                        │                    │
│   Each service:                                                              │
│   • Listens for specific events                                             │
│   • Performs its local transaction                                          │
│   • Publishes result event                                                  │
│   • Knows how to compensate if needed                                       │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key Characteristics:**
- **No central coordinator** - each service reacts to events independently
- **Event-driven** - services communicate through domain events via Kafka
- **Decentralized logic** - each service contains its own compensation logic
- **Eventually consistent** - system reaches consistency through event propagation

#### 5.1.1 Pattern Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        SAGA: Order Creation                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐   ┌───────────────┐   ┌────────────────┐   ┌───────────┐ │
│  │    Order     │──►│    Product    │──►│    Payment     │──►│   Order   │ │
│  │   Service    │   │    Service    │   │    Service     │   │ Completed │ │
│  │              │   │               │   │                │   │           │ │
│  │ Create Order │   │ Reserve Stock │   │ Process Payment│   │ Mark Paid │ │
│  └──────────────┘   └───────────────┘   └────────────────┘   └───────────┘ │
│         │                  │                    │                  │        │
│         │                  │                    │                  │        │
│         ▼                  ▼                    ▼                  ▼        │
│  ┌──────────────┐   ┌───────────────┐   ┌────────────────┐                 │
│  │   Publish    │   │    Publish    │   │    Publish     │                 │
│  │ OrderCreated │   │ StockReserved │   │ PaymentSuccess │                 │
│  └──────────────┘   └───────────────┘   └────────────────┘                 │
│                                                                              │
│  ◄───────────────── COMPENSATING TRANSACTIONS ─────────────────────────────►│
│                                                                              │
│  If PaymentFailed:     If StockReserveFailed:                               │
│  - Release Stock       - Cancel Order                                        │
│  - Cancel Order                                                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 5.1.2 Event Flow Definition

**Happy Path:**
```
OrderService                ProductService              PaymentService
     │                            │                           │
     │──── OrderCreated ─────────►│                           │
     │                            │                           │
     │                            │──── StockReserved ───────►│
     │                            │                           │
     │◄────────────────────────────────── PaymentCompleted ───│
     │                            │                           │
     │  (Update to Paid)          │                           │
```

**Compensation Path:**
```
OrderService                ProductService              PaymentService
     │                            │                           │
     │◄──────────────────────────────────── PaymentFailed ────│
     │                            │                           │
     │──── ReleaseStock ─────────►│                           │
     │                            │                           │
     │◄─────── StockReleased ─────│                           │
     │                            │                           │
     │  (Update to Cancelled)     │                           │
```

#### 5.1.3 Domain Events

Define the following domain events in the `common` module to enable event-driven communication between services:

**Order Service Events:**
- `OrderCreated` - Published when a new order is created, contains order details, customer ID, items, and total amount
- `OrderConfirmed` - Published when an order transitions from draft to confirmed status
- `OrderPaid` - Published when payment is successfully processed for an order
- `OrderCancelled` - Published when an order is cancelled, includes cancellation reason
- `OrderCompleted` - Published when an order completes its lifecycle

**Product Service Events:**
- `StockReserved` - Published when stock is successfully reserved for an order
- `StockReservationFailed` - Published when stock reservation fails (insufficient stock, product not found)
- `StockReleased` - Published when reserved stock is released (order cancelled)
- `StockConfirmed` - Published when reserved stock is confirmed (order completed)

**Payment Service Events:**
- `PaymentReserved` - Published when payment amount is reserved (TCC Try phase)
- `PaymentCompleted` - Published when payment is successfully captured
- `PaymentFailed` - Published when payment processing fails
- `PaymentRefunded` - Published when a refund is initiated

> **Implementation Details:** See [microservice-transaction-implement-guide.md](microservice-transaction-implement-guide.md#2-domain-events-schema) for complete Scala code.

### 5.2 Outbox Pattern (For Reliable Event Publishing)

Ensures atomic write of business data and events to guarantee delivery.

#### 5.2.1 Pattern Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           OUTBOX PATTERN                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        Database Transaction                          │    │
│  │  ┌───────────────────┐         ┌───────────────────┐                │    │
│  │  │   Business Table  │         │   Outbox Table    │                │    │
│  │  │                   │         │                   │                │    │
│  │  │  INSERT order     │   +     │  INSERT event     │                │    │
│  │  │                   │         │                   │                │    │
│  │  └───────────────────┘         └───────────────────┘                │    │
│  │                                                                      │    │
│  │                    ATOMIC COMMIT                                     │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                        │                                     │
│                                        ▼                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                     Outbox Processor (Polling/CDC)                   │    │
│  │                                                                      │    │
│  │   1. Read unpublished events                                         │    │
│  │   2. Publish to message broker                                       │    │
│  │   3. Mark as published                                               │    │
│  │                                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                        │                                     │
│                                        ▼                                     │
│                              ┌─────────────────┐                            │
│                              │  Message Broker │                            │
│                              │  (Kafka/Redis)  │                            │
│                              └─────────────────┘                            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 5.2.2 Outbox Table Schema

Create an `outbox_events` table in each service database with the following structure:

| Column | Type | Description |
|--------|------|-------------|
| `id` | BIGSERIAL | Primary key, auto-increment |
| `aggregate_type` | VARCHAR(100) | Entity type (e.g., 'ORDER', 'PAYMENT') |
| `aggregate_id` | VARCHAR(100) | Entity ID reference |
| `event_type` | VARCHAR(100) | Event name (e.g., 'ORDER_CREATED') |
| `payload` | JSONB | Serialized event data |
| `created_at` | TIMESTAMP | Event creation time |
| `published_at` | TIMESTAMP | When event was published (NULL if pending) |
| `retry_count` | INT | Number of publish attempts |
| `status` | VARCHAR(20) | PENDING, PUBLISHED, or FAILED |

Create an index on `(status, created_at)` for efficient polling of pending events.

> **Implementation Details:** See [microservice-transaction-implement-guide.md](microservice-transaction-implement-guide.md#11-outbox-events-table) for SQL schema.

#### 5.2.3 Implementation Components

The Outbox Pattern requires the following components:

**1. OutboxEvent Model**
- Data class representing an event to be published
- Fields: id, aggregateType, aggregateId, eventType, payload, timestamps, status

**2. OutboxRepository**
- Insert events within same transaction as business data
- Query pending events for publishing
- Mark events as published or failed
- Increment retry count on failures

**3. Repository with Outbox Integration**
- Modify existing repositories to insert outbox events atomically
- Use database transactions to ensure consistency
- Example: `createOrderWithEvent()` inserts order AND outbox event in single transaction

**4. OutboxProcessor**
- Background actor/service that polls for pending events
- Publishes events to message broker (Kafka)
- Handles retries and failure marking
- Runs on configurable interval (default: 1 second)

**5. KafkaEventPublisher**
- Publishes events to appropriate Kafka topics
- Routes events based on event type prefix
- Handles serialization and delivery confirmation

> **Implementation Details:** See [microservice-transaction-implement-guide.md](microservice-transaction-implement-guide.md#3-outbox-pattern-implementation) for complete Scala implementation.

### 5.3 TCC (Try-Confirm-Cancel) Pattern

Best suited for the Payment Processing flow with its 80% success simulation.

#### 5.3.0 Why Orchestration for TCC?

We chose **Orchestration** over Choreography for the TCC pattern because:

| Aspect | Orchestration (Chosen) | Choreography (Alternative) |
|--------|------------------------|----------------------------|
| **Transaction Control** | Centralized - coordinator manages all phases | Distributed - harder to coordinate 3 phases |
| **Timeout Handling** | Single place to handle timeouts | Each service must handle timeouts |
| **State Visibility** | Clear - coordinator tracks all participants | Unclear - state spread across services |
| **Recovery** | Simple - coordinator resumes on restart | Complex - each service must recover |
| **Best For** | Short-lived, atomic operations | Long-running workflows |

**How Orchestration Works:**
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ORCHESTRATION: Central Controller                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                         ┌─────────────────┐                                 │
│                         │  TCC Coordinator │                                │
│                         │   (Orchestrator) │                                │
│                         └────────┬────────┘                                 │
│                                  │                                          │
│            ┌─────────────────────┼─────────────────────┐                    │
│            │                     │                     │                    │
│            ▼                     ▼                     ▼                    │
│     ┌─────────────┐       ┌─────────────┐       ┌─────────────┐            │
│     │   Product   │       │   Payment   │       │    Order    │            │
│     │   Service   │       │   Service   │       │   Service   │            │
│     └─────────────┘       └─────────────┘       └─────────────┘            │
│                                                                              │
│   Coordinator:                                                               │
│   • Calls TRY on all participants                                           │
│   • Waits for all responses                                                 │
│   • Calls CONFIRM if all succeed, CANCEL if any fail                        │
│   • Handles timeouts and retries                                            │
│   • Persists transaction state for recovery                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key Characteristics:**
- **Central coordinator** - TCC Coordinator manages the entire transaction
- **Synchronous calls** - coordinator waits for each phase to complete
- **Strong consistency** - all-or-nothing semantics within timeout window
- **State persistence** - coordinator saves state for crash recovery

#### 5.3.1 Pattern Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TCC PATTERN: Payment Flow                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                              ┌─────────────┐                                │
│                              │   INITIAL   │                                │
│                              └──────┬──────┘                                │
│                                     │                                        │
│                                     ▼                                        │
│   ┌─────────────────────────────────────────────────────────────────┐       │
│   │                         TRY PHASE                                │       │
│   │                                                                  │       │
│   │  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │       │
│   │  │ Reserve Amt  │    │ Reserve Stock│    │ Lock Order   │      │       │
│   │  │ (Payment)    │    │ (Product)    │    │ (Order)      │      │       │
│   │  └──────────────┘    └──────────────┘    └──────────────┘      │       │
│   │                                                                  │       │
│   │  Status: TRYING                                                  │       │
│   └─────────────────────────────────────────────────────────────────┘       │
│                                     │                                        │
│                    ┌────────────────┼────────────────┐                      │
│                    │                │                │                      │
│               ALL SUCCESS      ANY FAILURE      TIMEOUT                     │
│                    │                │                │                      │
│                    ▼                ▼                ▼                      │
│   ┌────────────────────┐    ┌────────────────────────────────────┐         │
│   │   CONFIRM PHASE    │    │          CANCEL PHASE              │         │
│   │                    │    │                                    │         │
│   │ ┌──────────────┐   │    │ ┌──────────────┐ ┌──────────────┐ │         │
│   │ │ Capture Amt  │   │    │ │ Release Amt  │ │ Release Stock│ │         │
│   │ │ Deduct Stock │   │    │ │ Unlock Order │ │              │ │         │
│   │ │ Update Order │   │    │ └──────────────┘ └──────────────┘ │         │
│   │ └──────────────┘   │    │                                    │         │
│   │                    │    │ Status: CANCELLED                  │         │
│   │ Status: CONFIRMED  │    └────────────────────────────────────┘         │
│   └────────────────────┘                                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 5.3.2 TCC State Machine

Define a state machine to track TCC transaction lifecycle:

**States:**
| State | Description |
|-------|-------------|
| `Initial` | Transaction not yet started |
| `Trying` | TRY phase in progress, resources being reserved |
| `Confirmed` | All participants confirmed, transaction complete |
| `Cancelled` | Transaction rolled back, resources released |

**TCC Transaction Record:**
- `id` - Unique transaction identifier (UUID)
- `transactionType` - Type of operation ("PAYMENT", "ORDER_CREATE")
- `state` - Current TCC state
- `participants` - List of participating services with endpoints
- `createdAt` / `expiresAt` - Timestamps for timeout handling
- `confirmedAt` / `cancelledAt` - Completion timestamps

**TCC Participant Record:**
- `serviceName` - Name of participating service
- `resourceId` - ID of reserved resource
- `tryEndpoint` / `confirmEndpoint` / `cancelEndpoint` - Service endpoints
- `status` - Participant status (PENDING, TRIED, CONFIRMED, CANCELLED)

> **Implementation Details:** See [microservice-transaction-implement-guide.md](microservice-transaction-implement-guide.md#41-tcc-state-models) for Scala models.

#### 5.3.3 Payment Service TCC Implementation

Implement three TCC endpoints in Payment Service:

**TRY: Reserve Payment Amount**
- Create a `PaymentReservation` record with status "RESERVED"
- Set expiration time (default: 15 minutes)
- Do NOT charge the customer yet
- Return reservation ID for tracking

**CONFIRM: Capture Reserved Amount**
- Validate reservation exists and status is "RESERVED"
- Create actual `Payment` record with "completed" status
- Generate transaction ID
- Update reservation status to "CONFIRMED"
- This is when the customer is actually charged

**CANCEL: Release Reserved Amount**
- Update reservation status to "CANCELLED"
- No actual refund needed (payment wasn't captured)
- Called on timeout or explicit cancellation

**Additional Requirements:**
- `StockReservation` table in Product Service for stock TCC
- `PaymentReservation` table in Payment Service
- Background job to expire old reservations
- TCC Coordinator to orchestrate multi-service transactions

> **Implementation Details:** See [microservice-transaction-implement-guide.md](microservice-transaction-implement-guide.md#4-tcc-pattern-implementation) for complete implementation.

### 5.4 Idempotency Pattern

Prevents duplicate operations from retry logic.

#### 5.4.1 Idempotency Key Implementation

Implement idempotency to prevent duplicate operations from retry logic:

**Request Enhancement:**
- Add `idempotencyKey` field to requests that modify state (e.g., CreateOrderRequest)
- Client generates unique key (UUID) before sending request
- Same key = same operation, should return same result

**Idempotency Record:**
| Field | Description |
|-------|-------------|
| `key` | Primary key, client-provided idempotency key |
| `requestHash` | SHA-256 hash of request payload |
| `responseData` | Cached response (JSON serialized) |
| `status` | PROCESSING, COMPLETED, or FAILED |
| `createdAt` | Record creation timestamp |
| `expiresAt` | TTL for cached response (default: 24 hours) |

**Processing Logic:**
1. Check if idempotency key exists in database
2. If COMPLETED: return cached response
3. If PROCESSING: return 409 Conflict (prevent duplicate)
4. If FAILED or NOT EXISTS: create record with PROCESSING status, execute operation
5. On success: update status to COMPLETED, cache response
6. On failure: update status to FAILED

**Validation:**
- If same key used with different request hash → reject with error
- Prevents misuse of idempotency keys

> **Implementation Details:** See [microservice-transaction-implement-guide.md](microservice-transaction-implement-guide.md#5-idempotency-pattern-implementation) for Scala implementation.

---

## 6. Pattern Mapping to Use Cases

### 6.1 Use Case Pattern Matrix

| Use Case | Description | Primary Pattern | Secondary Pattern |
|----------|-------------|-----------------|-------------------|
| UC-03 | Create Order | **Saga** | Outbox, Idempotency |
| UC-04 | Confirm Order | **Saga** | Event Choreography |
| UC-05 | Payment | **TCC** | Saga (compensation) |
| UC-06 | Shipping | **Event Choreography** | - |
| UC-07 | Cancel Order | **Saga** (compensating) | Outbox |
| UC-08 | View Order List | N/A (read-only) | - |
| UC-10 | EOD Report | **Eventually Consistent** | - |

### 6.2 Detailed Pattern Application

#### UC-03: Create Order (Saga Pattern)

```
┌────────────────────────────────────────────────────────────────────────┐
│                    CREATE ORDER SAGA                                    │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Step 1: Create Order (Draft)                                          │
│  ├─ Service: Order Service                                             │
│  ├─ Action: Insert order record with status='draft'                    │
│  ├─ Event: OrderDraftCreated                                           │
│  └─ Compensation: Delete order record                                  │
│                                                                         │
│  Step 2: Validate Customer                                             │
│  ├─ Service: Customer Service                                          │
│  ├─ Action: Verify customer exists and is active                       │
│  ├─ Event: CustomerValidated                                           │
│  └─ Compensation: N/A (read-only)                                      │
│                                                                         │
│  Step 3: Reserve Stock                                                 │
│  ├─ Service: Product Service                                           │
│  ├─ Action: Reserve stock for each product                             │
│  ├─ Event: StockReserved                                               │
│  └─ Compensation: Release reserved stock                               │
│                                                                         │
│  Step 4: Confirm Order                                                 │
│  ├─ Service: Order Service                                             │
│  ├─ Action: Update order status to 'created'                           │
│  ├─ Event: OrderCreated                                                │
│  └─ Compensation: Update order status to 'cancelled'                   │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

#### UC-05: Payment (TCC Pattern)

```
┌────────────────────────────────────────────────────────────────────────┐
│                    PAYMENT TCC FLOW                                     │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  TRY PHASE (15-minute timeout):                                        │
│  ├─ Payment Service: Reserve payment amount                            │
│  ├─ Order Service: Lock order (prevent modifications)                  │
│  └─ All participants return reservation IDs                            │
│                                                                         │
│  CONFIRM PHASE (all TRY successful):                                   │
│  ├─ Payment Service: Capture reserved amount                           │
│  ├─ Order Service: Update status to 'paid'                             │
│  └─ Release locks, confirm reservations                                │
│                                                                         │
│  CANCEL PHASE (any TRY failed or timeout):                             │
│  ├─ Payment Service: Release reserved amount                           │
│  ├─ Order Service: Unlock order                                        │
│  └─ Order remains in 'created' status                                  │
│                                                                         │
│  RECOVERY (coordinator failure):                                        │
│  ├─ On startup: Check pending TCC transactions                         │
│  ├─ If expired: Execute CANCEL phase                                   │
│  └─ If confirmed: Verify completion                                    │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

#### UC-07: Cancel Order (Saga Compensation)

```
┌────────────────────────────────────────────────────────────────────────┐
│                    CANCEL ORDER SAGA                                    │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Pre-condition Check:                                                  │
│  └─ Order status must be: draft, created, or paid                      │
│                                                                         │
│  Step 1: Request Cancellation                                          │
│  ├─ Service: Order Service                                             │
│  ├─ Action: Validate cancellation allowed                              │
│  └─ Event: OrderCancellationRequested                                  │
│                                                                         │
│  Step 2: Release Stock (if reserved)                                   │
│  ├─ Service: Product Service                                           │
│  ├─ Action: Release reserved/deducted stock                            │
│  └─ Event: StockReleased                                               │
│                                                                         │
│  Step 3: Refund Payment (if paid)                                      │
│  ├─ Service: Payment Service                                           │
│  ├─ Action: Initiate refund                                            │
│  └─ Event: RefundInitiated                                             │
│                                                                         │
│  Step 4: Complete Cancellation                                         │
│  ├─ Service: Order Service                                             │
│  ├─ Action: Update order status to 'cancelled'                         │
│  └─ Event: OrderCancelled                                              │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Implementation Strategy

### 7.1 Phased Implementation Plan

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     IMPLEMENTATION ROADMAP                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  PHASE 1: Foundation (Week 1-2)                                             │
│  ├─ Add Outbox tables to all services                                       │
│  ├─ Implement OutboxProcessor for each service                              │
│  ├─ Set up message broker (Kafka/Redis Streams)                             │
│  └─ Add idempotency support                                                 │
│                                                                              │
│  PHASE 2: Event-Driven Communication (Week 3-4)                             │
│  ├─ Define domain events schema                                             │
│  ├─ Implement event publishers (using Outbox)                               │
│  ├─ Implement event subscribers                                             │
│  └─ Add event logging/auditing                                              │
│                                                                              │
│  PHASE 3: Saga Implementation (Week 5-6)                                    │
│  ├─ Implement Order Creation Saga                                           │
│  ├─ Implement Order Cancellation Saga                                       │
│  ├─ Add saga state persistence                                              │
│  └─ Implement compensation handlers                                         │
│                                                                              │
│  PHASE 4: TCC for Payments (Week 7-8)                                       │
│  ├─ Add TCC coordinator service                                             │
│  ├─ Implement Payment TCC endpoints                                         │
│  ├─ Add timeout handling                                                    │
│  └─ Implement recovery mechanism                                            │
│                                                                              │
│  PHASE 5: Testing & Hardening (Week 9-10)                                   │
│  ├─ Integration testing                                                     │
│  ├─ Failure injection testing                                               │
│  ├─ Performance testing                                                     │
│  └─ Documentation                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 Phase 1: Database Schema Changes

Add the following tables to each service database:

**All Services:**
- `outbox_events` - For reliable event publishing (Outbox Pattern)
- `idempotency_records` - For duplicate request prevention

**Order Service:**
- `saga_instances` - Track saga state and step progress

**Product Service:**
- `stock_reservations` - Track temporary stock reservations (TCC)

**Payment Service:**
- `payment_reservations` - Track payment reservations (TCC)

Each table requires appropriate indexes for performance:
- Outbox: Index on (status, created_at) for polling
- Idempotency: Index on expires_at for cleanup
- Saga: Index on (saga_type, state) for queries
- Reservations: Index on (order_id) and (status, expires_at)

> **Implementation Details:** See [implementation-guide.md](implementation-guide.md#1-database-schema) for complete SQL schemas.

### 7.3 Phase 2: Event Schema Definitions

Create a shared event schema in the `common` module (`com.oms.common.events`):

**Base Event Trait:**
- Define `DomainEvent` trait with common fields
- Fields: eventId, eventType, aggregateId, aggregateType, timestamp, version
- All domain events extend this trait

**Event Categories:**

| Module | Events |
|--------|--------|
| `OrderEvents` | OrderCreated, OrderConfirmed, OrderPaid, OrderCancelled |
| `StockEvents` | StockReserved, StockReservationFailed, StockReleased, StockConfirmed |
| `PaymentEvents` | PaymentReserved, PaymentCompleted, PaymentFailed, PaymentRefunded |

**Event Design Principles:**
- Include all necessary data (no need to query back)
- Use immutable case classes
- Include version for schema evolution
- Timestamp for ordering and debugging

> **Implementation Details:** See [implementation-guide.md](implementation-guide.md#2-domain-events-schema) for complete Scala definitions.

### 7.4 Service Subscription Matrix

| Event | Publisher | Subscribers | Action |
|-------|-----------|-------------|--------|
| `ORDER_CREATED` | Order Service | Product Service | Reserve stock |
| `STOCK_RESERVED` | Product Service | Order Service | Confirm order |
| `STOCK_RESERVATION_FAILED` | Product Service | Order Service | Cancel order |
| `ORDER_CONFIRMED` | Order Service | - | - |
| `ORDER_PAID` | Order Service | Product Service | Confirm stock deduction |
| `PAYMENT_COMPLETED` | Payment Service | Order Service | Update to paid |
| `PAYMENT_FAILED` | Payment Service | Order Service, Product | Release stock, notify |
| `ORDER_CANCELLED` | Order Service | Product, Payment | Release stock, refund |

---

## 8. Technology Recommendations

### 8.1 Akka-Based Implementation

Since the project already uses Akka, leverage the ecosystem:

| Component | Akka Technology | Purpose |
|-----------|-----------------|---------|
| Event Sourcing | Akka Persistence | Saga state persistence |
| Event Publishing | Akka Streams + Kafka | Event-driven messaging |
| Actor Coordination | Akka Cluster Sharding | Distributed saga coordinators |
| Reliable Delivery | Akka Persistence Query | Outbox processing |

### 8.2 Recommended Dependencies

Add the following Akka ecosystem dependencies to enable distributed transaction patterns:

**Akka Persistence:**
- `akka-persistence-typed` - Event sourcing and saga state persistence
- `akka-persistence-query` - Query event journal
- `akka-persistence-jdbc` - PostgreSQL persistence plugin

**Akka Kafka (Alpakka):**
- `akka-stream-kafka` - Kafka producer/consumer integration

**Akka Cluster (Optional):**
- `akka-cluster-typed` - Distributed actor system
- `akka-cluster-sharding-typed` - Distributed saga coordinators

**Serialization:**
- `akka-serialization-jackson` - JSON serialization for events

**Recommended Versions:**
- Akka: 2.8.5
- Akka Persistence JDBC: 5.2.1
- Alpakka Kafka: 5.0.0

> **Implementation Details:** See [implementation-guide.md](implementation-guide.md#7-dependencies) for complete `build.sbt` configuration.

### 8.3 Infrastructure Requirements

Add Kafka message broker to the existing Docker Compose setup:

**Required Services:**

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| Zookeeper | confluentinc/cp-zookeeper:7.5.0 | 2181 | Kafka coordination |
| Kafka | confluentinc/cp-kafka:7.5.0 | 9092, 29092 | Message broker |
| Kafka UI | provectuslabs/kafka-ui:latest | 8090 | Monitoring (optional) |

**Kafka Configuration:**
- Single broker setup for development
- Auto-create topics enabled
- Transaction support enabled

**Network:**
- All services on shared `oms-network`
- Kafka accessible at `kafka:9092` (internal) and `localhost:29092` (external)

> **Implementation Details:** See [implementation-guide.md](implementation-guide.md#61-docker-compose-addition) for complete Docker Compose configuration.

### 8.4 Kafka Topics Structure

| Topic Name | Partitions | Retention | Purpose |
|------------|------------|-----------|---------|
| `oms.orders.events` | 6 | 7 days | Order domain events |
| `oms.products.events` | 6 | 7 days | Product domain events |
| `oms.payments.events` | 6 | 7 days | Payment domain events |
| `oms.saga.commands` | 3 | 1 day | Saga coordination |
| `oms.dlq` | 1 | 30 days | Dead letter queue |

---

## 8.5 Complete Tool Stack

This section provides a comprehensive overview of all technologies required to implement the transactional patterns.

### 8.5.1 Technology Stack Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           TOOL STACK ARCHITECTURE                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                        APPLICATION LAYER                                 │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │    │
│  │  │   Scala     │  │  Akka HTTP  │  │ Akka Actor  │  │   Slick     │     │    │
│  │  │   2.13.x    │  │  REST API   │  │   Typed     │  │    ORM      │     │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘     │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                      │                                           │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                      TRANSACTIONAL PATTERNS LAYER                        │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │    │
│  │  │    Akka     │  │   Alpakka   │  │    Akka     │  │   Redis     │     │    │
│  │  │ Persistence │  │   Kafka     │  │  Scheduler  │  │   (Cache)   │     │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘     │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                      │                                           │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                       MESSAGE BROKER LAYER                               │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                      │    │
│  │  │   Apache    │  │  Zookeeper  │  │  Schema     │                      │    │
│  │  │   Kafka     │  │             │  │  Registry   │                      │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                      │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                      │                                           │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                        DATA STORAGE LAYER                                │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                      │    │
│  │  │ PostgreSQL  │  │   Redis     │  │  Flyway/    │                      │    │
│  │  │   16.x      │  │   7.x       │  │  Liquibase  │                      │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                      │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                      │                                           │
│  ┌─────────────────────────────────────────────────────────────────────────┐    │
│  │                      INFRASTRUCTURE LAYER                                │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │    │
│  │  │   Docker    │  │  Docker     │  │  Kubernetes │  │   Nginx     │     │    │
│  │  │             │  │  Compose    │  │  (Optional) │  │             │     │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘     │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 8.5.2 Tool Stack by Pattern

#### Saga Pattern (Choreography)

| Category | Tool | Version | Purpose |
|----------|------|---------|---------|
| **Message Broker** | Apache Kafka | 7.5.0 (Confluent) | Event publishing and subscription |
| **Coordination** | Zookeeper | 7.5.0 (Confluent) | Kafka cluster coordination |
| **Event Store** | PostgreSQL | 16.x | Outbox table storage |
| **Saga State** | Akka Persistence | 2.8.x | Persist saga state and events |
| **Event Streaming** | Alpakka Kafka | 5.0.0 | Kafka producer/consumer for Akka |
| **Serialization** | Jackson / Circe | 2.15.x / 0.14.x | JSON event serialization |

#### TCC Pattern (Orchestration)

| Category | Tool | Version | Purpose |
|----------|------|---------|---------|
| **HTTP Client** | Akka HTTP | 10.5.x | REST calls to TRY/CONFIRM/CANCEL |
| **Timeout Handling** | Akka Scheduler | 2.8.x | Schedule timeout for pending reservations |
| **State Storage** | PostgreSQL | 16.x | Store TCC transaction state |
| **Retry Logic** | Akka Streams | 2.8.x | Retry failed operations |
| **Circuit Breaker** | Akka Circuit Breaker | 2.8.x | Prevent cascade failures |

#### Outbox Pattern

| Category | Tool | Version | Purpose |
|----------|------|---------|---------|
| **Outbox Storage** | PostgreSQL | 16.x | Store pending events atomically |
| **Polling** | Akka Scheduler | 2.8.x | Poll outbox table periodically |
| **CDC (Alternative)** | Debezium | 2.4.x | Change Data Capture (optional) |
| **Message Relay** | Alpakka Kafka | 5.0.0 | Publish events to Kafka |
| **Dead Letter Queue** | Kafka | 7.5.0 | Store failed events |

#### Idempotency Pattern

| Category | Tool | Version | Purpose |
|----------|------|---------|---------|
| **Fast Lookup Cache** | Redis | 7.2.x | Check idempotency keys quickly |
| **Persistent Storage** | PostgreSQL | 16.x | Store processed request IDs |
| **Key Generation** | UUID / SHA-256 | - | Generate unique request IDs |
| **TTL Management** | Redis TTL | - | Auto-expire old idempotency keys |

### 8.5.3 Complete Dependency Matrix

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    DEPENDENCY MATRIX BY PATTERN                               │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│ Pattern          │ Required              │ Optional              │ Nice to    │
│                  │                       │                       │ Have       │
├──────────────────┼───────────────────────┼───────────────────────┼────────────┤
│ Saga             │ • Kafka               │ • Schema Registry     │ • Kafdrop  │
│ (Choreography)   │ • Zookeeper           │ • Kafka Connect       │ • Grafana  │
│                  │ • Alpakka Kafka       │                       │            │
│                  │ • Akka Persistence    │                       │            │
├──────────────────┼───────────────────────┼───────────────────────┼────────────┤
│ TCC              │ • Akka HTTP           │ • Circuit Breaker     │ • Zipkin   │
│ (Orchestration)  │ • Akka Scheduler      │ • Retry Library       │ • Jaeger   │
│                  │ • PostgreSQL          │                       │            │
├──────────────────┼───────────────────────┼───────────────────────┼────────────┤
│ Outbox           │ • PostgreSQL          │ • Debezium (CDC)      │ • Kafka    │
│                  │ • Akka Scheduler      │ • Kafka Connect       │   Connect  │
│                  │ • Alpakka Kafka       │                       │   UI       │
├──────────────────┼───────────────────────┼───────────────────────┼────────────┤
│ Idempotency      │ • PostgreSQL          │ • Redis               │ • Redis    │
│                  │                       │                       │   Insight  │
└──────────────────┴───────────────────────┴───────────────────────┴────────────┘
```

### 8.5.4 Infrastructure Services Summary

| Service | Docker Image | Ports | Memory | Purpose |
|---------|--------------|-------|--------|---------|
| **PostgreSQL** | postgres:16-alpine | 5432 | 512MB | Primary database |
| **Redis** | redis:7.2-alpine | 6379 | 256MB | Caching, idempotency |
| **Kafka** | confluentinc/cp-kafka:7.5.0 | 9092, 29092 | 1GB | Message broker |
| **Zookeeper** | confluentinc/cp-zookeeper:7.5.0 | 2181 | 512MB | Kafka coordination |
| **Schema Registry** | confluentinc/cp-schema-registry:7.5.0 | 8081 | 256MB | Event schema management |
| **Kafka UI** | provectuslabs/kafka-ui:latest | 8090 | 256MB | Kafka monitoring |
| **Redis Commander** | rediscommander/redis-commander:latest | 8091 | 128MB | Redis monitoring |

### 8.5.5 SBT Dependencies Summary

Add these dependencies to your `build.sbt`:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SBT DEPENDENCIES                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  AKKA CORE (Existing)                                                        │
│  ├── "com.typesafe.akka" %% "akka-actor-typed"        % "2.8.5"             │
│  ├── "com.typesafe.akka" %% "akka-stream"             % "2.8.5"             │
│  └── "com.typesafe.akka" %% "akka-http"               % "10.5.3"            │
│                                                                              │
│  AKKA PERSISTENCE (New - for Saga/Outbox)                                   │
│  ├── "com.typesafe.akka" %% "akka-persistence-typed"  % "2.8.5"             │
│  ├── "com.typesafe.akka" %% "akka-persistence-query"  % "2.8.5"             │
│  └── "com.lightbend.akka" %% "akka-persistence-jdbc"  % "5.2.1"             │
│                                                                              │
│  ALPAKKA KAFKA (New - for Event Publishing)                                 │
│  └── "com.typesafe.akka" %% "akka-stream-kafka"       % "5.0.0"             │
│                                                                              │
│  SERIALIZATION (New - for Events)                                           │
│  └── "com.typesafe.akka" %% "akka-serialization-jackson" % "2.8.5"          │
│                                                                              │
│  REDIS (New - for Idempotency)                                              │
│  └── "net.debasishg" %% "redisclient"                 % "3.42"              │
│  OR                                                                          │
│  └── "dev.profunktor" %% "redis4cats-effects"         % "1.5.2"             │
│                                                                              │
│  DATABASE (Existing)                                                         │
│  ├── "com.typesafe.slick" %% "slick"                  % "3.4.1"             │
│  ├── "com.typesafe.slick" %% "slick-hikaricp"         % "3.4.1"             │
│  └── "org.postgresql" % "postgresql"                  % "42.6.0"            │
│                                                                              │
│  MIGRATION (Recommended)                                                     │
│  └── "org.flywaydb" % "flyway-core"                   % "9.22.3"            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

> **Implementation Details:** See [implementation-guide.md](implementation-guide.md#7-dependencies) for complete `build.sbt` configuration.

### 8.5.6 Version Compatibility Matrix

| Component | Version | Scala Version | Java Version |
|-----------|---------|---------------|--------------|
| Scala | 2.13.12 | - | 11+ |
| SBT | 1.9.7 | 2.12.18 | 11+ |
| Akka | 2.8.5 | 2.13.x | 11+ |
| Akka HTTP | 10.5.3 | 2.13.x | 11+ |
| Akka Persistence JDBC | 5.2.1 | 2.13.x | 11+ |
| Alpakka Kafka | 5.0.0 | 2.13.x | 11+ |
| Slick | 3.4.1 | 2.13.x | 11+ |
| PostgreSQL JDBC | 42.6.0 | - | 8+ |
| Redis Client | 3.42 | 2.13.x | 11+ |
| Flyway | 9.22.3 | - | 11+ |

### 8.5.7 Development vs Production Tools

| Tool | Development | Production | Notes |
|------|-------------|------------|-------|
| **Kafka** | Single broker | 3+ brokers | Use managed Kafka in prod (AWS MSK, Confluent Cloud) |
| **PostgreSQL** | Single instance | RDS/Aurora | Use managed database with replicas |
| **Redis** | Single instance | Redis Cluster | Use ElastiCache or managed Redis |
| **Schema Registry** | Optional | Required | Ensures event schema compatibility |
| **Monitoring** | Kafka UI | Prometheus + Grafana | Production-grade monitoring |
| **Logging** | Console | ELK Stack / CloudWatch | Centralized logging |
| **Tracing** | None | Jaeger / Zipkin | Distributed tracing |

### 8.5.8 Quick Start Commands

**Start Infrastructure (Development):**

```bash
# Start all infrastructure services
docker-compose up -d postgres redis kafka zookeeper

# Verify services are running
docker-compose ps

# Check Kafka is ready
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

**Create Kafka Topics:**

```bash
# Create required topics
docker-compose exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic oms.orders.events \
  --partitions 6 \
  --replication-factor 1

docker-compose exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic oms.products.events \
  --partitions 6 \
  --replication-factor 1

docker-compose exec kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic oms.payments.events \
  --partitions 6 \
  --replication-factor 1
```

**Run Database Migrations:**

```bash
# Apply Flyway migrations
sbt "project order-service" flywayMigrate
sbt "project product-service" flywayMigrate
sbt "project payment-service" flywayMigrate
```

---

## 9. Conclusion

### 9.1 Summary of Recommendations

| Priority | Pattern | Benefit |
|----------|---------|---------|
| **High** | Saga Pattern | Coordinates multi-service transactions |
| **High** | Outbox Pattern | Guarantees event delivery |
| **Medium** | TCC Pattern | Safe payment processing |
| **Medium** | Idempotency | Prevents duplicate operations |
| **Low** | Event Sourcing | Full audit trail |

### 9.2 Expected Outcomes

After implementing these patterns:

- ✅ **Data Consistency**: No partial failures leaving inconsistent state
- ✅ **Fault Tolerance**: Automatic compensation on failures
- ✅ **Auditability**: Complete event log for debugging
- ✅ **Scalability**: Event-driven architecture scales better
- ✅ **Reliability**: Guaranteed event delivery with outbox pattern

### 9.3 Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Increased complexity | Phased implementation, thorough testing |
| Performance overhead | Async processing, proper indexing |
| Kafka dependency | Can start with in-memory queue, migrate later |
| Learning curve | Training, documentation, code reviews |

### 9.4 Next Steps

1. **Review** this document with the development team
2. **Approve** the implementation roadmap
3. **Set up** the message broker infrastructure
4. **Begin** Phase 1 implementation
5. **Monitor** and iterate based on feedback

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **Saga** | A sequence of local transactions coordinated through events |
| **Compensation** | An action that undoes the effect of a previous action |
| **TCC** | Try-Confirm-Cancel pattern for distributed transactions |
| **Outbox** | A pattern for reliable event publishing using database transactions |
| **Idempotency** | Property where an operation produces the same result regardless of repetition |
| **Event Sourcing** | Storing state changes as a sequence of events |
| **Choreography** | Decentralized coordination where each service listens for events and decides what to do next. No central controller - services communicate through published events. Used in this project for Saga pattern. |
| **Orchestration** | Centralized coordination where a single coordinator service controls the flow by calling other services directly. The coordinator knows all steps and manages the transaction state. Used in this project for TCC pattern. |

---

## Appendix B: References

1. [Microservices Patterns](https://microservices.io/patterns/) - Chris Richardson
2. [Saga Pattern](https://microservices.io/patterns/data/saga.html)
3. [Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
4. [Akka Persistence Documentation](https://doc.akka.io/docs/akka/current/typed/persistence.html)
5. [Alpakka Kafka Documentation](https://doc.akka.io/docs/alpakka-kafka/current/)

---

*Document maintained by: Architecture Team*  
*Last updated: January 13, 2026*
