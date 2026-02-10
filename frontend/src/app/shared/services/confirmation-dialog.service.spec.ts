import { TestBed } from '@angular/core/testing';
import { ConfirmationDialogService } from './confirmation-dialog.service';

describe('ConfirmationDialogService', () => {
  let service: ConfirmationDialogService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ConfirmationDialogService],
    });
    service = TestBed.inject(ConfirmationDialogService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('confirm', () => {
    it('should return an observable', (done) => {
      const result$ = service.confirm({
        message: 'Test message',
      });

      expect(result$).toBeDefined();
      expect(result$.subscribe).toBeDefined();

      // Clean up - simulate cancel to close dialog
      setTimeout(() => {
        const backdrop = document.querySelector('.dialog-backdrop') as HTMLElement;
        if (backdrop) {
          backdrop.click();
        }
        done();
      }, 100);
    });

    it('should create confirmation dialog with default values', (done) => {
      service
        .confirm({
          message: 'Test message',
        })
        .subscribe();

      setTimeout(() => {
        const dialog = document.querySelector('.confirmation-dialog');
        expect(dialog).toBeTruthy();

        const title = document.querySelector('.dialog-title');
        expect(title?.textContent).toContain('Confirm Action');

        const message = document.querySelector('.dialog-message');
        expect(message?.textContent).toContain('Test message');

        // Clean up
        const backdrop = document.querySelector('.dialog-backdrop') as HTMLElement;
        if (backdrop) backdrop.click();
        done();
      }, 100);
    });

    it('should create confirmation dialog with custom values', (done) => {
      service
        .confirm({
          title: 'Custom Title',
          message: 'Custom message',
          confirmText: 'Yes',
          cancelText: 'No',
          type: 'danger',
        })
        .subscribe();

      setTimeout(() => {
        const title = document.querySelector('.dialog-title');
        expect(title?.textContent).toContain('Custom Title');

        const message = document.querySelector('.dialog-message');
        expect(message?.textContent).toContain('Custom message');

        // Clean up
        const backdrop = document.querySelector('.dialog-backdrop') as HTMLElement;
        if (backdrop) backdrop.click();
        done();
      }, 100);
    });

    it('should emit true when confirmed', (done) => {
      service
        .confirm({
          message: 'Test message',
        })
        .subscribe((result) => {
          expect(result).toBe(true);
          done();
        });

      setTimeout(() => {
        const buttons = document.querySelectorAll('.dialog-actions button');
        const confirmButton = Array.from(buttons).find((btn) =>
          btn.textContent?.includes('Confirm'),
        ) as HTMLButtonElement;
        confirmButton?.click();
      }, 100);
    });

    it('should emit false when cancelled', (done) => {
      service
        .confirm({
          message: 'Test message',
        })
        .subscribe((result) => {
          expect(result).toBe(false);
          done();
        });

      setTimeout(() => {
        const buttons = document.querySelectorAll('.dialog-actions button');
        const cancelButton = Array.from(buttons).find((btn) =>
          btn.textContent?.includes('Cancel'),
        ) as HTMLButtonElement;
        cancelButton?.click();
      }, 100);
    });

    it('should remove dialog from DOM after closing', (done) => {
      service
        .confirm({
          message: 'Test message',
        })
        .subscribe();

      setTimeout(() => {
        const buttons = document.querySelectorAll('.dialog-actions button');
        const cancelButton = Array.from(buttons).find((btn) =>
          btn.textContent?.includes('Cancel'),
        ) as HTMLButtonElement;
        cancelButton?.click();

        // Wait for animation
        setTimeout(() => {
          const dialog = document.querySelector('.confirmation-dialog');
          expect(dialog).toBeFalsy();
          done();
        }, 350);
      }, 100);
    });
  });

  describe('confirmAction', () => {
    it('should create dialog with default type', (done) => {
      service.confirmAction('Do you want to continue?').subscribe();

      setTimeout(() => {
        const message = document.querySelector('.dialog-message');
        expect(message?.textContent).toContain('Do you want to continue?');

        const title = document.querySelector('.dialog-title');
        expect(title?.textContent).toContain('Confirm Action');

        // Clean up
        const backdrop = document.querySelector('.dialog-backdrop') as HTMLElement;
        if (backdrop) backdrop.click();
        done();
      }, 100);
    });

    it('should create dialog with custom title', (done) => {
      service.confirmAction('Do you want to continue?', 'Custom Title').subscribe();

      setTimeout(() => {
        const title = document.querySelector('.dialog-title');
        expect(title?.textContent).toContain('Custom Title');

        // Clean up
        const backdrop = document.querySelector('.dialog-backdrop') as HTMLElement;
        if (backdrop) backdrop.click();
        done();
      }, 100);
    });
  });

  describe('confirmDelete', () => {
    it('should create dialog with danger type', (done) => {
      service.confirmDelete('Delete this item?').subscribe();

      setTimeout(() => {
        const title = document.querySelector('.dialog-title');
        expect(title?.textContent).toContain('Confirm Delete');

        const icon = document.querySelector('.dialog-icon');
        expect(icon?.classList.contains('icon-danger')).toBe(true);

        // Clean up
        const backdrop = document.querySelector('.dialog-backdrop') as HTMLElement;
        if (backdrop) backdrop.click();
        done();
      }, 100);
    });

    it('should have Delete as confirm button text', (done) => {
      service.confirmDelete('Delete this item?').subscribe();

      setTimeout(() => {
        const buttons = document.querySelectorAll('.dialog-actions button');
        const confirmButton = Array.from(buttons).find((btn) =>
          btn.textContent?.includes('Delete'),
        );
        expect(confirmButton).toBeTruthy();

        // Clean up
        const backdrop = document.querySelector('.dialog-backdrop') as HTMLElement;
        if (backdrop) backdrop.click();
        done();
      }, 100);
    });
  });

  describe('confirmWarning', () => {
    it('should create dialog with warning type', (done) => {
      service.confirmWarning('This action cannot be undone!').subscribe();

      setTimeout(() => {
        const title = document.querySelector('.dialog-title');
        expect(title?.textContent).toContain('Warning');

        const icon = document.querySelector('.dialog-icon');
        expect(icon?.classList.contains('icon-warning')).toBe(true);

        // Clean up
        const backdrop = document.querySelector('.dialog-backdrop') as HTMLElement;
        if (backdrop) backdrop.click();
        done();
      }, 100);
    });
  });

  afterEach(() => {
    // Clean up any remaining dialogs
    const dialogs = document.querySelectorAll('.confirmation-dialog, .dialog-backdrop');
    dialogs.forEach((dialog) => dialog.remove());
  });
});
