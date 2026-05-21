import { Component, inject, signal, OnInit } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { AuthService } from '../auth/auth.service';
import { EmrService } from '../core/emr.service';
import { EmrState, EmrEvent } from '../core/models';

@Component({
  selector: 'app-patient-emr',
  standalone: true,
  imports: [JsonPipe],
  template: `
    <div class="space-y-6">

      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-slate-800">My Medical Records</h1>
        <button (click)="loadData()"
          class="flex items-center space-x-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-sm font-medium transition-colors">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
          </svg>
          <span>Refresh</span>
        </button>
      </div>

      <!-- Tabs -->
      <div class="flex space-x-1 bg-slate-100 rounded-xl p-1 w-fit">
        <button (click)="activeTab.set('current')"
          [class]="activeTab() === 'current' ? 'px-4 py-2 bg-white text-slate-800 rounded-lg shadow-sm text-sm font-medium' : 'px-4 py-2 text-slate-500 hover:text-slate-700 text-sm font-medium'">
          Current State
        </button>
        <button (click)="activeTab.set('history')"
          [class]="activeTab() === 'history' ? 'px-4 py-2 bg-white text-slate-800 rounded-lg shadow-sm text-sm font-medium' : 'px-4 py-2 text-slate-500 hover:text-slate-700 text-sm font-medium'">
          History
        </button>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else if (activeTab() === 'current') {
        @if (!emrState()) {
          <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
            <svg class="w-12 h-12 mx-auto mb-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            <h3 class="font-semibold text-slate-700 mb-1">No medical records</h3>
            <p class="text-sm text-slate-400">Your electronic medical record is empty.</p>
          </div>
        } @else {
          <div class="space-y-4">

            @if (emrState()!.currentDiagnosis) {
              <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                <h3 class="font-semibold text-slate-800 mb-3 flex items-center space-x-2">
                  <div class="w-8 h-8 bg-red-50 rounded-lg flex items-center justify-center">
                    <svg class="w-4 h-4 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/>
                    </svg>
                  </div>
                  <span>Current Diagnosis</span>
                </h3>
                <p class="text-slate-700 bg-red-50 rounded-xl px-4 py-3 text-sm">{{ emrState()!.currentDiagnosis }}</p>
              </div>
            }

            @if (emrState()!.medications && emrState()!.medications.length > 0) {
              <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                <h3 class="font-semibold text-slate-800 mb-3 flex items-center space-x-2">
                  <div class="w-8 h-8 bg-blue-50 rounded-lg flex items-center justify-center">
                    <svg class="w-4 h-4 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"/>
                    </svg>
                  </div>
                  <span>Current Medications</span>
                </h3>
                <div class="flex flex-wrap gap-2">
                  @for (med of emrState()!.medications; track med) {
                    <span class="px-3 py-1.5 bg-blue-50 text-blue-700 rounded-lg text-sm font-medium">{{ med }}</span>
                  }
                </div>
              </div>
            }

            @if (emrState()!.allergies && emrState()!.allergies.length > 0) {
              <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                <h3 class="font-semibold text-slate-800 mb-3 flex items-center space-x-2">
                  <div class="w-8 h-8 bg-orange-50 rounded-lg flex items-center justify-center">
                    <svg class="w-4 h-4 text-orange-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
                    </svg>
                  </div>
                  <span>Allergies</span>
                </h3>
                <div class="flex flex-wrap gap-2">
                  @for (allergy of emrState()!.allergies; track allergy) {
                    <span class="px-3 py-1.5 bg-orange-50 text-orange-700 rounded-lg text-sm font-medium">{{ allergy }}</span>
                  }
                </div>
              </div>
            }

            @if (emrState()!.conditions && emrState()!.conditions.length > 0) {
              <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                <h3 class="font-semibold text-slate-800 mb-3">Conditions</h3>
                <div class="flex flex-wrap gap-2">
                  @for (condition of emrState()!.conditions; track condition) {
                    <span class="px-3 py-1.5 bg-purple-50 text-purple-700 rounded-lg text-sm font-medium">{{ condition }}</span>
                  }
                </div>
              </div>
            }

            @if (emrState()!.lastUpdated) {
              <p class="text-xs text-slate-400 text-right">Last updated: {{ formatDate(emrState()!.lastUpdated) }}</p>
            }
          </div>
        }
      } @else {
        <!-- History tab -->
        @if (history().length === 0) {
          <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
            <h3 class="font-semibold text-slate-700 mb-1">No history</h3>
            <p class="text-sm text-slate-400">No EMR events recorded yet.</p>
          </div>
        } @else {
          <div class="space-y-3">
            @for (event of history(); track event.id) {
              <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
                <div class="flex items-start justify-between">
                  <div class="flex items-start space-x-3">
                    <div class="w-9 h-9 bg-indigo-50 rounded-lg flex items-center justify-center shrink-0">
                      <svg class="w-4 h-4 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                      </svg>
                    </div>
                    <div>
                      <p class="text-sm font-semibold text-slate-800">{{ event.eventType }}</p>
                      <p class="text-xs text-slate-400 mt-0.5">{{ formatDate(event.occurredAt) }}</p>
                      @if (event.recordedBy) {
                        <p class="text-xs text-slate-500 mt-1">Recorded by: {{ event.recordedBy }}</p>
                      }
                    </div>
                  </div>
                </div>
                @if (event.payload) {
                  <div class="mt-3 bg-slate-50 rounded-lg p-3">
                    <pre class="text-xs text-slate-600 whitespace-pre-wrap">{{ event.payload | json }}</pre>
                  </div>
                }
              </div>
            }
          </div>
        }
      }
    </div>
  `
})
export class PatientEmrComponent implements OnInit {
  auth = inject(AuthService);
  private emrService = inject(EmrService);

  emrState = signal<EmrState | null>(null);
  history = signal<EmrEvent[]>([]);
  loading = signal(true);
  activeTab = signal<'current' | 'history'>('current');

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    const patientId = this.auth.userId;
    if (!patientId) { this.loading.set(false); return; }

    this.loading.set(true);

    this.emrService.getCurrentState(patientId).subscribe({
      next: (state) => { this.emrState.set(state); this.loading.set(false); },
      error: () => { this.emrState.set(null); this.loading.set(false); }
    });

    this.emrService.getHistory(patientId).subscribe({
      next: (events) => this.history.set(events ?? []),
      error: () => this.history.set([])
    });
  }

  formatDate(dateStr: string) {
    if (!dateStr) return '';
    try {
      return new Date(dateStr).toLocaleString('en-IN', {
        day: 'numeric', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      });
    } catch { return dateStr; }
  }
}
