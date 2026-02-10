import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ReportService } from '../services/report.service';
import { DashboardSummary, ScheduledReport, DailyStats } from '../models/report.model';
import { ToastService } from '@shared/services/toast.service';

@Component({
  selector: 'app-reports-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './reports-dashboard.html',
  styleUrl: './reports-dashboard.scss',
})
export class ReportsDashboard implements OnInit {
  private reportService = inject(ReportService);
  private toastService = inject(ToastService);

  dashboardSummary: DashboardSummary | null = null;
  latestReport: ScheduledReport | null = null;
  dailyStats: DailyStats[] = [];
  loading = false;
  error: string | null = null;
  generating = false;

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading = true;
    this.error = null;

    // Load dashboard summary
    this.reportService.getDashboardSummary().subscribe({
      next: (summary) => {
        this.dashboardSummary = summary;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load dashboard data';
        this.loading = false;
        console.error('Error loading dashboard:', err);
      },
    });

    // Load latest report
    this.reportService.getLatestReport().subscribe({
      next: (report) => {
        this.latestReport = report;
      },
      error: (err) => {
        console.error('Error loading latest report:', err);
      },
    });

    // Load daily stats for chart
    this.reportService.getDailyStats(30).subscribe({
      next: (stats) => {
        this.dailyStats = stats;
      },
      error: (err) => {
        console.error('Error loading daily stats:', err);
      },
    });
  }

  generateReport(): void {
    this.generating = true;
    this.reportService.generateReport().subscribe({
      next: (report) => {
        this.generating = false;
        this.latestReport = report;
        this.toastService.success('Report generated successfully!');
        this.loadDashboardData();
      },
      error: (err) => {
        this.generating = false;
        this.toastService.error('Failed to generate report: ' + (err.error?.error || err.message));
        console.error('Error generating report:', err);
      },
    });
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

  getOrderStatusKeys(): string[] {
    return this.latestReport ? Object.keys(this.latestReport.ordersByStatus) : [];
  }

  getMaxRevenue(): number {
    if (this.dailyStats.length === 0) return 1;
    return Math.max(...this.dailyStats.map((s) => s.revenue));
  }
}
