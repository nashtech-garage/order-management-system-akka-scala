import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ProductList } from './product-list';
import { ProductService } from '@core/services/product.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ProductResponse } from '@shared/models/product.model';

describe('ProductList', () => {
    let component: ProductList;
    let fixture: ComponentFixture<ProductList>;
    let mockProductService: jasmine.SpyObj<ProductService>;
    let mockRouter: jasmine.SpyObj<Router>;

    const mockProducts: ProductResponse[] = [
        {
            id: 1,
            name: 'Test Product 1',
            description: 'Description 1',
            price: 100,
            stockQuantity: 10,
            categoryId: 1,
            createdAt: new Date().toISOString(),
        },
        {
            id: 2,
            name: 'Test Product 2',
            description: 'Description 2',
            price: 200,
            stockQuantity: 20,
            categoryId: 2,
            createdAt: new Date().toISOString(),
        },
    ];

    const mockCategories = [
        { id: 1, name: 'Category 1' },
        { id: 2, name: 'Category 2' },
    ];

    beforeEach(async () => {
        mockProductService = jasmine.createSpyObj('ProductService', [
            'getProducts',
            'getCategories',
            'deleteProduct',
        ]);
        mockRouter = jasmine.createSpyObj('Router', ['navigate']);

        await TestBed.configureTestingModule({
            imports: [ProductList],
            providers: [
                { provide: ProductService, useValue: mockProductService },
                { provide: Router, useValue: mockRouter },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProductList);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        mockProductService.getCategories.and.returnValue(of([]));
        mockProductService.getProducts.and.returnValue(of([]));
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    describe('Initialization', () => {
        it('should load categories and products on init', () => {
            mockProductService.getCategories.and.returnValue(of(mockCategories));
            mockProductService.getProducts.and.returnValue(of(mockProducts));

            fixture.detectChanges();

            expect(mockProductService.getCategories).toHaveBeenCalled();
            expect(mockProductService.getProducts).toHaveBeenCalledWith(0, 10, undefined, undefined);
            expect(component.categories()).toEqual(mockCategories);
            expect(component.products()).toEqual(mockProducts);
            expect(component.isLoading()).toBeFalse();
        });

        it('should handle error when loading products fails', () => {
            mockProductService.getCategories.and.returnValue(of([]));
            mockProductService.getProducts.and.returnValue(throwError(() => new Error('Error')));
            spyOn(console, 'error');

            fixture.detectChanges();

            expect(component.products()).toEqual([]);
            expect(component.error()).toBe('Failed to load products. Please try again.');
            expect(component.isLoading()).toBeFalse();
        });
    });

    describe('Search and Filter', () => {
        beforeEach(() => {
            mockProductService.getCategories.and.returnValue(of(mockCategories));
            mockProductService.getProducts.and.returnValue(of(mockProducts));
            fixture.detectChanges();
        });

        it('should reload products when search term changes', fakeAsync(() => {
            const searchTerm = 'test';
            component.searchControl.setValue(searchTerm);

            tick(300); // Wait for debounce

            expect(mockProductService.getProducts).toHaveBeenCalledWith(0, 10, searchTerm, undefined);
            expect(component.offset()).toBe(0);
        }));

        it('should reload products when category filter changes', () => {
            const categoryId = 1;
            component.categoryFilter.setValue(categoryId);

            expect(mockProductService.getProducts).toHaveBeenCalledWith(0, 10, undefined, categoryId);
            expect(component.offset()).toBe(0);
        });
    });

    describe('Pagination', () => {
        beforeEach(() => {
            mockProductService.getCategories.and.returnValue(of([]));
            mockProductService.getProducts.and.returnValue(of([]));
            fixture.detectChanges();
        });

        it('should change page and reload products', () => {
            component.changePage(1); // Next page

            expect(component.offset()).toBe(10);
            expect(mockProductService.getProducts).toHaveBeenCalledWith(10, 10, undefined, undefined);
        });

        it('should not change page if new offset is negative', () => {
            component.changePage(-1); // Previous page from 0

            expect(component.offset()).toBe(0);
            expect(mockProductService.getProducts).toHaveBeenCalledTimes(1); // Only initial call
        });

        it('should calculate current page correctly', () => {
            component.offset.set(20);
            component.limit.set(10);
            expect(component.currentPage).toBe(3);
        });
    });

    describe('Navigation', () => {
        beforeEach(() => {
            mockProductService.getCategories.and.returnValue(of([]));
            mockProductService.getProducts.and.returnValue(of([]));
            fixture.detectChanges();
        });

        it('should navigate to create page', () => {
            component.navigateToCreate();
            expect(mockRouter.navigate).toHaveBeenCalledWith(['/products/new']);
        });

        it('should navigate to edit page', () => {
            const productId = 123;
            component.navigateToEdit(productId);
            expect(mockRouter.navigate).toHaveBeenCalledWith(['/products', productId, 'edit']);
        });
    });

    describe('Delete Product', () => {
        beforeEach(() => {
            mockProductService.getCategories.and.returnValue(of([]));
            mockProductService.getProducts.and.returnValue(of([]));
            fixture.detectChanges();
        });

        it('should delete product and reload list on confirmation', () => {
            spyOn(window, 'confirm').and.returnValue(true);
            mockProductService.deleteProduct.and.returnValue(of({ message: 'Deleted' }));

            component.deleteProduct(1);

            expect(mockProductService.deleteProduct).toHaveBeenCalledWith(1);
            expect(mockProductService.getProducts).toHaveBeenCalledTimes(2); // Init + Reload
        });

        it('should not delete product if not confirmed', () => {
            spyOn(window, 'confirm').and.returnValue(false);

            component.deleteProduct(1);

            expect(mockProductService.deleteProduct).not.toHaveBeenCalled();
        });

        it('should handle error when delete fails', () => {
            spyOn(window, 'confirm').and.returnValue(true);
            mockProductService.deleteProduct.and.returnValue(throwError(() => new Error('Error')));
            spyOn(console, 'error');

            component.deleteProduct(1);

            expect(component.error()).toBe('Failed to delete product.');
        });
    });
});
