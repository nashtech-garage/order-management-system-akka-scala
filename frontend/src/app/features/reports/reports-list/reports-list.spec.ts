import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReportsList } from './reports-list';
import { ReportService } from '../services/report.service';
import { of, throwError } from 'rxjs';
import { ScheduledReport, ReportListResponse } from '../models/report.model';
import { provideRouter } from '@angular/router';

describe('ReportsList', () => {
  let component: ReportsList;
  let fixture: ComponentFixture<ReportsList>;
  let mockReportService: jasmine.SpyObj<ReportService>;

  const mockReports: ScheduledReport[] = [
    {
      id: 1,
      reportType: 'daily',
      reportDate: '2024-01-15',
      totalOrders: 200,
      totalRevenue: 10000,
      averageOrderValue: 50,
      ordersByStatus: { pending: 20, completed: 180 },
      metadata: {},
      generatedAt: '2024-01-15T23:59:00',
    },
    {
      id: 2,
      reportType: 'daily',
      reportDate: '2024-01-16',
      totalOrders: 150,
      totalRevenue: 7500,
      averageOrderValue: 50,
      ordersByStatus: { completed: 150 },
      metadata: {},
      generatedAt: '2024-01-16T23:59:00',
    },
  ];

  const mockReportListResponse: ReportListResponse = {
    reports: mockReports,
    total: 50,
    page: 0,
    pageSize: 20,
  };

  beforeEach(async () => {
    mockReportService = jasmine.createSpyObj('ReportService', ['getScheduledReports']);

    await TestBed.configureTestingModule({
      imports: [ReportsList],
      providers: [{ provide: ReportService, useValue: mockReportService }, provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportsList);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('ngOnInit', () => {
    it('should load reports on initialization', () => {
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.ngOnInit();

      expect(mockReportService.getScheduledReports).toHaveBeenCalledWith(
        0,
        20,
        undefined,
        undefined,
      );
    });
  });

  describe('loadReports', () => {
    it('should set loading to true initially', () => {
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.loadReports();

      expect(component.loading).toBe(true);
    });

    it('should load reports successfully', () => {
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.loadReports();

      expect(component.reports).toEqual(mockReports);
      expect(component.total).toBe(50);
      expect(component.loading).toBe(false);
      expect(component.error).toBeNull();
    });

    it('should pass current page and pageSize to service', () => {
      component.page = 2;
      component.pageSize = 10;
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.loadReports();

      expect(mockReportService.getScheduledReports).toHaveBeenCalledWith(
        2,
        10,
        undefined,
        undefined,
      );
    });

    it('should pass date filters when set', () => {
      component.startDate = '2024-01-01';
      component.endDate = '2024-01-31';
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.loadReports();

      expect(mockReportService.getScheduledReports).toHaveBeenCalledWith(
        0,
        20,
        '2024-01-01',
        '2024-01-31',
      );
    });

    it('should handle empty date filters', () => {
      component.startDate = '';
      component.endDate = '';
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.loadReports();

      expect(mockReportService.getScheduledReports).toHaveBeenCalledWith(
        0,
        20,
        undefined,
        undefined,
      );
    });

    it('should handle errors', () => {
      const errorResponse = { status: 500, message: 'Server error' };
      mockReportService.getScheduledReports.and.returnValue(throwError(() => errorResponse));

      spyOn(console, 'error');

      component.loadReports();

      expect(component.error).toBe('Failed to load reports');
      expect(component.loading).toBe(false);
      expect(console.error).toHaveBeenCalledWith('Error loading reports:', errorResponse);
    });

    it('should handle empty reports list', () => {
      const emptyResponse: ReportListResponse = {
        reports: [],
        total: 0,
        page: 0,
        pageSize: 20,
      };
      mockReportService.getScheduledReports.and.returnValue(of(emptyResponse));

      component.loadReports();

      expect(component.reports).toEqual([]);
      expect(component.total).toBe(0);
      expect(component.loading).toBe(false);
    });
  });

  describe('applyFilters', () => {
    it('should reset page to 0 and reload reports', () => {
      component.page = 3;
      component.startDate = '2024-01-01';
      component.endDate = '2024-01-31';
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.applyFilters();

      expect(component.page).toBe(0);
      expect(mockReportService.getScheduledReports).toHaveBeenCalledWith(
        0,
        20,
        '2024-01-01',
        '2024-01-31',
      );
    });
  });

  describe('clearFilters', () => {
    it('should clear date filters and reset page', () => {
      component.startDate = '2024-01-01';
      component.endDate = '2024-01-31';
      component.page = 2;
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.clearFilters();

      expect(component.startDate).toBe('');
      expect(component.endDate).toBe('');
      expect(component.page).toBe(0);
      expect(mockReportService.getScheduledReports).toHaveBeenCalledWith(
        0,
        20,
        undefined,
        undefined,
      );
    });
  });

  describe('nextPage', () => {
    it('should increment page and load reports when more pages available', () => {
      component.page = 0;
      component.pageSize = 20;
      component.total = 50;
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.nextPage();

      expect(component.page).toBe(1);
      expect(mockReportService.getScheduledReports).toHaveBeenCalled();
    });

    it('should not increment page when on last page', () => {
      component.page = 2;
      component.pageSize = 20;
      component.total = 50; // 3 pages total (0, 1, 2)
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.nextPage();

      expect(component.page).toBe(2); // Should stay on page 2
      expect(mockReportService.getScheduledReports).not.toHaveBeenCalled();
    });

    it('should handle edge case when total equals page size', () => {
      component.page = 0;
      component.pageSize = 20;
      component.total = 20;
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.nextPage();

      expect(component.page).toBe(0); // Should stay on page 0
      expect(mockReportService.getScheduledReports).not.toHaveBeenCalled();
    });
  });

  describe('previousPage', () => {
    it('should decrement page and load reports when not on first page', () => {
      component.page = 2;
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.previousPage();

      expect(component.page).toBe(1);
      expect(mockReportService.getScheduledReports).toHaveBeenCalled();
    });

    it('should not decrement page when on first page', () => {
      component.page = 0;
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.previousPage();

      expect(component.page).toBe(0);
      expect(mockReportService.getScheduledReports).not.toHaveBeenCalled();
    });
  });

  describe('getTotalPages', () => {
    it('should calculate total pages correctly', () => {
      component.total = 50;
      component.pageSize = 20;

      const totalPages = component.getTotalPages();

      expect(totalPages).toBe(3);
    });

    it('should handle exact division', () => {
      component.total = 60;
      component.pageSize = 20;

      const totalPages = component.getTotalPages();

      expect(totalPages).toBe(3);
    });

    it('should handle zero total', () => {
      component.total = 0;
      component.pageSize = 20;

      const totalPages = component.getTotalPages();

      expect(totalPages).toBe(0);
    });

    it('should handle single page', () => {
      component.total = 10;
      component.pageSize = 20;

      const totalPages = component.getTotalPages();

      expect(totalPages).toBe(1);
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

  describe('DOM rendering', () => {
    it('should display loading state', () => {
      component.loading = true;
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const loadingElement = compiled.querySelector('.loading');

      expect(loadingElement).toBeTruthy();
    });

    it('should display error message', () => {
      component.error = 'Failed to load reports';
      component.loading = false;
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const errorElement = compiled.querySelector('.error');

      expect(errorElement).toBeTruthy();
      expect(errorElement?.textContent).toContain('Failed to load reports');
    });

    it('should display reports when loaded', () => {
      mockReportService.getScheduledReports.and.returnValue(of(mockReportListResponse));

      component.ngOnInit();
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;

      expect(compiled.textContent).toContain('200');
      expect(compiled.textContent).toContain('$10,000.00');
    });

    it('should display empty state when no reports', () => {
      const emptyResponse: ReportListResponse = {
        reports: [],
        total: 0,
        page: 0,
        pageSize: 20,
      };
      mockReportService.getScheduledReports.and.returnValue(of(emptyResponse));

      component.ngOnInit();
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const emptyElement = compiled.querySelector('.empty-state');

      expect(emptyElement).toBeTruthy();
    });
  });

  describe('pagination controls', () => {
    it('should disable previous button on first page', () => {
      component.page = 0;
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const prevButton = compiled.querySelector(
        'button[aria-label="Previous page"]',
      ) as HTMLButtonElement;

      expect(prevButton?.disabled).toBe(true);
    });

    it('should enable previous button when not on first page', () => {
      component.page = 1;
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const prevButton = compiled.querySelector(
        'button[aria-label="Previous page"]',
      ) as HTMLButtonElement;

      expect(prevButton?.disabled).toBe(false);
    });

    it('should disable next button on last page', () => {
      component.page = 2;
      component.pageSize = 20;
      component.total = 50;
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const nextButton = compiled.querySelector(
        'button[aria-label="Next page"]',
      ) as HTMLButtonElement;

      expect(nextButton?.disabled).toBe(true);
    });

    it('should enable next button when more pages available', () => {
      component.page = 0;
      component.pageSize = 20;
      component.total = 50;
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const nextButton = compiled.querySelector(
        'button[aria-label="Next page"]',
      ) as HTMLButtonElement;

      expect(nextButton?.disabled).toBe(false);
    });
  });
});
