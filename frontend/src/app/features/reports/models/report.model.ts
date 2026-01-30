export interface ScheduledReport {
  id?: number;
  reportType: string;
  reportDate: string;
  totalOrders: number;
  totalRevenue: number;
  averageOrderValue: number;
  ordersByStatus: { [key: string]: number };
  metadata: { [key: string]: string };
  generatedAt: string;
}

export interface ReportListResponse {
  reports: ScheduledReport[];
  total: number;
  page: number;
  pageSize: number;
}

export interface DailyStats {
  date: string;
  orderCount: number;
  revenue: number;
}

export interface ProductReport {
  productId: number;
  productName: string;
  totalQuantitySold: number;
  totalRevenue: number;
}

export interface CustomerReport {
  customerId: number;
  customerName: string;
  totalOrders: number;
  totalSpent: number;
}

export interface DashboardSummary {
  totalOrders: number;
  totalRevenue: number;
  topProducts: ProductReport[];
  topCustomers: CustomerReport[];
  recentStats: DailyStats[];
}
