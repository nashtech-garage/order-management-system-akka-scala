import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { PaymentService } from '../payment.service';
import { OrderService } from '@features/orders/order.service';
import { Order } from '@shared/models/order.model';

@Component({
  selector: 'app-payment-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './payment-form.html',
  styleUrls: ['./payment-form.scss'],
})
export class PaymentForm implements OnInit {
  private fb = inject(FormBuilder);
  private paymentService = inject(PaymentService);
  private orderService = inject(OrderService);
  private router = inject(Router);

  paymentForm!: FormGroup;
  loading = signal(false);
  error = signal<string | null>(null);
  orders = signal<Order[]>([]);

  readonly paymentMethods = [
    { value: 'credit_card', label: 'Credit Card' },
    { value: 'debit_card', label: 'Debit Card' },
    { value: 'bank_transfer', label: 'Bank Transfer' },
    { value: 'paypal', label: 'PayPal' },
    { value: 'cash', label: 'Cash' },
  ];

  ngOnInit() {
    this.initForm();
    this.loadOrders();
  }

  initForm() {
    this.paymentForm = this.fb.group({
      orderId: ['', [Validators.required, Validators.min(1)]],
      amount: ['', [Validators.required, Validators.min(0.01)]],
      paymentMethod: ['credit_card', Validators.required],
    });
  }

  loadOrders() {
    this.orderService.getOrders({ status: 'created' }).subscribe({
      next: (orders) => {
        this.orders.set(orders);
      },
      error: (err) => {
        console.error('Failed to load orders:', err);
      },
    });
  }

  onOrderChange(event: Event) {
    const orderId = (event.target as HTMLSelectElement).value;
    const order = this.orders().find((o) => o.id === parseInt(orderId));
    if (order) {
      this.paymentForm.patchValue({ amount: order.totalAmount });
    }
  }

  onSubmit() {
    // This form is no longer used - payments are created from order page
    alert('Payment creation is now handled from the Order Management page');
    this.router.navigate(['/orders']);
  }

  cancel() {
    this.router.navigate(['/payments']);
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.paymentForm.get(fieldName);
    return !!(field && field.invalid && field.touched);
  }

  getFieldError(fieldName: string): string {
    const field = this.paymentForm.get(fieldName);
    if (field?.errors?.['required']) {
      return `${fieldName} is required`;
    }
    if (field?.errors?.['min']) {
      return `${fieldName} must be at least ${field.errors['min'].min}`;
    }
    return '';
  }
}
