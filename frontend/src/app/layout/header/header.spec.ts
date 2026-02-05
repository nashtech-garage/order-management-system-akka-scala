import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Header } from './header';

describe('Header', () => {
  let component: Header;
  let fixture: ComponentFixture<Header>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display system title', () => {
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.logo h1')?.textContent).toContain('Order Management System');
  });

  it('should display navigation links', () => {
    component.isDropdownOpen.set(true);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    const dropdownLink = compiled.querySelector('.dropdown-item[routerLink="/profile"]');
    expect(dropdownLink).toBeTruthy();
  });
});
