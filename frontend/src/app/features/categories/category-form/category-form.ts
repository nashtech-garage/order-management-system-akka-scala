import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CategoryService } from '@core/services/category.service';
import { Button } from '@shared/components/button/button';
import { CreateCategoryRequest } from '@shared/models/product.model';

@Component({
  selector: 'app-category-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, Button],
  templateUrl: './category-form.html',
  styleUrl: './category-form.scss',
})
export class CategoryForm {
  private fb = inject(FormBuilder);
  private categoryService = inject(CategoryService);
  private router = inject(Router);

  categoryForm!: FormGroup;
  isSubmitting = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor() {
    this.initForm();
  }

  private initForm() {
    this.categoryForm = this.fb.group({
      name: ['', [Validators.required]],
      description: [''],
    });
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.categoryForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  onSubmit() {
    if (this.categoryForm.invalid) {
      this.categoryForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.error.set(null);

    const formValue = this.categoryForm.value;
    const request: CreateCategoryRequest = {
      name: formValue.name,
      description: formValue.description || undefined,
    };

    this.categoryService.createCategory(request).subscribe({
      next: () => {
        this.router.navigate(['/categories']);
        this.isSubmitting.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.error || 'Failed to create category');
        this.isSubmitting.set(false);
      },
    });
  }

  cancel() {
    this.router.navigate(['/categories']);
  }
}
