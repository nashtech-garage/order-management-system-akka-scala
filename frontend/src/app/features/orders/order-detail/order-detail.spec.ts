import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OrderDetail } from './order-detail';
import { OrderService } from '../order.service';
import { Order, OrderStatus } from '@shared/models/order.model';
import { signal } from '@angular/core';

describe('OrderDetail', () => {
  let component: OrderDetail;
  let fixture: ComponentFixture<OrderDetail>;
  let mockOrderService: jasmine.SpyObj<OrderService>;
  let mockRouter: jasmine.SpyObj<Router>;
  let mockActivatedRoute: { snapshot: { paramMap: { get: jasmine.Spy } } };

  const mockOrder: Order = {
    id: 1,
    customerId: 1,
    createdBy: 1,
    status: OrderStatus.CREATED,
    totalAmount: 100,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    items: [
      {
        id: 1,
        productId: 1,
        quantity: 2,
        unitPrice: 50,
        productName: 'Test Product',
        subtotal: 100,
      },
    ],
  };

  beforeEach(async () => {
    mockOrderService = jasmine.createSpyObj('OrderService', [
      'getOrderById',
      'confirmOrder',
      'payOrder',
      'shipOrder',
      'completeOrder',
      'cancelOrder',
    ]);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);
    mockActivatedRoute = {
      snapshot: {
        paramMap: {
          get: jasmine.createSpy('get').and.returnValue('1'),
        },
      },
    };

    await TestBed.configureTestingModule({
      imports: [OrderDetail],
      providers: [
        { provide: OrderService, useValue: mockOrderService },
        { provide: Router, useValue: mockRouter },
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderDetail);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('ngOnInit', () => {
    it('should load order when valid id is provided', () => {
      mockOrderService.getOrderById.and.returnValue(of(mockOrder));

      fixture.detectChanges(); // triggers ngOnInit

      expect(mockActivatedRoute.snapshot.paramMap.get).toHaveBeenCalledWith('id');
      expect(mockOrderService.getOrderById).toHaveBeenCalledWith(1);
      expect(component.order()).toEqual(mockOrder);
      expect(component.loading()).toBeFalse();
      expect(component.error()).toBeNull();
    });

    it('should set error when no id is provided', () => {
      mockActivatedRoute.snapshot.paramMap.get.and.returnValue(null);

      fixture.detectChanges();

      expect(component.error()).toBe('Invalid order ID');
      expect(mockOrderService.getOrderById).not.toHaveBeenCalled();
    });

    it('should handle error when loading order fails', () => {
      const errorResponse = { error: { message: 'Order not found' }, message: 'Error' };
      mockOrderService.getOrderById.and.returnValue(throwError(() => errorResponse));

      fixture.detectChanges();

      expect(component.loading()).toBeFalse();
      expect(component.error()).toBe('Failed to load order: Order not found');
    });
  });

  describe('loadOrder', () => {
    it('should load order successfully', () => {
      mockOrderService.getOrderById.and.returnValue(of(mockOrder));

      component.loadOrder(1);

      expect(component.loading()).toBeFalse();
      expect(component.order()).toEqual(mockOrder);
      expect(component.error()).toBeNull();
    });

    it('should handle error during load', () => {
      const errorResponse = { message: 'Network error' };
      mockOrderService.getOrderById.and.returnValue(throwError(() => errorResponse));

      component.loadOrder(1);

      expect(component.loading()).toBeFalse();
      expect(component.error()).toContain('Failed to load order');
    });
  });

  describe('confirmOrder', () => {
    beforeEach(() => {
      component.order.set(mockOrder);
      spyOn(window, 'confirm').and.returnValue(true);
      spyOn(window, 'alert');
    });

    it('should confirm order successfully', () => {
      mockOrderService.confirmOrder.and.returnValue(of({ message: 'Confirmed' }));
      mockOrderService.getOrderById.and.returnValue(of(mockOrder));

      component.confirmOrder();

      expect(window.confirm).toHaveBeenCalledWith('Confirm this order?');
      expect(mockOrderService.confirmOrder).toHaveBeenCalledWith(1);
      expect(window.alert).toHaveBeenCalledWith('Order confirmed successfully!');
      expect(mockOrderService.getOrderById).toHaveBeenCalledWith(1);
    });

    it('should not confirm when user cancels', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);

      component.confirmOrder();

      expect(mockOrderService.confirmOrder).not.toHaveBeenCalled();
    });

    it('should handle error when confirm fails', () => {
      const errorResponse = { error: { error: 'Cannot confirm' }, message: 'Error' };
      mockOrderService.confirmOrder.and.returnValue(throwError(() => errorResponse));

      component.confirmOrder();

      expect(window.alert).toHaveBeenCalledWith('Failed to confirm order: Cannot confirm');
    });
  });

  describe('payOrder', () => {
    beforeEach(() => {
      component.order.set(mockOrder);
      spyOn(window, 'confirm').and.returnValue(true);
      spyOn(window, 'alert');
    });

    it('should process payment successfully', () => {
      const paymentInfo = {
        paymentId: 'pay_123',
        orderId: 1,
        status: 'PAID',
        message: 'Payment successful',
      };
      mockOrderService.payOrder.and.returnValue(of(paymentInfo));
      mockOrderService.getOrderById.and.returnValue(of(mockOrder));

      component.payOrder();

      expect(window.confirm).toHaveBeenCalledWith('Process payment for this order?');
      expect(mockOrderService.payOrder).toHaveBeenCalledWith(1, 'credit_card');
      expect(window.alert).toHaveBeenCalledWith('Payment PAID: Payment successful');
      expect(mockOrderService.getOrderById).toHaveBeenCalledWith(1);
    });

    it('should handle payment error', () => {
      const errorResponse = { error: { error: 'Payment failed' }, message: 'Error' };
      mockOrderService.payOrder.and.returnValue(throwError(() => errorResponse));

      component.payOrder();

      expect(window.alert).toHaveBeenCalledWith('Payment failed: Payment failed');
    });
  });

  describe('shipOrder', () => {
    beforeEach(() => {
      component.order.set(mockOrder);
      spyOn(window, 'confirm').and.returnValue(true);
      spyOn(window, 'alert');
    });

    it('should start shipping successfully', () => {
      mockOrderService.shipOrder.and.returnValue(of({ message: 'Shipped' }));
      mockOrderService.getOrderById.and.returnValue(of(mockOrder));

      component.shipOrder();

      expect(mockOrderService.shipOrder).toHaveBeenCalledWith(1);
      expect(window.alert).toHaveBeenCalledWith('Shipping started successfully!');
    });
  });

  describe('completeOrder', () => {
    beforeEach(() => {
      component.order.set(mockOrder);
      spyOn(window, 'confirm').and.returnValue(true);
      spyOn(window, 'alert');
    });

    it('should complete order successfully', () => {
      mockOrderService.completeOrder.and.returnValue(of({ message: 'Completed' }));
      mockOrderService.getOrderById.and.returnValue(of(mockOrder));

      component.completeOrder();

      expect(mockOrderService.completeOrder).toHaveBeenCalledWith(1);
      expect(window.alert).toHaveBeenCalledWith('Order completed successfully!');
    });
  });

  describe('cancelOrder', () => {
    beforeEach(() => {
      component.order.set(mockOrder);
      spyOn(window, 'confirm').and.returnValue(true);
      spyOn(window, 'alert');
    });

    it('should cancel order successfully', () => {
      mockOrderService.cancelOrder.and.returnValue(of({ message: 'Cancelled' }));
      mockOrderService.getOrderById.and.returnValue(of(mockOrder));

      component.cancelOrder();

      expect(window.confirm).toHaveBeenCalledWith(
        'Are you sure you want to cancel this order?',
      );
      expect(mockOrderService.cancelOrder).toHaveBeenCalledWith(1);
      expect(window.alert).toHaveBeenCalledWith('Order cancelled successfully!');
    });
  });

  describe('goBack', () => {
    it('should navigate to orders list', () => {
      component.goBack();

      expect(mockRouter.navigate).toHaveBeenCalledWith(['/orders']);
    });
  });

  describe('formatDate', () => {
    it('should format date string correctly', () => {
      const dateString = '2024-01-01T12:30:00Z';
      const result = component.formatDate(dateString);

      expect(result).toBeTruthy();
      expect(typeof result).toBe('string');
      expect(result).toContain('2024');
    });
  });
});
