import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OrderList } from './order-list';
import { OrderService } from '../order.service';
import { Order, OrderStatus } from '@shared/models/order.model';
import { ToastService } from '@shared/services/toast.service';
import { ConfirmationDialogService } from '@shared/services/confirmation-dialog.service';

describe('OrderList', () => {
  let component: OrderList;
  let fixture: ComponentFixture<OrderList>;
  let mockOrderService: jasmine.SpyObj<OrderService>;
  let mockRouter: jasmine.SpyObj<Router>;
  let mockToastService: jasmine.SpyObj<ToastService>;
  let mockConfirmationDialog: jasmine.SpyObj<ConfirmationDialogService>;

  const mockOrders: Order[] = [
    {
      id: 1,
      customerId: 1,
      createdBy: 1,
      status: OrderStatus.CREATED,
      totalAmount: 100,
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
      items: [],
    },
    {
      id: 2,
      customerId: 2,
      createdBy: 1,
      status: OrderStatus.CREATED,
      totalAmount: 200,
      createdAt: '2024-01-02T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z',
      items: [],
    },
    {
      id: 3,
      customerId: 1,
      createdBy: 1,
      status: OrderStatus.PAID,
      totalAmount: 150,
      createdAt: '2024-01-03T00:00:00Z',
      updatedAt: '2024-01-03T00:00:00Z',
      items: [],
    },
  ];

  beforeEach(async () => {
    mockOrderService = jasmine.createSpyObj('OrderService', [
      'getOrders',
      'confirmOrder',
      'payOrder',
      'cancelOrder',
      'completeOrder',
    ]);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);
    mockToastService = jasmine.createSpyObj('ToastService', [
      'success',
      'error',
      'warning',
      'info',
    ]);
    mockConfirmationDialog = jasmine.createSpyObj('ConfirmationDialogService', [
      'confirmAction',
      'confirmDelete',
      'confirmWarning',
    ]);

    await TestBed.configureTestingModule({
      imports: [OrderList],
      providers: [
        { provide: OrderService, useValue: mockOrderService },
        { provide: Router, useValue: mockRouter },
        { provide: ToastService, useValue: mockToastService },
        { provide: ConfirmationDialogService, useValue: mockConfirmationDialog },
      ],
    }).compileComponents();

    mockOrderService.getOrders.and.returnValue(of(mockOrders));

    fixture = TestBed.createComponent(OrderList);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('ngOnInit', () => {
    it('should load orders on initialization', () => {
      fixture.detectChanges();

      expect(mockOrderService.getOrders).toHaveBeenCalledWith({
        offset: 0,
        limit: 20,
      });
      expect(component.orders()).toEqual(mockOrders);
      expect(component.loading()).toBeFalse();
      expect(component.error()).toBeNull();
    });
  });

  describe('loadOrders', () => {
    it('should load orders without filters', () => {
      component.loadOrders();

      expect(mockOrderService.getOrders).toHaveBeenCalledWith({
        offset: 0,
        limit: 20,
      });
      expect(component.orders()).toEqual(mockOrders);
      expect(component.loading()).toBeFalse();
    });

    it('should load orders with status filter', () => {
      component.selectedStatus = 'PENDING';

      component.loadOrders();

      expect(mockOrderService.getOrders).toHaveBeenCalledWith({
        offset: 0,
        limit: 20,
        status: 'PENDING',
      });
    });

    it('should load orders with customerId filter', () => {
      component.selectedCustomerId = 1;

      component.loadOrders();

      expect(mockOrderService.getOrders).toHaveBeenCalledWith({
        offset: 0,
        limit: 20,
        customerId: 1,
      });
    });

    it('should load orders with both filters', () => {
      component.selectedStatus = 'CONFIRMED';
      component.selectedCustomerId = 2;

      component.loadOrders();

      expect(mockOrderService.getOrders).toHaveBeenCalledWith({
        offset: 0,
        limit: 20,
        status: 'CONFIRMED',
        customerId: 2,
      });
    });

    it('should handle error when loading orders fails', () => {
      const errorResponse = { error: { message: 'Server error' }, message: 'Error' };
      mockOrderService.getOrders.and.returnValue(throwError(() => errorResponse));

      component.loadOrders();

      expect(component.loading()).toBeFalse();
      expect(component.error()).toBe('Failed to load orders: Server error');
    });

    it('should load orders with custom pagination', () => {
      component.offset.set(20);
      component.limit.set(10);

      component.loadOrders();

      expect(mockOrderService.getOrders).toHaveBeenCalledWith({
        offset: 20,
        limit: 10,
      });
    });
  });

  describe('onFilterChange', () => {
    it('should reset offset and reload orders', () => {
      component.offset.set(40);
      spyOn(component, 'loadOrders');

      component.onFilterChange();

      expect(component.offset()).toBe(0);
      expect(component.loadOrders).toHaveBeenCalled();
    });
  });

  describe('viewOrder', () => {
    it('should navigate to order detail page', () => {
      component.viewOrder(1);

      expect(mockRouter.navigate).toHaveBeenCalledWith(['/orders', 1]);
    });
  });

  describe('navigateToCreate', () => {
    it('should navigate to order creation page', () => {
      component.navigateToCreate();

      expect(mockRouter.navigate).toHaveBeenCalledWith(['/orders/create']);
    });
  });

  describe('confirmOrder', () => {
    it('should confirm order successfully', () => {
      mockConfirmationDialog.confirmAction.and.returnValue(of(true));
      mockOrderService.confirmOrder.and.returnValue(of({ message: 'Confirmed' }));

      component.confirmOrder(1);

      expect(mockConfirmationDialog.confirmAction).toHaveBeenCalledWith('Confirm this order?');
      expect(mockOrderService.confirmOrder).toHaveBeenCalledWith(1);
      expect(mockToastService.success).toHaveBeenCalledWith('Order confirmed successfully!');
      expect(mockOrderService.getOrders).toHaveBeenCalled();
    });

    it('should not confirm when user cancels', () => {
      mockConfirmationDialog.confirmAction.and.returnValue(of(false));

      component.confirmOrder(1);

      expect(mockOrderService.confirmOrder).not.toHaveBeenCalled();
    });

    it('should handle error when confirm fails', () => {
      mockConfirmationDialog.confirmAction.and.returnValue(of(true));
      const errorResponse = { error: { error: 'Cannot confirm' }, message: 'Error' };
      mockOrderService.confirmOrder.and.returnValue(throwError(() => errorResponse));

      component.confirmOrder(1);

      expect(mockToastService.error).toHaveBeenCalledWith(
        'Failed to confirm order: Cannot confirm',
      );
    });
  });

  describe('payOrder', () => {
    it('should process payment successfully', () => {
      mockConfirmationDialog.confirmAction.and.returnValue(of(true));
      const paymentInfo = {
        paymentId: 'pay_123',
        orderId: 1,
        status: 'PAID',
        message: 'Payment successful',
      };
      mockOrderService.payOrder.and.returnValue(of(paymentInfo));

      component.payOrder(1);

      expect(mockConfirmationDialog.confirmAction).toHaveBeenCalledWith(
        'Process payment for this order?',
      );
      expect(mockOrderService.payOrder).toHaveBeenCalledWith(1, 'credit_card');
      expect(mockToastService.success).toHaveBeenCalledWith('Payment PAID: Payment successful');
      expect(mockOrderService.getOrders).toHaveBeenCalled();
    });

    it('should handle payment error', () => {
      mockConfirmationDialog.confirmAction.and.returnValue(of(true));
      const errorResponse = { error: { error: 'Payment failed' }, message: 'Error' };
      mockOrderService.payOrder.and.returnValue(throwError(() => errorResponse));

      component.payOrder(1);

      expect(mockToastService.error).toHaveBeenCalledWith('Payment failed: Payment failed');
    });
  });

  describe('cancelOrder', () => {
    it('should cancel order successfully', () => {
      mockConfirmationDialog.confirmWarning.and.returnValue(of(true));
      mockOrderService.cancelOrder.and.returnValue(of({ message: 'Cancelled' }));

      component.cancelOrder(1);

      expect(mockConfirmationDialog.confirmWarning).toHaveBeenCalledWith(
        'Are you sure you want to cancel this order?',
        'Cancel Order',
      );
      expect(mockOrderService.cancelOrder).toHaveBeenCalledWith(1);
      expect(mockToastService.success).toHaveBeenCalledWith('Order cancelled successfully!');
      expect(mockOrderService.getOrders).toHaveBeenCalled();
    });
  });

  describe('completeOrder', () => {
    it('should complete order successfully', () => {
      mockConfirmationDialog.confirmAction.and.returnValue(of(true));
      mockOrderService.completeOrder.and.returnValue(of({ message: 'Completed' }));

      component.completeOrder(1);

      expect(mockConfirmationDialog.confirmAction).toHaveBeenCalledWith(
        'Mark this order as completed?',
      );
      expect(mockOrderService.completeOrder).toHaveBeenCalledWith(1);
      expect(mockToastService.success).toHaveBeenCalledWith('Order completed successfully!');
      expect(mockOrderService.getOrders).toHaveBeenCalled();
    });
  });

  describe('Pagination', () => {
    it('should calculate current page correctly', () => {
      component.offset.set(0);
      component.limit.set(20);
      expect(component.currentPage()).toBe(1);

      component.offset.set(20);
      expect(component.currentPage()).toBe(2);

      component.offset.set(40);
      expect(component.currentPage()).toBe(3);
    });

    it('should navigate to previous page', () => {
      component.offset.set(40);
      component.limit.set(20);

      component.previousPage();

      expect(component.offset()).toBe(20);
      expect(mockOrderService.getOrders).toHaveBeenCalled();
    });

    it('should not go below offset 0 on previous page', () => {
      component.offset.set(10);
      component.limit.set(20);

      component.previousPage();

      expect(component.offset()).toBe(0);
    });

    it('should not navigate to previous page when already at first page', () => {
      component.offset.set(0);
      mockOrderService.getOrders.calls.reset();

      component.previousPage();

      expect(mockOrderService.getOrders).not.toHaveBeenCalled();
    });

    it('should navigate to next page', () => {
      component.offset.set(0);
      component.limit.set(20);

      component.nextPage();

      expect(component.offset()).toBe(20);
      expect(mockOrderService.getOrders).toHaveBeenCalled();
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

    it('should handle different date formats', () => {
      const dateString = '2024-12-31T23:59:59Z';
      const result = component.formatDate(dateString);

      expect(result).toBeTruthy();
      expect(typeof result).toBe('string');
    });
  });
});
