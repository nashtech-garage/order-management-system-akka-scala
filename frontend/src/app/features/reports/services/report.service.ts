import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  ScheduledReport,
  ReportListResponse,
  DashboardSummary,
  DailyStats,
} from '../models/report.model';

@Injectable({
  providedIn: 'root',
})
export class ReportService {
  private apiUrl = `${environment.apiUrl}/reports`;

  constructor(private http: HttpClient) {}

  /**
   * Get paginated list of scheduled reports
   */
  getScheduledReports(
    page = 0,
    pageSize = 20,
    startDate?: string,
    endDate?: string,
  ): Observable<ReportListResponse> {
    let params = new HttpParams().set('page', page.toString()).set('pageSize', pageSize.toString());

    if (startDate) {
      params = params.set('startDate', startDate);
    }
    if (endDate) {
      params = params.set('endDate', endDate);
    }

    return this.http.get<ReportListResponse>(`${this.apiUrl}/scheduled`, { params });
  }

  /**
   * Get a specific scheduled report by ID
   */
  getScheduledReportById(id: number): Observable<ScheduledReport> {
    return this.http.get<ScheduledReport>(`${this.apiUrl}/scheduled/${id}`);
  }

  /**
   * Get the latest scheduled report
   */
  getLatestReport(): Observable<ScheduledReport> {
    return this.http.get<ScheduledReport>(`${this.apiUrl}/scheduled/latest`);
  }

  /**
   * Manually trigger report generation
   */
  generateReport(): Observable<ScheduledReport> {
    return this.http.post<ScheduledReport>(`${this.apiUrl}/scheduled/generate`, {});
  }

  /**
   * Get dashboard summary
   */
  getDashboardSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.apiUrl}/dashboard`);
  }

  /**
   * Get daily statistics
   */
  getDailyStats(days = 30): Observable<DailyStats[]> {
    const params = new HttpParams().set('days', days.toString());
    return this.http.get<DailyStats[]>(`${this.apiUrl}/daily-stats`, {
      params,
    });
  }
}
