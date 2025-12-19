import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-button',
  standalone: true,
  template: `
    <button 
      [type]="type()" 
      [disabled]="disabled()"
      [class]="buttonClass()"
      (click)="clicked.emit()">
      <ng-content></ng-content>
    </button>
  `,
  styles: [`
    button {
      padding: 0.5rem 1rem;
      border-radius: 0.375rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
      
      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      
      &.primary {
        background-color: #3b82f6;
        color: white;
        border: none;
        
        &:hover:not(:disabled) {
          background-color: #2563eb;
        }
      }
      
      &.secondary {
        background-color: #6b7280;
        color: white;
        border: none;
        
        &:hover:not(:disabled) {
          background-color: #4b5563;
        }
      }
      
      &.danger {
        background-color: #ef4444;
        color: white;
        border: none;
        
        &:hover:not(:disabled) {
          background-color: #dc2626;
        }
      }
    }
  `]
})
export class ButtonComponent {
  type = input<'button' | 'submit' | 'reset'>('button');
  variant = input<'primary' | 'secondary' | 'danger'>('primary');
  disabled = input<boolean>(false);
  clicked = output<void>();
  
  buttonClass = () => this.variant();
}
