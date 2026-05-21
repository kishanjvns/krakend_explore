import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { JsonPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { EmrService } from '../core/emr.service';
import { EmrState, EmrEvent } from '../core/models';

@Component({
  selector: 'app-doctor-emr',
  standalone: true,
  imports: [FormsModule, JsonPipe],
  template: `
    <div class="space-y-6">

      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold text-slate-800">Patient EMR</h1>
          @if (patientId) {
            <p class="text-sm text-slate-500 mt-0.5">Patient ID: {{ patientId }}</p>
          }
        </div>
      </div>

      <!-- Patient ID input (when accessed directly without route param) -->
      @if (!patientId) {
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 class="font-semibold text-slate-800 mb-3">Look up Patient EMR</h3>
          <div class="flex gap-3">
            <input [(ngModel)]="searchPatientId" placeholder="Enter patient ID"
              class="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"/>
            <button (click)="loadPatient(searchPatientId)"
              class="px-5 py-2.5 bg-indigo-500 hover:bg-indigo-600 text-white rounded-xl text-sm font-medium transition-colors">
              Load
            </button>
          </div>
        </div>
      }

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else if (patientId) {

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
          <button (click)="activeTab.set('append')"
            [class]="activeTab() === 'append' ? 'px-4 py-2 bg-white text-slate-800 rounded-lg shadow-sm text-sm font-medium' : 'px-4 py-2 text-slate-500 hover:text-slate-700 text-sm font-medium'">
            Append Event
          </button>
        </div>

        @if (activeTab() === 'current') {
          @if (!emrState()) {
            <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
              <h3 class="font-semibold text-slate-700 mb-1">No EMR data</h3>
              <p class="text-sm text-slate-400">No electronic medical record found for this patient.</p>
            </div>
          } @else {
            <div class="space-y-4">
              @if (emrState()!.currentDiagnosis) {
                <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                  <h3 class="font-semibold text-slate-800 mb-3">Current Diagnosis</h3>
                  <p class="text-slate-700 bg-red-50 rounded-xl px-4 py-3 text-sm">{{ emrState()!.currentDiagnosis }}</p>
                </div>
              }

              @if (emrState()!.medications && emrState()!.medications.length > 0) {
                <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                  <h3 class="font-semibold text-slate-800 mb-3">Medications</h3>
                  <div class="flex flex-wrap gap-2">
                    @for (med of emrState()!.medications; track med) {
                      <span class="px-3 py-1.5 bg-blue-50 text-blue-700 rounded-lg text-sm font-medium">{{ med }}</span>
                    }
                  </div>
                </div>
              }

              @if (emrState()!.allergies && emrState()!.allergies.length > 0) {
                <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                  <h3 class="font-semibold text-slate-800 mb-3">Allergies</h3>
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
        } @else if (activeTab() === 'history') {
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
                    <div>
                      <p class="text-sm font-semibold text-slate-800">{{ event.eventType }}</p>
                      <p class="text-xs text-slate-400 mt-0.5">{{ formatDate(event.occurredAt) }}</p>
                      @if (event.recordedBy) {
                        <p class="text-xs text-slate-500 mt-1">Recorded by: {{ event.recordedBy }}</p>
                      }
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
        } @else {
          <!-- Append Event form -->
          <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
            <h3 class="font-semibold text-slate-800 mb-4">Append EMR Event</h3>

            @if (appendError()) {
              <div class="mb-4 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm">
                {{ appendError() }}
              </div>
            }

            @if (appendSuccess()) {
              <div class="mb-4 px-4 py-3 bg-emerald-50 border border-emerald-200 rounded-lg text-emerald-600 text-sm font-medium">
                Event appended successfully.
              </div>
            }

            <div class="space-y-4">
              <div>
                <label class="block text-xs font-medium text-slate-600 mb-1">Event Type</label>
                <select [(ngModel)]="eventType"
                  class="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white">
                  <option value="DIAGNOSIS">DIAGNOSIS</option>
                  <option value="MEDICATION_ADDED">MEDICATION_ADDED</option>
                  <option value="MEDICATION_REMOVED">MEDICATION_REMOVED</option>
                  <option value="ALLERGY_ADDED">ALLERGY_ADDED</option>
                  <option value="CONDITION_ADDED">CONDITION_ADDED</option>
                  <option value="VITALS_RECORDED">VITALS_RECORDED</option>
                  <option value="LAB_RESULT">LAB_RESULT</option>
                  <option value="PRESCRIPTION">PRESCRIPTION</option>
                  <option value="NOTE">NOTE</option>
                </select>
              </div>

              <div>
                <label class="block text-xs font-medium text-slate-600 mb-1">Payload (JSON)</label>
                <textarea [(ngModel)]="eventPayloadRaw" rows="5" placeholder='{"key": "value"}'
                  class="w-full px-3 py-2.5 border border-slate-200 rounded-xl text-slate-700 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-300 resize-none">
                </textarea>
                @if (payloadError()) {
                  <p class="text-xs text-red-500 mt-1">{{ payloadError() }}</p>
                }
              </div>

              <button (click)="appendEvent()" [disabled]="appendLoading() || !eventType"
                class="w-full py-2.5 bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 text-white rounded-xl text-sm font-medium transition-colors">
                @if (appendLoading()) { Appending... } @else { Append Event }
              </button>
            </div>
          </div>
        }
      }
    </div>
  `
})
export class DoctorEmrComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private emrService = inject(EmrService);

  patientId = '';
  searchPatientId = '';
  eventType = 'DIAGNOSIS';
  eventPayloadRaw = '{}';

  emrState = signal<EmrState | null>(null);
  history = signal<EmrEvent[]>([]);
  loading = signal(false);
  activeTab = signal<'current' | 'history' | 'append'>('current');
  appendLoading = signal(false);
  appendError = signal('');
  appendSuccess = signal(false);
  payloadError = signal('');

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('patientId');
    if (id) {
      this.patientId = id;
      this.loadPatient(id);
    }
  }

  loadPatient(id: string) {
    if (!id) return;
    this.patientId = id;
    this.loading.set(true);
    this.emrService.getCurrentState(id).subscribe({
      next: (state) => { this.emrState.set(state); this.loading.set(false); },
      error: () => { this.emrState.set(null); this.loading.set(false); }
    });
    this.emrService.getHistory(id).subscribe({
      next: (events) => this.history.set(events ?? []),
      error: () => this.history.set([])
    });
  }

  appendEvent() {
    this.payloadError.set('');
    this.appendError.set('');
    this.appendSuccess.set(false);

    let payload: any = {};
    try {
      payload = JSON.parse(this.eventPayloadRaw || '{}');
    } catch {
      this.payloadError.set('Invalid JSON payload.');
      return;
    }

    this.appendLoading.set(true);
    this.emrService.appendEvent(this.patientId, this.eventType, payload).subscribe({
      next: () => {
        this.appendLoading.set(false);
        this.appendSuccess.set(true);
        this.loadPatient(this.patientId);
      },
      error: (err) => {
        this.appendLoading.set(false);
        this.appendError.set(err?.error?.message || 'Failed to append event.');
      }
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
