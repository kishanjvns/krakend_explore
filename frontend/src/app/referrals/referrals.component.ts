import { Component, inject, signal, OnInit } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { ReferralService } from '../core/referral.service';
import { Referral } from '../core/models';

@Component({
  selector: 'app-referrals',
  standalone: true,
  template: `
    <div class="space-y-6">

      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-slate-800">Referrals</h1>
        <button (click)="load()"
          class="flex items-center space-x-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-sm font-medium transition-colors">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
          </svg>
          <span>Refresh</span>
        </button>
      </div>

      <!-- Filter Tabs -->
      <div class="flex space-x-1 bg-slate-100 rounded-xl p-1 w-fit">
        <button (click)="activeTab.set('all')"
          [class]="activeTab() === 'all' ? 'px-4 py-2 bg-white text-slate-800 rounded-lg shadow-sm text-sm font-medium' : 'px-4 py-2 text-slate-500 hover:text-slate-700 text-sm font-medium'">
          All
        </button>
        <button (click)="activeTab.set('open')"
          [class]="activeTab() === 'open' ? 'px-4 py-2 bg-white text-slate-800 rounded-lg shadow-sm text-sm font-medium' : 'px-4 py-2 text-slate-500 hover:text-slate-700 text-sm font-medium'">
          Open
        </button>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else if (filtered().length === 0) {
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
          <svg class="w-12 h-12 mx-auto mb-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"/>
          </svg>
          <h3 class="font-semibold text-slate-700 mb-1">No referrals</h3>
          <p class="text-sm text-slate-400">
            @if (activeTab() === 'open') { No open referrals found. } @else { No referrals found. }
          </p>
        </div>
      } @else {
        <div class="space-y-4">
          @for (ref of filtered(); track ref.id) {
            <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
              <div class="flex items-start justify-between">
                <div class="flex items-start space-x-4">
                  <div class="w-12 h-12 bg-purple-50 rounded-xl flex items-center justify-center shrink-0">
                    <svg class="w-6 h-6 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"/>
                    </svg>
                  </div>
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center space-x-2 mb-1">
                      <p class="font-semibold text-slate-800 text-sm">Referral #{{ ref.id.slice(0, 8) }}</p>
                      <span [class]="statusClass(ref.status) + ' text-xs px-2 py-0.5 rounded-full font-medium'">{{ ref.status }}</span>
                    </div>
                    <p class="text-sm text-slate-600 leading-relaxed">{{ ref.reason }}</p>
                    <div class="flex flex-wrap gap-x-4 gap-y-1 mt-2 text-xs text-slate-400">
                      <span>Patient: <span class="font-mono text-slate-500">{{ ref.patientId.slice(0, 8) }}...</span></span>
                      <span>From Dr: <span class="font-mono text-slate-500">{{ ref.fromDoctorId.slice(0, 8) }}...</span></span>
                      <span>To Dr: <span class="font-mono text-slate-500">{{ ref.toDoctorId.slice(0, 8) }}...</span></span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="mt-3 pt-3 border-t border-slate-100">
                <p class="text-xs text-slate-400">Created: {{ formatDate(ref.createdAt) }}</p>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class ReferralsComponent implements OnInit {
  auth = inject(AuthService);
  private referralService = inject(ReferralService);

  allReferrals = signal<Referral[]>([]);
  openReferrals = signal<Referral[]>([]);
  loading = signal(true);
  activeTab = signal<'all' | 'open'>('all');

  get filtered() {
    return () => this.activeTab() === 'all' ? this.allReferrals() : this.openReferrals();
  }

  ngOnInit() { this.load(); }

  load() {
    this.loading.set(true);

    const userId = this.auth.userId;

    if (this.auth.isPatient() && userId) {
      this.referralService.getByPatient(userId).subscribe({
        next: (data) => { this.allReferrals.set(data ?? []); this.loading.set(false); },
        error: () => { this.allReferrals.set([]); this.loading.set(false); }
      });
      this.referralService.getOpen().subscribe({
        next: (data) => this.openReferrals.set((data ?? []).filter(r => r.patientId === userId)),
        error: () => this.openReferrals.set([])
      });
    } else {
      // Doctor or Admin — see all referrals
      this.referralService.getAll().subscribe({
        next: (data) => { this.allReferrals.set(data ?? []); this.loading.set(false); },
        error: () => { this.allReferrals.set([]); this.loading.set(false); }
      });
      this.referralService.getOpen().subscribe({
        next: (data) => { this.openReferrals.set(data ?? []); },
        error: () => this.openReferrals.set([])
      });
    }
  }

  statusClass(status: string) {
    switch (status) {
      case 'OPEN': return 'bg-emerald-50 text-emerald-600';
      case 'CLOSED': return 'bg-slate-100 text-slate-600';
      case 'PENDING': return 'bg-orange-50 text-orange-600';
      case 'ACCEPTED': return 'bg-blue-50 text-blue-600';
      default: return 'bg-slate-100 text-slate-600';
    }
  }

  formatDate(dateStr: string) {
    if (!dateStr) return '';
    try {
      return new Date(dateStr).toLocaleString('en-IN', {
        day: 'numeric', month: 'short', year: 'numeric'
      });
    } catch { return dateStr; }
  }
}
