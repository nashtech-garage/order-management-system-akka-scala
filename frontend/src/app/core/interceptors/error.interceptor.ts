import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  return next(req).pipe(
    catchError((error) => {
      if (error.status === 401) {
        // Unauthorized - redirect to login
        router.navigate(['/auth/login']);
      } else if (error.status === 403) {
        // Forbidden
        console.error('Access forbidden');
      } else if (error.status === 500) {
        // Internal server error
        console.error('Server error occurred');
      }

      return throwError(() => error);
    }),
  );
};
