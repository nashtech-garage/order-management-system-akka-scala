import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink],
  template: `
    <header class="header">
      <div class="header-content">
        <div class="logo">
          <h1>Order Management System</h1>
        </div>
        <nav class="nav">
          <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
          <a routerLink="/orders" routerLinkActive="active">Orders</a>
          <a routerLink="/products" routerLinkActive="active">Products</a>
          <a routerLink="/customers" routerLinkActive="active">Customers</a>
        </nav>
        <div class="user-menu">
          <button class="user-btn">User Menu</button>
        </div>
      </div>
    </header>
  `,
  styles: [
    `
      .header {
        background-color: #1f2937;
        color: white;
        padding: 1rem 2rem;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      }

      .header-content {
        display: flex;
        justify-content: space-between;
        align-items: center;
        max-width: 1200px;
        margin: 0 auto;
      }

      .logo h1 {
        font-size: 1.25rem;
        font-weight: 600;
        margin: 0;
      }

      .nav {
        display: flex;
        gap: 1.5rem;
      }

      .nav a {
        color: #d1d5db;
        text-decoration: none;
        font-weight: 500;
        transition: color 0.2s;

        &:hover {
          color: white;
        }

        &.active {
          color: #3b82f6;
        }
      }

      .user-btn {
        background-color: #374151;
        color: white;
        border: none;
        padding: 0.5rem 1rem;
        border-radius: 0.375rem;
        cursor: pointer;

        &:hover {
          background-color: #4b5563;
        }
      }
    `,
  ],
})
export class HeaderComponent {}
