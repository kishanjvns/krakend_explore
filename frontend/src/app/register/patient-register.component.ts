import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from '../core/api.config';

@Component({
  selector: 'app-patient-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-indigo-900 flex items-center justify-center px-4">
      <div class="w-full max-w-md">

        <!-- Logo -->
        <div class="flex items-center justify-center space-x-2 mb-8">
          <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-400 to-indigo-400 flex items-center justify-center">
            <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4"/>
            </svg>
          </div>
          <span class="text-2xl font-bold text-white">Mediq</span>
        </div>

        <div class="bg-white/10 backdrop-blur-md border border-white/20 rounded-2xl p-8">
          <h2 class="text-2xl font-bold text-white mb-1">Create patient account</h2>
          <p class="text-blue-200/70 text-sm mb-6">Book appointments and manage your health records</p>

          @if (error()) {
            <div class="mb-4 px-4 py-3 bg-red-500/20 border border-red-500/40 rounded-lg text-red-200 text-sm">
              {{ error() }}
            </div>
          }

          <form (ngSubmit)="submit()" #f="ngForm" class="space-y-4">
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-medium text-blue-200 mb-1">First name</label>
                <input name="firstName" [(ngModel)]="form.firstName" required
                  class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                  placeholder="Rahul"/>
              </div>
              <div>
                <label class="block text-xs font-medium text-blue-200 mb-1">Last name</label>
                <input name="lastName" [(ngModel)]="form.lastName" required
                  class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                  placeholder="Sharma"/>
              </div>
            </div>

            <div>
              <label class="block text-xs font-medium text-blue-200 mb-1">Email</label>
              <input name="email" type="email" [(ngModel)]="form.email" required
                class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                placeholder="rahul@example.com"/>
            </div>

            <div>
              <label class="block text-xs font-medium text-blue-200 mb-1">Date of birth</label>
              <input name="dateOfBirth" type="date" [(ngModel)]="form.dateOfBirth" required
                class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors [color-scheme:dark]"/>
            </div>

            <div>
              <label class="block text-xs font-medium text-blue-200 mb-1">Phone</label>
              <input name="phone" [(ngModel)]="form.phone"
                class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                placeholder="+91 98765 43210"/>
            </div>

            <div>
              <label class="block text-xs font-medium text-blue-200 mb-1">Password</label>
              <input name="password" type="password" [(ngModel)]="form.password" required minlength="8"
                class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                placeholder="Min 8 characters"/>
            </div>

            <button type="submit" [disabled]="loading() || f.invalid"
              class="w-full py-3 bg-indigo-500 hover:bg-indigo-400 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold rounded-xl transition-colors mt-2">
              @if (loading()) { Creating account... } @else { Create account }
            </button>
          </form>

          <div class="mt-6 text-center space-y-3">
            <p class="text-blue-200/60 text-sm">
              Are you a doctor?
              <a routerLink="/register/doctor" class="text-indigo-300 hover:text-indigo-200 font-medium ml-1">Register as doctor</a>
            </p>
            <p class="text-blue-200/60 text-sm">
              Already have an account?
              <a routerLink="/" class="text-indigo-300 hover:text-indigo-200 font-medium ml-1" (click)="goLogin($event)">Sign in</a>
            </p>
          </div>
        </div>
      </div>
    </div>
  `
})
export class PatientRegisterComponent {
  private http = inject(HttpClient);
  private router = inject(Router);

  form = { firstName: '', lastName: '', email: '', phone: '', dateOfBirth: '', password: '' };
  loading = signal(false);
  error = signal('');

  goLogin(e: Event) {
    e.preventDefault();
    this.router.navigate(['/']);
  }

  submit() {
    this.loading.set(true);
    this.error.set('');

    const contacts = [];
    if (this.form.email) contacts.push({ contactType: 'EMAIL', contactValue: this.form.email, isPrimary: true });
    if (this.form.phone) contacts.push({ contactType: 'PHONE', contactValue: this.form.phone, isPrimary: !this.form.email });

    const payload = {
      firstName: this.form.firstName,
      lastName: this.form.lastName,
      dateOfBirth: this.form.dateOfBirth,
      password: this.form.password,
      contacts,
      addresses: []
    };

    this.http.post<any>(`${API_BASE}/users/patients/register`, payload).subscribe({
      next: (res) => {
        this.loading.set(false);
        const userId = res?.id || res?.userId || '';
        if (userId) {
          this.router.navigate(['/verify-otp', userId], {
            state: { userId, email: this.form.email, userName: this.form.firstName + ' ' + this.form.lastName }
          });
        } else {
          this.router.navigate(['/'], { queryParams: { registered: '1' } });
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || err?.error?.error || 'Registration failed. Please try again.');
      }
    });
  }
}
