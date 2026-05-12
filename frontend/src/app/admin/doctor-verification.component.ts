import { Component, inject, signal, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from '../core/api.config';

interface PendingDoctor {
  id: string;
  userId: string;
  fullName: string;
  licenseNumber: string;
  yearsOfExperience: number;
  consultationFee: number;
  verified: boolean;
  active: boolean;
}

@Component({
  selector: 'app-doctor-verification',
  standalone: true,
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-slate-800">Verify Doctors</h1>
        <button (click)="loadDoctors()"
          class="flex items-center space-x-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-sm font-medium transition-colors">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
          </svg>
          <span>Refresh</span>
        </button>
      </div>

      <!-- Tabs -->
      <div class="flex space-x-1 bg-slate-100 rounded-xl p-1 w-fit">
        <button (click)="tab.set('pending')"
          [class]="tab() === 'pending' ? 'px-4 py-2 bg-white text-slate-800 rounded-lg shadow-sm text-sm font-medium' : 'px-4 py-2 text-slate-500 text-sm font-medium'">
          Pending ({{ pendingDoctors().length }})
        </button>
        <button (click)="tab.set('verified')"
          [class]="tab() === 'verified' ? 'px-4 py-2 bg-white text-slate-800 rounded-lg shadow-sm text-sm font-medium' : 'px-4 py-2 text-slate-500 text-sm font-medium'">
          Verified ({{ verifiedDoctors().length }})
        </button>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else {
        @let list = tab() === 'pending' ? pendingDoctors() : verifiedDoctors();

        @if (list.length === 0) {
          <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
            <svg class="w-12 h-12 mx-auto mb-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/>
            </svg>
            <h3 class="font-semibold text-slate-700 mb-1">All clear</h3>
            <p class="text-sm text-slate-400">No {{ tab() === 'pending' ? 'pending' : 'verified' }} doctors.</p>
          </div>
        } @else {
          <div class="space-y-4">
            @for (doc of list; track doc.id) {
              <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
                <div class="flex items-start justify-between">
                  <div class="flex items-start space-x-4">
                    <div class="w-12 h-12 bg-gradient-to-tr from-blue-100 to-indigo-100 rounded-xl flex items-center justify-center shrink-0">
                      <span class="text-xl font-bold text-indigo-600">{{ doc.fullName.charAt(0) }}</span>
                    </div>
                    <div>
                      <h3 class="font-semibold text-slate-800">Dr. {{ doc.fullName }}</h3>
                      <p class="text-xs text-slate-400 mt-0.5">License: {{ doc.licenseNumber }}</p>
                      <div class="flex items-center space-x-4 mt-2 text-sm text-slate-600">
                        <span>{{ doc.yearsOfExperience }} yrs exp</span>
                        <span class="text-slate-300">·</span>
                        <span>₹{{ doc.consultationFee }}/consult</span>
                      </div>
                    </div>
                  </div>
                  <div class="flex items-center space-x-2">
                    @if (!doc.verified) {
                      <button (click)="verify(doc)"
                        [disabled]="processingId() === doc.id"
                        class="px-4 py-2 bg-emerald-500 hover:bg-emerald-600 disabled:opacity-50 text-white rounded-lg text-sm font-medium transition-colors">
                        @if (processingId() === doc.id) { Verifying... } @else { Verify }
                      </button>
                    } @else {
                      <div class="flex items-center space-x-1.5 bg-emerald-50 text-emerald-600 text-xs font-medium px-3 py-1.5 rounded-full">
                        <svg class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20">
                          <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/>
                        </svg>
                        <span>Verified</span>
                      </div>
                    }
                  </div>
                </div>
              </div>
            }
          </div>
        }
      }
    </div>
  `
})
export class DoctorVerificationComponent implements OnInit {
  private http = inject(HttpClient);

  allDoctors = signal<PendingDoctor[]>([]);
  loading = signal(true);
  processingId = signal('');
  tab = signal<'pending' | 'verified'>('pending');

  get pendingDoctors() {
    return () => this.allDoctors().filter(d => !d.verified);
  }

  get verifiedDoctors() {
    return () => this.allDoctors().filter(d => d.verified);
  }

  ngOnInit() { this.loadDoctors(); }

  loadDoctors() {
    this.loading.set(true);
    this.http.get<PendingDoctor[]>(`${API_BASE}/doctors/search?verified=false`).subscribe({
      next: (data) => {
        this.allDoctors.set(data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.http.get<PendingDoctor[]>(`${API_BASE}/doctors/search`).subscribe({
          next: (data) => { this.allDoctors.set(data ?? []); this.loading.set(false); },
          error: () => this.loading.set(false)
        });
      }
    });
  }

  verify(doc: PendingDoctor) {
    this.processingId.set(doc.id);
    this.http.put(`${API_BASE}/doctors/${doc.id}/verify`, {}).subscribe({
      next: () => {
        this.allDoctors.update(list =>
          list.map(d => d.id === doc.id ? { ...d, verified: true } : d)
        );
        this.processingId.set('');
      },
      error: () => this.processingId.set('')
    });
  }
}
