import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ReportService } from '../services/report.service';
import { DashboardSummary, ScheduledReport, DailyStats } from '../models/report.model';

@Component({
  selector: 'app-reports-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './reports-dashboard.html',
  styleUrl: './reports-dashboard.scss',
})
export class ReportsDashboard implements OnInit {
  dashboardSummary: DashboardSummary | null = null;
  latestReport: ScheduledReport | null = null;
  dailyStats: DailyStats[] = [];
  loading = false;
  error: string | null = null;
  generating = false;

  constructor(private reportService: ReportService) {}

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
        alert('Report generated successfully!');
        this.loadDashboardData();
      },
      error: (err) => {
        this.generating = false;
        alert('Failed to generate report: ' + (err.error?.error || err.message));
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
