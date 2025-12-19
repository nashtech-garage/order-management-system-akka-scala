import { Component } from '@angular/core';

@Component({
  selector: 'app-order-list',
  standalone: true,
  template: `
    <div class="order-list">
      <h1>Orders</h1>
      <p>Order list will be displayed here.</p>
    </div>
  `,
  styles: [
    `
      .order-list {
        padding: 2rem;
      }

      h1 {
        font-size: 2rem;
        font-weight: 700;
        color: #1f2937;
        margin-bottom: 1rem;
      }

      p {
        color: #6b7280;
        font-size: 1rem;
      }
    `,
  ],
})
export class OrderList {}
