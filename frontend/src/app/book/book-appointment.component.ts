import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DoctorService } from '../core/doctor.service';
import { AppointmentService } from '../core/appointment.service';
import { AuthService } from '../auth/auth.service';
import { SlotResponse, DoctorSearchResponse } from '../core/models';

@Component({
  selector: 'app-book-appointment',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="max-w-2xl mx-auto">

      <!-- Header -->
      <div class="mb-6">
        <button (click)="goBack()" class="flex items-center space-x-2 text-slate-500 hover:text-slate-700 mb-4 text-sm font-medium">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
          </svg>
          <span>Back to Find Doctors</span>
        </button>
        <h1 class="text-2xl font-bold text-slate-800">Book Appointment</h1>
      </div>

      @if (doctor()) {
        <!-- Doctor card -->
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 mb-6 flex items-center space-x-4">
          <div class="w-14 h-14 rounded-2xl bg-gradient-to-tr from-blue-100 to-indigo-100 flex items-center justify-center shrink-0">
            <span class="text-xl font-bold text-indigo-600">{{ doctor()!.fullName.charAt(0) }}</span>
          </div>
          <div>
            <h2 class="font-semibold text-slate-800">Dr. {{ doctor()!.fullName }}</h2>
            <p class="text-sm text-indigo-600">{{ doctor()!.primarySpecialization }}</p>
            <p class="text-sm text-slate-500 mt-0.5">₹{{ doctor()!.consultationFee }} consultation</p>
          </div>
        </div>
      }

      <!-- Date picker -->
      <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 mb-5">
        <h3 class="font-semibold text-slate-800 mb-4">Select Date</h3>
        <input type="date" [(ngModel)]="selectedDate" (ngModelChange)="onDateChange()"
          [min]="minDate"
          class="w-full px-4 py-2.5 border border-slate-200 rounded-xl text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300 focus:border-indigo-400"/>
      </div>

      <!-- Slots -->
      @if (selectedDate) {
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 mb-5">
          <h3 class="font-semibold text-slate-800 mb-4">Available Slots</h3>

          @if (slotsLoading()) {
            <div class="flex items-center space-x-2 text-slate-400 text-sm py-6">
              <div class="w-5 h-5 border-2 border-slate-200 border-t-slate-400 rounded-full animate-spin"></div>
              <span>Loading slots...</span>
            </div>
          } @else if (slots().length === 0) {
            <div class="text-center py-8 text-slate-400">
              <svg class="w-10 h-10 mx-auto mb-2 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/>
              </svg>
              <p class="text-sm font-medium">No slots available</p>
              <p class="text-xs mt-1">Try another date</p>
            </div>
          } @else {
            <div class="grid grid-cols-3 sm:grid-cols-4 gap-2">
              @for (slot of slots(); track slot.id) {
                <button
                  [class]="selectedSlot()?.id === slot.id
                    ? 'px-3 py-2 text-sm font-medium rounded-xl border-2 border-indigo-500 bg-indigo-50 text-indigo-700'
                    : slot.status === 'AVAILABLE'
                      ? 'px-3 py-2 text-sm rounded-xl border border-slate-200 text-slate-700 hover:border-indigo-300 hover:bg-indigo-50/50 transition-colors'
                      : 'px-3 py-2 text-sm rounded-xl border border-slate-100 bg-slate-50 text-slate-300 cursor-not-allowed'"
                  [disabled]="slot.status !== 'AVAILABLE'"
                  (click)="selectSlot(slot)">
                  {{ slot.startTime }}
                </button>
              }
            </div>
          }
        </div>
      }

      <!-- Confirm -->
      @if (selectedSlot()) {
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
          <h3 class="font-semibold text-slate-800 mb-3">Confirm Booking</h3>
          <div class="bg-indigo-50 rounded-xl p-4 mb-4 space-y-1 text-sm">
            <div class="flex justify-between">
              <span class="text-slate-500">Doctor</span>
              <span class="font-medium text-slate-800">Dr. {{ doctor()?.fullName }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate-500">Date</span>
              <span class="font-medium text-slate-800">{{ selectedDate }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-slate-500">Time</span>
              <span class="font-medium text-slate-800">{{ selectedSlot()!.startTime }} – {{ selectedSlot()!.endTime }}</span>
            </div>
            <div class="flex justify-between pt-1 border-t border-indigo-100 mt-1">
              <span class="text-slate-500">Fee</span>
              <span class="font-bold text-indigo-600">₹{{ doctor()?.consultationFee ?? 0 }}</span>
            </div>
          </div>

          @if (bookingError()) {
            <div class="mb-3 px-4 py-3 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm">
              {{ bookingError() }}
            </div>
          }

          @if (bookingSuccess()) {
            <div class="mb-3 px-4 py-3 bg-emerald-50 border border-emerald-200 rounded-lg text-emerald-600 text-sm font-medium">
              Appointment booked! Redirecting to your appointments...
            </div>
          }

          <button (click)="confirmBooking()" [disabled]="booking() || bookingSuccess()"
            class="w-full py-3 bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 text-white font-semibold rounded-xl transition-colors">
            @if (booking()) { Processing... } @else { Confirm Appointment }
          </button>
        </div>
      }
    </div>
  `
})
export class BookAppointmentComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private doctorService = inject(DoctorService);
  private appointmentService = inject(AppointmentService);
  private auth = inject(AuthService);

  doctor = signal<DoctorSearchResponse | null>(null);
  slots = signal<SlotResponse[]>([]);
  selectedSlot = signal<SlotResponse | null>(null);
  slotsLoading = signal(false);
  booking = signal(false);
  bookingSuccess = signal(false);
  bookingError = signal('');
  selectedDate = '';
  minDate = new Date().toISOString().split('T')[0];

  private doctorId = '';

  ngOnInit() {
    this.doctorId = this.route.snapshot.paramMap.get('doctorId')!;
    this.doctorService.search(undefined, false).subscribe({
      next: (docs) => {
        const found = docs?.find(d => d.id === this.doctorId) ?? null;
        this.doctor.set(found);
      }
    });
  }

  onDateChange() {
    this.selectedSlot.set(null);
    if (!this.selectedDate) return;
    this.slotsLoading.set(true);
    this.appointmentService.getSlots(this.doctorId, this.selectedDate).subscribe({
      next: (data) => { this.slots.set(data ?? []); this.slotsLoading.set(false); },
      error: () => { this.slots.set([]); this.slotsLoading.set(false); }
    });
  }

  selectSlot(slot: SlotResponse) {
    if (slot.status === 'AVAILABLE') this.selectedSlot.set(slot);
  }

  confirmBooking() {
    const slot = this.selectedSlot();
    if (!slot) return;
    this.booking.set(true);
    this.bookingError.set('');

    const patientId = this.auth.userId;
    this.appointmentService.book(slot.id, patientId, this.doctorId).subscribe({
      next: () => {
        this.booking.set(false);
        this.bookingSuccess.set(true);
        setTimeout(() => this.router.navigate(['/appointments']), 1500);
      },
      error: (err) => {
        this.booking.set(false);
        this.bookingError.set(err?.error?.message || 'Booking failed. Slot may already be taken.');
      }
    });
  }

  goBack() { this.router.navigate(['/find-doctors']); }
}
