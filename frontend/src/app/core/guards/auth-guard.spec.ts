import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '@features/auth/auth.service';
import { authGuard } from '@core/guards/auth-guard';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  const mockState = { url: '/dashboard' } as RouterStateSnapshot;
  const mockRoute = {} as ActivatedRouteSnapshot;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'validateToken']);

    router = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
  });

  function runGuard() {
    return TestBed.runInInjectionContext(() => authGuard(mockRoute, mockState));
  }

  it('should redirect to login if not authenticated', () => {
    authService.isAuthenticated.and.returnValue(false);

    const result = runGuard();

    expect(result).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: mockState.url },
    });
    expect(authService.validateToken).not.toHaveBeenCalled();
  });

  it('should redirect to login with returnUrl when no token exists', () => {
    authService.isAuthenticated.and.returnValue(false);
    const customState = { url: '/protected/resource' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => authGuard(mockRoute, customState));

    expect(result).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/protected/resource' },
    });
  });

  it('should redirect to login if token validation fails', (done) => {
    authService.isAuthenticated.and.returnValue(true);
    authService.validateToken.and.returnValue(of(false));

    const result = runGuard();

    if (typeof result === 'boolean') {
      fail('Expected Observable but got boolean');
      done();
      return;
    }

    (result as Observable<boolean>).subscribe((canActivate: boolean) => {
      expect(canActivate).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], {
        queryParams: { returnUrl: mockState.url },
      });
      expect(authService.validateToken).toHaveBeenCalled();
      done();
    });
  });

  it('should redirect to login if validation API errors', (done) => {
    authService.isAuthenticated.and.returnValue(true);
    authService.validateToken.and.returnValue(throwError(() => new Error('API error')));

    const result = runGuard();

    if (typeof result === 'boolean') {
      fail('Expected Observable but got boolean');
      done();
      return;
    }

    (result as Observable<boolean>).subscribe((canActivate: boolean) => {
      expect(canActivate).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], {
        queryParams: { returnUrl: mockState.url },
      });
      expect(authService.validateToken).toHaveBeenCalled();
      done();
    });
  });

  it('should allow navigation if token is valid', (done) => {
    authService.isAuthenticated.and.returnValue(true);
    authService.validateToken.and.returnValue(of(true));

    const result = runGuard();

    if (typeof result === 'boolean') {
      fail('Expected Observable but got boolean');
      done();
      return;
    }

    (result as Observable<boolean>).subscribe((canActivate: boolean) => {
      expect(canActivate).toBeTrue();
      expect(router.navigate).not.toHaveBeenCalled();
      expect(authService.validateToken).toHaveBeenCalled();
      done();
    });
  });

  it('should handle network errors gracefully', (done) => {
    authService.isAuthenticated.and.returnValue(true);
    authService.validateToken.and.returnValue(
      throwError(() => new Error('Network connection failed')),
    );

    const result = runGuard();

    if (typeof result === 'boolean') {
      fail('Expected Observable but got boolean');
      done();
      return;
    }

    (result as Observable<boolean>).subscribe((canActivate: boolean) => {
      expect(canActivate).toBeFalse();
      expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], {
        queryParams: { returnUrl: mockState.url },
      });
      done();
    });
  });

  it('should not call validateToken if already not authenticated', () => {
    authService.isAuthenticated.and.returnValue(false);

    runGuard();

    expect(authService.validateToken).not.toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalled();
  });

  it('should preserve returnUrl in query params for different routes', () => {
    authService.isAuthenticated.and.returnValue(false);
    const customState = { url: '/admin/users' } as RouterStateSnapshot;

    TestBed.runInInjectionContext(() => authGuard(mockRoute, customState));

    expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/admin/users' },
    });
  });
});
