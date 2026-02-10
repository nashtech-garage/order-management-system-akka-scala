import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { CategoryService } from '@core/services/category.service';
import { Category } from '@shared/models/product.model';
import { Button } from '@shared/components/button/button';
import { ToastService } from '@shared/services/toast.service';
import { ConfirmationDialogService } from '@shared/services/confirmation-dialog.service';

@Component({
  selector: 'app-category-list',
  standalone: true,
  imports: [CommonModule, Button],
  templateUrl: './category-list.html',
  styleUrl: './category-list.scss',
})
export class CategoryList implements OnInit {
  private categoryService = inject(CategoryService);
  private router = inject(Router);
  private toastService = inject(ToastService);
  private confirmationDialog = inject(ConfirmationDialogService);

  categories = signal<Category[]>([]);
  isLoading = signal<boolean>(false);
  error = signal<string | null>(null);

  ngOnInit() {
    this.loadCategories();
  }

  loadCategories() {
    this.isLoading.set(true);
    this.error.set(null);

    this.categoryService.getCategories().subscribe({
      next: (data) => {
        this.categories.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load categories. Please try again.');
        this.isLoading.set(false);
        console.error('Error loading categories:', err);
      },
    });
  }

  navigateToCreate() {
    this.router.navigate(['/categories/new']);
  }

  deleteCategory(id: number) {
    this.confirmationDialog
      .confirmWarning(
        'Are you sure you want to delete this category? This may affect products using this category.',
        'Delete Category',
      )
      .subscribe((confirmed) => {
        if (confirmed) {
          this.categoryService.deleteCategory(id).subscribe({
            next: () => {
              this.toastService.success('Category deleted successfully');
              this.loadCategories();
            },
            error: (err) => {
              this.toastService.error('Failed to delete category');
              console.error(err);
            },
          });
        }
      });
  }
}
