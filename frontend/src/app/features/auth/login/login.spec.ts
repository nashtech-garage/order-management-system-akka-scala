import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of, throwError, EMPTY, Subject } from 'rxjs';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { By } from '@angular/platform-browser';

import { Login } from './login';
import { AuthService } from '@features/auth/auth.service';
import { ToastService } from '@shared/services/toast.service';
import { LoginResponse, User } from '@shared/models/user.model';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let toastService: jasmine.SpyObj<ToastService>;
  let httpMock: HttpTestingController;

  const mockUser: User = {
    id: 1,
    username: 'testuser',
    email: 'test@example.com',
    role: 'user',
    createdAt: '2024-01-01',
  };

  const mockLoginResponse: LoginResponse = {
    token: 'fake-token',
    user: mockUser,
  };

  beforeEach(async () => {
    // Create spy objects for services
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);
    const routerSpy = jasmine.createSpyObj('Router', [
      'navigateByUrl',
      'createUrlTree',
      'serializeUrl',
    ]);
    const toastServiceSpy = jasmine.createSpyObj('ToastService', ['error', 'success']);

    // Setup router spy with necessary properties and methods
    routerSpy.events = EMPTY; // RouterLink needs this
    routerSpy.createUrlTree.and.returnValue({} as UrlTree);
    routerSpy.serializeUrl.and.returnValue('/auth/signup');

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParams: {},
            },
          },
        },
      ],
      schemas: [NO_ERRORS_SCHEMA], // Ignore child components like RouterLink
    }).compileComponents();

    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    toastService = TestBed.inject(ToastService) as jasmine.SpyObj<ToastService>;
    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('Component Initialization', () => {
    it('should initialize with empty form', () => {
      expect(component.loginForm.value).toEqual({
        usernameOrEmail: '',
        password: '',
      });
    });

    it('should initialize with isLoading as false', () => {
      expect(component.isLoading()).toBe(false);
    });

    it('should initialize with empty errorMessage', () => {
      expect(component.errorMessage()).toBe('');
    });

    it('should have an invalid form initially', () => {
      expect(component.loginForm.valid).toBe(false);
    });
  });

  describe('Form Validation', () => {
    it('should require usernameOrEmail field', () => {
      const usernameOrEmail = component.loginForm.get('usernameOrEmail');
      expect(usernameOrEmail?.valid).toBe(false);
      expect(usernameOrEmail?.hasError('required')).toBe(true);
    });

    it('should require minimum 3 characters for usernameOrEmail', () => {
      const usernameOrEmail = component.loginForm.get('usernameOrEmail');
      usernameOrEmail?.setValue('ab');
      expect(usernameOrEmail?.hasError('minlength')).toBe(true);
    });

    it('should accept valid usernameOrEmail', () => {
      const usernameOrEmail = component.loginForm.get('usernameOrEmail');
      usernameOrEmail?.setValue('testuser');
      expect(usernameOrEmail?.valid).toBe(true);
    });

    it('should require password field', () => {
      const password = component.loginForm.get('password');
      expect(password?.valid).toBe(false);
      expect(password?.hasError('required')).toBe(true);
    });

    it('should require minimum 6 characters for password', () => {
      const password = component.loginForm.get('password');
      password?.setValue('12345');
      expect(password?.hasError('minlength')).toBe(true);
    });

    it('should accept valid password', () => {
      const password = component.loginForm.get('password');
      password?.setValue('password123');
      expect(password?.valid).toBe(true);
    });

    it('should have valid form when all fields are valid', () => {
      component.loginForm.patchValue({
        usernameOrEmail: 'testuser',
        password: 'password123',
      });
      expect(component.loginForm.valid).toBe(true);
    });
  });

  describe('onSubmit()', () => {
    beforeEach(() => {
      component.loginForm.patchValue({
        usernameOrEmail: 'testuser',
        password: 'password123',
      });
    });

    it('should not submit if form is invalid', () => {
      component.loginForm.patchValue({
        usernameOrEmail: '',
        password: '',
      });
      component.onSubmit();
      expect(authService.login).not.toHaveBeenCalled();
    });

    it('should set isLoading to true when submitting', () => {
      const loginSubject = new Subject<LoginResponse>();
      authService.login.and.returnValue(loginSubject.asObservable());
      component.onSubmit();
      expect(component.isLoading()).toBe(true);
      loginSubject.complete();
    });

    it('should disable form when submitting', () => {
      const loginSubject = new Subject<LoginResponse>();
      authService.login.and.returnValue(loginSubject.asObservable());
      component.onSubmit();
      expect(component.loginForm.disabled).toBe(true);
      loginSubject.complete();
    });

    it('should clear error message when submitting', () => {
      component.errorMessage.set('Previous error');
      authService.login.and.returnValue(of(mockLoginResponse));
      component.onSubmit();
      expect(component.errorMessage()).toBe('');
    });

    it('should call authService.login with form values', () => {
      authService.login.and.returnValue(of(mockLoginResponse));
      component.onSubmit();
      expect(authService.login).toHaveBeenCalledWith({
        usernameOrEmail: 'testuser',
        password: 'password123',
      });
    });

    it('should navigate to /dashboard on successful login', fakeAsync(() => {
      authService.login.and.returnValue(of(mockLoginResponse));
      component.onSubmit();
      tick();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    }));

    it('should navigate to returnUrl if provided', fakeAsync(() => {
      const activatedRoute = TestBed.inject(ActivatedRoute);
      activatedRoute.snapshot.queryParams = { returnUrl: '/products' };
      authService.login.and.returnValue(of(mockLoginResponse));
      component.onSubmit();
      tick();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/products');
    }));

    it('should set isLoading to false after successful login', fakeAsync(() => {
      authService.login.and.returnValue(of(mockLoginResponse));
      component.onSubmit();
      tick();
      expect(component.isLoading()).toBe(false);
    }));

    it('should enable form after successful login', fakeAsync(() => {
      authService.login.and.returnValue(of(mockLoginResponse));
      component.onSubmit();
      tick();
      expect(component.loginForm.enabled).toBe(true);
    }));

    it('should handle login error with backend error message', fakeAsync(() => {
      const errorResponse = {
        error: { error: 'Invalid credentials' },
      };
      authService.login.and.returnValue(throwError(() => errorResponse));
      component.onSubmit();
      tick();
      expect(component.errorMessage()).toBe('Invalid credentials');
      expect(toastService.error).toHaveBeenCalledWith('Invalid credentials');
    }));

    it('should handle login error with default message', fakeAsync(() => {
      const errorResponse = { error: {} };
      authService.login.and.returnValue(throwError(() => errorResponse));
      component.onSubmit();
      tick();
      expect(component.errorMessage()).toBe('Login failed. Please check your credentials.');
      expect(toastService.error).toHaveBeenCalledWith(
        'Login failed. Please check your credentials.',
      );
    }));

    it('should set isLoading to false after error', fakeAsync(() => {
      const errorResponse = { error: { error: 'Error' } };
      authService.login.and.returnValue(throwError(() => errorResponse));
      component.onSubmit();
      tick();
      expect(component.isLoading()).toBe(false);
    }));

    it('should enable form after error', fakeAsync(() => {
      const errorResponse = { error: { error: 'Error' } };
      authService.login.and.returnValue(throwError(() => errorResponse));
      component.onSubmit();
      tick();
      expect(component.loginForm.enabled).toBe(true);
    }));
  });

  describe('Template Integration', () => {
    it('should display error message when errorMessage is set', () => {
      component.errorMessage.set('Test error message');
      fixture.detectChanges();
      const errorBanner = fixture.nativeElement.querySelector('.error-banner');
      expect(errorBanner).toBeTruthy();
      expect(errorBanner.textContent).toContain('Test error message');
    });

    it('should not display error banner when errorMessage is empty', () => {
      component.errorMessage.set('');
      fixture.detectChanges();
      const errorBanner = fixture.nativeElement.querySelector('.error-banner');
      expect(errorBanner).toBeFalsy();
    });

    it('should display "Logging in..." when isLoading is true', () => {
      component.isLoading.set(true);
      fixture.detectChanges();
      const button = fixture.nativeElement.querySelector('app-button');
      expect(button.textContent).toContain('Logging in...');
    });

    it('should display "Login" when isLoading is false', () => {
      component.isLoading.set(false);
      fixture.detectChanges();
      const button = fixture.nativeElement.querySelector('app-button');
      expect(button.textContent).toContain('Login');
    });

    it('should disable submit button when form is invalid', () => {
      component.loginForm.patchValue({
        usernameOrEmail: '',
        password: '',
      });
      fixture.detectChanges();
      const buttonDebug = fixture.debugElement.query(By.css('app-button'));
      const buttonCmp = buttonDebug.componentInstance;

      expect(buttonCmp.disabled()).toBeTrue();
    });

    it('should show validation error for usernameOrEmail when touched and invalid', () => {
      const usernameOrEmail = component.loginForm.get('usernameOrEmail');
      usernameOrEmail?.markAsTouched();
      fixture.detectChanges();
      const errorSpan = fixture.nativeElement.querySelector('.form-group:first-child .error');
      expect(errorSpan).toBeTruthy();
      expect(errorSpan.textContent).toContain('Username or Email is required (min 3 characters)');
    });

    it('should show validation error for password when touched and invalid', () => {
      const password = component.loginForm.get('password');
      password?.markAsTouched();
      fixture.detectChanges();
      const errorSpan = fixture.nativeElement.querySelector('.form-group:nth-child(2) .error');
      expect(errorSpan).toBeTruthy();
      expect(errorSpan.textContent).toContain('Password is required (min 6 characters)');
    });

    it('should have a link to signup page', () => {
      const signupLink = fixture.nativeElement.querySelector('a[routerLink="/auth/signup"]');
      expect(signupLink).toBeTruthy();
      expect(signupLink.textContent).toContain('Sign up');
    });

    it('should call onSubmit when form is submitted', () => {
      spyOn(component, 'onSubmit');
      const form = fixture.nativeElement.querySelector('form');
      form.dispatchEvent(new Event('submit'));
      expect(component.onSubmit).toHaveBeenCalled();
    });
  });
});
