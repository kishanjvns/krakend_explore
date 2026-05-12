import { Component, inject, signal, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DoctorService } from '../core/doctor.service';
import { AuthService } from '../auth/auth.service';
import { DoctorAvailability } from '../core/models';

@Component({
  selector: 'app-doctor-profile',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="min-h-screen bg-slate-50">
      <!-- Header -->
      <header class="bg-white border-b border-slate-200 px-6 py-4 flex items-center justify-between">
        <div class="flex items-center space-x-3">
          <a routerLink="/find-doctors" class="text-slate-400 hover:text-slate-600 mr-2">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"/>
            </svg>
          </a>
          <span class="text-lg font-semibold text-slate-800">Doctor Profile</span>
        </div>
        <div class="flex items-center space-x-3">
          @if (auth.isAuthenticated()) {
            <a routerLink="/dashboard" class="text-sm text-indigo-600 font-medium">Dashboard</a>
          }
        </div>
      </header>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else if (!profile()) {
        <div class="text-center py-20 text-slate-400">
          <p class="text-lg font-medium">Doctor not found</p>
          <a routerLink="/find-doctors" class="text-indigo-500 text-sm mt-2 inline-block">Back to search</a>
        </div>
      } @else {
        <div class="max-w-4xl mx-auto px-6 py-8">
          <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">

            <!-- Left: Profile card -->
            <div class="lg:col-span-1">
              <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 text-center">
                <div class="w-20 h-20 mx-auto rounded-2xl bg-gradient-to-tr from-blue-100 to-indigo-100 flex items-center justify-center mb-4">
                  <span class="text-3xl font-bold text-indigo-600">{{ profile()!.fullName.charAt(0) }}</span>
                </div>
                <h2 class="text-xl font-bold text-slate-800">Dr. {{ profile()!.fullName }}</h2>
                <p class="text-indigo-600 font-medium text-sm mt-1">{{ profile()!.primarySpecialization }}</p>

                @if (profile()!.verified) {
                  <div class="inline-flex items-center space-x-1.5 bg-emerald-50 text-emerald-600 text-xs font-medium px-3 py-1 rounded-full mt-3">
                    <svg class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
                    <span>Verified Doctor</span>
                  </div>
                }

                <div class="grid grid-cols-2 gap-3 mt-5">
                  <div class="bg-slate-50 rounded-xl p-3">
                    <p class="text-xs text-slate-500 mb-0.5">Experience</p>
                    <p class="font-bold text-slate-800">{{ profile()!.yearsOfExperience }}<span class="text-xs font-normal text-slate-500 ml-0.5">yrs</span></p>
                  </div>
                  <div class="bg-slate-50 rounded-xl p-3">
                    <p class="text-xs text-slate-500 mb-0.5">Fee</p>
                    <p class="font-bold text-slate-800">₹{{ profile()!.consultationFee }}</p>
                  </div>
                </div>

                <button (click)="bookNow()"
                  class="w-full mt-5 py-3 bg-indigo-500 hover:bg-indigo-600 text-white font-semibold rounded-xl transition-colors">
                  Book Appointment
                </button>
              </div>
            </div>

            <!-- Right: Availability -->
            <div class="lg:col-span-2 space-y-5">
              <!-- Specializations -->
              @if (profile()!.allSpecializations && profile()!.allSpecializations.length) {
                <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                  <h3 class="font-semibold text-slate-800 mb-3">Specializations</h3>
                  <div class="flex flex-wrap gap-2">
                    @for (spec of profile()!.allSpecializations; track spec) {
                      <span class="px-3 py-1 bg-indigo-50 text-indigo-600 rounded-full text-sm font-medium">{{ spec }}</span>
                    }
                  </div>
                </div>
              }

              <!-- Availability -->
              <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
                <h3 class="font-semibold text-slate-800 mb-4">Available Days & Times</h3>
                @if (availabilityLoading()) {
                  <div class="flex items-center space-x-2 text-slate-400 text-sm py-4">
                    <div class="w-5 h-5 border-2 border-slate-200 border-t-slate-400 rounded-full animate-spin"></div>
                    <span>Loading availability...</span>
                  </div>
                } @else if (availability().length === 0) {
                  <p class="text-slate-400 text-sm py-4">No availability schedule set yet.</p>
                } @else {
                  <div class="space-y-3">
                    @for (slot of availability(); track slot.id) {
                      <div class="flex items-center justify-between py-3 border-b border-slate-100 last:border-0">
                        <div class="flex items-center space-x-3">
                          <div class="w-10 h-10 bg-indigo-50 rounded-lg flex items-center justify-center">
                            <span class="text-xs font-bold text-indigo-600">{{ slot.dayOfWeek.slice(0,3) }}</span>
                          </div>
                          <div>
                            <p class="text-sm font-medium text-slate-700">{{ slot.dayOfWeek }}</p>
                            <p class="text-xs text-slate-400">{{ slot.startTime }} – {{ slot.endTime }}</p>
                          </div>
                        </div>
                        <span class="text-xs px-2 py-1 bg-emerald-50 text-emerald-600 rounded-full font-medium">
                          {{ slot.slotDuration }}min slots
                        </span>
                      </div>
                    }
                  </div>
                }
              </div>

              <!-- Book CTA -->
              <div class="bg-gradient-to-r from-indigo-500 to-blue-500 rounded-2xl p-6 text-white">
                <h3 class="font-semibold text-lg mb-1">Ready to book?</h3>
                <p class="text-indigo-100 text-sm mb-4">Choose a convenient slot and confirm your appointment online.</p>
                <button (click)="bookNow()"
                  class="px-6 py-2.5 bg-white text-indigo-600 font-semibold rounded-xl hover:bg-indigo-50 transition-colors text-sm">
                  Book Now
                </button>
              </div>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class DoctorProfileComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private doctorService = inject(DoctorService);
  private router = inject(Router);
  auth = inject(AuthService);

  profile = signal<any>(null);
  availability = signal<DoctorAvailability[]>([]);
  loading = signal(true);
  availabilityLoading = signal(true);

  ngOnInit() {
    const doctorId = this.route.snapshot.paramMap.get('id')!;

    this.doctorService.search(undefined, false).subscribe({
      next: (docs) => {
        const found = docs?.find(d => d.id === doctorId) ?? null;
        this.profile.set(found);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });

    this.doctorService.getAvailability(doctorId).subscribe({
      next: (data) => { this.availability.set(data ?? []); this.availabilityLoading.set(false); },
      error: () => this.availabilityLoading.set(false)
    });
  }

  bookNow() {
    const doctorId = this.route.snapshot.paramMap.get('id')!;
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/register/patient']);
    } else {
      this.router.navigate(['/book', doctorId]);
    }
  }
}
