# Order Management System (OMS)

A full-stack microservices-based Order Management System built with **Scala/Akka** backend and **Angular** frontend.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│               Angular Frontend (:4200)                       │
│          (SSR, Tailwind CSS, RxJS)                          │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP/REST
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  API Gateway (:8080)                         │
│            (Routing, HTTP/2, CORS)                          │
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
┌──────────────────────────────────────────────────────────────┐
│              PostgreSQL 16 (:5433)                           │
│  oms_users | oms_customers | oms_products | oms_orders       │
│  oms_payments                                                │
└──────────────────────────────────────────────────────────────┘
```

## 📦 Project Structure

```
order-management-system-akka-scala/
├── frontend/                    # Angular 20 Frontend
│   ├── src/
│   │   ├── app/
│   │   │   ├── core/           # Singleton services, guards, interceptors
│   │   │   ├── features/       # Feature modules (lazy-loaded)
│   │   │   ├── layout/         # Layout components
│   │   │   └── shared/         # Shared components, directives, pipes
│   │   ├── environments/       # Environment configurations
│   │   └── styles.scss         # Global styles with Tailwind CSS
│   ├── angular.json            # Angular workspace configuration
│   ├── package.json            # Frontend dependencies
│   └── tsconfig.json           # TypeScript configuration
│
├── api-gateway/                 # API Gateway Service (Port 8080)
│   ├── src/main/
│   │   ├── scala/              # Gateway routing logic
│   │   └── resources/
│   │       └── application.conf # Gateway configuration
│   └── build.sbt
│
├── common/                      # Shared Scala library
│   ├── src/main/scala/         # Common models, utilities
│   └── build.sbt               # Shared dependencies
│
├── user-service/               # User Management (Port 8081)
├── customer-service/           # Customer Management (Port 8082)
├── product-service/            # Product Catalog (Port 8083)
├── order-service/              # Order Processing (Port 8084)
├── payment-service/            # Payment Processing (Port 8085)
├── report-service/             # Analytics & Reporting (Port 8086)
│
├── scripts/
│   └── init-databases.sql      # Database initialization
├── docker-compose.yml          # PostgreSQL container
├── build.sbt                   # Root build configuration
└── README.md
```

## 🎯 Services

| Service | Port | Technology | Description |
|---------|------|------------|-------------|
| Frontend | 4200 | Angular 20, SSR | User interface with server-side rendering |
| API Gateway | 8080 | Akka HTTP | Entry point, routing, HTTP/2 support |
| User Service | 8081 | Akka HTTP + Slick | User registration, authentication |
| Customer Service | 8082 | Akka HTTP + Slick | Customer profiles, addresses |
| Product Service | 8083 | Akka HTTP + Slick | Product catalog, inventory |
| Order Service | 8084 | Akka HTTP + Slick | Order processing, lifecycle |
| Payment Service | 8085 | Akka HTTP + Slick | Payment transactions |
| Report Service | 8086 | Akka HTTP + Slick | Analytics, reporting |
| PostgreSQL | 5432 | PostgreSQL 16 | Shared database instance |

## ⚙️ Prerequisites

### Backend
- **JDK 11+** (Tested with JDK 11/17)
- **SBT 1.9+** (Scala Build Tool)
- **Docker & Docker Compose** (for PostgreSQL)

### Frontend
- **Node.js 18.x+**
- **npm 9.x+**
- **Angular CLI 20.x**
  ```bash
  npm install -g @angular/cli
  ```

### Database
- **PostgreSQL 16** (via Docker) or standalone installation

## 🚀 Quick Start

### 1. Start Database

Start PostgreSQL using Docker Compose:

```bash
docker-compose up -d
```

This starts a PostgreSQL 16 instance and automatically creates all required databases:
- `oms_users`
- `oms_customers`
- `oms_products`
- `oms_orders`
- `oms_payments`

### 2. Start Backend Services

**Option A: Run all services at once (Windows)**
```bash
run-all-services.bat
```

**Option B: Compile and run manually**

First, compile the entire project:
```bash
sbt compile
```

Then start each service in separate terminals:

```bash
# Terminal 1 - API Gateway
sbt "api-gateway/run"

# Terminal 2 - User Service
sbt "user-service/run"

# Terminal 3 - Customer Service
sbt "customer-service/run"

# Terminal 4 - Product Service
sbt "product-service/run"

# Terminal 5 - Order Service
sbt "order-service/run"

# Terminal 6 - Payment Service
sbt "payment-service/run"

# Terminal 7 - Report Service
sbt "report-service/run"
```

Wait for all services to start. You should see log messages indicating each service is listening on its port.

### 3. Start Frontend

Navigate to the frontend directory and install dependencies:

```bash
cd frontend
npm install
```

Start the development server:

```bash
npm start
```

The Angular app will be available at `http://localhost:4200`

### 4. Verify Installation

**Backend Health Check:**
```bash
# Check API Gateway
curl http://localhost:8080/health

# Check individual services
curl http://localhost:8081/health  # User Service
curl http://localhost:8082/health  # Customer Service
curl http://localhost:8083/health  # Product Service
curl http://localhost:8084/health  # Order Service
curl http://localhost:8085/health  # Payment Service
curl http://localhost:8086/health  # Report Service
```

**Frontend:**
Open your browser to `http://localhost:4200`

## 🔧 Configuration

### Backend Configuration

Each backend service has its own `application.conf` file located at `{service}/src/main/resources/application.conf`.

#### API Gateway Configuration (`api-gateway/src/main/resources/application.conf`)

```hocon
http {
  host = "0.0.0.0"
  port = 8080
}

services {
  user-service = "http://localhost:8081"
  customer-service = "http://localhost:8082"
  product-service = "http://localhost:8083"
  order-service = "http://localhost:8084"
  payment-service = "http://localhost:8085"
  report-service = "http://localhost:8086"
}

akka {
  loglevel = "INFO"
  http {
    server {
      preview.enable-http2 = on
      request-timeout = 60s
      idle-timeout = 120s
    }
  }
}
```

#### Microservice Configuration (example: `user-service/src/main/resources/application.conf`)

```hocon
http {
  host = "0.0.0.0"
  port = 8081  # Varies by service (8081-8086)
}

database {
  driver = "org.postgresql.Driver"
  url = "jdbc:postgresql://localhost:5432/oms_users"
  user = "admin"
  password = "admin"
  connectionPool = "HikariCP"
  numThreads = 10
}

akka {
  loglevel = "INFO"
  http {
    server {
      preview.enable-http2 = on
    }
  }
}
```

**Database URLs by Service:**
- User Service: `jdbc:postgresql://localhost:5432/oms_users`
- Customer Service: `jdbc:postgresql://localhost:5432/oms_customers`
- Product Service: `jdbc:postgresql://localhost:5432/oms_products`
- Order Service: `jdbc:postgresql://localhost:5432/oms_orders`
- Payment Service: `jdbc:postgresql://localhost:5432/oms_payments`

### Frontend Configuration

Environment configurations are located in `frontend/src/environments/`:

**Development (`environment.development.ts`):**
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  enableDebugMode: true
};
```

**Production (`environment.ts`):**
```typescript
export const environment = {
  production: true,
  apiUrl: 'https://api.yourdomain.com',
  enableDebugMode: false
};
```

### Docker Configuration

The `docker-compose.yml` uses PostgreSQL 16 with the following settings:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_USER=admin
      - POSTGRES_PASSWORD=admin
    volumes:
      - ./scripts/init-databases.sql:/docker-entrypoint-initdb.d/postgres_init.sql
```

## 🛠️ Development

### Backend Development

**Run specific service:**
```bash
sbt "user-service/run"
```

**Run tests:**
```bash
# Test all services
sbt test

# Test specific service
sbt "user-service/test"
```

**Build specific service:**
```bash
sbt "user-service/compile"
```

**Clean build artifacts:**
```bash
sbt clean
```

**Interactive SBT shell:**
```bash
sbt
> project user-service
> compile
> run
```

### Frontend Development

**Development server with hot reload:**
```bash
cd frontend
npm start
```

**Run unit tests:**
```bash
npm test
```

**Run tests in watch mode:**
```bash
npm run watch
```

**Lint code:**
```bash
npm run lint
```

**Fix linting issues:**
```bash
npm run lint:fix
```

**Format code with Prettier:**
```bash
npm run format
```

**Check formatting:**
```bash
npm run format:check
```

**Build for production:**
```bash
npm run build
```

**Run production build with SSR:**
```bash
npm run serve:ssr:frontend
```

### Project Structure Commands

**Add new Angular component:**
```bash
ng generate component features/my-component
```

**Add new service:**
```bash
ng generate service core/services/my-service
```

**Add new module:**
```bash
ng generate module features/my-module --routing
```

## 📦 Technology Stack

### Backend

| Technology | Version | Purpose |
|------------|---------|---------|
| **Scala** | 2.13.17 | Programming language |
| **Akka Actor Typed** | 2.10.12 | Actor-based concurrency model |
| **Akka Streams** | 2.10.12 | Reactive streams processing |
| **Akka HTTP** | 10.7.3 | HTTP server & client with HTTP/2 support |
| **Slick** | 3.4.1 | Functional Relational Mapping (FRM) |
| **HikariCP** | (via Slick) | High-performance JDBC connection pool |
| **PostgreSQL Driver** | 42.6.0 | JDBC driver for PostgreSQL |
| **Spray JSON** | (via Akka HTTP) | JSON serialization/deserialization |
| **Logback** | 1.4.11 | Logging framework |
| **ScalaTest** | 3.2.17 | Testing framework |
| **SBT** | 1.9+ | Build tool |

### Frontend

| Technology | Version | Purpose |
|------------|---------|---------|
| **Angular** | 20.3.0 | Frontend framework |
| **Angular SSR** | 20.3.7 | Server-side rendering |
| **TypeScript** | 5.9.2 | Type-safe JavaScript |
| **RxJS** | 7.8.0 | Reactive programming with observables |
| **Tailwind CSS** | 4.1.18 | Utility-first CSS framework |
| **PostCSS** | 8.5.6 | CSS processing |
| **Express** | 5.1.0 | Node.js server for SSR |
| **ESLint** | 9.28.0 | Code linting |
| **Prettier** | 3.4.2 | Code formatting |
| **Jasmine** | 5.9.0 | Testing framework |
| **Karma** | 6.4.0 | Test runner |

### Infrastructure

| Technology | Version | Purpose |
|------------|---------|---------|
| **Docker** | Latest | Containerization |
| **Docker Compose** | Latest | Multi-container orchestration |
| **PostgreSQL** | 16-alpine | Relational database |

### Build & Development Tools

- **SBT** - Scala build tool with multi-project support
- **Angular CLI** - Command-line interface for Angular
- **npm** - Node package manager
- **Git** - Version control

### Key Features

#### Backend Features
- ✅ Microservices architecture with independent deployability
- ✅ Actor-based concurrency for high scalability
- ✅ Reactive streams for backpressure handling
- ✅ HTTP/2 support for improved performance
- ✅ Type-safe database queries with Slick
- ✅ Connection pooling with HikariCP
- ✅ Comprehensive logging with Logback
- ✅ Environment-based configuration with Typesafe Config

#### Frontend Features
- ✅ Server-side rendering (SSR) for improved SEO and performance
- ✅ Responsive design with Tailwind CSS
- ✅ Reactive programming with RxJS
- ✅ Type-safe development with TypeScript
- ✅ Code quality with ESLint and Prettier
- ✅ Comprehensive testing with Jasmine and Karma
- ✅ Modular architecture with lazy-loaded feature modules
- ✅ Production-ready builds with optimization

- ✅ Production-ready builds with optimization

## 🏗️ Build & Deployment

### Backend Build

**Build all services:**
```bash
sbt compile
```

**Create distribution packages:**
```bash
sbt universal:packageBin
```

**Run in production mode:**
```bash
# Set environment variables
export HTTP_HOST=0.0.0.0
export HTTP_PORT=8081

# Run the service
sbt "user-service/run"
```

### Frontend Build

**Production build:**
```bash
cd frontend
npm run build
```

The build artifacts will be stored in the `dist/frontend` directory.

**Production build with SSR:**
```bash
npm run build
npm run serve:ssr:frontend
```

### Docker Deployment

**Build Docker images (example for user-service):**
```dockerfile
FROM eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY user-service/target/scala-2.13/*.jar app.jar
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
```

**Deploy with Docker Compose:**
```bash
docker-compose up -d
```

## 📚 API Documentation

### API Gateway Endpoints

All API requests should go through the API Gateway at `http://localhost:8080`

**Service Routes:**
- `/users/*` → User Service (8081)
- `/customers/*` → Customer Service (8082)
- `/products/*` → Product Service (8083)
- `/orders/*` → Order Service (8084)
- `/payments/*` → Payment Service (8085)
- `/reports/*` → Report Service (8086)

**Example Requests:**
```bash
# Create a user
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@example.com","password":"secret"}'

# Get products
curl http://localhost:8080/products

# Create an order
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"items":[{"productId":1,"quantity":2}]}'
```

## 🧪 Testing

### Backend Testing

```bash
# Run all tests
sbt test

# Run tests for specific service
sbt "user-service/test"

# Run with coverage
sbt coverage test coverageReport
```

### Frontend Testing

```bash
cd frontend

# Unit tests
npm test

# E2E tests (if configured)
npm run e2e

# Test coverage
npm test -- --code-coverage
```

## 🐛 Troubleshooting

### Common Issues

**Port already in use:**
```bash
# Find process using port 8080
netstat -ano | findstr :8080

# Kill the process (Windows)
taskkill /PID <PID> /F
```

**Database connection issues:**
- Ensure PostgreSQL is running: `docker-compose ps`
- Check database credentials in `application.conf`
- Verify databases are created: `docker exec -it <postgres-container> psql -U admin -l`

**SBT compilation errors:**
- Clean the project: `sbt clean`
- Delete target folders: `rm -rf target */target`
- Update dependencies: `sbt update`

**Angular build errors:**
- Clear node_modules: `rm -rf node_modules && npm install`
- Clear Angular cache: `npm run ng cache clean`
- Check Node version: `node --version` (should be 18.x+)

## 📖 Additional Resources

### Backend Documentation
- [Akka Documentation](https://doc.akka.io/)
- [Slick Documentation](https://scala-slick.org/doc/)
- [Scala Documentation](https://docs.scala-lang.org/)

### Frontend Documentation
- [Angular Documentation](https://angular.dev)
- [RxJS Documentation](https://rxjs.dev/)
- [Tailwind CSS Documentation](https://tailwindcss.com/docs)

### Related Files
- [Frontend README](frontend/README.md) - Detailed frontend documentation
- [Frontend Architecture](frontend/ARCHITECTURE.md) - Frontend architecture guide
- [Frontend Coding Conventions](frontend/CODING_CONVENTIONS.md) - Code style guide

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -am 'Add new feature'`
4. Push to the branch: `git push origin feature/my-feature`
5. Submit a pull request

### Code Style

**Backend:**
- Follow Scala style guide
- Use ScalaFmt for formatting
- Add ScalaDoc comments for public APIs

**Frontend:**
- Follow Angular style guide
- Use Prettier for formatting: `npm run format`
- Use ESLint: `npm run lint`

## 📄 License

MIT License

## 👥 Team & Support

For questions or issues, please:
1. Check existing documentation
2. Review closed issues on GitHub
3. Open a new issue with detailed information

---

**Built with ❤️ using Scala, Akka, and Angular**
