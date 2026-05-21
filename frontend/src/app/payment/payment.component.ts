import { Component, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { PaymentService } from '../core/payment.service';
import { PaymentIntent } from '../core/models';

@Component({
  selector: 'app-payment',
  standalone: true,
  template: `
    <div class="max-w-lg mx-auto space-y-6">

      <div class="mb-2">
        <h1 class="text-2xl font-bold text-slate-800">Complete Payment</h1>
        <p class="text-sm text-slate-500 mt-1">Confirm and pay for your appointment</p>
      </div>

      <!-- Summary Card -->
      <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <h3 class="font-semibold text-slate-800 mb-4">Payment Summary</h3>
        <div class="space-y-3">
          <div class="flex justify-between items-center py-2 border-b border-slate-100">
            <span class="text-sm text-slate-500">Appointment ID</span>
            <span class="text-sm font-mono text-slate-700">{{ appointmentId ? appointmentId.slice(0, 12) + '...' : 'N/A' }}</span>
          </div>
          <div class="flex justify-between items-center py-2 border-b border-slate-100">
            <span class="text-sm text-slate-500">Consultation Fee</span>
            <span class="text-sm font-semibold text-slate-800">₹{{ amount }}</span>
          </div>
          <div class="flex justify-between items-center py-2">
            <span class="text-sm font-semibold text-slate-700">Total</span>
            <span class="text-lg font-bold text-indigo-600">₹{{ amount }}</span>
          </div>
        </div>
      </div>

      <!-- Payment Status -->
      @if (paymentIntent()) {
        <div class="bg-emerald-50 border border-emerald-200 rounded-2xl p-5">
          <div class="flex items-center space-x-3">
            <div class="w-10 h-10 bg-emerald-100 rounded-full flex items-center justify-center shrink-0">
              <svg class="w-5 h-5 text-emerald-600" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
              </svg>
            </div>
            <div>
              <p class="font-semibold text-emerald-800">Payment Initiated Successfully</p>
              <p class="text-sm text-emerald-600 mt-0.5">Payment ID: {{ paymentIntent()!.id }}</p>
              <p class="text-sm text-emerald-600">Status: {{ paymentIntent()!.status }}</p>
            </div>
          </div>
          <p class="text-xs text-emerald-600 mt-3">Redirecting to your appointments...</p>
        </div>
      }

      @if (error()) {
        <div class="px-4 py-3 bg-red-50 border border-red-200 rounded-xl text-red-600 text-sm">
          {{ error() }}
        </div>
      }

      @if (!paymentIntent()) {
        <!-- Payment method (mock UI) -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 class="font-semibold text-slate-800 mb-4">Payment Method</h3>
          <div class="border-2 border-indigo-200 bg-indigo-50/50 rounded-xl p-4 mb-4 flex items-center space-x-3">
            <div class="w-10 h-7 bg-gradient-to-r from-indigo-500 to-blue-500 rounded-md flex items-center justify-center shrink-0">
              <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"/>
              </svg>
            </div>
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-slate-800">Demo Payment</p>
              <p class="text-xs text-slate-500">Simulated payment for testing</p>
            </div>
            <div class="w-4 h-4 rounded-full border-2 border-indigo-500 bg-indigo-500 flex items-center justify-center">
              <div class="w-1.5 h-1.5 bg-white rounded-full"></div>
            </div>
          </div>

          <button (click)="confirmPayment()" [disabled]="loading() || !appointmentId"
            class="w-full py-3 bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold rounded-xl transition-colors flex items-center justify-center space-x-2">
            @if (loading()) {
              <div class="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
              <span>Processing...</span>
            } @else {
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"/>
              </svg>
              <span>Confirm Payment — ₹{{ amount }}</span>
            }
          </button>

          <button (click)="skip()"
            class="w-full mt-2 py-2.5 text-sm text-slate-500 hover:text-slate-700 transition-colors">
            Skip for now
          </button>
        </div>
      }
    </div>
  `
})
export class PaymentComponent implements OnInit {
  private paymentService = inject(PaymentService);
  private router = inject(Router);

  appointmentId = '';
  amount = 100;

  paymentIntent = signal<PaymentIntent | null>(null);
  loading = signal(false);
  error = signal('');

  ngOnInit() {
    const state = window.history.state;
    if (state) {
      this.appointmentId = state['appointmentId'] || '';
      this.amount = state['amount'] ?? 100;
    }
  }

  confirmPayment() {
    if (!this.appointmentId) return;
    this.loading.set(true);
    this.error.set('');

    this.paymentService.createIntent(this.appointmentId, this.amount).subscribe({
      next: (intent) => {
        this.loading.set(false);
        this.paymentIntent.set(intent);
        setTimeout(() => this.router.navigate(['/appointments']), 2000);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Payment failed. Please try again or skip.');
      }
    });
  }

  skip() {
    this.router.navigate(['/appointments']);
  }
}
