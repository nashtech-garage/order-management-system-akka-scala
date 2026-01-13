# Order Management System (OMS)

## 1. Project Overview

The Order Management System (OMS) is a system designed to manage orders for a small online shop. It includes:

- Product Management
- Customer Management
- Order Management
- Order Processing Workflow: Draft → Created → Paid → Shipping → Completed → Cancelled
- End-of-Day Reporting

## 2. Business Scope

### 2.1 Product Management

The system allows users to:

- Create products
- Update product information
- Deactivate products
- Search products by name or SKU
- View product list
- View product details

**Product Data Model:**

| Field | Description |
|-------|-------------|
| productId | Unique identifier |
| name | Product name |
| sku | Stock Keeping Unit |
| price | Unit price |
| status | ACTIVE / INACTIVE |

### 2.2 Customer Management

Features include:

- Add new customer
- Update customer information
- View customer list
- View customer details
- Search by name, email, or phone

**Customer Data Model:**

| Field | Description |
|----------|-------------|
| customerId | Unique identifier |
| name | Customer name |
| email | Email address |
| phone | Phone number |

### 2.3 Order Management

#### 2.3.1 Create Order

**Input:**
- customerId
- List of products (productId, quantity)

**Logic:**
- System automatically calculates total amount: `totalAmount = sum(item.price * quantity)`
- Order is initialized with status Draft
- When confirmed, status changes to Created

#### 2.3.2 Order Statuses

| Status | Description |
|--------|-------------|
| Draft | Newly created but not yet confirmed |
| Created | Confirmed, awaiting payment |
| Paid | Successfully paid |
| Shipping | Order is being delivered |
| Completed | Delivered successfully |
| Cancelled | Cancelled at any stage |

#### 2.3.3 State Transition Rules

```
Draft → Created → Paid → Shipping → Completed
  |       |        |         |
  └───────┴────────┴─────────┴──→ Cancelled
```

**Main rules:**
- Draft → Created: only if there is at least 1 product
- Created → Paid: only if totalAmount > 0
- Paid → Shipping: only when paymentSuccess = true
- Shipping → Completed: only when the shipper confirms

### 2.4 Payment Processing

Payment is mocked (no real gateway).

**Logic:**

When order is in Created state, system can trigger payment:
- 80% success probability
- 20% simulated failure
- On failure → order remains in Created
- On success → order moves to Paid

**Payment log structure:**
- paymentId
- orderId
- timestamp
- result (SUCCESS / FAILED)
- message (error details if any)

### 2.5 Shipping Process

Shipping logic is simulated:
- When order is Paid → system auto-triggers shipping
- Process time: 1–3 seconds
- After successful delivery → status becomes "Completed"

### 2.6 End-of-Day Report

A batch job is triggered manually by the user.

**JSON Output:**
- totalOrders
- completedOrders
- failedOrders
- revenue (sum of all Completed orders)
- reportDate

## 3. Detailed Use Cases

### UC-01: Product Management

**Goal:** Manage product catalog  
**Actor:** Admin / Staff

**Main Flow:**
1. User opens Products page
2. System displays product list
3. User can:
   - create
   - edit
   - deactivate
   - search by name / SKU

**Rules:**
- price must be > 0
- sku must be unique

### UC-02: Customer Management

**Goal:** Maintain customer list for order creation

**Flow:**
1. View customer list
2. Add customer
3. Edit customer
4. Search customer

**Rules:**
- email must be valid
- phone number must be valid

### UC-03: Create Order

**Flow:**
1. Select customer
2. Add products with quantities
3. Confirm order
4. System calculates total
5. Order is saved as Draft

**Rules:**
- at least one item
- quantity ≥ 1
- customerId must exist

### UC-04: Confirm Order

**Flow:**
1. User clicks "Confirm Order"
2. System transitions Draft → Created

**Rule:**
- only allowed if order has valid items

### UC-05: Payment

**Flow:**
1. User or system triggers payment
2. Mock payment executed with 20% failure rate
3. On success → order → Paid
4. On failure → order remains Created

### UC-06: Shipping

**Flow:**
1. When order is Paid → system automatically starts shipping
2. After 1–3 seconds → Shipping → Completed

### UC-07: Cancel Order

**Allowed statuses:**
- Draft
- Created
- Paid

**Not allowed:**
- Shipping
- Completed

### UC-08: View Order List

**Filters:**
- status
- customer
- created date
- amount range

**Sorting:**
- createdAt
- totalAmount

### UC-09: Dashboard

Dashboard provides:
- Total number of orders
- Number of pending orders
- Number of completed orders
- Total revenue
- Latest orders

### UC-10: End-of-Day Report

**Flow:**
1. User selects "Generate EOD Report"
2. System runs batch job
3. Computes revenue + completed count
4. Exports JSON to ./reports
5. UI displays result

## 4. Business Workflow

### Order Lifecycle

```
+---------+     +----------+     +------+     +-----------+     +------------+
| Draft   | --> | Created  | --> | Paid | --> | Shipping  | --> | Completed  |
+---------+     +----------+     +------+     +-----------+     +------------+
     |               |              |               |
     |               |              |               └──→ Cancelled
     |               |              └──────────────────→ Cancelled
     |               └─────────────────────────────────→ Cancelled
     └─────────────────────────────────────────────────→ Cancelled
```

## 5. Business Entity Model

### Product
- productId (string)
- name (string)
- sku (string)
- price (decimal)
- status (ACTIVE/INACTIVE)

### Customer
- customerId
- name
- email
- phone

### Order
- orderId
- customerId
- items: List<OrderItem>
- status
- totalAmount
- createdAt
- updatedAt

### OrderItem
- productId
- quantity
- unitPrice
- lineAmount

## 6. Business Validation Rules

### Order
- Customer must exist
- Must contain at least one item
- All products must be ACTIVE
- quantity ≥ 1

### Payment
- If payment fails → status does not change
- Cannot pay twice

### Shipping
- Shipping only allowed when status = Paid

### Cancel
- Cannot cancel when status = Shipping or Completed

## 7. Business Non-Functional Requirements

(simplified)

- UI load time < 2s
- Order list paging < 200ms
- Max 1000 orders per day for batch testing
- Log correlated by orderId

## 8. UI Samples

### Dashboard (Sample)

```
+-------------------------------------------+
| Total Orders: 120    Completed: 110      |
| Revenue: 82,000,000 VND                   |
| Pending: 10                               |
+-------------------------------------------+

Recent Orders:
[OrderId] [Customer] [Amount] [Status]
```

### Order List (Sample)

```
Filters: [Status] [Customer] [Date From] [Date To] [Min Amount] [Max Amount]

Table:
OrderId | Customer | Total | Status | CreatedAt | Actions
```

### Order Detail (Sample)

```
Order Info: customer, total, status

Items:
product | price | qty | lineAmount

Buttons:
[Confirm] [Pay] [Ship] [Cancel]
```

## 9. Business Value Summary

The OMS provides:
- Simple sales and order management
- Clear end-to-end order workflow
- Daily revenue reporting
- Management dashboard
- Realistic workflow simulation (payment → shipping → completion)