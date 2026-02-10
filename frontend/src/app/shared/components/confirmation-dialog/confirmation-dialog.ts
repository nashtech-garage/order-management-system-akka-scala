import { Component, EventEmitter, Output, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ConfirmationDialogData {
  title?: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  type?: 'default' | 'danger' | 'warning' | 'info';
}

@Component({
  selector: 'app-confirmation-dialog',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './confirmation-dialog.html',
  styleUrls: ['./confirmation-dialog.scss'],
})
export class ConfirmationDialog {
  @Input() data: ConfirmationDialogData = {
    title: 'Confirm Action',
    message: '',
    confirmText: 'Confirm',
    cancelText: 'Cancel',
    type: 'default',
  };

  @Output() confirmed = new EventEmitter<boolean>();

  onConfirm(): void {
    this.confirmed.emit(true);
  }

  onCancel(): void {
    this.confirmed.emit(false);
  }

  getIconName(): string {
    switch (this.data.type) {
      case 'danger':
        return '⚠️';
      case 'warning':
        return '⚠️';
      case 'info':
        return 'ℹ️';
      default:
        return '❓';
    }
  }

  getIconClass(): string {
    switch (this.data.type) {
      case 'danger':
        return 'icon-danger';
      case 'warning':
        return 'icon-warning';
      case 'info':
        return 'icon-info';
      default:
        return 'icon-default';
    }
  }
}
