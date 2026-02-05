import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OrderService } from './order.service';
import { API_ENDPOINTS } from '@core/constants/api-endpoints';
import { Order, CreateOrderRequest, PaymentInfo, OrderStats, OrderStatus } from '@shared/models/order.model';
import { environment } from '@environments/environment';

describe('OrderService', () => {
  let service: OrderService;
  let httpMock: HttpTestingController;
  const apiUrl = environment.apiUrl;

  const mockOrder: Order = {
    id: 1,
    customerId: 1,
    status: OrderStatus.DRAFT,
    totalAmount: 100,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    createdBy: 1,
    items: [],
  };

  const mockOrders: Order[] = [
    mockOrder,
    {
      id: 2,
      customerId: 2,
      status: OrderStatus.CREATED,
      totalAmount: 200,
      createdAt: '2024-01-02T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z',
      createdBy: 2,
      items: [],
    },
  ];

  const mockOrderStats: OrderStats = {
    totalOrders: 100,
    pendingOrders: 20,
    totalRevenue: 15,
    completedOrders: 5,
    cancelledOrders: 5,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getOrders', () => {
    it('should retrieve orders without parameters', () => {
      service.getOrders().subscribe((orders) => {
        expect(orders).toEqual(mockOrders);
        expect(orders.length).toBe(2);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.BASE}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockOrders);
    });

    it('should retrieve orders with status filter', () => {
      service.getOrders({ status: 'PENDING' }).subscribe((orders) => {
        expect(orders).toEqual(mockOrders);
      });

      const req = httpMock.expectOne(
        `${apiUrl}${API_ENDPOINTS.ORDERS.BASE}?status=PENDING`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockOrders);
    });

    it('should retrieve orders with customerId filter', () => {
      service.getOrders({ customerId: 1 }).subscribe((orders) => {
        expect(orders).toEqual(mockOrders);
      });

      const req = httpMock.expectOne(
        `${apiUrl}${API_ENDPOINTS.ORDERS.BASE}?customerId=1`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockOrders);
    });

    it('should retrieve orders with pagination', () => {
      service.getOrders({ offset: 10, limit: 20 }).subscribe((orders) => {
        expect(orders).toEqual(mockOrders);
      });

      const req = httpMock.expectOne(
        `${apiUrl}${API_ENDPOINTS.ORDERS.BASE}?offset=10&limit=20`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockOrders);
    });

    it('should retrieve orders with all parameters', () => {
      service
        .getOrders({ status: 'PENDING', customerId: 1, offset: 0, limit: 10 })
        .subscribe((orders) => {
          expect(orders).toEqual(mockOrders);
        });

      const req = httpMock.expectOne(
        `${apiUrl}${API_ENDPOINTS.ORDERS.BASE}?status=PENDING&customerId=1&limit=10`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockOrders);
    });
  });

  describe('getOrderById', () => {
    it('should retrieve a single order by id', () => {
      service.getOrderById(1).subscribe((order) => {
        expect(order).toEqual(mockOrder);
        expect(order.id).toBe(1);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.BY_ID('1')}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockOrder);
    });
  });

  describe('getOrdersByCustomer', () => {
    it('should retrieve orders by customer id', () => {
      service.getOrdersByCustomer(1).subscribe((orders) => {
        expect(orders).toEqual(mockOrders);
      });

      const req = httpMock.expectOne(
        `${apiUrl}${API_ENDPOINTS.ORDERS.BASE}?customerId=1`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockOrders);
    });
  });

  describe('getOrdersByStatus', () => {
    it('should retrieve orders by status', () => {
      service.getOrdersByStatus('PENDING').subscribe((orders) => {
        expect(orders).toEqual(mockOrders);
      });

      const req = httpMock.expectOne(
        `${apiUrl}${API_ENDPOINTS.ORDERS.BASE}?status=PENDING`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockOrders);
    });
  });

  describe('getOrderStats', () => {
    it('should retrieve order statistics', () => {
      service.getOrderStats().subscribe((stats) => {
        expect(stats).toEqual(mockOrderStats);
        expect(stats.totalOrders).toBe(100);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.STATS}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockOrderStats);
    });
  });

  describe('createOrder', () => {
    it('should create a new order', () => {
      const createRequest: CreateOrderRequest = {
        customerId: 1,
        items: [{ productId: 1, quantity: 2 }],
      };

      service.createOrder(createRequest).subscribe((order) => {
        expect(order).toEqual(mockOrder);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.BASE}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createRequest);
      req.flush(mockOrder);
    });
  });

  describe('confirmOrder', () => {
    it('should confirm an order', () => {
      const response = { message: 'Order confirmed' };

      service.confirmOrder(1).subscribe((res) => {
        expect(res).toEqual(response);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.CONFIRM('1')}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(response);
    });
  });

  describe('payOrder', () => {
    it('should process payment for an order', () => {
      const paymentInfo: PaymentInfo = {
        paymentId: 'pay_123',
        orderId: 1,
        status: OrderStatus.PAID,
        message: 'Payment successful',
      };

      service.payOrder(1, 'credit_card').subscribe((info) => {
        expect(info).toEqual(paymentInfo);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.PAY('1')}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ paymentMethod: 'credit_card' });
      req.flush(paymentInfo);
    });
  });

  describe('shipOrder', () => {
    it('should start shipping for an order', () => {
      const response = { message: 'Shipping started' };

      service.shipOrder(1).subscribe((res) => {
        expect(res).toEqual(response);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.SHIP('1')}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(response);
    });
  });

  describe('completeOrder', () => {
    it('should mark an order as completed', () => {
      const response = { message: 'Order completed' };

      service.completeOrder(1).subscribe((res) => {
        expect(res).toEqual(response);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.COMPLETE('1')}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(response);
    });
  });

  describe('cancelOrder', () => {
    it('should cancel an order', () => {
      const response = { message: 'Order cancelled' };

      service.cancelOrder(1).subscribe((res) => {
        expect(res).toEqual(response);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.CANCEL('1')}`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(response);
    });
  });

  describe('updateOrderStatus', () => {
    it('should update order status', () => {
      const response = { message: 'Status updated' };

      service.updateOrderStatus(1, 'CONFIRMED').subscribe((res) => {
        expect(res).toEqual(response);
      });

      const req = httpMock.expectOne(`${apiUrl}${API_ENDPOINTS.ORDERS.BY_ID('1')}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ status: 'CONFIRMED' });
      req.flush(response);
    });
  });
});
