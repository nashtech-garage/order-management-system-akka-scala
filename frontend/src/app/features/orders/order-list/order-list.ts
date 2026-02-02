import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OrderService } from '../order.service';
import { Order } from '@shared/models/order.model';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './order-list.html',
  styleUrls: ['./order-list.scss'],
})
export class OrderList implements OnInit {
  private orderService = inject(OrderService);
  private router = inject(Router);

  orders = signal<Order[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  offset = signal(0);
  limit = signal(20);
  selectedStatus = '';
  selectedCustomerId: number | null = null;

  ngOnInit() {
    this.loadOrders();
  }

  loadOrders() {
    this.loading.set(true);
    this.error.set(null);

    const params: { offset: number; limit: number; status?: string; customerId?: number } = {
      offset: this.offset(),
      limit: this.limit(),
    };

    if (this.selectedStatus) {
      params.status = this.selectedStatus;
    }

    if (this.selectedCustomerId) {
      params.customerId = this.selectedCustomerId;
    }

    this.orderService.getOrders(params).subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load orders: ' + (err.error?.message || err.message));
        this.loading.set(false);
      },
    });
  }

  onFilterChange() {
    this.offset.set(0);
    this.loadOrders();
  }

  viewOrder(id: number) {
    this.router.navigate(['/orders', id]);
  }

  navigateToCreate() {
    this.router.navigate(['/orders/create']);
  }

  confirmOrder(id: number) {
    if (confirm('Confirm this order?')) {
      this.orderService.confirmOrder(id).subscribe({
        next: () => {
          alert('Order confirmed successfully!');
          this.loadOrders();
        },
        error: (err) => {
          alert('Failed to confirm order: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  payOrder(id: number) {
    if (confirm('Process payment for this order?')) {
      this.orderService.payOrder(id, 'credit_card').subscribe({
        next: (paymentInfo) => {
          alert(`Payment ${paymentInfo.status}: ${paymentInfo.message}`);
          this.loadOrders();
        },
        error: (err) => {
          alert('Payment failed: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  cancelOrder(id: number) {
    if (confirm('Are you sure you want to cancel this order?')) {
      this.orderService.cancelOrder(id).subscribe({
        next: () => {
          alert('Order cancelled successfully!');
          this.loadOrders();
        },
        error: (err) => {
          alert('Failed to cancel order: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  completeOrder(id: number) {
    if (confirm('Mark this order as completed?')) {
      this.orderService.completeOrder(id).subscribe({
        next: () => {
          alert('Order completed successfully!');
          this.loadOrders();
        },
        error: (err) => {
          alert('Failed to complete order: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  currentPage() {
    return Math.floor(this.offset() / this.limit()) + 1;
  }

  previousPage() {
    if (this.offset() > 0) {
      this.offset.set(Math.max(0, this.offset() - this.limit()));
      this.loadOrders();
    }
  }

  nextPage() {
    this.offset.set(this.offset() + this.limit());
    this.loadOrders();
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  }
}
