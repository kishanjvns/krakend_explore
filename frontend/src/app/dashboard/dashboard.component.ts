import { Component } from '@angular/core';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  template: `
    <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6 relative overflow-hidden group">
      <div class="absolute -right-10 -top-10 w-32 h-32 bg-indigo-50 rounded-full group-hover:scale-150 transition-transform duration-500 ease-in-out z-0"></div>
      
      <div class="relative z-10">
        <h3 class="text-xl font-bold text-slate-800 mb-2">Secure Dashboard Overview</h3>
        <p class="text-slate-600 mb-6">
          You have successfully authenticated via Keycloak and PKCE. Your role-based navigation is active.
        </p>
        
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div class="p-4 rounded-lg bg-blue-50 border border-blue-100">
            <div class="text-blue-500 font-semibold text-sm uppercase tracking-wide">Appointments</div>
            <div class="text-3xl font-bold text-slate-800 mt-1">12</div>
          </div>
          <div class="p-4 rounded-lg bg-emerald-50 border border-emerald-100">
            <div class="text-emerald-600 font-semibold text-sm uppercase tracking-wide">Patients</div>
            <div class="text-3xl font-bold text-slate-800 mt-1">48</div>
          </div>
          <div class="p-4 rounded-lg bg-purple-50 border border-purple-100">
            <div class="text-purple-600 font-semibold text-sm uppercase tracking-wide">Pending Tasks</div>
            <div class="text-3xl font-bold text-slate-800 mt-1">3</div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class DashboardComponent {}
