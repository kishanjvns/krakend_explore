import { Component, inject, signal, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { AnalyticsService } from '../core/analytics.service';
import { AnalyticsDashboard, DailyAppointment, DoctorPerformance } from '../core/models';

@Component({
  selector: 'app-analytics',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <div class="space-y-6">

      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-slate-800">Analytics</h1>
        <button (click)="loadAll()"
          class="flex items-center space-x-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-sm font-medium transition-colors">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
          </svg>
          <span>Refresh</span>
        </button>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else {

        <!-- Summary Cards -->
        @if (dashboard()) {
          <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
              <div class="flex items-center justify-between mb-3">
                <p class="text-sm text-slate-500 font-medium">Total Users</p>
                <div class="w-9 h-9 bg-indigo-50 rounded-lg flex items-center justify-center">
                  <svg class="w-5 h-5 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"/>
                  </svg>
                </div>
              </div>
              <p class="text-3xl font-bold text-slate-800">{{ dashboard()!.totalUsers | number }}</p>
              <p class="text-xs text-slate-400 mt-1">registered</p>
            </div>

            <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
              <div class="flex items-center justify-between mb-3">
                <p class="text-sm text-slate-500 font-medium">Total Doctors</p>
                <div class="w-9 h-9 bg-blue-50 rounded-lg flex items-center justify-center">
                  <svg class="w-5 h-5 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
                  </svg>
                </div>
              </div>
              <p class="text-3xl font-bold text-slate-800">{{ dashboard()!.totalDoctors | number }}</p>
              <p class="text-xs text-slate-400 mt-1">verified</p>
            </div>

            <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
              <div class="flex items-center justify-between mb-3">
                <p class="text-sm text-slate-500 font-medium">Appointments</p>
                <div class="w-9 h-9 bg-emerald-50 rounded-lg flex items-center justify-center">
                  <svg class="w-5 h-5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
                  </svg>
                </div>
              </div>
              <p class="text-3xl font-bold text-slate-800">{{ dashboard()!.totalAppointments | number }}</p>
              <p class="text-xs text-slate-400 mt-1">total</p>
            </div>

            <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
              <div class="flex items-center justify-between mb-3">
                <p class="text-sm text-slate-500 font-medium">Revenue</p>
                <div class="w-9 h-9 bg-purple-50 rounded-lg flex items-center justify-center">
                  <svg class="w-5 h-5 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
                  </svg>
                </div>
              </div>
              <p class="text-3xl font-bold text-slate-800">₹{{ dashboard()!.totalRevenue | number }}</p>
              <p class="text-xs text-slate-400 mt-1">earned</p>
            </div>
          </div>
        }

        <!-- Daily Appointments Table -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 class="font-semibold text-slate-800 mb-4">Daily Appointments</h3>
          @if (dailyAppointments().length === 0) {
            <p class="text-slate-400 text-sm py-4 text-center">No daily appointment data available.</p>
          } @else {
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-slate-200">
                    <th class="text-left text-xs font-semibold text-slate-500 uppercase tracking-wide pb-3">Date</th>
                    <th class="text-right text-xs font-semibold text-slate-500 uppercase tracking-wide pb-3">Appointments</th>
                    <th class="text-right text-xs font-semibold text-slate-500 uppercase tracking-wide pb-3">Revenue</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  @for (day of dailyAppointments(); track day.date) {
                    <tr class="hover:bg-slate-50 transition-colors">
                      <td class="py-3 text-slate-700 font-medium">{{ day.date }}</td>
                      <td class="py-3 text-right">
                        <span class="px-2.5 py-1 bg-indigo-50 text-indigo-700 rounded-full font-medium text-xs">{{ day.count }}</span>
                      </td>
                      <td class="py-3 text-right text-slate-700 font-medium">₹{{ day.revenue | number }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>

        <!-- Doctor Performance Table -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 class="font-semibold text-slate-800 mb-4">Doctor Performance</h3>
          @if (doctorPerformance().length === 0) {
            <p class="text-slate-400 text-sm py-4 text-center">No doctor performance data available.</p>
          } @else {
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-slate-200">
                    <th class="text-left text-xs font-semibold text-slate-500 uppercase tracking-wide pb-3">Doctor</th>
                    <th class="text-right text-xs font-semibold text-slate-500 uppercase tracking-wide pb-3">Appointments</th>
                    <th class="text-right text-xs font-semibold text-slate-500 uppercase tracking-wide pb-3">Rating</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-slate-100">
                  @for (doc of doctorPerformance(); track doc.doctorId) {
                    <tr class="hover:bg-slate-50 transition-colors">
                      <td class="py-3">
                        <div class="flex items-center space-x-3">
                          <div class="w-8 h-8 rounded-lg bg-gradient-to-tr from-blue-100 to-indigo-100 flex items-center justify-center shrink-0">
                            <span class="text-xs font-bold text-indigo-600">{{ doc.name.charAt(0) }}</span>
                          </div>
                          <span class="font-medium text-slate-700">{{ doc.name }}</span>
                        </div>
                      </td>
                      <td class="py-3 text-right">
                        <span class="px-2.5 py-1 bg-emerald-50 text-emerald-700 rounded-full font-medium text-xs">{{ doc.appointmentCount }}</span>
                      </td>
                      <td class="py-3 text-right">
                        <div class="flex items-center justify-end space-x-1">
                          <svg class="w-4 h-4 text-amber-400" fill="currentColor" viewBox="0 0 20 20">
                            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z"/>
                          </svg>
                          <span class="font-medium text-slate-700">{{ doc.rating?.toFixed(1) ?? 'N/A' }}</span>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class AnalyticsComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);

  dashboard = signal<AnalyticsDashboard | null>(null);
  dailyAppointments = signal<DailyAppointment[]>([]);
  doctorPerformance = signal<DoctorPerformance[]>([]);
  loading = signal(true);

  ngOnInit() { this.loadAll(); }

  loadAll() {
    this.loading.set(true);

    this.analyticsService.getDashboard().subscribe({
      next: (data) => { this.dashboard.set(data); this.checkDone(); },
      error: () => this.checkDone()
    });

    this.analyticsService.getDailyAppointments().subscribe({
      next: (data) => this.dailyAppointments.set(data ?? []),
      error: () => this.dailyAppointments.set([])
    });

    this.analyticsService.getDoctorPerformance().subscribe({
      next: (data) => this.doctorPerformance.set(data ?? []),
      error: () => this.doctorPerformance.set([])
    });
  }

  private checkDone() {
    this.loading.set(false);
  }
}
