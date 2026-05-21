import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api.config';
import { AnalyticsDashboard, DailyAppointment, DoctorPerformance } from './models';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);

  getDashboard(): Observable<AnalyticsDashboard> {
    return this.http.get<AnalyticsDashboard>(`${API_BASE}/analytics/dashboard`);
  }

  getDailyAppointments(): Observable<DailyAppointment[]> {
    return this.http.get<DailyAppointment[]>(`${API_BASE}/analytics/appointments/daily`);
  }

  getDoctorPerformance(): Observable<DoctorPerformance[]> {
    return this.http.get<DoctorPerformance[]>(`${API_BASE}/analytics/doctors/performance`);
  }
}
