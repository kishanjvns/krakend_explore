import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE } from './api.config';
import { AppointmentResponse, BookingInitiated, SlotResponse } from './models';

@Injectable({ providedIn: 'root' })
export class AppointmentService {
  private http = inject(HttpClient);

  listByPatient(patientId: string) {
    const params = new HttpParams().set('patientId', patientId);
    return this.http.get<{ appointments: AppointmentResponse[] }>(`${API_BASE}/appointments`, { params });
  }

  listByDoctor(doctorId: string) {
    const params = new HttpParams().set('doctorId', doctorId);
    return this.http.get<{ appointments: AppointmentResponse[] }>(`${API_BASE}/appointments`, { params });
  }

  get(appointmentId: string) {
    return this.http.get<AppointmentResponse>(`${API_BASE}/appointments/${appointmentId}`);
  }

  book(slotId: string, patientId: string, doctorId: string) {
    return this.http.post<BookingInitiated>(`${API_BASE}/appointments`, { slotId, patientId, doctorId, amount: 0 });
  }

  confirm(appointmentId: string) {
    return this.http.put<AppointmentResponse>(`${API_BASE}/appointments/${appointmentId}/confirm`, {});
  }

  cancel(appointmentId: string, reason?: string) {
    return this.http.put<AppointmentResponse>(`${API_BASE}/appointments/${appointmentId}/cancel`, { reason });
  }

  getSlots(doctorId: string, date: string) {
    const params = new HttpParams().set('doctorId', doctorId).set('date', date);
    return this.http.get<SlotResponse[]>(`${API_BASE}/slots`, { params });
  }

  createSlot(doctorId: string, slotDate: string, startTime: string, endTime: string) {
    return this.http.post<SlotResponse>(`${API_BASE}/slots`, { doctorId, slotDate, startTime, endTime });
  }
}
