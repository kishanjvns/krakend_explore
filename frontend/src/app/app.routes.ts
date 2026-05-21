import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { roleGuard } from './auth/role.guard';
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
    path: 'verify-otp/:userId',
    loadComponent: () => import('./register/otp-verification.component').then(m => m.OtpVerificationComponent)
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
      {
        path: 'dashboard',
        loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'profile',
        loadComponent: () => import('./profile/user-profile.component').then(m => m.UserProfileComponent)
      },
      {
        path: 'book/:doctorId',
        loadComponent: () => import('./book/book-appointment.component').then(m => m.BookAppointmentComponent)
      },
      {
        path: 'appointments',
        loadComponent: () => import('./appointments/my-appointments.component').then(m => m.MyAppointmentsComponent)
      },
      {
        path: 'schedule',
        loadComponent: () => import('./schedule/doctor-schedule.component').then(m => m.DoctorScheduleComponent)
      },
      {
        path: 'notifications',
        loadComponent: () => import('./notifications/notifications.component').then(m => m.NotificationsComponent)
      },
      {
        path: 'emr',
        loadComponent: () => import('./emr/patient-emr.component').then(m => m.PatientEmrComponent)
      },
      {
        path: 'patients/:patientId/emr',
        loadComponent: () => import('./emr/doctor-emr.component').then(m => m.DoctorEmrComponent)
      },
      {
        path: 'patients/:patientId/summary',
        loadComponent: () => import('./patient-summary/patient-summary.component').then(m => m.PatientSummaryComponent)
      },
      {
        path: 'payment',
        loadComponent: () => import('./payment/payment.component').then(m => m.PaymentComponent)
      },
      {
        path: 'analytics',
        loadComponent: () => import('./analytics/analytics.component').then(m => m.AnalyticsComponent)
      },
      {
        path: 'admin/verify',
        loadComponent: () => import('./admin/doctor-verification.component').then(m => m.DoctorVerificationComponent),
        canActivate: [roleGuard],
        data: { role: 'ADMIN' }
      },
    ]
  },
  { path: '**', redirectTo: '' }
];
