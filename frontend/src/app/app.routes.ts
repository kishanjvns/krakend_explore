import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { 
    path: 'dashboard', 
    loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard],
    // Only users with one of these roles can access the dashboard
    data: { roles: ['PATIENT', 'DOCTOR', 'ADMIN', 'NURSE'] }
  },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
];
