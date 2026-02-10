import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { OrderService } from '../order.service';
import { Order } from '@shared/models/order.model';
import { ToastService } from '@shared/services/toast.service';
import { ConfirmationDialogService } from '@shared/services/confirmation-dialog.service';

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
  private toastService = inject(ToastService);
  private confirmationDialog = inject(ConfirmationDialogService);

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
    this.confirmationDialog.confirmAction('Confirm this order?').subscribe((confirmed) => {
      if (confirmed) {
        this.orderService.confirmOrder(this.order()!.id).subscribe({
          next: () => {
            this.toastService.success('Order confirmed successfully!');
            this.loadOrder(this.order()!.id);
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

  payOrder() {
    this.confirmationDialog
      .confirmAction('Process payment for this order?')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.orderService.payOrder(this.order()!.id, 'credit_card').subscribe({
            next: (paymentInfo) => {
              this.toastService.success(`Payment ${paymentInfo.status}: ${paymentInfo.message}`);
              this.loadOrder(this.order()!.id);
            },
            error: (err) => {
              this.toastService.error('Payment failed: ' + (err.error?.error || err.message));
            },
          });
        }
      });
  }

  shipOrder() {
    this.confirmationDialog.confirmAction('Start shipping this order?').subscribe((confirmed) => {
      if (confirmed) {
        this.orderService.shipOrder(this.order()!.id).subscribe({
          next: () => {
            this.toastService.success('Shipping started successfully!');
            this.loadOrder(this.order()!.id);
          },
          error: (err) => {
            this.toastService.error(
              'Failed to start shipping: ' + (err.error?.error || err.message),
            );
          },
        });
      }
    });
  }

  completeOrder() {
    this.confirmationDialog
      .confirmAction('Mark this order as completed?')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.orderService.completeOrder(this.order()!.id).subscribe({
            next: () => {
              this.toastService.success('Order completed successfully!');
              this.loadOrder(this.order()!.id);
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

  cancelOrder() {
    this.confirmationDialog
      .confirmWarning('Are you sure you want to cancel this order?', 'Cancel Order')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.orderService.cancelOrder(this.order()!.id).subscribe({
            next: () => {
              this.toastService.success('Order cancelled successfully!');
              this.loadOrder(this.order()!.id);
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

  goBack() {
    this.router.navigate(['/orders']);
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  }
}
