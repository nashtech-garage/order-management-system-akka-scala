import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { OrderForm } from './order-form';
import { OrderService } from '../order.service';
import { CustomerService } from '@core/services/customer.service';
import { ProductService } from '@core/services/product.service';
import { Customer } from '@shared/models/customer.model';
import { ProductResponse } from '@shared/models/product.model';
import { Order, OrderStatus } from '@shared/models/order.model';

describe('OrderForm', () => {
  let component: OrderForm;
  let fixture: ComponentFixture<OrderForm>;
  let mockOrderService: jasmine.SpyObj<OrderService>;
  let mockCustomerService: jasmine.SpyObj<CustomerService>;
  let mockProductService: jasmine.SpyObj<ProductService>;
  let mockRouter: jasmine.SpyObj<Router>;

  const mockCustomers: Customer[] = [
    {
      id: 1,
      firstName: 'John',
      lastName: 'Doe',
      email: 'john@example.com',
      phone: '1234567890',
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
    },
    {
      id: 2,
      firstName: 'Jane',
      lastName: 'Smith',
      email: 'jane@example.com',
      phone: '0987654321',
      createdAt: '2024-01-02T00:00:00Z',
      updatedAt: '2024-01-02T00:00:00Z',
    },
  ];

  const mockProducts: ProductResponse[] = [
    {
      id: 1,
      name: 'Product 1',
      description: 'Description 1',
      price: 50,
      stockQuantity: 100,
      categoryId: 1,
      categoryName: 'Category 1',
      createdAt: '2024-01-01T00:00:00Z',
    },
    {
      id: 2,
      name: 'Product 2',
      description: 'Description 2',
      price: 75,
      stockQuantity: 50,
      categoryId: 2,
      categoryName: 'Category 2',
      createdAt: '2024-01-02T00:00:00Z',
    },
  ];

  const mockOrder: Order = {
    id: 1,
    customerId: 1,
    createdBy: 1,
    status: OrderStatus.CREATED,
    totalAmount: 100,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    items: [],
  };

  beforeEach(async () => {
    mockOrderService = jasmine.createSpyObj('OrderService', ['createOrder']);
    mockCustomerService = jasmine.createSpyObj('CustomerService', ['getCustomers']);
    mockProductService = jasmine.createSpyObj('ProductService', ['getProducts']);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [OrderForm],
      providers: [
        { provide: OrderService, useValue: mockOrderService },
        { provide: CustomerService, useValue: mockCustomerService },
        { provide: ProductService, useValue: mockProductService },
        { provide: Router, useValue: mockRouter },
      ],
    }).compileComponents();

    mockCustomerService.getCustomers.and.returnValue(of(mockCustomers));
    mockProductService.getProducts.and.returnValue(of(mockProducts));

    fixture = TestBed.createComponent(OrderForm);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('ngOnInit', () => {
    it('should load customers and products', () => {
      fixture.detectChanges();

      expect(mockCustomerService.getCustomers).toHaveBeenCalledWith(0, 100);
      expect(mockProductService.getProducts).toHaveBeenCalledWith(0, 100);
      expect(component.customers()).toEqual(mockCustomers);
      expect(component.products()).toEqual(mockProducts);
    });

    it('should handle error when loading customers fails', () => {
      spyOn(console, 'error');
      mockCustomerService.getCustomers.and.returnValue(
        throwError(() => new Error('Failed to load')),
      );

      fixture.detectChanges();

      expect(console.error).toHaveBeenCalledWith('Failed to load customers:', jasmine.any(Error));
    });

    it('should handle error when loading products fails', () => {
      spyOn(console, 'error');
      mockProductService.getProducts.and.returnValue(throwError(() => new Error('Failed to load')));

      fixture.detectChanges();

      expect(console.error).toHaveBeenCalledWith('Failed to load products:', jasmine.any(Error));
    });
  });

  describe('Form initialization', () => {
    it('should initialize form with one item', () => {
      expect(component.orderForm).toBeDefined();
      expect(component.items.length).toBe(1);
      expect(component.orderForm.get('customerId')?.value).toBe('');
    });

    it('should have required validators', () => {
      const customerIdControl = component.orderForm.get('customerId');
      customerIdControl?.markAsTouched();
      expect(customerIdControl?.hasError('required')).toBeTrue();

      const firstItem = component.items.at(0);
      firstItem.get('productId')?.markAsTouched();
      expect(firstItem.get('productId')?.hasError('required')).toBeTrue();

      // Quantity has default value of 1, so it's valid initially
      expect(firstItem.get('quantity')?.value).toBe(1);
      expect(firstItem.get('quantity')?.valid).toBeTrue();

      // But if we clear it, it should be invalid
      firstItem.get('quantity')?.setValue(null);
      firstItem.get('quantity')?.markAsTouched();
      expect(firstItem.get('quantity')?.hasError('required')).toBeTrue();
    });
  });

  describe('addItem', () => {
    it('should add a new item to the form', () => {
      const initialLength = component.items.length;

      component.addItem();

      expect(component.items.length).toBe(initialLength + 1);
    });
  });

  describe('removeItem', () => {
    it('should remove an item when there are multiple items', () => {
      component.addItem();
      component.addItem();
      const initialLength = component.items.length;

      component.removeItem(1);

      expect(component.items.length).toBe(initialLength - 1);
    });

    it('should not remove the last item', () => {
      component.removeItem(0);

      expect(component.items.length).toBe(1);
    });
  });

  describe('getItemSubtotal', () => {
    beforeEach(() => {
      fixture.detectChanges();
      component.products.set(mockProducts);
    });

    it('should calculate subtotal correctly', () => {
      const item = component.items.at(0);
      item.patchValue({ productId: '1', quantity: 2 });

      const subtotal = component.getItemSubtotal(0);

      expect(subtotal).toBe(100); // 50 * 2
    });

    it('should return 0 when no product is selected', () => {
      const subtotal = component.getItemSubtotal(0);

      expect(subtotal).toBe(0);
    });

    it('should return 0 when product is not found', () => {
      const item = component.items.at(0);
      item.patchValue({ productId: '999', quantity: 2 });

      const subtotal = component.getItemSubtotal(0);

      expect(subtotal).toBe(0);
    });
  });

  describe('updateTotal', () => {
    beforeEach(() => {
      fixture.detectChanges();
      component.products.set(mockProducts);
    });

    it('should update total amount correctly', () => {
      component.addItem();
      const item1 = component.items.at(0);
      const item2 = component.items.at(1);

      item1.patchValue({ productId: '1', quantity: 2 });
      item2.patchValue({ productId: '2', quantity: 1 });

      component.updateTotal();

      expect(component.totalAmount()).toBe(175); // (50*2) + (75*1)
    });

    it('should set total to 0 when no items have products', () => {
      component.updateTotal();

      expect(component.totalAmount()).toBe(0);
    });
  });

  describe('onProductChange', () => {
    it('should call updateTotal', () => {
      spyOn(component, 'updateTotal');

      component.onProductChange();

      expect(component.updateTotal).toHaveBeenCalled();
    });
  });

  describe('onSubmit', () => {
    beforeEach(() => {
      fixture.detectChanges();
      spyOn(window, 'alert');
    });

    it('should not submit when form is invalid', () => {
      component.onSubmit();

      expect(mockOrderService.createOrder).not.toHaveBeenCalled();
      expect(component.orderForm.touched).toBeTrue();
    });

    it('should submit successfully when form is valid', () => {
      mockOrderService.createOrder.and.returnValue(of(mockOrder));

      component.orderForm.patchValue({
        customerId: '1',
      });
      component.items.at(0).patchValue({
        productId: '1',
        quantity: '2',
      });

      component.onSubmit();

      expect(mockOrderService.createOrder).toHaveBeenCalledWith({
        customerId: 1,
        items: [{ productId: 1, quantity: 2 }],
      });
      expect(window.alert).toHaveBeenCalledWith('Order created successfully!');
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/orders', 1]);
      expect(component.submitting()).toBeFalse();
    });

    it('should handle error when submission fails', () => {
      const errorResponse = { error: { error: 'Creation failed' }, message: 'Error' };
      mockOrderService.createOrder.and.returnValue(throwError(() => errorResponse));

      component.orderForm.patchValue({
        customerId: '1',
      });
      component.items.at(0).patchValue({
        productId: '1',
        quantity: '2',
      });

      component.onSubmit();

      expect(component.submitting()).toBeFalse();
      expect(component.error()).toBe('Failed to create order: Creation failed');
    });

    it('should handle multiple items submission', () => {
      mockOrderService.createOrder.and.returnValue(of(mockOrder));

      component.orderForm.patchValue({
        customerId: '1',
      });
      component.addItem();
      component.items.at(0).patchValue({
        productId: '1',
        quantity: '2',
      });
      component.items.at(1).patchValue({
        productId: '2',
        quantity: '3',
      });

      component.onSubmit();

      expect(mockOrderService.createOrder).toHaveBeenCalledWith({
        customerId: 1,
        items: [
          { productId: 1, quantity: 2 },
          { productId: 2, quantity: 3 },
        ],
      });
    });
  });

  describe('goBack', () => {
    it('should navigate to orders list', () => {
      component.goBack();

      expect(mockRouter.navigate).toHaveBeenCalledWith(['/orders']);
    });
  });
});
