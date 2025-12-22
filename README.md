# Order Management System (OMS)

A microservices-based Order Management System built with **Akka Actor**, **Akka Streams**, **Akka HTTP**, and **PostgreSQL**.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     API Gateway (:8080)                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
    ┌──────────┬──────────┼──────────┬──────────┬───────────┐
    ▼          ▼          ▼          ▼          ▼           ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│ User   │ │Customer│ │Product │ │ Order  │ │Payment │ │Report  │
│ :8081  │ │ :8082  │ │ :8083  │ │ :8084  │ │ :8085  │ │ :8086  │
└───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘
    │          │          │          │          │          │
    ▼          ▼          ▼          ▼          ▼          │
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐     │
│users_db│ │cust_db │ │prod_db │ │order_db│ │pay_db  │     │
└────────┘ └────────┘ └────────┘ └────────┘ └────────┘     │
                                     ▲                     │
                                     └─────────────────────┘
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| API Gateway | 8080 | Entry point, routing, auth middleware |
| User Service | 8081 | User registration, authentication |
| Customer Service | 8082 | Customer profiles, addresses |
| Product Service | 8083 | Product catalog, inventory |
| Order Service | 8084 | Order processing, lifecycle |
| Payment Service | 8085 | Payment transactions |
| Report Service | 8086 | Analytics, reporting |

## Prerequisites

- **JDK 11+**
- **SBT 1.9+**
- **Docker & Docker Compose**
- **PostgreSQL 15** (or use Docker)

## Quick Start

### 1. Start Databases

```bash
docker-compose up -d
```

This starts 5 PostgreSQL instances for each service.

### 2. Compile the Project

```bash
sbt compile
```

### 3. Run Services

Open separate terminals for each service:

```bash
# Terminal 1 - User Service
sbt "user-service/run"

# Terminal 2 - Customer Service
sbt "customer-service/run"

# Terminal 3 - Product Service
sbt "product-service/run"

# Terminal 4 - Order Service
sbt "order-service/run"

# Terminal 5 - Payment Service
sbt "payment-service/run"

# Terminal 6 - Report Service
sbt "report-service/run"

# Terminal 7 - API Gateway
sbt "api-gateway/run"
```

### 4. Test the API

```bash
# Health check
curl http://localhost:8080/health

# Check all services health
curl http://localhost:8080/services/health
```

## Configuration

Each service reads from `application.conf`. Key settings:

```hocon
http {
  host = "0.0.0.0"
  port = 8081  # Varies by service
}

database {
  properties {
    serverName = "localhost"
    portNumber = "5432"
    databaseName = "oms_users"
    user = "postgres"
    password = "postgres"
  }
}
```

## Development

### Run Tests
```bash
sbt test
```

### Build All Services
```bash
sbt compile
```

### Package for Production
```bash
sbt universal:packageBin
```

## Technology Stack

- **Scala 2.13** - Programming language
- **Akka Actor Typed 2.8** - Actor model
- **Akka Streams 2.8** - Reactive streams
- **Akka HTTP 10.5** - HTTP server
- **Slick 3.4** - Database access
- **PostgreSQL 15** - Database
- **Spray JSON** - JSON serialization
- **Docker** - Containerization

## License

MIT License
