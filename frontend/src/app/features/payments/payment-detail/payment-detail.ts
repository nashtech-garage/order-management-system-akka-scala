import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { PaymentService } from '../payment.service';
import { Payment, PaymentStatus } from '@shared/models/payment.model';

@Component({
  selector: 'app-payment-detail',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payment-detail.html',
  styleUrls: ['./payment-detail.scss'],
})
export class PaymentDetail implements OnInit {
  private paymentService = inject(PaymentService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  payment = signal<Payment | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  readonly PaymentStatus = PaymentStatus;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadPayment(parseInt(id));
    }
  }

  loadPayment(id: number) {
    this.loading.set(true);
    this.error.set(null);

    this.paymentService.getPaymentById(id).subscribe({
      next: (payment) => {
        this.payment.set(payment);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load payment: ' + (err.error?.message || err.message));
        this.loading.set(false);
      },
    });
  }

  // Payment processing removed - payments are now handled from order page

  goBack() {
    this.router.navigate(['/payments']);
  }

  viewOrder() {
    const payment = this.payment();
    if (payment) {
      this.router.navigate(['/orders', payment.orderId]);
    }
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case PaymentStatus.SUCCESS:
        return 'badge-success';
      case PaymentStatus.FAILED:
        return 'badge-danger';
      default:
        return 'badge-default';
    }
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  }
}
