import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE } from './api.config';
import { DoctorSearchResponse, DoctorAvailability, PatientSummary } from './models';

@Injectable({ providedIn: 'root' })
export class DoctorService {
  private http = inject(HttpClient);

  search(specialization?: string, verified = true) {
    let params = new HttpParams().set('verified', String(verified));
    if (specialization) params = params.set('specialization', specialization);
    return this.http.get<DoctorSearchResponse[]>(`${API_BASE}/doctors/search`, { params });
  }

  getDoctorProfile(doctorId: string) {
    return this.http.get<DoctorSearchResponse>(`${API_BASE}/doctors/${doctorId}`);
  }

  getAvailability(doctorId: string) {
    return this.http.get<DoctorAvailability[]>(`${API_BASE}/doctors/${doctorId}/availability`);
  }

  setAvailability(doctorId: string, body: { dayOfWeek: string; startTime: string; endTime: string; slotDuration: number }) {
    return this.http.post<void>(`${API_BASE}/doctors/${doctorId}/availability`, body);
  }

  addSpecialization(doctorId: string, name: string, isPrimary: boolean) {
    return this.http.post<void>(`${API_BASE}/doctors/${doctorId}/specializations`, { name, isPrimary });
  }

  getPatientSummary(patientId: string) {
    return this.http.get<PatientSummary>(`${API_BASE}/patient-summary/${patientId}`);
  }
}
