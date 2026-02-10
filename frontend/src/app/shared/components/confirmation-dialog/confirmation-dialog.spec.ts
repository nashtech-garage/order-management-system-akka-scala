import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationDialog, ConfirmationDialogData } from './confirmation-dialog';

describe('ConfirmationDialog', () => {
  let component: ConfirmationDialog;
  let fixture: ComponentFixture<ConfirmationDialog>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmationDialog],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmationDialog);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should have default values', () => {
      expect(component.data.title).toBe('Confirm Action');
      expect(component.data.message).toBe('');
      expect(component.data.confirmText).toBe('Confirm');
      expect(component.data.cancelText).toBe('Cancel');
      expect(component.data.type).toBe('default');
    });

    it('should accept custom data', () => {
      const customData: ConfirmationDialogData = {
        title: 'Delete Item',
        message: 'Are you sure?',
        confirmText: 'Delete',
        cancelText: 'Keep',
        type: 'danger',
      };
      component.data = customData;
      fixture.detectChanges();

      expect(component.data.title).toBe('Delete Item');
      expect(component.data.message).toBe('Are you sure?');
      expect(component.data.confirmText).toBe('Delete');
      expect(component.data.cancelText).toBe('Keep');
      expect(component.data.type).toBe('danger');
    });
  });

  describe('onConfirm', () => {
    it('should emit true when confirmed', (done) => {
      component.confirmed.subscribe((result) => {
        expect(result).toBe(true);
        done();
      });

      component.onConfirm();
    });
  });

  describe('onCancel', () => {
    it('should emit false when cancelled', (done) => {
      component.confirmed.subscribe((result) => {
        expect(result).toBe(false);
        done();
      });

      component.onCancel();
    });
  });

  describe('getIconName', () => {
    it('should return danger icon for danger type', () => {
      component.data.type = 'danger';
      expect(component.getIconName()).toBe('⚠️');
    });

    it('should return warning icon for warning type', () => {
      component.data.type = 'warning';
      expect(component.getIconName()).toBe('⚠️');
    });

    it('should return info icon for info type', () => {
      component.data.type = 'info';
      expect(component.getIconName()).toBe('ℹ️');
    });

    it('should return default icon for default type', () => {
      component.data.type = 'default';
      expect(component.getIconName()).toBe('❓');
    });
  });

  describe('getIconClass', () => {
    it('should return icon-danger for danger type', () => {
      component.data.type = 'danger';
      expect(component.getIconClass()).toBe('icon-danger');
    });

    it('should return icon-warning for warning type', () => {
      component.data.type = 'warning';
      expect(component.getIconClass()).toBe('icon-warning');
    });

    it('should return icon-info for info type', () => {
      component.data.type = 'info';
      expect(component.getIconClass()).toBe('icon-info');
    });

    it('should return icon-default for default type', () => {
      component.data.type = 'default';
      expect(component.getIconClass()).toBe('icon-default');
    });
  });

  describe('DOM rendering', () => {
    it('should display title', () => {
      component.data.title = 'Test Title';
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const title = compiled.querySelector('.dialog-title');

      expect(title?.textContent).toContain('Test Title');
    });

    it('should display message', () => {
      component.data.message = 'Test message';
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const message = compiled.querySelector('.dialog-message');

      expect(message?.textContent).toContain('Test message');
    });

    it('should display confirm button with custom text', () => {
      component.data.confirmText = 'Yes, Delete';
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const buttons = compiled.querySelectorAll('button');
      const confirmButton = Array.from(buttons).find((btn) =>
        btn.textContent?.includes('Yes, Delete'),
      );

      expect(confirmButton).toBeTruthy();
    });

    it('should display cancel button with custom text', () => {
      component.data.cancelText = 'No, Keep';
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const buttons = compiled.querySelectorAll('button');
      const cancelButton = Array.from(buttons).find((btn) => btn.textContent?.includes('No, Keep'));

      expect(cancelButton).toBeTruthy();
    });

    it('should call onConfirm when confirm button is clicked', () => {
      spyOn(component, 'onConfirm');
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const buttons = compiled.querySelectorAll('button');
      const confirmButton = Array.from(buttons).find((btn) =>
        btn.textContent?.trim().includes(component.data.confirmText || 'Confirm'),
      ) as HTMLButtonElement;

      confirmButton.click();

      expect(component.onConfirm).toHaveBeenCalled();
    });

    it('should call onCancel when cancel button is clicked', () => {
      spyOn(component, 'onCancel');
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      const buttons = compiled.querySelectorAll('button');
      const cancelButton = Array.from(buttons).find((btn) =>
        btn.textContent?.trim().includes(component.data.cancelText || 'Cancel'),
      ) as HTMLButtonElement;

      cancelButton.click();

      expect(component.onCancel).toHaveBeenCalled();
    });
  });
});
