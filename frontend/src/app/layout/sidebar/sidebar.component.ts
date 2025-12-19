import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink],
  template: `
    <aside class="sidebar">
      <nav class="sidebar-nav">
        <div class="nav-section">
          <h3 class="nav-title">Main</h3>
          <a routerLink="/dashboard" routerLinkActive="active" class="nav-item">
            <span>📊</span> Dashboard
          </a>
        </div>

        <div class="nav-section">
          <h3 class="nav-title">Orders</h3>
          <a routerLink="/orders" routerLinkActive="active" class="nav-item">
            <span>📦</span> All Orders
          </a>
          <a routerLink="/orders/create" routerLinkActive="active" class="nav-item">
            <span>➕</span> Create Order
          </a>
        </div>

        <div class="nav-section">
          <h3 class="nav-title">Catalog</h3>
          <a routerLink="/products" routerLinkActive="active" class="nav-item">
            <span>🏷️</span> Products
          </a>
        </div>

        <div class="nav-section">
          <h3 class="nav-title">Management</h3>
          <a routerLink="/customers" routerLinkActive="active" class="nav-item">
            <span>👥</span> Customers
          </a>
          <a routerLink="/users" routerLinkActive="active" class="nav-item">
            <span>👤</span> Users
          </a>
          <a routerLink="/payments" routerLinkActive="active" class="nav-item">
            <span>💳</span> Payments
          </a>
        </div>

        <div class="nav-section">
          <h3 class="nav-title">Analytics</h3>
          <a routerLink="/reports" routerLinkActive="active" class="nav-item">
            <span>📈</span> Reports
          </a>
        </div>
      </nav>
    </aside>
  `,
  styles: [
    `
      .sidebar {
        background-color: #f9fafb;
        width: 250px;
        height: 100%;
        overflow-y: auto;
        border-right: 1px solid #e5e7eb;
      }

      .sidebar-nav {
        padding: 1rem;
      }

      .nav-section {
        margin-bottom: 1.5rem;
      }

      .nav-title {
        font-size: 0.75rem;
        font-weight: 600;
        text-transform: uppercase;
        color: #6b7280;
        margin-bottom: 0.5rem;
        padding: 0 0.75rem;
      }

      .nav-item {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.75rem;
        color: #374151;
        text-decoration: none;
        border-radius: 0.375rem;
        transition: all 0.2s;

        &:hover {
          background-color: #e5e7eb;
        }

        &.active {
          background-color: #dbeafe;
          color: #1e40af;
          font-weight: 500;
        }

        span {
          font-size: 1.25rem;
        }
      }
    `,
  ],
})
export class SidebarComponent {}
