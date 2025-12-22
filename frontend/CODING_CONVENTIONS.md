# Coding Conventions

> Standards and best practices for the Order Management System frontend

## 📋 Table of Contents

- [General Principles](#general-principles)
- [File Naming Conventions](#file-naming-conventions)
- [TypeScript Conventions](#typescript-conventions)
- [Component Guidelines](#component-guidelines)
- [Service Guidelines](#service-guidelines)
- [Template Conventions](#template-conventions)
- [Styling Guidelines](#styling-guidelines)
- [Testing Standards](#testing-standards)
- [Code Organization](#code-organization)
- [Best Practices](#best-practices)

## 🎯 General Principles

### Core Values
1. **Readability**: Code should be self-documenting and easy to understand
2. **Consistency**: Follow established patterns throughout the codebase
3. **Maintainability**: Write code that's easy to modify and extend
4. **Performance**: Consider performance implications of architectural decisions
5. **Testability**: Design code that's easy to test

## 📁 File Naming Conventions

### General Rules
- Use **kebab-case** for all file names
- Use descriptive, meaningful names
- Keep names concise but clear

### Component Files
```
component-name.ts           # Component class
component-name.html         # Template
component-name.scss         # Styles
component-name.spec.ts      # Unit tests
```

Examples:
- `user-list.ts`
- `order-detail.html`
- `product-card.scss`
- `customer-form.spec.ts`

### Service Files
```
service-name.service.ts
service-name.service.spec.ts
```

Examples:
- `auth.service.ts`
- `order.service.ts`
- `api.service.ts`

### Other Files
```
guard-name.guard.ts
interceptor-name.interceptor.ts
pipe-name.pipe.ts
model-name.model.ts
directive-name.directive.ts
```

## 💻 TypeScript Conventions

### Naming Conventions

#### Classes and Interfaces
```typescript
// Classes: PascalCase with descriptive suffix
export class UserList { }
export class AuthService { }
export class AuthGuard { }

// Interfaces: PascalCase, no 'I' prefix
export interface Order {
  id: string;
  orderId: string;
  customerId: string;
}
```

#### Variables and Functions
```typescript
// Variables: camelCase
let orderCount = 0;

// Constants: UPPER_SNAKE_CASE for true constants
const MAX_RETRY_ATTEMPTS = 3;
const API_TIMEOUT = 5000;

// Functions: camelCase with verb prefix
function getUserById(id: string): User { }
```

#### Enums
```typescript
// PascalCase for enum name, UPPER_SNAKE_CASE for values
export enum OrderStatus {
  PENDING = 'PENDING',
  PROCESSING = 'PROCESSING',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED'
}
```

### Type Safety

#### Always Use Strong Typing
```typescript
// ❌ Bad
function getUser(id: any): any {
  return this.http.get('/api/users/' + id);
}

// ✅ Good
function getUser(id: string): Observable<User> {
  return this.http.get<User>(`/api/users/${id}`);
}
```

#### Avoid 'any' Type
```typescript
// ❌ Bad
let data: any;

// ✅ Good
let data: User | null = null;
// or
let data: unknown; // if type is truly unknown
```

### Function Guidelines

#### Arrow Functions vs Regular Functions
```typescript
// Use arrow functions for callbacks and short functions
const numbers = [1, 2, 3];
const doubled = numbers.map(n => n * 2);

// Use regular functions for class methods
class UserService {
  getUsers(): Observable<User[]> {
    return this.http.get<User[]>('/api/users');
  }
}
```

#### Function Length
- Keep functions short (ideally < 20 lines)
- One function = one responsibility
- Extract complex logic into separate functions

#### Parameters
```typescript
// ❌ Bad: Too many parameters
function createOrder(userId: string, products: Product[], 
                    address: string, city: string, zip: string, 
                    payment: string) { }

// ✅ Good: Use object parameter
interface CreateOrderParams {
  userId: string;
  products: Product[];
  address: Address;
  payment: PaymentInfo;
}

function createOrder(params: CreateOrderParams) { }
```

## 🧩 Component Guidelines

### Standalone Components
```typescript
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss'
})
export class UserListComponent {
  // Component logic
}
```

### Component Structure
```typescript
@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [/* dependencies */],
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.scss'
})
export class OrderDetailComponent implements OnInit, OnDestroy {
  // 1. Inputs and Outputs
  @Input() orderId!: string;
  @Output() orderUpdated = new EventEmitter<Order>();

  // 2. Public properties
  order: Order | null = null;
  isLoading = false;

  // 3. Private properties
  private destroy$ = new Subject<void>();

  // 4. Constructor with dependency injection
  constructor(
    private orderService: OrderService,
    private router: Router
  ) {}

  // 5. Lifecycle hooks
  ngOnInit(): void {
    this.loadOrder();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // 6. Public methods
  updateOrder(): void {
    // Implementation
  }

  // 7. Private methods
  private loadOrder(): void {
    // Implementation
  }
}
```

### Component Best Practices

#### Smart vs Presentational Components
```typescript
// ✅ Smart Component (Container)
// - Handles business logic
// - Communicates with services
// - Manages state
@Component({
  selector: 'app-order-list-container',
  // ...
})
export class OrderListContainerComponent {
  orders$ = this.orderService.getOrders();
  
  constructor(private orderService: OrderService) {}
}

// ✅ Presentational Component (Dumb)
// - Only displays data
// - Uses @Input and @Output
// - No service dependencies
@Component({
  selector: 'app-order-list-view',
  // ...
})
export class OrderListViewComponent {
  @Input() orders: Order[] = [];
  @Output() orderSelected = new EventEmitter<Order>();
}
```

#### Change Detection Strategy
```typescript
// Use OnPush for better performance
@Component({
  selector: 'app-product-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  // ...
})
export class ProductCardComponent {
  @Input() product!: Product;
}
```

#### Unsubscribe from Observables
```typescript
// ✅ Good: Using takeUntil pattern
export class MyComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    this.userService.getUsers()
      .pipe(takeUntil(this.destroy$))
      .subscribe(users => {
        // Handle users
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}

// ✅ Alternative: Using async pipe (preferred)
export class MyComponent {
  users$ = this.userService.getUsers();
}
```

## 🔧 Service Guidelines

### Service Structure
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root' // Singleton service
})
export class UserService {
  private readonly apiUrl = '/api/users';

  constructor(private http: HttpClient) {}

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.apiUrl);
  }
}
```

### Service Best Practices

#### Error Handling
```typescript
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';

@Injectable()
export class ProductService {
  getProducts(): Observable<Product[]> {
    return this.http.get<Product[]>('/api/products')
      .pipe(
        catchError(error => {
          console.error('Error fetching products:', error);
          return throwError(() => new Error('Failed to load products'));
        })
      );
  }
}
```

#### Use Environment Configuration
```typescript
import { environment } from '@/environments/environment';

@Injectable()
export class ApiService {
  private readonly baseUrl = environment.apiUrl;

  getResource(path: string): Observable<any> {
    return this.http.get(`${this.baseUrl}${path}`);
  }
}
```

## 📄 Template Conventions

### Template Syntax

#### Property Binding
```html
<!-- ✅ Good -->
<img [src]="user.avatar" [alt]="user.name">

<!-- ❌ Bad -->
<img src="{{ user.avatar }}" alt="{{ user.name }}">
```

#### Event Binding
```html
<!-- ✅ Good: Use parentheses -->
<button (click)="onSave()">Save</button>

<!-- Use $event when needed -->
<input (input)="onSearch($event.target.value)">
```

#### Two-Way Binding
```html
<!-- Use [(ngModel)] for two-way binding -->
<input [(ngModel)]="username" type="text">
```

### Structural Directives

#### *ngIf
```html
<!-- ✅ Good: Use async pipe with ngIf -->
<div *ngIf="users$ | async as users; else loading">
  <app-user-list [users]="users"></app-user-list>
</div>

<ng-template #loading>
  <app-loader></app-loader>
</ng-template>
```

#### *ngFor
```html
<!-- ✅ Good: Always use trackBy -->
<div *ngFor="let item of items; trackBy: trackById">
  {{ item.name }}
</div>
```

```typescript
// In component
trackById(index: number, item: { id: string }): string {
  return item.id;
}
```

### Template Organization

#### Keep Templates Clean
```html
<!-- ❌ Bad: Complex logic in template -->
<div *ngIf="user && user.roles && user.roles.includes('admin') && !user.suspended">
  Admin content
</div>

<!-- ✅ Good: Extract to component property -->
<div *ngIf="isActiveAdmin">
  Admin content
</div>
```

```typescript
// In component
get isActiveAdmin(): boolean {
  return this.user?.roles?.includes('admin') && !this.user?.suspended;
}
```

## 🎨 Styling Guidelines

### SCSS Structure
```scss
// Component-specific styles
.user-list {
  // Use BEM-like naming
  &__item {
    padding: 1rem;
    border-bottom: 1px solid #eee;

    &--active {
      background-color: #f0f0f0;
    }
  }

  &__name {
    font-weight: bold;
  }
}
```

### Tailwind CSS
```html
<!-- Use Tailwind utility classes -->
<div class="flex items-center justify-between p-4 bg-white rounded-lg shadow">
  <h2 class="text-xl font-semibold text-gray-800">Title</h2>
  <button class="px-4 py-2 text-white bg-blue-500 rounded hover:bg-blue-600">
    Action
  </button>
</div>
```

### Style Organization
- Use Tailwind for layout and common utilities
- Use component SCSS for complex, component-specific styles
- Avoid inline styles
- Use CSS variables for theming

## 🧪 Testing Standards

### Unit Test Structure
```typescript
describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [UserService]
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getUsers', () => {
    it('should return users array', () => {
      const mockUsers: User[] = [
        { id: '1', name: 'User 1' },
        { id: '2', name: 'User 2' }
      ];

      service.getUsers().subscribe(users => {
        expect(users).toEqual(mockUsers);
      });

      const req = httpMock.expectOne('/api/users');
      expect(req.request.method).toBe('GET');
      req.flush(mockUsers);
    });
  });
});
```

### Test Coverage
- Aim for 80%+ code coverage
- Test all public methods
- Test error scenarios
- Test edge cases

### Test Naming
```typescript
// Pattern: should [expected behavior] when [condition]
it('should return empty array when no users exist', () => {});
it('should throw error when API request fails', () => {});
it('should update user when valid data is provided', () => {});
```

## 📦 Code Organization

### Import Organization
```typescript
// 1. Angular imports
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

// 2. Third-party imports
import { Observable } from 'rxjs';

// 3. Application imports
import { UserService } from '@/app/core/services/user.service';
import { User } from '@/app/shared/models/user.model';
```

### Module Organization

#### Core Module (Singleton Services)
- Authentication service
- API service
- Guards
- Interceptors
- App-wide constants

#### Shared Module (Reusable Components)
- UI components
- Pipes
- Directives
- Shared models

#### Feature Modules
- Feature-specific components
- Feature services
- Feature models
- Feature routing

## ✅ Best Practices

### General

1. **Use Reactive Programming**
   ```typescript
   // ✅ Good: Use observables
   users$ = this.userService.getUsers();
   
   // ❌ Bad: Subscribe in component
   users: User[] = [];
   ngOnInit() {
     this.userService.getUsers().subscribe(u => this.users = u);
   }
   ```

2. **Lazy Load Feature Modules**
   ```typescript
   const routes: Routes = [
     {
       path: 'orders',
       loadComponent: () => import('./features/orders/order-list/order-list')
         .then(m => m.OrderListComponent)
     }
   ];
   ```

3. **Use Dependency Injection**
   ```typescript
   // ✅ Good
   constructor(private userService: UserService) {}
   
   // ❌ Bad
   userService = new UserService();
   ```

4. **Follow Single Responsibility Principle**
   - One component = one responsibility
   - One service = one data domain
   - Keep functions focused and small

5. **Use TypeScript Strict Mode**
   ```json
   {
     "compilerOptions": {
       "strict": true,
       "noImplicitAny": true,
       "strictNullChecks": true
     }
   }
   ```

### Performance

1. **Use OnPush Change Detection**
2. **Implement trackBy in ngFor**
3. **Use async pipe in templates**
4. **Lazy load routes and components**
5. **Optimize images and assets**

### Security

1. **Sanitize user inputs**
2. **Use HTTPS for all API calls**
3. **Implement proper authentication**
4. **Never expose sensitive data in frontend**
5. **Use environment variables for configuration**

### Accessibility

1. **Use semantic HTML**
2. **Add ARIA labels where needed**
3. **Ensure keyboard navigation**
4. **Provide alt text for images**
5. **Test with screen readers**

## 📝 Code Comments

### When to Comment
```typescript
// ✅ Good: Explain WHY, not WHAT
// Using setTimeout to defer execution after view initialization
// to prevent ExpressionChangedAfterItHasBeenCheckedError
setTimeout(() => this.loadData(), 0);

// ❌ Bad: Obvious comments
// Set username to value
this.username = value;
```

### JSDoc for Public APIs
```typescript
/**
 * Retrieves a user by their unique identifier
 * @param id - The unique user identifier
 * @returns Observable containing the user data
 * @throws {NotFoundException} When user is not found
 */
getUserById(id: string): Observable<User> {
  return this.http.get<User>(`/api/users/${id}`);
}
```

## 🔍 Code Review Checklist

- [ ] Code follows naming conventions
- [ ] Type safety is maintained (no 'any' types)
- [ ] Components use OnPush change detection
- [ ] Observables are properly unsubscribed
- [ ] Unit tests are included and passing
- [ ] Code is properly formatted (Prettier)
- [ ] No linting errors (ESLint)
- [ ] Accessibility considerations addressed
- [ ] Performance implications considered
- [ ] Security best practices followed

---

**Last Updated**: December 2025

For questions or suggestions, please reach out to the development team.
