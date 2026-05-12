import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from './api.config';
import { NotificationResponse } from './models';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);

  getForUser(userId: string) {
    return this.http.get<NotificationResponse[]>(`${API_BASE}/notifications/user/${userId}`);
  }
}
