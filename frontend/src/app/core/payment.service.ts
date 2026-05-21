import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from './api.config';
import { PaymentIntent, Payment } from './models';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private http = inject(HttpClient);

  createIntent(appointmentId: string, amount: number): Observable<PaymentIntent> {
    return this.http.post<PaymentIntent>(`${API_BASE}/payments/intent`, { appointmentId, amount });
  }

  getPayment(paymentId: string): Observable<Payment> {
    return this.http.get<Payment>(`${API_BASE}/payments/${paymentId}`);
  }
}
