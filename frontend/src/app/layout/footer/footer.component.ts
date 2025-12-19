import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  standalone: true,
  template: `
    <footer class="footer">
      <div class="footer-content">
        <p>&copy; {{ currentYear }} Order Management System. All rights reserved.</p>
      </div>
    </footer>
  `,
  styles: [
    `
      .footer {
        background-color: #1f2937;
        color: #9ca3af;
        padding: 1.5rem 2rem;
        margin-top: auto;
      }

      .footer-content {
        text-align: center;
        max-width: 1200px;
        margin: 0 auto;
      }

      p {
        margin: 0;
        font-size: 0.875rem;
      }
    `,
  ],
})
export class FooterComponent {
  currentYear = new Date().getFullYear();
}
