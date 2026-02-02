import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PaymentService } from '../payment.service';
import { Payment, PaymentStatus } from '@shared/models/payment.model';

@Component({
  selector: 'app-payment-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './payment-list.html',
  styleUrls: ['./payment-list.scss'],
})
export class PaymentList implements OnInit {
  private paymentService = inject(PaymentService);
  private router = inject(Router);

  payments = signal<Payment[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);
  offset = signal(0);
  limit = signal(20);
  selectedStatus = '';

  readonly PaymentStatus = PaymentStatus;

  ngOnInit() {
    this.loadPayments();
  }

  loadPayments() {
    this.loading.set(true);
    this.error.set(null);

    const params: { offset: number; limit: number; status?: string } = {
      offset: this.offset(),
      limit: this.limit(),
    };

    if (this.selectedStatus) {
      params.status = this.selectedStatus;
    }

    this.paymentService.getPayments(params).subscribe({
      next: (payments) => {
        this.payments.set(payments);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load payments: ' + (err.error?.message || err.message));
        this.loading.set(false);
      },
    });
  }

  onFilterChange() {
    this.offset.set(0);
    this.loadPayments();
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

  currentPage() {
    return Math.floor(this.offset() / this.limit()) + 1;
  }

  previousPage() {
    if (this.offset() > 0) {
      this.offset.set(Math.max(0, this.offset() - this.limit()));
      this.loadPayments();
    }
  }

  nextPage() {
    this.offset.set(this.offset() + this.limit());
    this.loadPayments();
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
  