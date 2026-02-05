import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProductForm } from './product-form';
import { ProductService } from '@core/services/product.service';
import { CategoryService } from '@core/services/category.service';
import { Router, ActivatedRoute, ParamMap, ActivatedRouteSnapshot } from '@angular/router';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ProductForm', () => {
  let component: ProductForm;
  let fixture: ComponentFixture<ProductForm>;
  let mockProductService: jasmine.SpyObj<ProductService>;
  let mockCategoryService: jasmine.SpyObj<CategoryService>;
  let mockRouter: jasmine.SpyObj<Router>;
  let mockParamMap: jasmine.SpyObj<ParamMap>;
  let mockActivatedRoute: Partial<ActivatedRoute>;

  const mockCategories = [
    { id: 1, name: 'Category 1' },
    { id: 2, name: 'Category 2' },
  ];

  const mockProduct = {
    id: 1,
    name: 'Test Product',
    description: 'Test Description',
    price: 100,
    stockQuantity: 10,
    categoryId: 1,
    imageUrl: 'http://test.com/image.jpg',
    createdAt: new Date().toISOString(),
  };

  beforeEach(async () => {
    mockProductService = jasmine.createSpyObj('ProductService', [
      'getProduct',
      'createProduct',
      'updateProduct',
    ]);
    mockCategoryService = jasmine.createSpyObj('CategoryService', ['getCategories']);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);
    mockParamMap = jasmine.createSpyObj('ParamMap', ['get', 'has', 'getAll', 'keys']);
    mockParamMap.get.and.returnValue(null);
    mockActivatedRoute = {
      snapshot: {
        paramMap: mockParamMap,
      } as unknown as ActivatedRouteSnapshot,
    };

    await TestBed.configureTestingModule({
      imports: [ProductForm],
      providers: [
        { provide: ProductService, useValue: mockProductService },
        { provide: CategoryService, useValue: mockCategoryService },
        { provide: Router, useValue: mockRouter },
        { provide: ActivatedRoute, useValue: mockActivatedRoute },
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  describe('Create Mode', () => {
    beforeEach(() => {
      mockCategoryService.getCategories.and.returnValue(of(mockCategories));
      fixture = TestBed.createComponent(ProductForm);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should create', () => {
      expect(component).toBeTruthy();
      expect(component.isEditMode()).toBeFalse();
    });

    it('should load categories on init', () => {
      expect(mockCategoryService.getCategories).toHaveBeenCalled();
      expect(component.categories()).toEqual(mockCategories);
    });

    it('should initialize empty form', () => {
      expect(component.productForm.get('name')?.value).toBe('');
      expect(component.productForm.get('price')?.value).toBe(0);
    });

    it('should validate required fields', () => {
      const nameControl = component.productForm.get('name');
      expect(nameControl?.valid).toBeFalse();

      nameControl?.setValue('Test');
      expect(nameControl?.valid).toBeTrue();
    });

    it('should call createProduct on valid submit', () => {
      component.productForm.patchValue({
        name: 'New Product',
        price: 50,
        stockQuantity: 5,
        categoryId: 1,
      });

      mockProductService.createProduct.and.returnValue(of(mockProduct));

      component.onSubmit();

      expect(mockProductService.createProduct).toHaveBeenCalledWith(
        jasmine.objectContaining({
          name: 'New Product',
          price: 50,
          stockQuantity: 5,
          categoryId: 1,
        }),
      );
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/products']);
    });

    it('should handle create error', () => {
      component.productForm.patchValue({
        name: 'New Product',
        price: 50,
        stockQuantity: 5,
      });

      mockProductService.createProduct.and.returnValue(
        throwError(() => ({ error: { error: 'Creation failed' } })),
      );

      component.onSubmit();

      expect(component.error()).toBe('Creation failed');
      expect(component.isSubmitting()).toBeFalse();
    });

    it('should navigate back on cancel', () => {
      component.cancel();
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/products']);
    });
  });

  describe('Edit Mode', () => {
    beforeEach(() => {
      mockCategoryService.getCategories.and.returnValue(of(mockCategories));
      mockProductService.getProduct.and.returnValue(of(mockProduct));
      mockParamMap.get.and.returnValue('1'); // Edit mode

      TestBed.overrideProvider(ActivatedRoute, { useValue: mockActivatedRoute });
      fixture = TestBed.createComponent(ProductForm);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should initialize in edit mode', () => {
      expect(component.isEditMode()).toBeTrue();
      expect(component.productId()).toBe(1);
    });

    it('should load product data', () => {
      expect(mockProductService.getProduct).toHaveBeenCalledWith(1);
      expect(component.productForm.value).toEqual(
        jasmine.objectContaining({
          name: mockProduct.name,
          price: mockProduct.price,
          stockQuantity: mockProduct.stockQuantity,
        }),
      );
    });

    it('should call updateProduct on valid submit', () => {
      component.productForm.patchValue({ name: 'Updated Name' });
      mockProductService.updateProduct.and.returnValue(of({ message: 'Updated' }));

      component.onSubmit();

      expect(mockProductService.updateProduct).toHaveBeenCalledWith(
        1,
        jasmine.objectContaining({
          name: 'Updated Name',
        }),
      );
      expect(mockRouter.navigate).toHaveBeenCalledWith(['/products']);
    });
  });
});
