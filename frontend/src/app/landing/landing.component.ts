import { Component, inject, signal, OnInit } from '@angular/core';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-indigo-900 flex flex-col">

      <!-- Navbar -->
      <nav class="flex items-center justify-between px-8 py-5">
        <div class="flex items-center space-x-3">
          <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-400 to-indigo-400 flex items-center justify-center shadow-lg shadow-blue-500/40">
            <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4"/>
            </svg>
          </div>
          <span class="text-2xl font-bold text-white tracking-tight">Mediq</span>
        </div>
        <div class="flex items-center space-x-3">
          <button (click)="authService.login()"
            class="px-5 py-2 text-sm font-medium text-blue-200 hover:text-white border border-blue-500/40 hover:border-blue-400 rounded-lg transition-all duration-200 hover:bg-white/5">
            Sign In
          </button>
          <a routerLink="/register/patient"
            class="px-5 py-2 text-sm font-medium bg-indigo-500 hover:bg-indigo-400 text-white rounded-lg transition-all duration-200 shadow-lg shadow-indigo-500/30 hover:shadow-indigo-400/40">
            Register
          </a>
        </div>
      </nav>

      <!-- Hero -->
      <div class="flex-1 flex items-center justify-center px-6 py-16">
        <div class="max-w-4xl mx-auto text-center">

          <!-- Badge -->
          <div class="inline-flex items-center space-x-2 bg-blue-500/10 border border-blue-500/20 rounded-full px-4 py-1.5 mb-8">
            <div class="w-2 h-2 bg-emerald-400 rounded-full animate-pulse"></div>
            <span class="text-sm text-blue-200 font-medium">All systems operational</span>
          </div>

          <!-- Headline -->
          <h1 class="text-5xl md:text-6xl font-extrabold text-white leading-tight mb-6 tracking-tight">
            Healthcare,<br/>
            <span class="bg-gradient-to-r from-blue-400 to-indigo-400 bg-clip-text text-transparent">
              digitally connected
            </span>
          </h1>
          <p class="text-lg text-blue-200/80 max-w-xl mx-auto mb-10 leading-relaxed">
            Appointments, medical records, prescriptions and care teams — all in one secure, role-aware platform.
          </p>

          <!-- Registration success banner -->
          @if (registered()) {
            <div class="mb-6 inline-flex items-center space-x-2 bg-emerald-500/20 border border-emerald-500/30 rounded-full px-5 py-2">
              <svg class="w-4 h-4 text-emerald-400" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
              <span class="text-sm text-emerald-300 font-medium">Account created! Sign in to get started.</span>
            </div>
          }

          <!-- Session expired banner -->
          @if (sessionExpired()) {
            <div class="mb-6 inline-flex items-center space-x-2 bg-orange-500/20 border border-orange-500/30 rounded-full px-5 py-2">
              <svg class="w-4 h-4 text-orange-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/></svg>
              <span class="text-sm text-orange-300 font-medium">Your session has expired. Please sign in again.</span>
            </div>
          }

          <!-- CTA Buttons -->
          <div class="flex flex-col sm:flex-row items-center justify-center gap-4">
            <a routerLink="/register/patient"
              class="w-full sm:w-auto px-8 py-3.5 bg-indigo-500 hover:bg-indigo-400 text-white font-semibold rounded-xl transition-all duration-200 shadow-xl shadow-indigo-500/30 hover:shadow-indigo-400/40 hover:-translate-y-0.5">
              Create an account
            </a>
            <button (click)="authService.login()"
              class="w-full sm:w-auto px-8 py-3.5 bg-white/10 hover:bg-white/15 text-white font-semibold rounded-xl border border-white/10 hover:border-white/20 transition-all duration-200 hover:-translate-y-0.5">
              Sign in to your account
            </button>
          </div>

          <!-- Feature Cards -->
          <div class="grid grid-cols-1 md:grid-cols-3 gap-5 mt-20">
            <div class="bg-white/5 border border-white/10 rounded-2xl p-6 text-left hover:bg-white/8 transition-colors">
              <div class="w-10 h-10 bg-blue-500/20 rounded-lg flex items-center justify-center mb-4">
                <svg class="w-5 h-5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
                </svg>
              </div>
              <h3 class="text-white font-semibold mb-2">Smart Appointments</h3>
              <p class="text-blue-200/60 text-sm leading-relaxed">Book, reschedule and track appointments with real-time slot management.</p>
            </div>
            <div class="bg-white/5 border border-white/10 rounded-2xl p-6 text-left hover:bg-white/8 transition-colors">
              <div class="w-10 h-10 bg-emerald-500/20 rounded-lg flex items-center justify-center mb-4">
                <svg class="w-5 h-5 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                </svg>
              </div>
              <h3 class="text-white font-semibold mb-2">Electronic Medical Records</h3>
              <p class="text-blue-200/60 text-sm leading-relaxed">Immutable event-sourced patient history. Full audit trail, always accurate.</p>
            </div>
            <div class="bg-white/5 border border-white/10 rounded-2xl p-6 text-left hover:bg-white/8 transition-colors">
              <div class="w-10 h-10 bg-purple-500/20 rounded-lg flex items-center justify-center mb-4">
                <svg class="w-5 h-5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
                </svg>
              </div>
              <h3 class="text-white font-semibold mb-2">Role-Based Access</h3>
              <p class="text-blue-200/60 text-sm leading-relaxed">Doctors, nurses, admins and patients each see exactly what they need.</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Footer -->
      <footer class="text-center py-6 text-blue-200/30 text-xs">
        © 2026 Mediq Healthcare Platform · Secured by Keycloak + KrakenD
      </footer>
    </div>
  `
})
export class LandingComponent implements OnInit {
  public authService = inject(AuthService);
  private route = inject(ActivatedRoute);

  registered = signal(false);
  sessionExpired = signal(false);

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['registered']) this.registered.set(true);
      if (params['sessionExpired']) this.sessionExpired.set(true);
    });
  }
}
