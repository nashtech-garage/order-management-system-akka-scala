import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CategoryForm } from './category-form';
import { CategoryService } from '@core/services/category.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Category, CreateCategoryRequest } from '@shared/models/product.model';

describe('CategoryForm', () => {
    let component: CategoryForm;
    let fixture: ComponentFixture<CategoryForm>;
    let mockCategoryService: jasmine.SpyObj<CategoryService>;
    let mockRouter: jasmine.SpyObj<Router>;

    beforeEach(async () => {
        mockCategoryService = jasmine.createSpyObj('CategoryService', ['createCategory']);
        mockRouter = jasmine.createSpyObj('Router', ['navigate']);

        await TestBed.configureTestingModule({
            imports: [CategoryForm],
            providers: [
                { provide: CategoryService, useValue: mockCategoryService },
                { provide: Router, useValue: mockRouter },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CategoryForm);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize empty form', () => {
        expect(component.categoryForm.get('name')?.value).toBe('');
        expect(component.categoryForm.get('description')?.value).toBe('');
    });

    it('should validate required fields', () => {
        const nameControl = component.categoryForm.get('name');
        expect(nameControl?.valid).toBeFalse();

        nameControl?.setValue('Test Category');
        expect(nameControl?.valid).toBeTrue();
    });

    it('should call createCategory on valid submit', () => {
        component.categoryForm.patchValue({
            name: 'New Category',
            description: 'Test Description',
        });

        const mockResponse: Category = {
            id: 1,
            name: 'New Category',
            description: 'Test Description'
        };

        mockCategoryService.createCategory.and.returnValue(of(mockResponse));

        component.onSubmit();

        expect(mockCategoryService.createCategory).toHaveBeenCalledWith(
            jasmine.objectContaining({
                name: 'New Category',
                description: 'Test Description',
            }),
        );
        expect(mockRouter.navigate).toHaveBeenCalledWith(['/categories']);
    });

    it('should handle create error', () => {
        component.categoryForm.patchValue({
            name: 'New Category',
        });

        mockCategoryService.createCategory.and.returnValue(
            throwError(() => ({ error: { error: 'Creation failed' } })),
        );

        component.onSubmit();

        expect(component.error()).toBe('Creation failed');
        expect(component.isSubmitting()).toBeFalse();
    });

    it('should navigate back on cancel', () => {
        component.cancel();
        expect(mockRouter.navigate).toHaveBeenCalledWith(['/categories']);
    });
});
