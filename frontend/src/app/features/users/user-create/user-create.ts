import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '@core/services/user.service';
import { CreateUserRequest } from '@shared/models/user.model';

@Component({
  selector: 'app-user-create',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './user-create.html',
  styleUrls: ['./user-create.scss'],
})
export class UserCreate implements OnInit {
  private userService = inject(UserService);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  createForm!: FormGroup;
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit() {
    this.initForm();
  }

  initForm() {
    this.createForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required],
      role: ['user', Validators.required],
      phoneNumber: [''],
    }, { validators: this.passwordMatchValidator });
  }

  passwordMatchValidator(form: FormGroup) {
    const password = form.get('password');
    const confirmPassword = form.get('confirmPassword');
    
    if (password && confirmPassword && password.value !== confirmPassword.value) {
      confirmPassword.setErrors({ passwordMismatch: true });
      return { passwordMismatch: true };
    }
    return null;
  }

  onSubmit() {
    if (this.createForm.invalid) {
      this.markFormGroupTouched(this.createForm);
      return;
    }

    const formValue = this.createForm.value;
    const request: CreateUserRequest = {
      username: formValue.username,
      email: formValue.email,
      password: formValue.password,
      role: formValue.role,
      phoneNumber: formValue.phoneNumber || undefined,
    };

    this.loading.set(true);
    this.error.set(null);

    this.userService.register(request).subscribe({
      next: (user) => {
        alert(`User "${user.username}" created successfully!`);
        this.router.navigate(['/users', user.id]);
      },
      error: (err) => {
        this.error.set(err.error?.error || err.message || 'Failed to create user');
        this.loading.set(false);
      },
    });
  }

  private markFormGroupTouched(formGroup: FormGroup) {
    Object.keys(formGroup.controls).forEach((key) => {
      const control = formGroup.get(key);
      control?.markAsTouched();
    });
  }

  getFieldError(fieldName: string): string | null {
    const field = this.createForm.get(fieldName);
    if (field?.invalid && field?.touched) {
      if (field.errors?.['required']) return `${fieldName} is required`;
      if (field.errors?.['email']) return 'Invalid email format';
      if (field.errors?.['minlength']) {
        return `Minimum ${field.errors['minlength'].requiredLength} characters`;
      }
      if (field.errors?.['passwordMismatch']) return 'Passwords do not match';
    }
    return null;
  }
}
