import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OrderService } from '../order.service';
import { Order } from '@shared/models/order.model';
import { ToastService } from '@shared/services/toast.service';
import { ConfirmationDialogService } from '@shared/services/confirmation-dialog.service';

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
  private toastService = inject(ToastService);
  private confirmationDialog = inject(ConfirmationDialogService);

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
    this.confirmationDialog.confirmAction('Confirm this order?').subscribe((confirmed) => {
      if (confirmed) {
        this.orderService.confirmOrder(id).subscribe({
          next: () => {
            this.toastService.success('Order confirmed successfully!');
            this.loadOrders();
          },
          error: (err) => {
            this.toastService.error(
              'Failed to confirm order: ' + (err.error?.error || err.message),
            );
          },
        });
      }
    });
  }

  payOrder(id: number) {
    this.confirmationDialog
      .confirmAction('Process payment for this order?')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.orderService.payOrder(id, 'credit_card').subscribe({
            next: (paymentInfo) => {
              this.toastService.success(`Payment ${paymentInfo.status}: ${paymentInfo.message}`);
              this.loadOrders();
            },
            error: (err) => {
              this.toastService.error('Payment failed: ' + (err.error?.error || err.message));
            },
          });
        }
      });
  }

  cancelOrder(id: number) {
    this.confirmationDialog
      .confirmWarning('Are you sure you want to cancel this order?', 'Cancel Order')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.orderService.cancelOrder(id).subscribe({
            next: () => {
              this.toastService.success('Order cancelled successfully!');
              this.loadOrders();
            },
            error: (err) => {
              this.toastService.error(
                'Failed to cancel order: ' + (err.error?.error || err.message),
              );
            },
          });
        }
      });
  }

  completeOrder(id: number) {
    this.confirmationDialog
      .confirmAction('Mark this order as completed?')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.orderService.completeOrder(id).subscribe({
            next: () => {
              this.toastService.success('Order completed successfully!');
              this.loadOrders();
            },
            error: (err) => {
              this.toastService.error(
                'Failed to complete order: ' + (err.error?.error || err.message),
              );
            },
          });
        }
      });
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
