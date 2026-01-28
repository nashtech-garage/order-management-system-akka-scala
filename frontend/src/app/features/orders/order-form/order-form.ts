import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormArray, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OrderService } from '../order.service';
import { CustomerService } from '@core/services/customer.service';
import { ProductService } from '@core/services/product.service';
import { Customer } from '@shared/models/customer.model';
import { ProductResponse } from '@shared/models/product.model';

@Component({
  selector: 'app-order-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './order-form.html',
  styleUrls: ['./order-form.scss'],
})
export class OrderForm implements OnInit {
  private fb = inject(FormBuilder);
  private orderService = inject(OrderService);
  private customerService = inject(CustomerService);
  private productService = inject(ProductService);
  private router = inject(Router);

  customers = signal<Customer[]>([]);
  products = signal<ProductResponse[]>([]);
  totalAmount = signal(0);
  submitting = signal(false);
  error = signal<string | null>(null);

  orderForm: FormGroup;

  constructor() {
    this.orderForm = this.fb.group({
      customerId: ['', Validators.required],
      items: this.fb.array([this.createItem()]),
    });
  }

  get items(): FormArray {
    return this.orderForm.get('items') as FormArray;
  }

  ngOnInit() {
    this.loadCustomers();
    this.loadProducts();
  }

  loadCustomers() {
    this.customerService.getCustomers(0, 100).subscribe({
      next: (customers) => this.customers.set(customers),
      error: (err) => console.error('Failed to load customers:', err),
    });
  }

  loadProducts() {
    this.productService.getProducts(0, 100).subscribe({
      next: (products) => this.products.set(products),
      error: (err) => console.error('Failed to load products:', err),
    });
  }

  createItem(): FormGroup {
    return this.fb.group({
      productId: ['', Validators.required],
      quantity: [1, [Validators.required, Validators.min(1)]],
    });
  }

  addItem() {
    this.items.push(this.createItem());
  }

  removeItem(index: number) {
    if (this.items.length > 1) {
      this.items.removeAt(index);
      this.updateTotal();
    }
  }

  onProductChange() {
    this.updateTotal();
  }

  getItemSubtotal(index: number): number {
    const item = this.items.at(index);
    const productId = item.get('productId')?.value;
    const quantity = item.get('quantity')?.value || 0;

    if (!productId) return 0;

    const product = this.products().find((p) => p.id === parseInt(productId));
    return product ? product.price * quantity : 0;
  }

  updateTotal() {
    let total = 0;
    for (let i = 0; i < this.items.length; i++) {
      total += this.getItemSubtotal(i);
    }
    this.totalAmount.set(total);
  }

  onSubmit() {
    if (this.orderForm.invalid) {
      this.orderForm.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    const formValue = this.orderForm.value;
    const orderRequest = {
      customerId: parseInt(formValue.customerId),
      items: formValue.items.map((item: { productId: string; quantity: string }) => ({
        productId: parseInt(item.productId),
        quantity: parseInt(item.quantity),
      })),
    };

    this.orderService.createOrder(orderRequest).subscribe({
      next: (order) => {
        this.submitting.set(false);
        alert('Order created successfully!');
        this.router.navigate(['/orders', order.id]);
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set('Failed to create order: ' + (err.error?.error || err.message));
      },
    });
  }

  goBack() {
    this.router.navigate(['/orders']);
  }
}
