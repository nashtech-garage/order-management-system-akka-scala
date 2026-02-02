# Payment Management UI

This module provides a complete user interface for managing payments in the Order Management System.

## Features

### 1. Payment List (`/payments`)
- View all payments with pagination
- Filter payments by status (Pending, Processing, Completed, Failed, Refunded)
- Display payment details: ID, Order ID, Amount, Payment Method, Status, Transaction ID, Created Date
- Quick actions: View, Process (for pending), Refund (for completed)
- Status badges with color coding
- Navigate to related order

### 2. Payment Creation (`/payments/create`)
- Create new payment for an order
- Select from available orders (created status)
- Auto-fill amount from selected order
- Choose payment method: Credit Card, Debit Card, Bank Transfer, PayPal, Cash
- Form validation with error messages

### 3. Payment Detail (`/payments/:id`)
- View complete payment information
- Display payment status with visual badge
- Link to associated order
- Actions based on status:
  - Process payment (for pending)
  - Refund payment (for completed)
- Status descriptions

## Components

### PaymentList
**Location:** `features/payments/payment-list/`
- Displays paginated table of payments
- Filterable by status
- Actions: View, Process, Refund

### PaymentForm
**Location:** `features/payments/payment-form/`
- Reactive form for creating payments
- Order selection with amount auto-fill
- Payment method dropdown
- Form validation

### PaymentDetail
**Location:** `features/payments/payment-detail/`
- Shows detailed payment information
- Status-based action buttons
- Navigation to related order

## Service

### PaymentService
**Location:** `features/payments/payment.service.ts`

Methods:
- `getPayments(params?)` - Get list of payments with optional filters
- `getPaymentById(id)` - Get single payment by ID
- `getPaymentByOrderId(orderId)` - Get payment for specific order
- `getPaymentsByStatus(status)` - Filter payments by status
- `createPayment(payment)` - Create new payment
- `processPayment(id)` - Process pending payment
- `completePayment(id, transactionId)` - Mark payment as completed
- `failPayment(id, reason?)` - Mark payment as failed
- `refundPayment(id)` - Refund completed payment
- `getPaymentStats()` - Get payment statistics

## Models

### Payment
```typescript
interface Payment {
  id: number;
  orderId: number;
  createdBy: number;
  amount: number;
  paymentMethod: string;
  status: PaymentStatus;
  transactionId?: string;
  createdAt: string;
}
```

### PaymentStatus
```typescript
enum PaymentStatus {
  PENDING = 'pending',
  PROCESSING = 'processing',
  COMPLETED = 'completed',
  FAILED = 'failed',
  REFUNDED = 'refunded',
}
```

## API Endpoints

The following API endpoints are configured:
- `GET /payments` - List all payments
- `GET /payments/:id` - Get payment by ID
- `GET /payments/order/:orderId` - Get payment by order ID
- `POST /payments` - Create new payment
- `POST /payments/:id/process` - Process payment
- `POST /payments/:id/complete` - Complete payment
- `POST /payments/:id/fail` - Fail payment
- `POST /payments/:id/refund` - Refund payment
- `GET /payments/stats` - Get payment statistics

## Routing

Payment routes are configured in `app.routes.ts`:
```typescript
{
  path: 'payments',
  children: [
    { path: '', component: PaymentList },
    { path: 'create', component: PaymentForm },
    { path: ':id', component: PaymentDetail },
  ]
}
```

## Styling

Each component has its own SCSS file with:
- Responsive design
- Status badge colors
- Form styling with validation states
- Table layouts with hover effects
- Action button styles

## Usage

### Viewing Payments
1. Navigate to `/payments` from the sidebar menu (💳 Payments)
2. Use the status filter to narrow results
3. Click "View" to see payment details
4. Use pagination to browse through pages

### Creating a Payment
1. Click "Create Payment" button
2. Select an order from the dropdown
3. Verify the amount (auto-filled from order)
4. Choose payment method
5. Click "Create Payment"

### Processing a Payment
1. Open a payment with "Pending" status
2. Click "Process Payment" button
3. Confirm the action
4. Payment will be processed with 80% success rate (as per backend simulation)

### Refunding a Payment
1. Open a payment with "Completed" status
2. Click "Refund Payment" button
3. Confirm the action
4. Payment status will change to "Refunded"

## Integration with Backend

The payment service integrates with the Scala/Akka backend payment service:
- Base URL: Configured in `environment.ts`
- Authentication: Uses auth token from local storage
- Error Handling: Displays user-friendly error messages

## Future Enhancements

- Payment history timeline
- Transaction receipt generation
- Payment gateway integration details
- Bulk payment operations
- Advanced filtering (date range, amount range)
- Export to CSV/PDF
- Payment analytics dashboard
- Real-time payment status updates via WebSocket
