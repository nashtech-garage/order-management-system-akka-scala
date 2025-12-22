# Order Management System - Frontend

> Modern Angular-based frontend for the Order Management System microservices architecture

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 20.3.7.

## 📋 Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Available Scripts](#available-scripts)
- [Project Structure](#project-structure)
- [Features](#features)
- [Development](#development)
- [Testing](#testing)
- [Building & Deployment](#building--deployment)
- [Code Quality](#code-quality)
- [Additional Resources](#additional-resources)

## 🎯 Overview

This Angular application serves as the frontend interface for the Order Management System, providing a comprehensive dashboard for managing:
- Customer information
- Product catalog
- Order processing
- Payment transactions
- User management
- Analytics and reporting

## 📦 Prerequisites

Before you begin, ensure you have the following installed:

- **Node.js**: v18.x or higher
- **npm**: v9.x or higher
- **Angular CLI**: v20.3.x
  ```bash
  npm install -g @angular/cli
  ```

## 🚀 Getting Started

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Configure environment:**
   - Update API endpoints in `src/environments/environment.development.ts`
   - Ensure backend services are running

3. **Start development server:**
   ```bash
   npm start
   # or
   ng serve
   ```

4. **Open browser:**
   Navigate to `http://localhost:4200/`
   
   The application will automatically reload when you modify source files.

## 📜 Available Scripts

| Script | Description |
|--------|-------------|
| `npm start` | Start development server on port 4200 |
| `npm run build` | Build for production |
| `npm run watch` | Build in watch mode for development |
| `npm test` | Run unit tests with Karma |
| `npm run lint` | Lint TypeScript files |
| `npm run lint:fix` | Lint and auto-fix issues |
| `npm run format` | Format code with Prettier |
| `npm run format:check` | Check code formatting |
| `npm run serve:ssr:frontend` | Serve SSR build |

## 📁 Project Structure

```
src/
├── app/
│   ├── core/                 # Singleton services, guards, interceptors
│   ├── features/             # Feature modules (auth, orders, products, etc.)
│   ├── layout/               # Layout components (header, footer, sidebar)
│   └── shared/               # Reusable components, models, pipes
├── environments/             # Environment configurations
├── index.html
├── main.ts
└── styles.scss
```

For detailed architecture information, see [ARCHITECTURE.md](ARCHITECTURE.md).

## ✨ Features

### Core Functionality
- **Authentication & Authorization**: Secure login/signup with JWT tokens
- **Order Management**: Create, view, update, and track orders
- **Product Catalog**: Browse and manage product inventory
- **Customer Management**: Track customer information and history
- **Payment Processing**: Handle payment transactions
- **User Management**: Admin capabilities for user administration
- **Reports & Analytics**: Data visualization and insights

### Technical Features
- **Standalone Components**: Using Angular's latest standalone API
- **Lazy Loading**: Optimized bundle sizes with lazy-loaded modules
- **Reactive Forms**: Type-safe form handling
- **HTTP Interceptors**: Centralized request/response handling
- **Route Guards**: Protected routes with authentication checks
- **Responsive Design**: Mobile-first approach with Tailwind CSS
- **Server-Side Rendering (SSR)**: Improved SEO and performance

## 🔧 Development

### Code Scaffolding

Generate new components, services, or modules:

```bash
# Generate a component
ng generate component features/new-feature/my-component

# Generate a service
ng generate service features/new-feature/my-service

# Generate a guard
ng generate guard core/guards/my-guard

# Generate a pipe
ng generate pipe shared/pipes/my-pipe
```

For all available schematics:
```bash
ng generate --help
```

### Development Guidelines

Please refer to [CODING_CONVENTIONS.md](CODING_CONVENTIONS.md) for:
- Naming conventions
- File organization
- Component structure
- Best practices
- Code style guidelines

### Hot Module Replacement

The development server supports HMR for a better development experience with instant updates.

## 🧪 Testing

### Unit Tests

Run unit tests with Karma:
```bash
npm test
```

Run tests in headless mode:
```bash
ng test --browsers=ChromeHeadless --watch=false
```

### Test Coverage

Generate coverage report:
```bash
ng test --code-coverage
```

Coverage reports will be available in `coverage/` directory.

### End-to-End Tests

```bash
ng e2e
```

*Note: E2E testing framework needs to be configured separately.*

## 🏗️ Building & Deployment

### Development Build
```bash
npm run build
```

### Production Build
```bash
ng build --configuration production
```

Build artifacts will be stored in the `dist/` directory.

### Build Optimization

Production builds include:
- Tree shaking for smaller bundles
- Ahead-of-time (AOT) compilation
- Minification and uglification
- CSS optimization
- Lazy loading for route-based code splitting

### Server-Side Rendering

Build for SSR:
```bash
ng build --configuration production
```

Serve SSR application:
```bash
npm run serve:ssr:frontend
```

## 🎨 Code Quality

### Linting

The project uses ESLint for code quality:
```bash
npm run lint        # Check for issues
npm run lint:fix    # Auto-fix issues
```

### Formatting

The project uses Prettier for code formatting:
```bash
npm run format        # Format all files
npm run format:check  # Check formatting
```

### Pre-commit Hooks

Consider setting up Husky for automated pre-commit checks.

## 📚 Technology Stack

- **Framework**: Angular 20.3.x (Standalone Components)
- **Language**: TypeScript 5.x
- **Styling**: Tailwind CSS 4.x, SCSS
- **State Management**: RxJS
- **HTTP Client**: Angular HttpClient
- **Routing**: Angular Router
- **Forms**: Angular Reactive Forms
- **Build Tool**: Angular CLI with Vite
- **Testing**: Jasmine, Karma
- **Linting**: ESLint
- **Formatting**: Prettier

## 🔗 Integration with Backend

This frontend integrates with the following backend microservices:
- **API Gateway**: Main entry point for all API requests
- **User Service**: Authentication and user management
- **Order Service**: Order processing
- **Product Service**: Product catalog
- **Customer Service**: Customer management
- **Payment Service**: Payment processing
- **Report Service**: Analytics and reporting

API endpoints are configured in `src/app/core/constants/api-endpoints.ts`.

## 🤝 Contributing

1. Follow the coding conventions in [CODING_CONVENTIONS.md](CODING_CONVENTIONS.md)
2. Write unit tests for new features
3. Ensure all tests pass before submitting
4. Run linting and formatting checks
5. Update documentation as needed

## 📖 Additional Resources

- [Angular Documentation](https://angular.dev)
- [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli)
- [TypeScript Documentation](https://www.typescriptlang.org/docs/)
- [RxJS Documentation](https://rxjs.dev)
- [Tailwind CSS Documentation](https://tailwindcss.com/docs)
- [Project Architecture Guide](ARCHITECTURE.md)
- [Coding Conventions](CODING_CONVENTIONS.md)

## 📝 License

This project is part of the Order Management System microservices architecture.

---

**Last Updated**: December 2025
