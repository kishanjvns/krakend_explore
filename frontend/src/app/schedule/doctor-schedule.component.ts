import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SlicePipe } from '@angular/common';
import { AuthService } from '../auth/auth.service';
import { DoctorService } from '../core/doctor.service';
import { AppointmentService } from '../core/appointment.service';
import { DoctorAvailability, AppointmentResponse, DoctorSearchResponse } from '../core/models';

@Component({
  selector: 'app-doctor-schedule',
  standalone: true,
  imports: [FormsModule, SlicePipe],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-slate-800">My Schedule</h1>
        @if (doctorProfile()) {
          <div class="bg-indigo-50 border border-indigo-200 rounded-xl px-4 py-2 flex items-center space-x-2">
            <svg class="w-4 h-4 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z"/>
            </svg>
            <span class="text-sm text-indigo-700 font-medium">Consultation Fee: ₹{{ doctorProfile()!.consultationFee }}</span>
          </div>
        }
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">

        <!-- Availability setup -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h2 class="font-semibold text-slate-800 mb-4 flex items-center space-x-2">
            <svg class="w-5 h-5 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
            </svg>
            <span>Availability</span>
          </h2>

          <!-- Existing availability -->
          @if (availability().length > 0) {
            <div class="space-y-2 mb-4">
              @for (a of availability(); track a.id) {
                <div class="flex items-center justify-between px-3 py-2 bg-slate-50 rounded-lg">
                  <div>
                    <span class="text-sm font-medium text-slate-700">{{ a.dayOfWeek }}</span>
                    <span class="text-xs text-slate-400 ml-2">{{ a.startTime }} – {{ a.endTime }}</span>
                  </div>
                  <span class="text-xs text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded-full">{{ a.slotDuration }}min</span>
                </div>
              }
            </div>
          }

          <!-- Add availability form -->
          <form (ngSubmit)="addAvailability()" #avForm="ngForm" class="space-y-3">
            <h3 class="text-sm font-medium text-slate-600">Add New Slot</h3>
            <div>
              <label class="block text-xs text-slate-500 mb-1">Day of week</label>
              <select name="day" [(ngModel)]="newSlot.day" required
                class="w-full px-3 py-2 border border-slate-200 rounded-lg text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white">
                @for (d of days; track d) {
                  <option [value]="d">{{ d }}</option>
                }
              </select>
            </div>
            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="block text-xs text-slate-500 mb-1">Start time</label>
                <input type="time" name="start" [(ngModel)]="newSlot.startTime" required
                  class="w-full px-3 py-2 border border-slate-200 rounded-lg text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"/>
              </div>
              <div>
                <label class="block text-xs text-slate-500 mb-1">End time</label>
                <input type="time" name="end" [(ngModel)]="newSlot.endTime" required
                  class="w-full px-3 py-2 border border-slate-200 rounded-lg text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"/>
              </div>
            </div>
            <div>
              <label class="block text-xs text-slate-500 mb-1">Slot duration (minutes)</label>
              <select name="duration" [(ngModel)]="newSlot.slotDuration"
                class="w-full px-3 py-2 border border-slate-200 rounded-lg text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300 bg-white">
                <option [value]="15">15 min</option>
                <option [value]="20">20 min</option>
                <option [value]="30">30 min</option>
                <option [value]="45">45 min</option>
                <option [value]="60">60 min</option>
              </select>
            </div>
            @if (avError()) {
              <p class="text-xs text-red-500">{{ avError() }}</p>
            }
            @if (avSuccess()) {
              <p class="text-xs text-emerald-600">Availability added!</p>
            }
            <button type="submit" [disabled]="avForm.invalid || avLoading()"
              class="w-full py-2 bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 text-white rounded-lg text-sm font-medium transition-colors">
              @if (avLoading()) { Saving... } @else { Add Availability }
            </button>
          </form>
        </div>

        <!-- Generate slots + upcoming appointments -->
        <div class="space-y-5">
          <!-- Generate slots for a date -->
          <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
            <h2 class="font-semibold text-slate-800 mb-4">Create Slots for Date</h2>
            <div class="space-y-3">
              <input type="date" [(ngModel)]="slotDate" [min]="minDate"
                class="w-full px-3 py-2 border border-slate-200 rounded-lg text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"/>
              <div class="grid grid-cols-2 gap-2">
                <input type="time" [(ngModel)]="slotStart" placeholder="Start"
                  class="px-3 py-2 border border-slate-200 rounded-lg text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"/>
                <input type="time" [(ngModel)]="slotEnd" placeholder="End"
                  class="px-3 py-2 border border-slate-200 rounded-lg text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300"/>
              </div>
              @if (slotSuccess()) {
                <p class="text-xs text-emerald-600">Slot created!</p>
              }
              <button (click)="createSlot()" [disabled]="!slotDate || !slotStart || !slotEnd || slotLoading()"
                class="w-full py-2 bg-emerald-500 hover:bg-emerald-600 disabled:opacity-50 text-white rounded-lg text-sm font-medium transition-colors">
                @if (slotLoading()) { Creating... } @else { Create Slot }
              </button>
            </div>
          </div>

          <!-- Upcoming appointments -->
          <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
            <h2 class="font-semibold text-slate-800 mb-4">Upcoming Appointments</h2>
            @if (aptLoading()) {
              <div class="flex items-center space-x-2 text-slate-400 text-sm py-4">
                <div class="w-4 h-4 border-2 border-slate-200 border-t-slate-400 rounded-full animate-spin"></div>
                <span>Loading...</span>
              </div>
            } @else if (appointments().length === 0) {
              <p class="text-slate-400 text-sm py-4">No upcoming appointments.</p>
            } @else {
              <div class="space-y-3">
                @for (apt of appointments().slice(0, 5); track apt.id) {
                  <div class="flex items-center justify-between py-2 border-b border-slate-100 last:border-0">
                    <div>
                      <p class="text-sm font-medium text-slate-700">Patient: {{ apt.patientId.slice(0, 8) }}...</p>
                      <p class="text-xs text-slate-400">{{ apt.bookedAt | slice:0:16 }}</p>
                    </div>
                    <span [class]="statusClass(apt.status) + ' text-xs px-2.5 py-1 rounded-full font-medium'">
                      {{ apt.status }}
                    </span>
                  </div>
                }
              </div>
            }
          </div>
        </div>
      </div>
    </div>
  `
})
export class DoctorScheduleComponent implements OnInit {
  auth = inject(AuthService);
  private doctorService = inject(DoctorService);
  private appointmentService = inject(AppointmentService);

  days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
  minDate = new Date().toISOString().split('T')[0];

  availability = signal<DoctorAvailability[]>([]);
  appointments = signal<AppointmentResponse[]>([]);
  doctorProfile = signal<DoctorSearchResponse | null>(null);
  aptLoading = signal(true);
  avLoading = signal(false);
  avError = signal('');
  avSuccess = signal(false);
  slotLoading = signal(false);
  slotSuccess = signal(false);

  newSlot = { day: 'MONDAY', startTime: '09:00', endTime: '17:00', slotDuration: 30 };
  slotDate = '';
  slotStart = '';
  slotEnd = '';

  ngOnInit() {
    const profileId = this.auth.doctorProfileId;
    if (profileId) {
      this.doctorService.getDoctorProfile(profileId).subscribe({
        next: (profile) => this.doctorProfile.set(profile),
        error: () => {}
      });
      this.doctorService.getAvailability(profileId).subscribe({
        next: (data) => this.availability.set(data ?? []),
        error: () => {}
      });
      this.appointmentService.listByDoctor(profileId).subscribe({
        next: (res) => { this.appointments.set(res.appointments ?? []); this.aptLoading.set(false); },
        error: () => this.aptLoading.set(false)
      });
    } else {
      this.aptLoading.set(false);
    }
  }

  addAvailability() {
    const profileId = this.auth.doctorProfileId;
    if (!profileId) { this.avError.set('Doctor profile not found.'); return; }
    this.avLoading.set(true);
    this.avError.set('');
    this.avSuccess.set(false);
    this.doctorService.setAvailability(profileId, {
      dayOfWeek: this.newSlot.day,
      startTime: this.newSlot.startTime,
      endTime: this.newSlot.endTime,
      slotDuration: this.newSlot.slotDuration
    }).subscribe({
      next: () => {
        this.avLoading.set(false);
        this.avSuccess.set(true);
        this.doctorService.getAvailability(profileId).subscribe({
          next: (data) => this.availability.set(data ?? [])
        });
      },
      error: (err) => {
        this.avLoading.set(false);
        this.avError.set(err?.error?.message || 'Failed to save availability.');
      }
    });
  }

  createSlot() {
    const profileId = this.auth.doctorProfileId;
    if (!profileId) return;
    this.slotLoading.set(true);
    this.slotSuccess.set(false);
    this.appointmentService.createSlot(profileId, this.slotDate, this.slotStart, this.slotEnd).subscribe({
      next: () => { this.slotLoading.set(false); this.slotSuccess.set(true); },
      error: () => this.slotLoading.set(false)
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
