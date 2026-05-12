import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { LayoutComponent } from './shared/layout/layout.component';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./landing/landing.component').then(m => m.LandingComponent),
    pathMatch: 'full'
  },
  {
    path: 'register/patient',
    loadComponent: () => import('./register/patient-register.component').then(m => m.PatientRegisterComponent)
  },
  {
    path: 'register/doctor',
    loadComponent: () => import('./register/doctor-register.component').then(m => m.DoctorRegisterComponent)
  },
  {
    path: 'find-doctors',
    loadComponent: () => import('./find-doctors/find-doctors.component').then(m => m.FindDoctorsComponent)
  },
  {
    path: 'doctors/:id',
    loadComponent: () => import('./doctors/doctor-profile.component').then(m => m.DoctorProfileComponent)
  },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'book/:doctorId', loadComponent: () => import('./book/book-appointment.component').then(m => m.BookAppointmentComponent) },
      { path: 'appointments', loadComponent: () => import('./appointments/my-appointments.component').then(m => m.MyAppointmentsComponent) },
      { path: 'schedule', loadComponent: () => import('./schedule/doctor-schedule.component').then(m => m.DoctorScheduleComponent) },
      { path: 'admin/verify', loadComponent: () => import('./admin/doctor-verification.component').then(m => m.DoctorVerificationComponent) },
      { path: 'notifications', loadComponent: () => import('./notifications/notifications.component').then(m => m.NotificationsComponent) },
    ]
  },
  { path: '**', redirectTo: '' }
];
