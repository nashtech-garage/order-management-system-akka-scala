import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserService } from '@core/services/user.service';
import { ToastService } from '@shared/services/toast.service';
import { ConfirmationDialogService } from '@shared/services/confirmation-dialog.service';
import {
  User,
  UpdateUserRequest,
  AccountStatusRequest,
  USER_STATUS_LABELS,
  USER_STATUS_COLORS,
  USER_ROLE_LABELS,
} from '@shared/models/user.model';

@Component({
  selector: 'app-user-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './user-detail.html',
  styleUrls: ['./user-detail.scss'],
})
export class UserDetail implements OnInit {
  private userService = inject(UserService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private fb = inject(FormBuilder);
  private toastService = inject(ToastService);
  private confirmationDialog = inject(ConfirmationDialogService);

  user = signal<User | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  editMode = signal(false);
  editForm!: FormGroup;

  statusLabels = USER_STATUS_LABELS;
  statusColors = USER_STATUS_COLORS;
  roleLabels = USER_ROLE_LABELS;

  ngOnInit() {
    const userId = this.route.snapshot.params['id'];
    // Check if URL ends with /edit to enable edit mode
    const isEditRoute = this.router.url.endsWith('/edit');
    this.editMode.set(isEditRoute);
    this.loadUser(userId);
    this.initForm();
  }

  initForm() {
    this.editForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      role: ['', Validators.required],
      status: ['', Validators.required],
      phoneNumber: [''],
    });
  }

  loadUser(id: number) {
    this.loading.set(true);
    this.error.set(null);

    this.userService.getUserById(id).subscribe({
      next: (user) => {
        this.user.set(user);
        this.patchForm(user);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load user: ' + err.message);
        this.loading.set(false);
      },
    });
  }

  patchForm(user: User) {
    this.editForm.patchValue({
      email: user.email,
      role: user.role,
      status: user.status || 'active',
      phoneNumber: user.phoneNumber || '',
    });
  }

  toggleEditMode() {
    this.editMode.update((mode) => !mode);
    if (!this.editMode() && this.user()) {
      this.patchForm(this.user()!);
    }
  }

  saveChanges() {
    if (this.editForm.invalid) {
      this.toastService.warning('Please fill all required fields correctly');
      return;
    }

    const userId = this.user()!.id;
    const formValue = this.editForm.value;
    const request: UpdateUserRequest = {
      email: formValue.email,
      role: formValue.role,
      status: formValue.status,
      phoneNumber: formValue.phoneNumber || undefined,
    };

    this.loading.set(true);

    this.userService.updateUser(userId, request).subscribe({
      next: (response) => {
        this.toastService.success(response.message);
        this.editMode.set(false);
        this.loadUser(userId);
      },
      error: (err) => {
        this.toastService.error('Failed to update user: ' + (err.error?.error || err.message));
        this.loading.set(false);
      },
    });
  }

  updateStatus(newStatus: string) {
    const reason = prompt(`Why are you changing status to "${newStatus}"?`);
    if (reason === null) return;

    const request: AccountStatusRequest = {
      status: newStatus as 'active' | 'locked',
      reason: reason || undefined,
    };

    this.userService.updateAccountStatus(this.user()!.id, request).subscribe({
      next: (response) => {
        this.toastService.success(response.message);
        this.loadUser(this.user()!.id);
      },
      error: (err) => {
        this.toastService.error('Failed to update status: ' + (err.error?.error || err.message));
      },
    });
  }

  deleteUser() {
    const username = this.user()!.username;
    this.confirmationDialog
      .confirmDelete(`Delete user "${username}"? This cannot be undone!`, 'Delete User')
      .subscribe((confirmed) => {
        if (confirmed) {
          this.userService.deleteUser(this.user()!.id).subscribe({
            next: (response) => {
              this.toastService.success(response.message);
              this.router.navigate(['/users']);
            },
            error: (err) => {
              this.toastService.error(
                'Failed to delete user: ' + (err.error?.error || err.message),
              );
            },
          });
        }
      });
  }

  formatDate(date?: string): string {
    if (!date) return 'Never';
    return new Date(date).toLocaleString();
  }

  getFullName(): string {
    const user = this.user();
    if (!user) return '';
    return user.username;
  }
}
