import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from '../core/api.config';

@Component({
  selector: 'app-otp-verification',
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
          <div class="text-center mb-6">
            <div class="w-16 h-16 mx-auto bg-indigo-500/20 rounded-2xl flex items-center justify-center mb-4">
              <svg class="w-8 h-8 text-indigo-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/>
              </svg>
            </div>
            <h2 class="text-2xl font-bold text-white mb-1">Verify your email</h2>
            <p class="text-blue-200/70 text-sm">
              We sent a 6-digit code to<br/>
              <span class="text-blue-200 font-medium">{{ email }}</span>
            </p>
          </div>

          @if (error()) {
            <div class="mb-4 px-4 py-3 bg-red-500/20 border border-red-500/40 rounded-lg text-red-200 text-sm">
              {{ error() }}
            </div>
          }

          @if (success()) {
            <div class="mb-4 px-4 py-3 bg-emerald-500/20 border border-emerald-500/40 rounded-lg text-emerald-200 text-sm font-medium">
              Email verified successfully! Redirecting...
            </div>
          }

          @if (resendSuccess()) {
            <div class="mb-4 px-4 py-3 bg-blue-500/20 border border-blue-500/40 rounded-lg text-blue-200 text-sm">
              OTP resent successfully. Check your email.
            </div>
          }

          <form (ngSubmit)="verify()" #f="ngForm" class="space-y-4">
            <div>
              <label class="block text-xs font-medium text-blue-200 mb-1">6-digit OTP</label>
              <input
                name="otp"
                [(ngModel)]="otp"
                required
                minlength="6"
                maxlength="6"
                pattern="[0-9]{6}"
                placeholder="000000"
                class="w-full px-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-xl font-mono text-center tracking-[0.5em] focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                [disabled]="success()"/>
            </div>

            <button type="submit" [disabled]="loading() || f.invalid || success()"
              class="w-full py-3 bg-indigo-500 hover:bg-indigo-400 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold rounded-xl transition-colors">
              @if (loading()) { Verifying... } @else { Verify OTP }
            </button>
          </form>

          <div class="mt-4 text-center">
            <p class="text-blue-200/60 text-sm mb-2">Didn't receive the code?</p>
            <button (click)="resend()" [disabled]="resendLoading() || resendCooldown() > 0"
              class="text-indigo-300 hover:text-indigo-200 text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed transition-colors">
              @if (resendLoading()) {
                Sending...
              } @else if (resendCooldown() > 0) {
                Resend in {{ resendCooldown() }}s
              } @else {
                Resend OTP
              }
            </button>
          </div>

          <div class="mt-4 text-center">
            <a routerLink="/" class="text-blue-200/50 hover:text-blue-200/80 text-xs transition-colors">
              Back to home
            </a>
          </div>
        </div>
      </div>
    </div>
  `
})
export class OtpVerificationComponent implements OnInit {
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  otp = '';
  userId = '';
  email = '';
  userName = '';

  loading = signal(false);
  error = signal('');
  success = signal(false);
  resendLoading = signal(false);
  resendSuccess = signal(false);
  resendCooldown = signal(0);

  private cooldownInterval: any = null;

  ngOnInit() {
    this.userId = this.route.snapshot.paramMap.get('userId') || '';
    const state = this.router.getCurrentNavigation()?.extras?.state as any;
    if (state) {
      this.email = state['email'] || '';
      this.userName = state['userName'] || '';
    }
    // If state is not available (e.g., page reload), try to retrieve from history state
    const historyState = window.history.state;
    if (historyState && !this.email) {
      this.email = historyState['email'] || '';
      this.userName = historyState['userName'] || '';
    }
  }

  verify() {
    if (!this.userId || !this.otp) return;
    this.loading.set(true);
    this.error.set('');

    this.http.post<any>(`${API_BASE}/users/${this.userId}/verify-otp`, { otp: this.otp }).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true);
        setTimeout(() => this.router.navigate(['/dashboard']), 1500);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Invalid or expired OTP. Please try again.');
      }
    });
  }

  resend() {
    if (!this.userId) return;
    this.resendLoading.set(true);
    this.resendSuccess.set(false);
    this.error.set('');

    const body = {
      contactType: 'EMAIL',
      destination: this.email,
      userName: this.userName
    };

    this.http.post<any>(`${API_BASE}/users/${this.userId}/send-otp`, body).subscribe({
      next: () => {
        this.resendLoading.set(false);
        this.resendSuccess.set(true);
        this.startCooldown(60);
      },
      error: (err) => {
        this.resendLoading.set(false);
        this.error.set(err?.error?.message || 'Failed to resend OTP. Please try again.');
      }
    });
  }

  private startCooldown(seconds: number) {
    this.resendCooldown.set(seconds);
    if (this.cooldownInterval) clearInterval(this.cooldownInterval);
    this.cooldownInterval = setInterval(() => {
      const current = this.resendCooldown();
      if (current <= 1) {
        this.resendCooldown.set(0);
        clearInterval(this.cooldownInterval);
      } else {
        this.resendCooldown.set(current - 1);
      }
    }, 1000);
  }
}
