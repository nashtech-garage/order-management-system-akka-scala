import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { ProductService } from '@core/services/product.service';
import { ProductResponse } from '@shared/models/product.model';
import { Button } from '@shared/components/button/button';
import { ToastService } from '@shared/services/toast.service';
import { ConfirmationDialogService } from '@shared/services/confirmation-dialog.service';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, Button],
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList implements OnInit {
  private productService = inject(ProductService);
  private router = inject(Router);
  private toastService = inject(ToastService);
  private confirmationDialog = inject(ConfirmationDialogService);

  products = signal<ProductResponse[]>([]);
  categories = signal<{ id: number; name: string; description?: string }[]>([]);
  isLoading = signal<boolean>(false);
  error = signal<string | null>(null);
  offset = signal<number>(0);
  limit = signal<number>(10);

  searchControl = new FormControl('');
  categoryFilter = new FormControl<number | null>(null);

  ngOnInit() {
    this.loadCategories();
    this.loadProducts();

    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => {
        this.offset.set(0);
        this.loadProducts();
      });

    this.categoryFilter.valueChanges.subscribe(() => {
      this.offset.set(0);
      this.loadProducts();
    });
  }

  loadCategories() {
    this.productService.getCategories().subscribe({
      next: (data) => {
        this.categories.set(data);
      },
      error: (err) => {
        console.error('Error loading categories:', err);
      },
    });
  }

  loadProducts() {
    this.isLoading.set(true);
    this.error.set(null);

    const searchTerm = this.searchControl.value || undefined;
    const categoryId = this.categoryFilter.value || undefined;

    this.productService.getProducts(this.offset(), this.limit(), searchTerm, categoryId).subscribe({
      next: (data) => {
        this.products.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load products. Please try again.');
        this.isLoading.set(false);
        console.error('Error loading products:', err);
      },
    });
  }

  changePage(direction: number) {
    const newOffset = this.offset() + direction * this.limit();
    if (newOffset >= 0) {
      this.offset.set(newOffset);
      this.loadProducts();
    }
  }

  get currentPage(): number {
    return Math.floor(this.offset() / this.limit()) + 1;
  }

  navigateToCreate() {
    this.router.navigate(['/products/new']);
  }

  navigateToEdit(id: number) {
    this.router.navigate(['/products', id, 'edit']);
  }

  deleteProduct(id: number) {
    this.confirmationDialog
      .confirmDelete('Are you sure you want to delete this product?', 'Delete Product')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.productService.deleteProduct(id).subscribe({
            next: () => {
              this.toastService.success('Product deleted successfully');
              this.loadProducts();
            },
            error: (err) => {
              this.toastService.error('Failed to delete product');
              console.error(err);
            },
          });
        }
      });
  }
}
