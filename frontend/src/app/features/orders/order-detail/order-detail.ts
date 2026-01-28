import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { OrderService } from '../order.service';
import { Order } from '@shared/models/order.model';

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './order-detail.html',
  styleUrls: ['./order-detail.scss'],
})
export class OrderDetail implements OnInit {
  private orderService = inject(OrderService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  order = signal<Order | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadOrder(parseInt(id, 10));
    } else {
      this.error.set('Invalid order ID');
    }
  }

  loadOrder(id: number) {
    this.loading.set(true);
    this.error.set(null);

    this.orderService.getOrderById(id).subscribe({
      next: (order) => {
        this.order.set(order);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load order: ' + (err.error?.message || err.message));
        this.loading.set(false);
      },
    });
  }

  confirmOrder() {
    if (confirm('Confirm this order?')) {
      this.orderService.confirmOrder(this.order()!.id).subscribe({
        next: () => {
          alert('Order confirmed successfully!');
          this.loadOrder(this.order()!.id);
        },
        error: (err) => {
          alert('Failed to confirm order: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  payOrder() {
    if (confirm('Process payment for this order?')) {
      this.orderService.payOrder(this.order()!.id, 'credit_card').subscribe({
        next: (paymentInfo) => {
          alert(`Payment ${paymentInfo.status}: ${paymentInfo.message}`);
          this.loadOrder(this.order()!.id);
        },
        error: (err) => {
          alert('Payment failed: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  shipOrder() {
    if (confirm('Start shipping this order?')) {
      this.orderService.shipOrder(this.order()!.id).subscribe({
        next: () => {
          alert('Shipping started successfully!');
          this.loadOrder(this.order()!.id);
        },
        error: (err) => {
          alert('Failed to start shipping: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  completeOrder() {
    if (confirm('Mark this order as completed?')) {
      this.orderService.completeOrder(this.order()!.id).subscribe({
        next: () => {
          alert('Order completed successfully!');
          this.loadOrder(this.order()!.id);
        },
        error: (err) => {
          alert('Failed to complete order: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  cancelOrder() {
    if (confirm('Are you sure you want to cancel this order?')) {
      this.orderService.cancelOrder(this.order()!.id).subscribe({
        next: () => {
          alert('Order cancelled successfully!');
          this.loadOrder(this.order()!.id);
        },
        error: (err) => {
          alert('Failed to cancel order: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  goBack() {
    this.router.navigate(['/orders']);
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  }
}
