# Angular Frontend Structure

## Overview
This is an Angular-based frontend for the Order Management System, following best practices for scalability and maintainability.

## Project Structure

```
src/
├── app/
│   ├── core/                     # Singleton services, guards, interceptors
│   │   ├── guards/              # Route guards
│   │   │   └── auth.guard.ts
│   │   ├── interceptors/        # HTTP interceptors
│   │   │   ├── auth.interceptor.ts
│   │   │   └── error.interceptor.ts
│   │   ├── services/            # Core services
│   │   │   └── api.service.ts
│   │   ├── models/              # Core models
│   │   └── constants/           # Application constants
│   │       └── api-endpoints.ts
│   │
│   ├── shared/                   # Reusable components, directives, pipes
│   │   ├── components/          # Shared components
│   │   │   ├── button/
│   │   │   └── loader/
│   │   ├── directives/          # Custom directives
│   │   ├── pipes/               # Custom pipes
│   │   │   └── currency.pipe.ts
│   │   └── models/              # Shared models
│   │       ├── user.model.ts
│   │       ├── order.model.ts
│   │       ├── product.model.ts
│   │       ├── customer.model.ts
│   │       └── payment.model.ts
│   │
│   ├── features/                 # Feature modules
│   │   ├── auth/                # Authentication
│   │   │   ├── login/
│   │   │   ├── register/
│   │   │   └── auth.service.ts
│   │   ├── dashboard/           # Dashboard
│   │   ├── orders/              # Order management
│   │   │   ├── order-list/
│   │   │   ├── order-detail/
│   │   │   ├── create-order/
│   │   │   └── order.service.ts
│   │   ├── products/            # Product management
│   │   │   ├── product-list/
│   │   │   ├── product-detail/
│   │   │   └── product.service.ts
│   │   ├── customers/           # Customer management
│   │   │   ├── customer-list/
│   │   │   ├── customer-detail/
│   │   │   └── customer.service.ts
│   │   ├── users/               # User management
│   │   │   └── user-list/
│   │   ├── payments/            # Payment management
│   │   │   ├── payment-list/
│   │   │   └── payment.service.ts
│   │   └── reports/             # Reports & analytics
│   │
│   ├── layout/                   # Layout components
│   │   ├── header/
│   │   ├── footer/
│   │   ├── sidebar/
│   │   └── main-layout/
│   │
│   ├── app.routes.ts            # Application routes
│   ├── app.config.ts            # Application configuration
│   └── app.ts                   # Root component
│
├── environments/                 # Environment configurations
│   ├── environment.ts
│   └── environment.development.ts
│
├── index.html
├── main.ts
└── styles.scss
```

## Architecture Principles

### 1. Core Module (`core/`)
- **Purpose**: Contains singleton services and application-wide functionality
- **Contents**: 
  - Guards for route protection
  - HTTP interceptors for request/response handling
  - Core services used across the application
  - Application constants
- **Import**: Should be imported only once in the app configuration

### 2. Shared Module (`shared/`)
- **Purpose**: Contains reusable components, directives, and pipes
- **Contents**:
  - UI components used across multiple features
  - Custom directives for DOM manipulation
  - Pipes for data transformation
  - Shared data models
- **Import**: Can be imported by multiple feature modules

### 3. Features Module (`features/`)
- **Purpose**: Contains feature-specific components and logic
- **Structure**: Each feature has its own folder with:
  - Components for different views
  - Feature-specific services
  - Feature-specific models (if needed)
- **Lazy Loading**: Features can be lazy-loaded for better performance

### 4. Layout Module (`layout/`)
- **Purpose**: Contains layout components that structure the application
- **Contents**:
  - Header with navigation
  - Footer
  - Sidebar for navigation
  - Main layout wrapper

### 5. Environments
- **Purpose**: Configuration for different environments
- **Files**:
  - `environment.ts` - Production configuration
  - `environment.development.ts` - Development configuration

## Best Practices Implemented

1. **Standalone Components**: Using Angular's new standalone component approach
2. **Dependency Injection**: Using `inject()` function for cleaner code
3. **TypeScript Interfaces**: Strong typing for all data models
4. **Lazy Loading**: Feature modules can be lazy-loaded
5. **Separation of Concerns**: Clear separation between core, shared, and feature code
6. **Service Layer**: Services handle API communication
7. **Route Guards**: Authentication and authorization guards
8. **HTTP Interceptors**: Centralized request/response handling
9. **Environment Configuration**: Separate configs for dev and prod
10. **Component Composition**: Reusable components in shared module

## Getting Started

### Development
```bash
npm install
npm start
```

### Build
```bash
npm run build
```

### Testing
```bash
npm test
```

## Feature Implementation Guide

### Adding a New Feature

1. Create feature folder in `features/`
2. Create service for API calls
3. Create components for different views
4. Add routes in `app.routes.ts`
5. Add navigation links in layout components

### Adding a Shared Component

1. Create component in `shared/components/`
2. Make it standalone
3. Export from shared module if using modules
4. Import where needed

### Adding a Service

1. Create service with `@Injectable({ providedIn: 'root' })`
2. Use `inject()` for dependencies
3. Use `ApiService` for HTTP calls
4. Return Observables for async operations
