import { TestBed } from '@angular/core/testing';
import {
    HttpClientTestingModule,
    HttpTestingController,
} from '@angular/common/http/testing';
import { ReportService } from './report.service';
import {
    ScheduledReport,
    ReportListResponse,
    DashboardSummary,
    DailyStats,
} from '../models/report.model';
import { environment } from '../../../../environments/environment';

describe('ReportService', () => {
    let service: ReportService;
    let httpMock: HttpTestingController;
    const apiUrl = `${environment.apiUrl}/reports`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [ReportService],
        });
        service = TestBed.inject(ReportService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify(); // Verify no outstanding HTTP requests
    });

    describe('getScheduledReports', () => {
        it('should fetch scheduled reports with default parameters', () => {
            const mockResponse: ReportListResponse = {
                reports: [
                    {
                        id: 1,
                        reportType: 'daily',
                        reportDate: '2024-01-01',
                        totalOrders: 100,
                        totalRevenue: 5000,
                        averageOrderValue: 50,
                        ordersByStatus: { pending: 10, completed: 90 },
                        metadata: {},
                        generatedAt: '2024-01-01T10:00:00',
                    },
                ],
                total: 1,
                page: 0,
                pageSize: 20,
            };

            service.getScheduledReports().subscribe((response) => {
                expect(response).toEqual(mockResponse);
                expect(response.reports.length).toBe(1);
                expect(response.total).toBe(1);
            });

            const req = httpMock.expectOne(
                `${apiUrl}/scheduled?page=0&pageSize=20`
            );
            expect(req.request.method).toBe('GET');
            req.flush(mockResponse);
        });

        it('should include date filters when provided', () => {
            const mockResponse: ReportListResponse = {
                reports: [],
                total: 0,
                page: 0,
                pageSize: 20,
            };

            service
                .getScheduledReports(0, 20, '2024-01-01', '2024-01-31')
                .subscribe();

            const req = httpMock.expectOne(
                `${apiUrl}/scheduled?page=0&pageSize=20&startDate=2024-01-01&endDate=2024-01-31`
            );
            expect(req.request.method).toBe('GET');
            req.flush(mockResponse);
        });

        it('should handle custom page and pageSize', () => {
            const mockResponse: ReportListResponse = {
                reports: [],
                total: 50,
                page: 2,
                pageSize: 10,
            };

            service.getScheduledReports(2, 10).subscribe((response) => {
                expect(response.page).toBe(2);
                expect(response.pageSize).toBe(10);
            });

            const req = httpMock.expectOne(`${apiUrl}/scheduled?page=2&pageSize=10`);
            expect(req.request.method).toBe('GET');
            req.flush(mockResponse);
        });

        it('should handle HTTP errors', () => {
            service.getScheduledReports().subscribe({
                next: () => fail('should have failed with 500 error'),
                error: (error) => {
                    expect(error.status).toBe(500);
                },
            });

            const req = httpMock.expectOne(
                `${apiUrl}/scheduled?page=0&pageSize=20`
            );
            req.flush('Server error', { status: 500, statusText: 'Server Error' });
        });
    });

    describe('getScheduledReportById', () => {
        it('should fetch a specific report by ID', () => {
            const mockReport: ScheduledReport = {
                id: 123,
                reportType: 'daily',
                reportDate: '2024-01-01',
                totalOrders: 100,
                totalRevenue: 5000,
                averageOrderValue: 50,
                ordersByStatus: { pending: 10, completed: 90 },
                metadata: {},
                generatedAt: '2024-01-01T10:00:00',
            };

            service.getScheduledReportById(123).subscribe((report) => {
                expect(report).toEqual(mockReport);
                expect(report.id).toBe(123);
            });

            const req = httpMock.expectOne(`${apiUrl}/scheduled/123`);
            expect(req.request.method).toBe('GET');
            req.flush(mockReport);
        });

        it('should handle 404 errors for non-existent reports', () => {
            service.getScheduledReportById(999).subscribe({
                next: () => fail('should have failed with 404 error'),
                error: (error) => {
                    expect(error.status).toBe(404);
                },
            });

            const req = httpMock.expectOne(`${apiUrl}/scheduled/999`);
            req.flush('Not found', { status: 404, statusText: 'Not Found' });
        });
    });

    describe('getLatestReport', () => {
        it('should fetch the latest scheduled report', () => {
            const mockReport: ScheduledReport = {
                id: 1,
                reportType: 'daily',
                reportDate: '2024-01-15',
                totalOrders: 200,
                totalRevenue: 10000,
                averageOrderValue: 50,
                ordersByStatus: { completed: 200 },
                metadata: {},
                generatedAt: '2024-01-15T23:59:00',
            };

            service.getLatestReport().subscribe((report) => {
                expect(report).toEqual(mockReport);
                expect(report.reportDate).toBe('2024-01-15');
            });

            const req = httpMock.expectOne(`${apiUrl}/scheduled/latest`);
            expect(req.request.method).toBe('GET');
            req.flush(mockReport);
        });

        it('should handle errors when no reports exist', () => {
            service.getLatestReport().subscribe({
                next: () => fail('should have failed with 404 error'),
                error: (error) => {
                    expect(error.status).toBe(404);
                },
            });

            const req = httpMock.expectOne(`${apiUrl}/scheduled/latest`);
            req.flush('No reports found', { status: 404, statusText: 'Not Found' });
        });
    });

    describe('generateReport', () => {
        it('should trigger report generation', () => {
            const mockReport: ScheduledReport = {
                id: 2,
                reportType: 'daily',
                reportDate: '2024-01-16',
                totalOrders: 150,
                totalRevenue: 7500,
                averageOrderValue: 50,
                ordersByStatus: { completed: 150 },
                metadata: {},
                generatedAt: '2024-01-16T12:00:00',
            };

            service.generateReport().subscribe((report) => {
                expect(report).toEqual(mockReport);
                expect(report.id).toBe(2);
            });

            const req = httpMock.expectOne(`${apiUrl}/scheduled/generate`);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual({});
            req.flush(mockReport);
        });

        it('should handle generation errors', () => {
            service.generateReport().subscribe({
                next: () => fail('should have failed with 400 error'),
                error: (error) => {
                    expect(error.status).toBe(400);
                },
            });

            const req = httpMock.expectOne(`${apiUrl}/scheduled/generate`);
            req.flush('Generation failed', {
                status: 400,
                statusText: 'Bad Request',
            });
        });
    });

    describe('getDashboardSummary', () => {
        it('should fetch dashboard summary', () => {
            const mockSummary: DashboardSummary = {
                totalOrders: 500,
                totalRevenue: 25000,
                topProducts: [
                    {
                        productId: 1,
                        productName: 'Product A',
                        totalQuantitySold: 100,
                        totalRevenue: 5000,
                    },
                ],
                topCustomers: [
                    {
                        customerId: 1,
                        customerName: 'Customer A',
                        totalOrders: 50,
                        totalSpent: 2500,
                    },
                ],
                recentStats: [
                    { date: '2024-01-01', orderCount: 10, revenue: 500 },
                ],
            };

            service.getDashboardSummary().subscribe((summary) => {
                expect(summary).toEqual(mockSummary);
                expect(summary.totalOrders).toBe(500);
                expect(summary.topProducts.length).toBe(1);
            });

            const req = httpMock.expectOne(`${apiUrl}/dashboard`);
            expect(req.request.method).toBe('GET');
            req.flush(mockSummary);
        });

        it('should handle dashboard errors', () => {
            service.getDashboardSummary().subscribe({
                next: () => fail('should have failed with 500 error'),
                error: (error) => {
                    expect(error.status).toBe(500);
                },
            });

            const req = httpMock.expectOne(`${apiUrl}/dashboard`);
            req.flush('Server error', { status: 500, statusText: 'Server Error' });
        });
    });

    describe('getDailyStats', () => {
        it('should fetch daily stats with default days parameter', () => {
            const mockStats: DailyStats[] = [
                { date: '2024-01-01', orderCount: 10, revenue: 500 },
                { date: '2024-01-02', orderCount: 15, revenue: 750 },
            ];

            service.getDailyStats().subscribe((stats) => {
                expect(stats).toEqual(mockStats);
                expect(stats.length).toBe(2);
            });

            const req = httpMock.expectOne(`${apiUrl}/daily-stats?days=30`);
            expect(req.request.method).toBe('GET');
            req.flush(mockStats);
        });

        it('should fetch daily stats with custom days parameter', () => {
            const mockStats: DailyStats[] = [
                { date: '2024-01-01', orderCount: 10, revenue: 500 },
            ];

            service.getDailyStats(7).subscribe((stats) => {
                expect(stats.length).toBe(1);
            });

            const req = httpMock.expectOne(`${apiUrl}/daily-stats?days=7`);
            expect(req.request.method).toBe('GET');
            req.flush(mockStats);
        });

        it('should handle empty stats response', () => {
            service.getDailyStats(30).subscribe((stats) => {
                expect(stats).toEqual([]);
                expect(stats.length).toBe(0);
            });

            const req = httpMock.expectOne(`${apiUrl}/daily-stats?days=30`);
            req.flush([]);
        });

        it('should handle stats errors', () => {
            service.getDailyStats().subscribe({
                next: () => fail('should have failed with 500 error'),
                error: (error) => {
                    expect(error.status).toBe(500);
                },
            });

            const req = httpMock.expectOne(`${apiUrl}/daily-stats?days=30`);
            req.flush('Server error', { status: 500, statusText: 'Server Error' });
        });
    });
});
