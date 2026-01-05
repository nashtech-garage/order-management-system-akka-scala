import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { CustomerService } from '@core/services/customer.service';
import { Customer } from '@shared/models/customer.model';
import { Button } from '@shared/components/button/button';

@Component({
    selector: 'app-customer-list',
    standalone: true,
    imports: [CommonModule, ReactiveFormsModule, Button],
    templateUrl: './customer-list.html',
    styleUrl: './customer-list.scss',
})
export class CustomerList implements OnInit {
    private customerService = inject(CustomerService);
    private router = inject(Router);

    customers = signal<Customer[]>([]);
    isLoading = signal<boolean>(false);
    error = signal<string | null>(null);
    offset = signal<number>(0);
    limit = signal<number>(10);

    searchControl = new FormControl('');

    ngOnInit() {
        this.loadCustomers();

        this.searchControl.valueChanges.pipe(
            debounceTime(300),
            distinctUntilChanged()
        ).subscribe(term => {
            this.offset.set(0);
            this.loadCustomers();
        });
    }

    loadCustomers() {
        this.isLoading.set(true);
        this.error.set(null);

        this.customerService.getCustomers(this.offset(), this.limit()).subscribe({
            next: (data) => {
                this.customers.set(data);
                this.isLoading.set(false);
            },
            error: (err) => {
                this.error.set('Failed to load customers. Please try again.');
                this.isLoading.set(false);
                console.error('Error loading customers:', err);
            }
        });
    }

    changePage(direction: number) {
        const newOffset = this.offset() + (direction * this.limit());
        if (newOffset >= 0) {
            this.offset.set(newOffset);
            this.loadCustomers();
        }
    }

    get currentPage(): number {
        return Math.floor(this.offset() / this.limit()) + 1;
    }

    navigateToCreate() {
        this.router.navigate(['/customers/new']);
    }

    navigateToEdit(id: number) {
        this.router.navigate(['/customers', id, 'edit']);
    }

    deleteCustomer(id: number) {
        if (confirm('Are you sure you want to delete this customer?')) {
            this.customerService.deleteCustomer(id).subscribe({
                next: () => {
                    this.loadCustomers();
                },
                error: (err) => {
                    this.error.set('Failed to delete customer.');
                    console.error(err);
                }
            });
        }
    }
}
