import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { UserService } from '@core/services/user.service';
import { AuthService } from '@features/auth/auth.service';
import {
  User,
  UserSearchRequest,
  UserStatsResponse,
  USER_STATUS_LABELS,
  USER_STATUS_COLORS,
  USER_ROLE_LABELS,
} from '@shared/models/user.model';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './user-list.html',
  styleUrls: ['./user-list.scss'],
})
export class UserList implements OnInit {
  private userService = inject(UserService);
  private authService = inject(AuthService);

  users = signal<User[]>([]);
  stats = signal<UserStatsResponse | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);
  selectedUsers = signal<number[]>([]);

  // Search filters
  searchQuery = signal('');
  filterRole = signal('');
  filterStatus = signal('');
  currentPage = signal(1);
  pageSize = signal(20);
  totalUsers = signal(0);

  // Constants for templates
  statusLabels = USER_STATUS_LABELS;
  statusColors = USER_STATUS_COLORS;
  roleLabels = USER_ROLE_LABELS;

  // Expose Math to template
  Math = Math;

  ngOnInit() {
    this.loadUsers();
    this.loadStats();
  }

  loadUsers() {
    this.loading.set(true);
    this.error.set(null);

    const request: UserSearchRequest = {
      query: this.searchQuery() || undefined,
      role: this.filterRole() || undefined,
      status: this.filterStatus() || undefined,
      offset: (this.currentPage() - 1) * this.pageSize(),
      limit: this.pageSize(),
    };

    this.userService.searchUsers(request).subscribe({
      next: (response) => {
        this.users.set(response.users);
        this.totalUsers.set(response.total);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load users: ' + err.message);
        this.loading.set(false);
      },
    });
  }

  loadStats() {
    this.userService.getUserStats().subscribe({
      next: (stats) => {
        this.stats.set(stats);
      },
      error: (err) => {
        console.error('Failed to load stats:', err);
      },
    });
  }

  onSearch() {
    this.currentPage.set(1);
    this.loadUsers();
  }

  onFilterChange() {
    this.currentPage.set(1);
    this.loadUsers();
  }

  clearFilters() {
    this.searchQuery.set('');
    this.filterRole.set('');
    this.filterStatus.set('');
    this.currentPage.set(1);
    this.loadUsers();
  }

  toggleUserSelection(userId: number) {
    const selected = this.selectedUsers();
    if (selected.includes(userId)) {
      this.selectedUsers.set(selected.filter((id) => id !== userId));
    } else {
      this.selectedUsers.set([...selected, userId]);
    }
  }

  toggleAllUsers() {
    const currentUser = this.authService.currentUser();
    const selectableUsers = this.users().filter((u) => !currentUser || u.id !== currentUser.id);

    if (this.selectedUsers().length === selectableUsers.length) {
      this.selectedUsers.set([]);
    } else {
      this.selectedUsers.set(selectableUsers.map((u) => u.id));
    }
  }

  bulkActivate() {
    if (this.selectedUsers().length === 0) return;

    if (confirm(`Activate ${this.selectedUsers().length} users?`)) {
      this.userService
        .bulkAction({
          userIds: this.selectedUsers(),
          action: 'activate',
        })
        .subscribe({
          next: (response) => {
            alert(response.message);
            this.selectedUsers.set([]);
            this.loadUsers();
            this.loadStats();
          },
          error: (err) => {
            alert('Failed: ' + err.error?.error || err.message);
          },
        });
    }
  }

  bulkSuspend() {
    if (this.selectedUsers().length === 0) return;

    if (confirm(`Suspend ${this.selectedUsers().length} users?`)) {
      this.userService
        .bulkAction({
          userIds: this.selectedUsers(),
          action: 'suspend',
        })
        .subscribe({
          next: (response) => {
            alert(response.message);
            this.selectedUsers.set([]);
            this.loadUsers();
            this.loadStats();
          },
          error: (err) => {
            alert('Failed: ' + err.error?.error || err.message);
          },
        });
    }
  }

  bulkDelete() {
    if (this.selectedUsers().length === 0) return;

    const currentUser = this.authService.currentUser();
    if (currentUser && this.selectedUsers().includes(currentUser.id)) {
      alert('You cannot delete your own account!');
      return;
    }

    if (confirm(`Delete ${this.selectedUsers().length} users? This cannot be undone!`)) {
      this.userService
        .bulkAction({
          userIds: this.selectedUsers(),
          action: 'delete',
        })
        .subscribe({
          next: (response) => {
            alert(response.message);
            this.selectedUsers.set([]);
            this.loadUsers();
            this.loadStats();
          },
          error: (err) => {
            alert('Failed: ' + err.error?.error || err.message);
          },
        });
    }
  }

  deleteUser(user: User) {
    const currentUser = this.authService.currentUser();
    if (currentUser && user.id === currentUser.id) {
      alert('You cannot delete your own account!');
      return;
    }

    if (confirm(`Delete user "${user.username}"? This cannot be undone!`)) {
      this.userService.deleteUser(user.id).subscribe({
        next: (response) => {
          alert(response.message);
          this.loadUsers();
          this.loadStats();
        },
        error: (err) => {
          alert('Failed to delete user: ' + (err.error?.error || err.message));
        },
      });
    }
  }

  nextPage() {
    if (this.currentPage() * this.pageSize() < this.totalUsers()) {
      this.currentPage.update((p) => p + 1);
      this.loadUsers();
    }
  }

  previousPage() {
    if (this.currentPage() > 1) {
      this.currentPage.update((p) => p - 1);
      this.loadUsers();
    }
  }

  get totalPages(): number {
    return Math.ceil(this.totalUsers() / this.pageSize());
  }

  getFullName(user: User): string {
    return user.username;
  }

  isCurrentUser(user: User): boolean {
    const currentUser = this.authService.currentUser();
    return currentUser ? user.id === currentUser.id : false;
  }

  formatDate(date?: string): string {
    if (!date) return 'Never';
    return new Date(date).toLocaleString();
  }
}
