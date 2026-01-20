import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ProductService } from '@core/services/product.service';
import { CategoryService } from '@core/services/category.service';
import { Button } from '@shared/components/button/button';
import { CreateProductRequest, UpdateProductRequest, Category } from '@shared/models/product.model';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, Button],
  templateUrl: './product-form.html',
  styleUrl: './product-form.scss',
})
export class ProductForm implements OnInit {
  private fb = inject(FormBuilder);
  private productService = inject(ProductService);
  private categoryService = inject(CategoryService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  productForm!: FormGroup;
  isEditMode = signal<boolean>(false);
  isLoading = signal<boolean>(false);
  isSubmitting = signal<boolean>(false);
  error = signal<string | null>(null);
  productId = signal<number | null>(null);
  categories = signal<Category[]>([]);

  constructor() {
    this.initForm();
  }

  ngOnInit() {
    this.loadCategories();

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode.set(true);
      this.productId.set(Number(id));
      this.loadProduct(Number(id));
    }
  }

  private initForm() {
    this.productForm = this.fb.group({
      name: ['', [Validators.required]],
      description: [''],
      price: [0, [Validators.required, Validators.min(0)]],
      stockQuantity: [0, [Validators.required, Validators.min(0)]],
      categoryId: [null],
      imageUrl: [''],
    });
  }

  loadCategories() {
    this.categoryService.getCategories().subscribe({
      next: (data) => {
        this.categories.set(data);
      },
      error: (err) => {
        console.error('Error loading categories:', err);
      },
    });
  }

  loadProduct(id: number) {
    this.isLoading.set(true);
    this.productService.getProduct(id).subscribe({
      next: (response) => {
        this.productForm.patchValue({
          name: response.name,
          description: response.description || '',
          price: response.price,
          stockQuantity: response.stockQuantity,
          categoryId: response.categoryId,
          imageUrl: response.imageUrl || '',
        });
        this.isLoading.set(false);
      },
      error: () => {
        this.error.set('Failed to load product details');
        this.isLoading.set(false);
      },
    });
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.productForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  onSubmit() {
    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.error.set(null);

    const formValue = this.productForm.value;

    if (this.isEditMode()) {
      this.updateProduct(formValue);
    } else {
      this.createProduct(formValue);
    }
  }

  private createProduct(formValue: {
    name: string;
    description?: string;
    price: number;
    stockQuantity: number;
    categoryId?: number;
    imageUrl?: string;
  }) {
    const request: CreateProductRequest = {
      name: formValue.name,
      description: formValue.description || undefined,
      price: formValue.price,
      stockQuantity: formValue.stockQuantity,
      categoryId: formValue.categoryId ? Number(formValue.categoryId) : undefined,
      imageUrl: formValue.imageUrl || undefined,
    };

    this.productService.createProduct(request).subscribe({
      next: () => {
        this.router.navigate(['/products']);
        this.isSubmitting.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.error || 'Failed to create product');
        this.isSubmitting.set(false);
      },
    });
  }

  private updateProduct(formValue: {
    name: string;
    description?: string;
    price: number;
    stockQuantity: number;
    categoryId?: number;
    imageUrl?: string;
  }) {
    const id = this.productId();
    if (!id) return;

    const request: UpdateProductRequest = {
      name: formValue.name,
      description: formValue.description || undefined,
      price: formValue.price,
      stockQuantity: formValue.stockQuantity,
      categoryId: formValue.categoryId ? Number(formValue.categoryId) : undefined,
      imageUrl: formValue.imageUrl || undefined,
    };

    this.productService.updateProduct(id, request).subscribe({
      next: () => {
        this.router.navigate(['/products']);
        this.isSubmitting.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.error || 'Failed to update product');
        this.isSubmitting.set(false);
      },
    });
  }

  cancel() {
    this.router.navigate(['/products']);
  }
}
