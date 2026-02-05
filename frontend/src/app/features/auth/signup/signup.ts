import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '@features/auth/auth.service';
import { Button } from '@app/shared/components/button/button';
import { RegisterRequest } from '@shared/models/user.model';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [ReactiveFormsModule, Button, RouterLink],
  templateUrl: './signup.html',
  styleUrl: './signup.scss',
})
export class Signup {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  isLoading = signal(false);
  errorMessage = signal<string>('');

  signupForm = this.fb.group(
    {
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]],
    },
    {
      validators: this.passwordMatchValidator,
    },
  );

  passwordMatchValidator(form: { get: (key: string) => { value: string } | null }) {
    const password = form.get('password');
    const confirmPassword = form.get('confirmPassword');

    if (password && confirmPassword && password.value !== confirmPassword.value) {
      return { passwordMismatch: true };
    }
    return null;
  }

  onSubmit() {
    if (this.signupForm.valid) {
      this.isLoading.set(true);
      this.signupForm.disable();
      this.errorMessage.set('');

      const formValue = this.signupForm.value;
      const userData = {
        email: formValue.email,
        username: formValue.username,
        password: formValue.password,
      };

      this.authService.register(userData as RegisterRequest).subscribe({
        next: () => {
          this.isLoading.set(false);
          this.signupForm.enable();
          this.router.navigate(['/dashboard']);
        },
        error: (error) => {
          this.isLoading.set(false);
          this.signupForm.enable();
          const message = error.error?.message || 'Registration failed. Please try again.';
          this.errorMessage.set(message);
          console.error('Signup failed', error);
        },
      });
    }
  }
}
