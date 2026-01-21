import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ToastService } from '@shared/services/toast.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const toastService = inject(ToastService);

  return next(req).pipe(
    catchError((error) => {
      if (error.status === 401) {
        // Check if this is a login endpoint
        const isLoginEndpoint = req.url.includes('/login');
        const isRegisterEndpoint = req.url.includes('/register');

        if (isLoginEndpoint) {
          // Don't show toast for login failures, let the component handle it
          // The error message from backend will be displayed in the form
        } else if (isRegisterEndpoint) {
          // Don't show session expired for register, let component handle it
        } else {
          // Unauthorized - session expired, redirect to login
          toastService.error('Session expired. Please login again.');
          router.navigate(['/auth/login']);
        }
      } else if (error.status === 403) {
        // Forbidden
        toastService.error('Access forbidden. You do not have permission to perform this action.');
      } else if (error.status === 400) {
        // Bad request - show error from backend if available
        const errorMessage = error.error?.error || 'Invalid request. Please check your input.';
        // Only show toast if not handled by component (register/create endpoints)
        toastService.error(errorMessage);
      } else if (error.status === 500) {
        // Internal server error
        toastService.error('Server error occurred. Please try again later.');
      }

      return throwError(() => error);
    }),
  );
};
