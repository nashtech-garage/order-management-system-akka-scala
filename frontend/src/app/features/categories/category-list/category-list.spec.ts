import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CategoryList } from './category-list';
import { CategoryService } from '@core/services/category.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Category } from '@shared/models/product.model';

describe('CategoryList', () => {
    let component: CategoryList;
    let fixture: ComponentFixture<CategoryList>;
    let mockCategoryService: jasmine.SpyObj<CategoryService>;
    let mockRouter: jasmine.SpyObj<Router>;

    const mockCategories: Category[] = [
        { id: 1, name: 'Category 1', description: 'Description 1' },
        { id: 2, name: 'Category 2', description: 'Description 2' },
    ];

    beforeEach(async () => {
        mockCategoryService = jasmine.createSpyObj('CategoryService', [
            'getCategories',
            'deleteCategory',
        ]);
        mockRouter = jasmine.createSpyObj('Router', ['navigate']);

        await TestBed.configureTestingModule({
            imports: [CategoryList],
            providers: [
                { provide: CategoryService, useValue: mockCategoryService },
                { provide: Router, useValue: mockRouter },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CategoryList);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        mockCategoryService.getCategories.and.returnValue(of([]));
        fixture.detectChanges();
        expect(component).toBeTruthy();
    });

    describe('Initialization', () => {
        it('should load categories on init', () => {
            mockCategoryService.getCategories.and.returnValue(of(mockCategories));

            fixture.detectChanges();

            expect(mockCategoryService.getCategories).toHaveBeenCalled();
            expect(component.categories()).toEqual(mockCategories);
            expect(component.isLoading()).toBeFalse();
        });

        it('should handle error when loading categories fails', () => {
            mockCategoryService.getCategories.and.returnValue(throwError(() => new Error('Error')));
            spyOn(console, 'error');

            fixture.detectChanges();

            expect(component.categories()).toEqual([]);
            expect(component.error()).toBe('Failed to load categories. Please try again.');
            expect(component.isLoading()).toBeFalse();
        });
    });

    describe('Navigation', () => {
        beforeEach(() => {
            mockCategoryService.getCategories.and.returnValue(of([]));
            fixture.detectChanges();
        });

        it('should navigate to create page', () => {
            component.navigateToCreate();
            expect(mockRouter.navigate).toHaveBeenCalledWith(['/categories/new']);
        });
    });

    describe('Delete Category', () => {
        beforeEach(() => {
            mockCategoryService.getCategories.and.returnValue(of([]));
            fixture.detectChanges();
        });

        it('should delete category and reload list on confirmation', () => {
            spyOn(window, 'confirm').and.returnValue(true);
            mockCategoryService.deleteCategory.and.returnValue(of({ message: 'Deleted' }));
            // Setup re-fetch
            mockCategoryService.getCategories.and.returnValue(of([]));

            component.deleteCategory(1);

            expect(mockCategoryService.deleteCategory).toHaveBeenCalledWith(1);
            expect(mockCategoryService.getCategories).toHaveBeenCalledTimes(2); // Init + Reload
        });

        it('should not delete category if not confirmed', () => {
            spyOn(window, 'confirm').and.returnValue(false);

            component.deleteCategory(1);

            expect(mockCategoryService.deleteCategory).not.toHaveBeenCalled();
        });

        it('should handle error when delete fails', () => {
            spyOn(window, 'confirm').and.returnValue(true);
            mockCategoryService.deleteCategory.and.returnValue(throwError(() => new Error('Error')));
            spyOn(console, 'error');

            component.deleteCategory(1);

            expect(component.error()).toBe('Failed to delete category.');
        });
    });
});
