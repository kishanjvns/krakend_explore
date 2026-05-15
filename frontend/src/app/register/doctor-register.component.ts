import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from '../core/api.config';

@Component({
  selector: 'app-doctor-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-indigo-900 flex items-center justify-center px-4 py-10">
      <div class="w-full max-w-lg">

        <!-- Logo -->
        <div class="flex items-center justify-center space-x-2 mb-8">
          <div class="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-400 to-indigo-400 flex items-center justify-center">
            <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4"/>
            </svg>
          </div>
          <span class="text-2xl font-bold text-white">Mediq</span>
        </div>

        <div class="bg-white/10 backdrop-blur-md border border-white/20 rounded-2xl p-8">
          <h2 class="text-2xl font-bold text-white mb-1">Join as a doctor</h2>
          <p class="text-blue-200/70 text-sm mb-6">Your profile will be reviewed and verified by our admin team</p>

          @if (error()) {
            <div class="mb-4 px-4 py-3 bg-red-500/20 border border-red-500/40 rounded-lg text-red-200 text-sm">
              {{ error() }}
            </div>
          }

          <form (ngSubmit)="submit()" #f="ngForm" class="space-y-4">
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-medium text-blue-200 mb-1">First name</label>
                <input name="firstName" [(ngModel)]="form.firstName" required
                  class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                  placeholder="Arvind"/>
              </div>
              <div>
                <label class="block text-xs font-medium text-blue-200 mb-1">Last name</label>
                <input name="lastName" [(ngModel)]="form.lastName" required
                  class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                  placeholder="Kumar"/>
              </div>
            </div>

            <div>
              <label class="block text-xs font-medium text-blue-200 mb-1">Email</label>
              <input name="email" type="email" [(ngModel)]="form.email" required
                class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                placeholder="dr.arvind@example.com"/>
            </div>

            <div>
              <label class="block text-xs font-medium text-blue-200 mb-1">Password</label>
              <input name="password" type="password" [(ngModel)]="form.password" required minlength="8"
                class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                placeholder="Min 8 characters"/>
            </div>

            <div>
              <label class="block text-xs font-medium text-blue-200 mb-1">Date of birth</label>
              <input name="dateOfBirth" type="date" [(ngModel)]="form.dateOfBirth" required
                class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors [color-scheme:dark]"/>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-xs font-medium text-blue-200 mb-1">Medical license no.</label>
                <input name="licenseNumber" [(ngModel)]="form.licenseNumber" required
                  class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                  placeholder="MCI-2024-12345"/>
              </div>
              <div>
                <label class="block text-xs font-medium text-blue-200 mb-1">License expiry</label>
                <input name="licenseExpiry" type="date" [(ngModel)]="form.licenseExpiry" required
                  class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors [color-scheme:dark]"/>
              </div>
            </div>

            <div>
              <label class="block text-xs font-medium text-blue-200 mb-1">Years of experience</label>
              <input name="yearsOfExperience" type="number" min="0" [(ngModel)]="form.yearsOfExperience"
                class="w-full px-3 py-2.5 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/30 text-sm focus:outline-none focus:border-indigo-400 focus:bg-white/15 transition-colors"
                placeholder="5"/>
            </div>

            <button type="submit" [disabled]="loading() || f.invalid"
              class="w-full py-3 bg-indigo-500 hover:bg-indigo-400 disabled:opacity-50 disabled:cursor-not-allowed text-white font-semibold rounded-xl transition-colors mt-2">
              @if (loading()) { Submitting... } @else { Submit registration }
            </button>
          </form>

          <div class="mt-6 text-center">
            <p class="text-blue-200/60 text-sm">
              Are you a patient?
              <a routerLink="/register/patient" class="text-indigo-300 hover:text-indigo-200 font-medium ml-1">Register as patient</a>
            </p>
          </div>
        </div>

        <p class="text-center text-blue-200/40 text-xs mt-4">
          Your profile will be verified before you can accept appointments.
        </p>
      </div>
    </div>
  `
})
export class DoctorRegisterComponent {
  private http = inject(HttpClient);
  private router = inject(Router);

  specializations = [
    'General Physician', 'Cardiology', 'Dermatology', 'Endocrinology',
    'Gastroenterology', 'Neurology', 'Oncology', 'Ophthalmology',
    'Orthopedics', 'Pediatrics', 'Psychiatry', 'Pulmonology',
    'Radiology', 'Surgery', 'Urology'
  ];

  form = {
    firstName: '', lastName: '', email: '', password: '',
    dateOfBirth: '', licenseNumber: '', licenseExpiry: '', yearsOfExperience: 0
  };
  loading = signal(false);
  error = signal('');

  submit() {
    this.loading.set(true);
    this.error.set('');

    const payload = {
      firstName: this.form.firstName,
      lastName: this.form.lastName,
      dateOfBirth: this.form.dateOfBirth,
      password: this.form.password,
      licenseNumber: this.form.licenseNumber,
      licenseExpiry: this.form.licenseExpiry,
      yearsOfExperience: this.form.yearsOfExperience,
      contacts: [{ contactType: 'EMAIL', contactValue: this.form.email, isPrimary: true }],
      addresses: []
    };

    this.http.post(`${API_BASE}/users/doctors/register`, payload).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/'], { queryParams: { registered: 'doctor' } });
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || err?.error?.error || 'Registration failed. Please try again.');
      }
    });
  }
}
