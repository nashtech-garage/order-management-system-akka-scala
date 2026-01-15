import { Component, inject, signal, HostListener } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '@features/auth/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, CommonModule],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {
  private authService = inject(AuthService);
  private router = inject(Router);

  isDropdownOpen = signal(false);

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    const target = event.target as HTMLElement;
    const clickedInside = target.closest('.user-menu');

    if (!clickedInside && this.isDropdownOpen()) {
      this.closeDropdown();
    }
  }

  toggleDropdown() {
    this.isDropdownOpen.update((val) => !val);
  }

  closeDropdown() {
    this.isDropdownOpen.set(false);
  }

  onLogout() {
    this.closeDropdown();
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/auth/login']);
      },
      error: (error) => {
        console.error('Logout failed', error);
        // Even if logout API fails, still clear local state and redirect
        this.authService.removeToken();
        this.authService.currentUser.set(null);
        this.router.navigate(['/auth/login']);
      },
    });
  }
}
