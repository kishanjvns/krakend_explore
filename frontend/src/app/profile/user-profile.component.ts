import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { API_BASE } from '../core/api.config';
import { UserResponse } from '../core/models';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  template: `
    <div class="max-w-2xl mx-auto space-y-6">

      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-slate-800">My Profile</h1>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else if (!user()) {
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
          <svg class="w-12 h-12 mx-auto mb-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
          </svg>
          <h3 class="font-semibold text-slate-700 mb-1">Profile not available</h3>
          <p class="text-sm text-slate-400">Your profile data could not be loaded.</p>
        </div>
      } @else {

        <!-- Avatar + Name -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <div class="flex items-center space-x-5">
            <div class="w-20 h-20 rounded-2xl bg-gradient-to-tr from-indigo-500 to-purple-500 flex items-center justify-center text-white font-bold text-3xl shrink-0">
              {{ (user()!.firstName || auth.userName).charAt(0).toUpperCase() }}
            </div>
            <div>
              <h2 class="text-xl font-bold text-slate-800">{{ user()!.firstName }} {{ user()!.lastName }}</h2>
              <p class="text-indigo-600 font-medium text-sm mt-0.5">{{ user()!.userType || user()!.role }}</p>
              <div class="flex items-center space-x-2 mt-2">
                @if (user()!.active) {
                  <span class="inline-flex items-center space-x-1 text-xs bg-emerald-50 text-emerald-600 px-2.5 py-1 rounded-full font-medium">
                    <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
                    <span>Active</span>
                  </span>
                }
              </div>
            </div>
          </div>
        </div>

        <!-- Details -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 class="font-semibold text-slate-800 mb-4">Account Details</h3>
          <div class="space-y-4">
            <div class="flex items-start justify-between py-3 border-b border-slate-100">
              <div>
                <p class="text-xs text-slate-400 font-medium uppercase tracking-wide">Full Name</p>
                <p class="text-sm font-medium text-slate-700 mt-0.5">{{ user()!.firstName }} {{ user()!.lastName }}</p>
              </div>
            </div>

            <div class="flex items-start justify-between py-3 border-b border-slate-100">
              <div>
                <p class="text-xs text-slate-400 font-medium uppercase tracking-wide">Email</p>
                <p class="text-sm font-medium text-slate-700 mt-0.5">{{ user()!.email }}</p>
              </div>
            </div>

            <div class="flex items-start justify-between py-3 border-b border-slate-100">
              <div>
                <p class="text-xs text-slate-400 font-medium uppercase tracking-wide">Account Type</p>
                <p class="text-sm font-medium text-slate-700 mt-0.5">{{ user()!.userType || user()!.role }}</p>
              </div>
            </div>

            <div class="flex items-start justify-between py-3 border-b border-slate-100">
              <div>
                <p class="text-xs text-slate-400 font-medium uppercase tracking-wide">User ID</p>
                <p class="text-sm font-mono text-slate-500 mt-0.5">{{ user()!.id }}</p>
              </div>
            </div>

            @if (user()!.contacts && user()!.contacts.length > 0) {
              <div class="py-3 border-b border-slate-100">
                <p class="text-xs text-slate-400 font-medium uppercase tracking-wide mb-2">Contacts</p>
                <div class="space-y-1.5">
                  @for (contact of user()!.contacts; track contact.contactValue) {
                    <div class="flex items-center space-x-2">
                      <span class="text-xs px-2 py-0.5 bg-slate-100 text-slate-500 rounded-full font-medium">{{ contact.contactType }}</span>
                      <span class="text-sm text-slate-700">{{ contact.contactValue }}</span>
                      @if (contact.isPrimary) {
                        <span class="text-xs px-2 py-0.5 bg-indigo-50 text-indigo-600 rounded-full">Primary</span>
                      }
                    </div>
                  }
                </div>
              </div>
            }
          </div>
        </div>

        <!-- Danger Zone -->
        <div class="bg-white rounded-2xl border border-red-200 shadow-sm p-6">
          <h3 class="font-semibold text-red-600 mb-2">Danger Zone</h3>
          <p class="text-sm text-slate-500 mb-4">Permanently delete your account and all associated data. This action cannot be undone.</p>

          @if (deleteError()) {
            <div class="mb-3 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm">
              {{ deleteError() }}
            </div>
          }

          @if (!confirmDelete()) {
            <button (click)="confirmDelete.set(true)"
              class="px-4 py-2 text-sm text-red-500 hover:text-red-600 border border-red-200 hover:border-red-300 rounded-lg transition-colors font-medium">
              Delete Account
            </button>
          } @else {
            <div class="bg-red-50 border border-red-200 rounded-xl p-4 space-y-3">
              <p class="text-sm text-red-700 font-medium">Are you absolutely sure? This cannot be undone.</p>
              <div class="flex items-center space-x-3">
                <button (click)="deleteAccount()" [disabled]="deleteLoading()"
                  class="px-4 py-2 text-sm bg-red-500 hover:bg-red-600 disabled:opacity-50 text-white rounded-lg transition-colors font-medium">
                  @if (deleteLoading()) { Deleting... } @else { Yes, delete my account }
                </button>
                <button (click)="confirmDelete.set(false)"
                  class="px-4 py-2 text-sm text-slate-600 hover:text-slate-800 border border-slate-200 rounded-lg transition-colors font-medium">
                  Cancel
                </button>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class UserProfileComponent implements OnInit {
  auth = inject(AuthService);
  private http = inject(HttpClient);
  private router = inject(Router);

  user = signal<UserResponse | null>(null);
  loading = signal(true);
  confirmDelete = signal(false);
  deleteLoading = signal(false);
  deleteError = signal('');

  ngOnInit() {
    const userId = this.auth.userId;
    if (!userId) { this.loading.set(false); return; }

    this.http.get<UserResponse>(`${API_BASE}/users/${userId}`).subscribe({
      next: (data) => { this.user.set(data); this.loading.set(false); },
      error: () => {
        // Fallback to currentUser signal
        const current = this.auth.currentUser();
        this.user.set(current);
        this.loading.set(false);
      }
    });
  }

  deleteAccount() {
    const userId = this.auth.userId;
    if (!userId) return;
    this.deleteLoading.set(true);
    this.deleteError.set('');

    this.http.delete(`${API_BASE}/users/${userId}`).subscribe({
      next: () => {
        this.deleteLoading.set(false);
        this.auth.logout();
      },
      error: (err) => {
        this.deleteLoading.set(false);
        this.deleteError.set(err?.error?.message || 'Failed to delete account. Please try again.');
        this.confirmDelete.set(false);
      }
    });
  }
}
