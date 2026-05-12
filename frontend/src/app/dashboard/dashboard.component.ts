import { Component, inject, signal, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { SlicePipe } from '@angular/common';
import { AuthService } from '../auth/auth.service';
import { AppointmentService } from '../core/appointment.service';
import { NotificationService } from '../core/notification.service';
import { AppointmentResponse, NotificationResponse } from '../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, SlicePipe],
  template: `
    <div class="space-y-6">

      <!-- Welcome banner -->
      <div class="bg-gradient-to-r from-indigo-500 to-blue-500 rounded-2xl p-6 text-white">
        <h2 class="text-xl font-bold mb-1">Welcome back, {{ auth.userName }} 👋</h2>
        <p class="text-indigo-100 text-sm">{{ today }}</p>
      </div>

      <!-- Stats row -->
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
          <div class="flex items-center justify-between mb-3">
            <p class="text-sm text-slate-500 font-medium">
              @if (auth.isDoctor()) { Today's Patients } @else { My Appointments }
            </p>
            <div class="w-9 h-9 bg-indigo-50 rounded-lg flex items-center justify-center">
              <svg class="w-5 h-5 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
              </svg>
            </div>
          </div>
          <p class="text-3xl font-bold text-slate-800">{{ appointments().length }}</p>
          <p class="text-xs text-slate-400 mt-1">total</p>
        </div>

        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
          <div class="flex items-center justify-between mb-3">
            <p class="text-sm text-slate-500 font-medium">Notifications</p>
            <div class="w-9 h-9 bg-emerald-50 rounded-lg flex items-center justify-center">
              <svg class="w-5 h-5 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
              </svg>
            </div>
          </div>
          <p class="text-3xl font-bold text-slate-800">{{ notifications().length }}</p>
          <p class="text-xs text-slate-400 mt-1">unread</p>
        </div>

        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5">
          <div class="flex items-center justify-between mb-3">
            <p class="text-sm text-slate-500 font-medium">Status</p>
            <div class="w-9 h-9 bg-purple-50 rounded-lg flex items-center justify-center">
              <svg class="w-5 h-5 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
              </svg>
            </div>
          </div>
          <p class="text-xl font-bold text-slate-800 truncate">{{ auth.role }}</p>
          <p class="text-xs text-slate-400 mt-1">role</p>
        </div>
      </div>

      <!-- Quick actions -->
      <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <h3 class="font-semibold text-slate-800 mb-4">Quick Actions</h3>
        <div class="flex flex-wrap gap-3">
          @if (auth.isPatient() || !auth.role) {
            <a routerLink="/find-doctors"
              class="flex items-center space-x-2 px-4 py-2.5 bg-indigo-500 hover:bg-indigo-600 text-white rounded-xl text-sm font-medium transition-colors">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
              <span>Find a Doctor</span>
            </a>
            <a routerLink="/appointments"
              class="flex items-center space-x-2 px-4 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-sm font-medium transition-colors">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>
              <span>My Appointments</span>
            </a>
          }
          @if (auth.isDoctor()) {
            <a routerLink="/schedule"
              class="flex items-center space-x-2 px-4 py-2.5 bg-indigo-500 hover:bg-indigo-600 text-white rounded-xl text-sm font-medium transition-colors">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
              <span>Manage Schedule</span>
            </a>
          }
          @if (auth.isAdmin()) {
            <a routerLink="/admin/verify"
              class="flex items-center space-x-2 px-4 py-2.5 bg-purple-500 hover:bg-purple-600 text-white rounded-xl text-sm font-medium transition-colors">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/></svg>
              <span>Verify Doctors</span>
            </a>
          }
          <a routerLink="/notifications"
            class="flex items-center space-x-2 px-4 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 rounded-xl text-sm font-medium transition-colors">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/></svg>
            <span>Notifications</span>
          </a>
        </div>
      </div>

      <!-- Recent appointments -->
      @if (appointments().length > 0) {
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="font-semibold text-slate-800">Recent Appointments</h3>
            <a [routerLink]="auth.isDoctor() ? '/schedule' : '/appointments'"
              class="text-sm text-indigo-500 hover:text-indigo-600 font-medium">View all</a>
          </div>
          <div class="space-y-3">
            @for (apt of appointments().slice(0, 3); track apt.id) {
              <div class="flex items-center justify-between py-3 border-b border-slate-100 last:border-0">
                <div class="flex items-center space-x-3">
                  <div class="w-10 h-10 bg-indigo-50 rounded-lg flex items-center justify-center">
                    <svg class="w-5 h-5 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>
                    </svg>
                  </div>
                  <div>
                    <p class="text-sm font-medium text-slate-700">Appointment #{{ apt.id.slice(0, 8) }}</p>
                    <p class="text-xs text-slate-400">{{ apt.bookedAt | slice:0:10 }}</p>
                  </div>
                </div>
                <span [class]="statusClass(apt.status)" class="text-xs px-2.5 py-1 rounded-full font-medium">
                  {{ apt.status }}
                </span>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class DashboardComponent implements OnInit {
  auth = inject(AuthService);
  private appointmentService = inject(AppointmentService);
  private notificationService = inject(NotificationService);

  appointments = signal<AppointmentResponse[]>([]);
  notifications = signal<NotificationResponse[]>([]);
  today = new Date().toLocaleDateString('en-IN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });

  ngOnInit() {
    const userId = this.auth.userId;
    if (!userId) return;

    if (this.auth.isDoctor()) {
      const profileId = this.auth.doctorProfileId;
      if (profileId) {
        this.appointmentService.listByDoctor(profileId).subscribe({
          next: (res) => this.appointments.set(res.appointments ?? []),
          error: () => {}
        });
      }
    } else {
      this.appointmentService.listByPatient(userId).subscribe({
        next: (res) => this.appointments.set(res.appointments ?? []),
        error: () => {}
      });
    }

    this.notificationService.getForUser(userId).subscribe({
      next: (data) => this.notifications.set(data ?? []),
      error: () => {}
    });
  }

  statusClass(status: string) {
    switch (status) {
      case 'CONFIRMED': return 'bg-emerald-50 text-emerald-600';
      case 'PENDING': return 'bg-orange-50 text-orange-600';
      case 'CANCELLED': return 'bg-red-50 text-red-600';
      default: return 'bg-slate-100 text-slate-600';
    }
  }
}
