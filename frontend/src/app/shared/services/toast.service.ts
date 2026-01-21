import { Injectable, inject } from '@angular/core';
import { ToastrService } from 'ngx-toastr';

@Injectable({
  providedIn: 'root',
})
export class ToastService {
  private toastr = inject(ToastrService);

  success(message: string, title?: string) {
    this.toastr.success(message, title, {
      timeOut: 5000,
    });
  }

  error(message: string, title?: string) {
    this.toastr.error(message, title, {
      timeOut: 7000,
    });
  }

  warning(message: string, title?: string) {
    this.toastr.warning(message, title, {
      timeOut: 6000,
    });
  }

  info(message: string, title?: string) {
    this.toastr.info(message, title, {
      timeOut: 5000,
    });
  }

  clear() {
    this.toastr.clear();
  }
}
