import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api.config';
import { Referral } from './models';

@Injectable({ providedIn: 'root' })
export class ReferralService {
  private http = inject(HttpClient);

  getAll(): Observable<Referral[]> {
    return this.http.get<Referral[]>(`${API_BASE}/referrals`);
  }

  getOpen(): Observable<Referral[]> {
    return this.http.get<Referral[]>(`${API_BASE}/referrals/open`);
  }

  getByPatient(patientId: string): Observable<Referral[]> {
    return this.http.get<Referral[]>(`${API_BASE}/referrals/patient/${patientId}`);
  }

  getById(id: string): Observable<Referral> {
    return this.http.get<Referral>(`${API_BASE}/referrals/${id}`);
  }
}
