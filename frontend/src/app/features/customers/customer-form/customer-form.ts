import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, FormGroup, FormArray, Validators, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CustomerService } from '@core/services/customer.service';
import { Button } from '@shared/components/button/button';
import {
  Address,
  CreateCustomerRequest,
  UpdateCustomerRequest,
  CreateAddressRequest,
} from '@shared/models/customer.model';
import { switchMap, of, forkJoin } from 'rxjs';

@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, Button],
  templateUrl: './customer-form.html',
  styleUrl: './customer-form.scss',
})
export class CustomerForm implements OnInit {
  private fb = inject(FormBuilder);
  private customerService = inject(CustomerService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  customerForm!: FormGroup;
  isEditMode = signal<boolean>(false);
  isLoading = signal<boolean>(false);
  isSubmitting = signal<boolean>(false);
  error = signal<string | null>(null);
  customerId = signal<number | null>(null);

  constructor() {
    this.initForm();
  }

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode.set(true);
      this.customerId.set(Number(id));
      this.loadCustomer2(Number(id));
    }
  }

  private initForm() {
    this.customerForm = this.fb.group({
      firstName: ['', [Validators.required]],
      lastName: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      addresses: this.fb.array([]),
    });
  }

  get addresses() {
    return this.customerForm.get('addresses') as FormArray;
  }

  addAddress(address?: Address) {
    const addressGroup = this.fb.group({
      street: [address?.street || '', Validators.required],
      city: [address?.city || '', Validators.required],
      state: [address?.state || '', Validators.required],
      postalCode: [address?.postalCode || '', Validators.required],
      country: [address?.country || '', Validators.required],
      isDefault: [address?.isDefault || false],
    });
    this.addresses.push(addressGroup);
  }

  removeAddress(index: number) {
    this.addresses.removeAt(index);
  }

  loadCustomer2(id: number) {
    this.isLoading.set(true);
    this.customerService.getCustomer(id).subscribe({
      next: (response) => {
        this.customerForm.patchValue({
          firstName: response.firstName,
          lastName: response.lastName,
          email: response.email,
          phone: response.phone,
        });

        // Clear existing addresses and add fetched ones
        this.addresses.clear();
        if (response.addresses && response.addresses.length > 0) {
          response.addresses.forEach((addr) => this.addAddress(addr));
        }

        this.isLoading.set(false);
      },
      error: () => {
        this.error.set('Failed to load customer details');
        this.isLoading.set(false);
      },
    });
  }

  isFieldInvalid(fieldName: string): boolean {
    const field = this.customerForm.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  isAddressFieldInvalid(index: number, fieldName: string): boolean {
    const group = this.addresses.at(index) as FormGroup;
    const field = group.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  onSubmit() {
    if (this.customerForm.invalid) {
      this.customerForm.markAllAsTouched();
      return;
    }

    this.isSubmitting.set(true);
    this.error.set(null);

    const formValue = this.customerForm.value;

    if (this.isEditMode()) {
      this.updateCustomer(formValue);
    } else {
      this.createCustomer(formValue);
    }
  }

  private createCustomer(formValue: {
    firstName: string;
    lastName: string;
    email: string;
    phone?: string;
    addresses?: CreateAddressRequest[];
  }) {
    const request: CreateCustomerRequest = {
      firstName: formValue.firstName,
      lastName: formValue.lastName,
      email: formValue.email,
      phone: formValue.phone,
    };

    this.customerService
      .createCustomer(request)
      .pipe(
        switchMap((customer) => {
          // If we have addresses, we need to add them sequentially
          const addressRequests = (formValue.addresses || []).map((addr: CreateAddressRequest) => {
            const addressReq: CreateAddressRequest = { ...addr };
            return this.customerService.addAddress(customer.id!, addressReq);
          });

          return addressRequests.length > 0 ? forkJoin(addressRequests) : of(null);
        }),
      )
      .subscribe({
        next: () => {
          this.router.navigate(['/customers']);
          this.isSubmitting.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.error.set(err.error?.error || 'Failed to create customer');
          this.isSubmitting.set(false);
        },
      });
  }

  private updateCustomer(formValue: {
    firstName: string;
    lastName: string;
    email: string;
    phone?: string;
  }) {
    const id = this.customerId();
    if (!id) return;

    const request: UpdateCustomerRequest = {
      firstName: formValue.firstName,
      lastName: formValue.lastName,
      email: formValue.email,
      phone: formValue.phone,
    };

    this.customerService.updateCustomer(id, request).subscribe({
      next: () => {
        this.router.navigate(['/customers']);
        this.isSubmitting.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.error || 'Failed to update customer');
        this.isSubmitting.set(false);
      },
    });
  }

  cancel() {
    this.router.navigate(['/customers']);
  }
}
