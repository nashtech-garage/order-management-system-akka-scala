import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { By } from '@angular/platform-browser';
import { of, throwError, Subject } from 'rxjs';

import { Signup } from './signup';
import { AuthService } from '@features/auth/auth.service';
import { User } from '@shared/models/user.model';

describe('Signup', () => {
  let component: Signup;
  let fixture: ComponentFixture<Signup>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockUser: User = {
    id: 1,
    username: 'testuser',
    email: 'test@example.com',
    role: 'user',
    createdAt: '2024-01-01',
  };

  beforeEach(async () => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['register']);
    await TestBed.configureTestingModule({
      imports: [Signup],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    }).compileComponents();

    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router);
    spyOn(router, 'navigate');
    fixture = TestBed.createComponent(Signup);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  describe('Component Initialization', () => {
    it('should initialize with empty form', () => {
      expect(component.signupForm.value).toEqual({
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
      });
    });

    it('should initialize with isLoading as false', () => {
      expect(component.isLoading()).toBe(false);
    });

    it('should initialize with empty errorMessage', () => {
      expect(component.errorMessage()).toBe('');
    });

    it('should have an invalid form initially', () => {
      expect(component.signupForm.valid).toBe(false);
    });
  });

  describe('Form Validation', () => {
    it('should require username', () => {
      const username = component.signupForm.get('username');
      expect(username?.hasError('required')).toBe(true);
    });

    it('should require minimum 3 characters for username', () => {
      const username = component.signupForm.get('username');
      username?.setValue('ab');
      expect(username?.hasError('minlength')).toBe(true);
    });

    it('should validate email format', () => {
      const email = component.signupForm.get('email');
      email?.setValue('not-an-email');
      expect(email?.hasError('email')).toBe(true);
    });

    it('should require password', () => {
      const password = component.signupForm.get('password');
      expect(password?.hasError('required')).toBe(true);
    });

    it('should require minimum 6 characters for password', () => {
      const password = component.signupForm.get('password');
      password?.setValue('12345');
      expect(password?.hasError('minlength')).toBe(true);
    });

    it('should require confirmPassword', () => {
      const confirmPassword = component.signupForm.get('confirmPassword');
      expect(confirmPassword?.hasError('required')).toBe(true);
    });

    it('should mark form invalid when passwords do not match', () => {
      component.signupForm.patchValue({
        username: 'testuser',
        email: 'test@example.com',
        password: 'password123',
        confirmPassword: 'password456',
      });
      expect(component.signupForm.hasError('passwordMismatch')).toBe(true);
      expect(component.signupForm.valid).toBe(false);
    });

    it('should have valid form when all fields are valid and match', () => {
      component.signupForm.patchValue({
        username: 'testuser',
        email: 'test@example.com',
        password: 'password123',
        confirmPassword: 'password123',
      });
      expect(component.signupForm.valid).toBe(true);
    });
  });

  describe('onSubmit()', () => {
    beforeEach(() => {
      component.signupForm.patchValue({
        username: 'testuser',
        email: 'test@example.com',
        password: 'password123',
        confirmPassword: 'password123',
      });
    });

    it('should not submit if form is invalid', () => {
      component.signupForm.patchValue({
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
      });
      component.onSubmit();
      expect(authService.register).not.toHaveBeenCalled();
    });

    it('should set isLoading to true when submitting', () => {
      const registerSubject = new Subject<User>();
      authService.register.and.returnValue(registerSubject.asObservable());
      component.onSubmit();
      expect(component.isLoading()).toBe(true);
      registerSubject.complete();
    });

    it('should disable form when submitting', () => {
      const registerSubject = new Subject<User>();
      authService.register.and.returnValue(registerSubject.asObservable());
      component.onSubmit();
      expect(component.signupForm.disabled).toBe(true);
      registerSubject.complete();
    });

    it('should clear error message when submitting', () => {
      component.errorMessage.set('Previous error');
      authService.register.and.returnValue(of(mockUser));
      component.onSubmit();
      expect(component.errorMessage()).toBe('');
    });

    it('should call authService.register with user data', () => {
      authService.register.and.returnValue(of(mockUser));
      component.onSubmit();
      expect(authService.register).toHaveBeenCalledWith({
        username: 'testuser',
        email: 'test@example.com',
        password: 'password123',
      });
    });

    it('should navigate to /dashboard on successful signup', fakeAsync(() => {
      authService.register.and.returnValue(of(mockUser));
      component.onSubmit();
      tick();
      expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    }));

    it('should set isLoading to false after successful signup', fakeAsync(() => {
      authService.register.and.returnValue(of(mockUser));
      component.onSubmit();
      tick();
      expect(component.isLoading()).toBe(false);
    }));

    it('should enable form after successful signup', fakeAsync(() => {
      authService.register.and.returnValue(of(mockUser));
      component.onSubmit();
      tick();
      expect(component.signupForm.enabled).toBe(true);
    }));

    it('should handle signup error with backend message', fakeAsync(() => {
      const errorResponse = { error: { message: 'Email already exists' } };
      authService.register.and.returnValue(throwError(() => errorResponse));
      component.onSubmit();
      tick();
      expect(component.errorMessage()).toBe('Email already exists');
    }));

    it('should handle signup error with default message', fakeAsync(() => {
      const errorResponse = { error: {} };
      authService.register.and.returnValue(throwError(() => errorResponse));
      component.onSubmit();
      tick();
      expect(component.errorMessage()).toBe('Registration failed. Please try again.');
    }));

    it('should set isLoading to false after error', fakeAsync(() => {
      const errorResponse = { error: { message: 'Error' } };
      authService.register.and.returnValue(throwError(() => errorResponse));
      component.onSubmit();
      tick();
      expect(component.isLoading()).toBe(false);
    }));

    it('should enable form after error', fakeAsync(() => {
      const errorResponse = { error: { message: 'Error' } };
      authService.register.and.returnValue(throwError(() => errorResponse));
      component.onSubmit();
      tick();
      expect(component.signupForm.enabled).toBe(true);
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

    it('should display "Creating account..." when isLoading is true', () => {
      component.isLoading.set(true);
      fixture.detectChanges();
      const button = fixture.nativeElement.querySelector('app-button');
      expect(button.textContent).toContain('Creating account...');
    });

    it('should display "Sign Up" when isLoading is false', () => {
      component.isLoading.set(false);
      fixture.detectChanges();
      const button = fixture.nativeElement.querySelector('app-button');
      expect(button.textContent).toContain('Sign Up');
    });

    it('should disable submit button when form is invalid', () => {
      component.signupForm.patchValue({
        username: '',
        email: '',
        password: '',
        confirmPassword: '',
      });
      fixture.detectChanges();
      const buttonDebug = fixture.debugElement.query(By.css('app-button'));
      const buttonCmp = buttonDebug.componentInstance;
      expect(buttonCmp.disabled()).toBeTrue();
    });

    it('should show validation error for username when touched and invalid', () => {
      const username = component.signupForm.get('username');
      username?.markAsTouched();
      fixture.detectChanges();
      const errorSpan = fixture.nativeElement.querySelector('.form-group:first-child .error');
      expect(errorSpan).toBeTruthy();
      expect(errorSpan.textContent).toContain('Username is required (min 3 characters)');
    });

    it('should show validation error for email when touched and invalid', () => {
      const email = component.signupForm.get('email');
      email?.markAsTouched();
      email?.setValue('invalid');
      fixture.detectChanges();
      const errorSpan = fixture.nativeElement.querySelector('.form-group:nth-child(2) .error');
      expect(errorSpan).toBeTruthy();
      expect(errorSpan.textContent).toContain('Please enter a valid email');
    });

    it('should show validation error for password when touched and invalid', () => {
      const password = component.signupForm.get('password');
      password?.markAsTouched();
      fixture.detectChanges();
      const errorSpan = fixture.nativeElement.querySelector('.form-group:nth-child(3) .error');
      expect(errorSpan).toBeTruthy();
      expect(errorSpan.textContent).toContain('Password is required (min 6 characters)');
    });

    it('should show password mismatch error when confirmPassword touched', () => {
      component.signupForm.patchValue({
        password: 'password123',
        confirmPassword: 'password456',
      });
      const confirmPassword = component.signupForm.get('confirmPassword');
      confirmPassword?.markAsTouched();
      fixture.detectChanges();
      const errorSpan = fixture.nativeElement.querySelector('.form-group:nth-child(4) .error');
      expect(errorSpan).toBeTruthy();
      expect(errorSpan.textContent).toContain('Passwords do not match');
    });

    it('should have a link to login page', () => {
      const loginLink = fixture.nativeElement.querySelector('a[routerLink="/auth/login"]');
      expect(loginLink).toBeTruthy();
      expect(loginLink.textContent).toContain('Login');
    });

    it('should call onSubmit when form is submitted', () => {
      spyOn(component, 'onSubmit');
      const form = fixture.nativeElement.querySelector('form');
      form.dispatchEvent(new Event('submit'));
      expect(component.onSubmit).toHaveBeenCalled();
    });
  });
});
