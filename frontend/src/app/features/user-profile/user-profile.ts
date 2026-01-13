import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UserProfileService } from '../auth/user-profile.service';
import { AuthService } from '../auth/auth.service';
import { User } from '@shared/models/user.model';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './user-profile.html',
  styleUrls: ['./user-profile.scss'],
})
export class UserProfileComponent implements OnInit {
  private userProfileService = inject(UserProfileService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private router = inject(Router);

  user = signal<User | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  success = signal<string | null>(null);

  profileForm!: FormGroup;
  passwordForm!: FormGroup;

  activeTab = signal<'profile' | 'password'>('profile');

  ngOnInit(): void {
    this.initializeForms();
    this.loadUserProfile();
  }

  initializeForms(): void {
    this.profileForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
    });

    this.passwordForm = this.fb.group(
      {
        currentPassword: ['', [Validators.required]],
        newPassword: ['', [Validators.required, Validators.minLength(6)]],
        confirmPassword: ['', [Validators.required]],
      },
      { validators: this.passwordMatchValidator },
    );
  }

  passwordMatchValidator(g: FormGroup) {
    const newPassword = g.get('newPassword')?.value;
    const confirmPassword = g.get('confirmPassword')?.value;
    return newPassword === confirmPassword ? null : { mismatch: true };
  }

  loadUserProfile(): void {
    this.loading.set(true);
    this.error.set(null);

    this.userProfileService.getProfile().subscribe({
      next: (user) => {
        this.user.set(user);
        this.profileForm.patchValue({
          username: user.username,
          email: user.email,
        });
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load profile. Please try again.');
        this.loading.set(false);
        console.error('Error loading profile:', err);
      },
    });
  }

  updateProfile(): void {
    if (this.profileForm.invalid) {
      this.markFormGroupTouched(this.profileForm);
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);

    const formValue = this.profileForm.value;
    const currentUser = this.user();

    // Only send changed fields
    const updateData: Partial<Pick<User, 'username' | 'email'>> = {};
    if (formValue.username !== currentUser?.username) {
      updateData.username = formValue.username;
    }
    if (formValue.email !== currentUser?.email) {
      updateData.email = formValue.email;
    }

    // Check if there are any changes
    if (Object.keys(updateData).length === 0) {
      this.error.set('No changes detected');
      this.loading.set(false);
      return;
    }

    this.userProfileService.updateProfile(updateData).subscribe({
      next: (response) => {
        this.success.set(response.message);
        this.loading.set(false);
        // Reload profile to get updated data
        setTimeout(() => {
          this.loadUserProfile();
          this.success.set(null);
        }, 2000);
      },
      error: (err) => {
        this.error.set(err.error?.error || 'Failed to update profile. Please try again.');
        this.loading.set(false);
        console.error('Error updating profile:', err);
      },
    });
  }

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.markFormGroupTouched(this.passwordForm);
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);

    const { currentPassword, newPassword } = this.passwordForm.value;

    this.userProfileService
      .changePassword({
        currentPassword,
        newPassword,
      })
      .subscribe({
        next: (response) => {
          this.success.set(response.message);
          this.passwordForm.reset();
          this.loading.set(false);
          // Optionally redirect to login after password change
          setTimeout(() => {
            this.success.set(null);
          }, 3000);
        },
        error: (err) => {
          this.error.set(err.error?.error || 'Failed to change password. Please try again.');
          this.loading.set(false);
          console.error('Error changing password:', err);
        },
      });
  }

  setActiveTab(tab: 'profile' | 'password'): void {
    this.activeTab.set(tab);
    this.error.set(null);
    this.success.set(null);
  }

  private markFormGroupTouched(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach((key) => {
      const control = formGroup.get(key);
      control?.markAsTouched();
    });
  }

  isFieldInvalid(form: FormGroup, fieldName: string): boolean {
    const field = form.get(fieldName);
    return !!(field && field.invalid && (field.dirty || field.touched));
  }

  getFieldError(form: FormGroup, fieldName: string): string {
    const field = form.get(fieldName);
    if (field?.hasError('required')) {
      return 'This field is required';
    }
    if (field?.hasError('email')) {
      return 'Please enter a valid email';
    }
    if (field?.hasError('minlength')) {
      const minLength = field.getError('minlength').requiredLength;
      return `Minimum length is ${minLength} characters`;
    }
    return '';
  }

  hasPasswordMismatch(): boolean | undefined {
    return (
      this.passwordForm.hasError('mismatch') && this.passwordForm.get('confirmPassword')?.touched
    );
  }
}
