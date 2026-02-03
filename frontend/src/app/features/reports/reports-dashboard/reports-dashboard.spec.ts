import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReportsDashboard } from './reports-dashboard';
import { ReportService } from '../services/report.service';
import { of, throwError } from 'rxjs';
import {
    DashboardSummary,
    ScheduledReport,
    DailyStats,
} from '../models/report.model';
import { provideRouter } from '@angular/router';

describe('ReportsDashboard', () => {
    let component: ReportsDashboard;
    let fixture: ComponentFixture<ReportsDashboard>;
    let mockReportService: jasmine.SpyObj<ReportService>;

    const mockDashboardSummary: DashboardSummary = {
        totalOrders: 500,
        totalRevenue: 25000,
        topProducts: [
            {
                productId: 1,
                productName: 'Product A',
                totalQuantitySold: 100,
                totalRevenue: 5000,
            },
            {
                productId: 2,
                productName: 'Product B',
                totalQuantitySold: 80,
                totalRevenue: 4000,
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

    const mockLatestReport: ScheduledReport = {
        id: 1,
        reportType: 'daily',
        reportDate: '2024-01-15',
        totalOrders: 200,
        totalRevenue: 10000,
        averageOrderValue: 50,
        ordersByStatus: { pending: 20, completed: 180 },
        metadata: {},
        generatedAt: '2024-01-15T23:59:00',
    };

    const mockDailyStats: DailyStats[] = [
        { date: '2024-01-01', orderCount: 10, revenue: 500 },
        { date: '2024-01-02', orderCount: 15, revenue: 750 },
        { date: '2024-01-03', orderCount: 20, revenue: 1000 },
    ];

    beforeEach(async () => {
        // Create spy object for ReportService
        mockReportService = jasmine.createSpyObj('ReportService', [
            'getDashboardSummary',
            'getLatestReport',
            'getDailyStats',
            'generateReport',
        ]);

        await TestBed.configureTestingModule({
            imports: [ReportsDashboard],
            providers: [
                { provide: ReportService, useValue: mockReportService },
                provideRouter([]),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ReportsDashboard);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('ngOnInit', () => {
        it('should load dashboard data on initialization', () => {
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            component.ngOnInit();

            expect(mockReportService.getDashboardSummary).toHaveBeenCalled();
            expect(mockReportService.getLatestReport).toHaveBeenCalled();
            expect(mockReportService.getDailyStats).toHaveBeenCalledWith(30);
        });
    });

    describe('loadDashboardData', () => {
        it('should set loading to true initially', () => {
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            component.loadDashboardData();

            expect(component.loading).toBe(true);
        });

        it('should load dashboard summary successfully', () => {
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            component.loadDashboardData();

            expect(component.dashboardSummary).toEqual(mockDashboardSummary);
            expect(component.loading).toBe(false);
            expect(component.error).toBeNull();
        });

        it('should load latest report successfully', () => {
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            component.loadDashboardData();

            expect(component.latestReport).toEqual(mockLatestReport);
        });

        it('should load daily stats successfully', () => {
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            component.loadDashboardData();

            expect(component.dailyStats).toEqual(mockDailyStats);
        });

        it('should handle dashboard summary error', () => {
            const errorResponse = { status: 500, message: 'Server error' };
            mockReportService.getDashboardSummary.and.returnValue(
                throwError(() => errorResponse)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            spyOn(console, 'error');

            component.loadDashboardData();

            expect(component.error).toBe('Failed to load dashboard data');
            expect(component.loading).toBe(false);
            expect(console.error).toHaveBeenCalledWith(
                'Error loading dashboard:',
                errorResponse
            );
        });

        it('should handle latest report error gracefully', () => {
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(
                throwError(() => ({ status: 404 }))
            );
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            spyOn(console, 'error');

            component.loadDashboardData();

            expect(console.error).toHaveBeenCalled();
            // Dashboard should still load even if latest report fails
            expect(component.dashboardSummary).toEqual(mockDashboardSummary);
        });

        it('should handle daily stats error gracefully', () => {
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(
                throwError(() => ({ status: 500 }))
            );

            spyOn(console, 'error');

            component.loadDashboardData();

            expect(console.error).toHaveBeenCalled();
            // Dashboard should still load even if daily stats fails
            expect(component.dashboardSummary).toEqual(mockDashboardSummary);
        });
    });

    describe('generateReport', () => {
        it('should set generating to true when generating report', () => {
            mockReportService.generateReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            component.generateReport();

            expect(component.generating).toBe(true);
        });

        it('should generate report successfully', () => {
            mockReportService.generateReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            spyOn(window, 'alert');

            component.generateReport();

            expect(component.generating).toBe(false);
            expect(component.latestReport).toEqual(mockLatestReport);
            expect(window.alert).toHaveBeenCalledWith(
                'Report generated successfully!'
            );
            expect(mockReportService.getDashboardSummary).toHaveBeenCalled();
        });

        it('should handle report generation error', () => {
            const errorResponse = { error: { error: 'Generation failed' } };
            mockReportService.generateReport.and.returnValue(
                throwError(() => errorResponse)
            );

            spyOn(window, 'alert');
            spyOn(console, 'error');

            component.generateReport();

            expect(component.generating).toBe(false);
            expect(window.alert).toHaveBeenCalledWith(
                'Failed to generate report: Generation failed'
            );
            expect(console.error).toHaveBeenCalledWith(
                'Error generating report:',
                errorResponse
            );
        });

        it('should handle report generation error without error message', () => {
            const errorResponse = { message: 'Network error' };
            mockReportService.generateReport.and.returnValue(
                throwError(() => errorResponse)
            );

            spyOn(window, 'alert');

            component.generateReport();

            expect(window.alert).toHaveBeenCalledWith(
                'Failed to generate report: Network error'
            );
        });
    });

    describe('formatCurrency', () => {
        it('should format currency correctly', () => {
            const result = component.formatCurrency(1234.56);
            expect(result).toBe('$1,234.56');
        });

        it('should format zero correctly', () => {
            const result = component.formatCurrency(0);
            expect(result).toBe('$0.00');
        });

        it('should format large numbers correctly', () => {
            const result = component.formatCurrency(1000000);
            expect(result).toBe('$1,000,000.00');
        });

        it('should format negative numbers correctly', () => {
            const result = component.formatCurrency(-500);
            expect(result).toBe('-$500.00');
        });
    });

    describe('formatDate', () => {
        it('should format date correctly', () => {
            const result = component.formatDate('2024-01-15');
            expect(result).toContain('Jan');
            expect(result).toContain('15');
            expect(result).toContain('2024');
        });

        it('should format ISO date string correctly', () => {
            const result = component.formatDate('2024-12-25T10:30:00');
            expect(result).toContain('Dec');
            expect(result).toContain('25');
            expect(result).toContain('2024');
        });
    });

    describe('getOrderStatusKeys', () => {
        it('should return order status keys when latest report exists', () => {
            component.latestReport = mockLatestReport;

            const keys = component.getOrderStatusKeys();

            expect(keys).toEqual(['pending', 'completed']);
        });

        it('should return empty array when latest report is null', () => {
            component.latestReport = null;

            const keys = component.getOrderStatusKeys();

            expect(keys).toEqual([]);
        });
    });

    describe('getMaxRevenue', () => {
        it('should return max revenue from daily stats', () => {
            component.dailyStats = mockDailyStats;

            const maxRevenue = component.getMaxRevenue();

            expect(maxRevenue).toBe(1000);
        });

        it('should return 1 when daily stats is empty', () => {
            component.dailyStats = [];

            const maxRevenue = component.getMaxRevenue();

            expect(maxRevenue).toBe(1);
        });

        it('should handle single stat entry', () => {
            component.dailyStats = [
                { date: '2024-01-01', orderCount: 10, revenue: 500 },
            ];

            const maxRevenue = component.getMaxRevenue();

            expect(maxRevenue).toBe(500);
        });
    });

    describe('DOM rendering', () => {
        it('should display loading state', () => {
            component.loading = true;
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const loadingElement = compiled.querySelector('.loading');

            expect(loadingElement).toBeTruthy();
        });

        it('should display error message', () => {
            component.error = 'Failed to load dashboard data';
            component.loading = false;
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;
            const errorElement = compiled.querySelector('.error');

            expect(errorElement).toBeTruthy();
            expect(errorElement?.textContent).toContain(
                'Failed to load dashboard data'
            );
        });

        it('should display dashboard summary when loaded', () => {
            mockReportService.getDashboardSummary.and.returnValue(
                of(mockDashboardSummary)
            );
            mockReportService.getLatestReport.and.returnValue(of(mockLatestReport));
            mockReportService.getDailyStats.and.returnValue(of(mockDailyStats));

            component.ngOnInit();
            fixture.detectChanges();

            const compiled = fixture.nativeElement as HTMLElement;

            expect(compiled.textContent).toContain('500');
            expect(compiled.textContent).toContain('$25,000.00');
        });
    });
});
