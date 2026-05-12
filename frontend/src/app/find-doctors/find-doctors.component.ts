import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DoctorService } from '../core/doctor.service';
import { AuthService } from '../auth/auth.service';
import { DoctorSearchResponse } from '../core/models';

@Component({
  selector: 'app-find-doctors',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-slate-50">

      <!-- Header bar -->
      <header class="bg-white border-b border-slate-200 px-6 py-4 flex items-center justify-between">
        <div class="flex items-center space-x-3">
          <div class="w-8 h-8 rounded-lg bg-gradient-to-tr from-blue-500 to-indigo-500 flex items-center justify-center">
            <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4"/>
            </svg>
          </div>
          <span class="text-xl font-bold text-slate-800">Mediq</span>
        </div>
        <div class="flex items-center space-x-3">
          @if (auth.isAuthenticated()) {
            <a routerLink="/dashboard" class="px-4 py-2 text-sm text-indigo-600 hover:text-indigo-700 font-medium">Dashboard</a>
          } @else {
            <a routerLink="/" class="px-4 py-2 text-sm text-slate-600 hover:text-slate-800">Sign In</a>
            <a routerLink="/register/patient" class="px-4 py-2 text-sm bg-indigo-500 hover:bg-indigo-600 text-white rounded-lg font-medium transition-colors">Register</a>
          }
        </div>
      </header>

      <div class="max-w-6xl mx-auto px-6 py-8">

        <!-- Search bar -->
        <div class="bg-white rounded-2xl shadow-sm border border-slate-200 p-6 mb-8">
          <h1 class="text-2xl font-bold text-slate-800 mb-4">Find Doctors</h1>
          <div class="flex gap-3 flex-col sm:flex-row">
            <select [(ngModel)]="selectedSpecialization" (ngModelChange)="search()"
              class="flex-1 px-4 py-2.5 border border-slate-200 rounded-xl text-slate-700 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-300 focus:border-indigo-400 bg-white">
              <option value="">All Specializations</option>
              @for (s of specializations; track s) {
                <option [value]="s">{{ s }}</option>
              }
            </select>
            <button (click)="search()"
              class="px-6 py-2.5 bg-indigo-500 hover:bg-indigo-600 text-white font-medium rounded-xl transition-colors text-sm">
              Search
            </button>
          </div>
        </div>

        <!-- Results -->
        @if (loading()) {
          <div class="flex items-center justify-center py-20">
            <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
          </div>
        } @else if (doctors().length === 0) {
          <div class="text-center py-20 text-slate-400">
            <svg class="w-12 h-12 mx-auto mb-3 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
            </svg>
            <p class="text-lg font-medium">No doctors found</p>
            <p class="text-sm mt-1">Try a different specialization</p>
          </div>
        } @else {
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            @for (doc of doctors(); track doc.id) {
              <div class="bg-white rounded-2xl border border-slate-200 shadow-sm hover:shadow-md transition-shadow p-6">
                <!-- Avatar + name -->
                <div class="flex items-start space-x-4 mb-4">
                  <div class="w-14 h-14 rounded-2xl bg-gradient-to-tr from-blue-100 to-indigo-100 flex items-center justify-center shrink-0">
                    <span class="text-xl font-bold text-indigo-600">{{ doc.fullName.charAt(0) }}</span>
                  </div>
                  <div class="min-w-0">
                    <h3 class="font-semibold text-slate-800 truncate">Dr. {{ doc.fullName }}</h3>
                    <p class="text-sm text-indigo-600 font-medium">{{ doc.primarySpecialization }}</p>
                    @if (doc.verified) {
                      <span class="inline-flex items-center space-x-1 text-xs text-emerald-600 font-medium mt-0.5">
                        <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
                        <span>Verified</span>
                      </span>
                    }
                  </div>
                </div>

                <!-- Stats -->
                <div class="grid grid-cols-2 gap-3 mb-4">
                  <div class="bg-slate-50 rounded-lg px-3 py-2">
                    <p class="text-xs text-slate-500">Experience</p>
                    <p class="text-sm font-semibold text-slate-700">{{ doc.yearsOfExperience }} yrs</p>
                  </div>
                  <div class="bg-slate-50 rounded-lg px-3 py-2">
                    <p class="text-xs text-slate-500">Consultation</p>
                    <p class="text-sm font-semibold text-slate-700">₹{{ doc.consultationFee }}</p>
                  </div>
                </div>

                <!-- Specializations -->
                @if (doc.allSpecializations && doc.allSpecializations.length > 1) {
                  <div class="flex flex-wrap gap-1 mb-4">
                    @for (spec of doc.allSpecializations.slice(0, 3); track spec) {
                      <span class="text-xs px-2 py-0.5 bg-indigo-50 text-indigo-600 rounded-full">{{ spec }}</span>
                    }
                  </div>
                }

                <!-- Action buttons -->
                <div class="flex gap-2 mt-2">
                  <a [routerLink]="['/doctors', doc.id]"
                    class="flex-1 text-center py-2 text-sm text-indigo-600 hover:text-indigo-700 border border-indigo-200 hover:border-indigo-300 rounded-lg transition-colors font-medium">
                    View Profile
                  </a>
                  <button (click)="bookNow(doc)"
                    class="flex-1 py-2 text-sm bg-indigo-500 hover:bg-indigo-600 text-white rounded-lg transition-colors font-medium">
                    Book Now
                  </button>
                </div>
              </div>
            }
          </div>
        }
      </div>
    </div>
  `
})
export class FindDoctorsComponent implements OnInit {
  private doctorService = inject(DoctorService);
  private router = inject(Router);
  auth = inject(AuthService);

  specializations = [
    'General Physician', 'Cardiology', 'Dermatology', 'Endocrinology',
    'Gastroenterology', 'Neurology', 'Oncology', 'Ophthalmology',
    'Orthopedics', 'Pediatrics', 'Psychiatry', 'Pulmonology',
    'Radiology', 'Surgery', 'Urology'
  ];

  selectedSpecialization = '';
  doctors = signal<DoctorSearchResponse[]>([]);
  loading = signal(true);

  ngOnInit() { this.search(); }

  search() {
    this.loading.set(true);
    this.doctorService.search(this.selectedSpecialization || undefined, true).subscribe({
      next: (data) => { this.doctors.set(data ?? []); this.loading.set(false); },
      error: () => { this.doctors.set([]); this.loading.set(false); }
    });
  }

  bookNow(doc: DoctorSearchResponse) {
    if (!this.auth.isAuthenticated()) {
      this.router.navigate(['/register/patient']);
    } else {
      this.router.navigate(['/book', doc.id]);
    }
  }
}
