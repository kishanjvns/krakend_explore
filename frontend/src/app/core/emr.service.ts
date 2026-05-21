import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api.config';
import { EmrState, EmrEvent } from './models';

@Injectable({ providedIn: 'root' })
export class EmrService {
  private http = inject(HttpClient);

  getCurrentState(patientId: string): Observable<EmrState> {
    return this.http.get<EmrState>(`${API_BASE}/emr/patients/${patientId}/current`);
  }

  getHistory(patientId: string): Observable<EmrEvent[]> {
    return this.http.get<EmrEvent[]>(`${API_BASE}/emr/patients/${patientId}/history`);
  }

  getAsOf(patientId: string, date: string): Observable<EmrState> {
    return this.http.get<EmrState>(`${API_BASE}/emr/patients/${patientId}/as-of?date=${date}`);
  }

  appendEvent(patientId: string, eventType: string, payload: any): Observable<any> {
    return this.http.post<any>(`${API_BASE}/emr/patients/${patientId}/events/${eventType}`, payload);
  }
}
