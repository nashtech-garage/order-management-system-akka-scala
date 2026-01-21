import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '@features/auth/auth.service';
import { Button } from '@app/shared/components/button/button';
import { ToastService } from '@shared/services/toast.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, Button, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private toastService = inject(ToastService);

  isLoading = signal(false);
  errorMessage = signal<string>('');

  loginForm = this.fb.group({
    usernameOrEmail: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  onSubmit() {
    if (this.loginForm.valid) {
      this.isLoading.set(true);
      this.loginForm.disable();
      this.errorMessage.set('');

      this.authService
        .login(this.loginForm.value as { usernameOrEmail: string; password: string })
        .subscribe({
          next: () => {
            this.isLoading.set(false);
            this.loginForm.enable();

            // Get the returnUrl from query params or default to dashboard
            const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
            this.router.navigateByUrl(returnUrl);
          },
          error: (error) => {
            this.isLoading.set(false);
            this.loginForm.enable();

            // Extract error message from backend
            const message = error.error?.error || 'Login failed. Please check your credentials.';
            this.errorMessage.set(message);

            // Also show toast notification
            this.toastService.error(message);

            console.error('Login failed', error);
          },
        });
    }
  }
}
