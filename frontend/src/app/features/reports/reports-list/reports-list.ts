import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ReportService } from '../services/report.service';
import { ScheduledReport, ReportListResponse } from '../models/report.model';

@Component({
  selector: 'app-reports-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './reports-list.html',
  styleUrl: './reports-list.scss',
})
export class ReportsList implements OnInit {
  reports: ScheduledReport[] = [];
  total = 0;
  page = 0;
  pageSize = 20;
  loading = false;
  error: string | null = null;

  // Filters
  startDate = '';
  endDate = '';

  constructor(private reportService: ReportService) {}

  ngOnInit(): void {
    this.loadReports();
  }

  loadReports(): void {
    this.loading = true;
    this.error = null;

    this.reportService
      .getScheduledReports(
        this.page,
        this.pageSize,
        this.startDate || undefined,
        this.endDate || undefined,
      )
      .subscribe({
        next: (response: ReportListResponse) => {
          this.reports = response.reports;
          this.total = response.total;
          this.loading = false;
        },
        error: (err) => {
          this.error = 'Failed to load reports';
          this.loading = false;
          console.error('Error loading reports:', err);
        },
      });
  }

  applyFilters(): void {
    this.page = 0;
    this.loadReports();
  }

  clearFilters(): void {
    this.startDate = '';
    this.endDate = '';
    this.page = 0;
    this.loadReports();
  }

  nextPage(): void {
    if ((this.page + 1) * this.pageSize < this.total) {
      this.page++;
      this.loadReports();
    }
  }

  previousPage(): void {
    if (this.page > 0) {
      this.page--;
      this.loadReports();
    }
  }

  getTotalPages(): number {
    return Math.ceil(this.total / this.pageSize);
  }

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(value);
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  }
}
