import {
  Injectable,
  ApplicationRef,
  createComponent,
  EnvironmentInjector,
  inject,
} from '@angular/core';
import { Observable, Subject } from 'rxjs';
import {
  ConfirmationDialog,
  ConfirmationDialogData,
} from '@shared/components/confirmation-dialog/confirmation-dialog';

@Injectable({
  providedIn: 'root',
})
export class ConfirmationDialogService {
  private appRef = inject(ApplicationRef);
  private injector = inject(EnvironmentInjector);

  /**
   * Open a confirmation dialog and return an observable that emits true if confirmed, false if cancelled
   */
  confirm(data: ConfirmationDialogData): Observable<boolean> {
    const result$ = new Subject<boolean>();

    // Set defaults
    const dialogData: ConfirmationDialogData = {
      title: data.title || 'Confirm Action',
      message: data.message,
      confirmText: data.confirmText || 'Confirm',
      cancelText: data.cancelText || 'Cancel',
      type: data.type || 'default',
    };

    // Create component
    const componentRef = createComponent(ConfirmationDialog, {
      environmentInjector: this.injector,
    });

    // Set input data
    componentRef.instance.data = dialogData;

    // Subscribe to output
    componentRef.instance.confirmed.subscribe((confirmed: boolean) => {
      result$.next(confirmed);
      result$.complete();

      // Remove component from DOM with animation delay
      setTimeout(() => {
        this.appRef.detachView(componentRef.hostView);
        componentRef.destroy();
      }, 300);
    });

    // Attach to application
    this.appRef.attachView(componentRef.hostView);
    const domElem = (componentRef.hostView as unknown as { rootNodes: unknown[] })
      .rootNodes[0] as HTMLElement;
    document.body.appendChild(domElem);

    return result$.asObservable();
  }

  /**
   * Show a default confirmation dialog
   */
  confirmAction(message: string, title?: string): Observable<boolean> {
    return this.confirm({
      title: title || 'Confirm Action',
      message,
      type: 'default',
    });
  }

  /**
   * Show a danger/delete confirmation dialog
   */
  confirmDelete(message: string, title?: string): Observable<boolean> {
    return this.confirm({
      title: title || 'Confirm Delete',
      message,
      type: 'danger',
      confirmText: 'Delete',
    });
  }

  /**
   * Show a warning confirmation dialog
   */
  confirmWarning(message: string, title?: string): Observable<boolean> {
    return this.confirm({
      title: title || 'Warning',
      message,
      type: 'warning',
    });
  }
}
