import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { AuthService } from '../auth/auth.service';
import { AppointmentService } from '../core/appointment.service';
import { AppointmentResponse } from '../core/models';

@Component({
  selector: 'app-my-appointments',
  standalone: true,
  imports: [RouterLink, SlicePipe],
  template: `
    <div class="space-y-6">

      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-slate-800">My Appointments</h1>
        <a routerLink="/find-doctors"
          class="flex items-center space-x-2 px-4 py-2 bg-indigo-500 hover:bg-indigo-600 text-white rounded-xl text-sm font-medium transition-colors">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
          </svg>
          <span>Book New</span>
        </a>
      </div>

      <!-- Filter tabs -->
      <div class="flex space-x-1 bg-slate-100 rounded-xl p-1 w-fit">
        @for (tab of tabs; track tab) {
          <button (click)="activeTab.set(tab)"
            [class]="activeTab() === tab
              ? 'px-4 py-2 bg-white text-slate-800 rounded-lg shadow-sm text-sm font-medium'
              : 'px-4 py-2 text-slate-500 hover:text-slate-700 text-sm font-medium'">
            {{ tab }}
          </button>
        }
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else if (filtered().length === 0) {
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
          <svg class="w-12 h-12 mx-auto mb-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
          </svg>
          <h3 class="font-semibold text-slate-700 mb-1">No appointments</h3>
          <p class="text-sm text-slate-400 mb-4">
            @if (activeTab() === 'All') { You don't have any appointments yet. }
            @else { No {{ activeTab().toLowerCase() }} appointments. }
          </p>
          <a routerLink="/find-doctors"
            class="inline-flex items-center px-4 py-2 bg-indigo-500 hover:bg-indigo-600 text-white rounded-xl text-sm font-medium transition-colors">
            Find a Doctor
          </a>
        </div>
      } @else {
        <div class="space-y-4">
          @for (apt of filtered(); track apt.id) {
            <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
              <div class="flex items-start justify-between">
                <div class="flex items-start space-x-4">
                  <div class="w-12 h-12 bg-indigo-50 rounded-xl flex items-center justify-center shrink-0">
                    <svg class="w-6 h-6 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
                    </svg>
                  </div>
                  <div>
                    <p class="font-semibold text-slate-800">Appointment</p>
                    <p class="text-xs text-slate-400 mt-0.5">ID: {{ apt.id.slice(0, 12) }}...</p>
                    <p class="text-sm text-slate-600 mt-1">Booked: {{ apt.bookedAt | slice:0:10 }}</p>
                  </div>
                </div>
                <span [class]="statusClass(apt.status) + ' text-xs px-3 py-1 rounded-full font-medium'">
                  {{ apt.status }}
                </span>
              </div>

              <div class="mt-4 flex items-center space-x-2">
                @if (apt.status === 'PENDING') {
                  <button (click)="cancel(apt)"
                    class="px-4 py-2 text-sm text-red-500 hover:text-red-600 border border-red-200 hover:border-red-300 rounded-lg transition-colors font-medium">
                    Cancel
                  </button>
                }
                @if (apt.status === 'BOOKED' || apt.status === 'PENDING') {
                  <button (click)="confirm(apt)"
                    [disabled]="processingId() === apt.id"
                    class="px-4 py-2 text-sm bg-emerald-500 hover:bg-emerald-600 disabled:opacity-50 text-white rounded-lg transition-colors font-medium">
                    @if (processingId() === apt.id) { Processing... } @else { Confirm }
                  </button>
                }
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class MyAppointmentsComponent implements OnInit {
  auth = inject(AuthService);
  private appointmentService = inject(AppointmentService);

  appointments = signal<AppointmentResponse[]>([]);
  loading = signal(true);
  activeTab = signal('All');
  processingId = signal('');

  tabs = ['All', 'PENDING', 'CONFIRMED', 'CANCELLED'];

  get filtered() {
    return () => {
      const tab = this.activeTab();
      const all = this.appointments();
      return tab === 'All' ? all : all.filter(a => a.status === tab);
    };
  }

  ngOnInit() {
    const userId = this.auth.userId;
    if (!userId) { this.loading.set(false); return; }

    this.appointmentService.listByPatient(userId).subscribe({
      next: (res) => { this.appointments.set(res.appointments ?? []); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  confirm(apt: AppointmentResponse) {
    this.processingId.set(apt.id);
    this.appointmentService.confirm(apt.id).subscribe({
      next: (updated) => {
        this.appointments.update(list => list.map(a => a.id === updated.id ? updated : a));
        this.processingId.set('');
      },
      error: () => this.processingId.set('')
    });
  }

  cancel(apt: AppointmentResponse) {
    this.processingId.set(apt.id);
    this.appointmentService.cancel(apt.id).subscribe({
      next: (updated) => {
        this.appointments.update(list => list.map(a => a.id === updated.id ? updated : a));
        this.processingId.set('');
      },
      error: () => this.processingId.set('')
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
}
