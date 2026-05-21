import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DoctorService } from '../core/doctor.service';
import { PatientSummary } from '../core/models';

@Component({
  selector: 'app-patient-summary',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="space-y-6">

      <div class="flex items-center space-x-4">
        <a routerLink="/appointments"
          class="flex items-center space-x-1 text-slate-500 hover:text-slate-700 text-sm font-medium">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
          </svg>
          <span>Back</span>
        </a>
        <h1 class="text-2xl font-bold text-slate-800">Patient Summary</h1>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else if (!summary()) {
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
          <svg class="w-12 h-12 mx-auto mb-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
          </svg>
          <h3 class="font-semibold text-slate-700 mb-1">Patient not found</h3>
          <p class="text-sm text-slate-400">Could not load patient summary.</p>
        </div>
      } @else {

        <!-- Patient Info -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <div class="flex items-center space-x-5">
            <div class="w-16 h-16 rounded-2xl bg-gradient-to-tr from-blue-100 to-indigo-100 flex items-center justify-center shrink-0">
              <span class="text-2xl font-bold text-indigo-600">
                {{ (summary()!.firstName || 'P').charAt(0).toUpperCase() }}
              </span>
            </div>
            <div>
              <h2 class="text-xl font-bold text-slate-800">{{ summary()!.firstName }} {{ summary()!.lastName }}</h2>
              <p class="text-sm text-slate-500 mt-0.5">{{ summary()!.email }}</p>
              <p class="text-xs font-mono text-slate-400 mt-1">ID: {{ summary()!.patientId }}</p>
            </div>
          </div>
        </div>

        <!-- Quick links for Doctor -->
        <div class="flex flex-wrap gap-3">
          <a [routerLink]="['/patients', summary()!.patientId, 'emr']"
            class="flex items-center space-x-2 px-4 py-2 bg-indigo-500 hover:bg-indigo-600 text-white rounded-xl text-sm font-medium transition-colors">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            <span>View EMR</span>
          </a>
        </div>

        <!-- Appointments -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 class="font-semibold text-slate-800 mb-4">Appointments ({{ summary()!.appointments?.length ?? 0 }})</h3>
          @if (!summary()!.appointments || summary()!.appointments.length === 0) {
            <p class="text-slate-400 text-sm py-4">No appointments found.</p>
          } @else {
            <div class="space-y-3">
              @for (apt of summary()!.appointments; track apt.id) {
                <div class="flex items-center justify-between py-3 border-b border-slate-100 last:border-0">
                  <div>
                    <p class="text-sm font-medium text-slate-700">Appointment #{{ apt.id.slice(0, 8) }}</p>
                    <p class="text-xs text-slate-400">{{ apt.bookedAt?.slice(0, 10) }}</p>
                  </div>
                  <span [class]="statusClass(apt.status) + ' text-xs px-2.5 py-1 rounded-full font-medium'">{{ apt.status }}</span>
                </div>
              }
            </div>
          }
        </div>

        <!-- Referrals -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 class="font-semibold text-slate-800 mb-4">Referrals ({{ summary()!.referrals?.length ?? 0 }})</h3>
          @if (!summary()!.referrals || summary()!.referrals.length === 0) {
            <p class="text-slate-400 text-sm py-4">No referrals found.</p>
          } @else {
            <div class="space-y-3">
              @for (ref of summary()!.referrals; track ref.id) {
                <div class="py-3 border-b border-slate-100 last:border-0">
                  <div class="flex items-center justify-between mb-1">
                    <p class="text-sm font-medium text-slate-700">{{ ref.reason }}</p>
                    <span [class]="refStatusClass(ref.status) + ' text-xs px-2.5 py-1 rounded-full font-medium'">{{ ref.status }}</span>
                  </div>
                  <p class="text-xs text-slate-400">{{ ref.createdAt?.slice(0, 10) }}</p>
                </div>
              }
            </div>
          }
        </div>

        <!-- EMR State snippet -->
        @if (summary()!.emrState) {
          <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
            <h3 class="font-semibold text-slate-800 mb-4">Latest EMR Snapshot</h3>
            @if (summary()!.emrState!.currentDiagnosis) {
              <div class="mb-3">
                <p class="text-xs font-medium text-slate-500 uppercase tracking-wide mb-1">Diagnosis</p>
                <p class="text-sm text-slate-700 bg-red-50 rounded-lg px-3 py-2">{{ summary()!.emrState!.currentDiagnosis }}</p>
              </div>
            }
            @if (summary()!.emrState!.medications?.length) {
              <div class="mb-3">
                <p class="text-xs font-medium text-slate-500 uppercase tracking-wide mb-2">Medications</p>
                <div class="flex flex-wrap gap-2">
                  @for (med of summary()!.emrState!.medications; track med) {
                    <span class="px-2.5 py-1 bg-blue-50 text-blue-700 rounded-lg text-xs font-medium">{{ med }}</span>
                  }
                </div>
              </div>
            }
          </div>
        }
      }
    </div>
  `
})
export class PatientSummaryComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private doctorService = inject(DoctorService);

  summary = signal<PatientSummary | null>(null);
  loading = signal(true);

  ngOnInit() {
    const patientId = this.route.snapshot.paramMap.get('patientId')!;
    this.doctorService.getPatientSummary(patientId).subscribe({
      next: (data) => { this.summary.set(data); this.loading.set(false); },
      error: () => { this.summary.set(null); this.loading.set(false); }
    });
  }

  statusClass(status: string) {
    switch (status) {
      case 'CONFIRMED': return 'bg-emerald-50 text-emerald-600';
      case 'PENDING': return 'bg-orange-50 text-orange-600';
      case 'CANCELLED': return 'bg-red-50 text-red-600';
      case 'BOOKED': return 'bg-blue-50 text-blue-600';
      default: return 'bg-slate-100 text-slate-600';
    }
  }

  refStatusClass(status: string) {
    switch (status) {
      case 'OPEN': return 'bg-emerald-50 text-emerald-600';
      case 'CLOSED': return 'bg-slate-100 text-slate-600';
      case 'PENDING': return 'bg-orange-50 text-orange-600';
      default: return 'bg-slate-100 text-slate-600';
    }
  }
}
