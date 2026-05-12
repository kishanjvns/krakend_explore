import { Component, inject, signal, OnInit } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../core/notification.service';
import { NotificationResponse } from '../core/models';

@Component({
  selector: 'app-notifications',
  standalone: true,
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-bold text-slate-800">Notifications</h1>
        <span class="text-sm text-slate-400">{{ notifications().length }} total</span>
      </div>

      @if (loading()) {
        <div class="flex items-center justify-center py-20">
          <div class="w-8 h-8 border-4 border-indigo-200 border-t-indigo-500 rounded-full animate-spin"></div>
        </div>
      } @else if (notifications().length === 0) {
        <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-12 text-center">
          <svg class="w-14 h-14 mx-auto mb-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
          </svg>
          <h3 class="font-semibold text-slate-700 mb-1">No notifications yet</h3>
          <p class="text-sm text-slate-400">You'll see appointment updates and alerts here.</p>
        </div>
      } @else {
        <div class="space-y-3">
          @for (n of notifications(); track n.id) {
            <div class="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 hover:border-indigo-200 transition-colors">
              <div class="flex items-start justify-between">
                <div class="flex items-start space-x-4">
                  <div [class]="channelIconClass(n.channel) + ' w-10 h-10 rounded-xl flex items-center justify-center shrink-0'">
                    @if (n.channel === 'EMAIL') {
                      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/>
                      </svg>
                    } @else {
                      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z"/>
                      </svg>
                    }
                  </div>
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center space-x-2 mb-1">
                      <p class="font-medium text-slate-800 text-sm">{{ n.subject }}</p>
                      <span [class]="statusClass(n.status) + ' text-xs px-2 py-0.5 rounded-full font-medium'">{{ n.status }}</span>
                    </div>
                    <p class="text-sm text-slate-500 leading-relaxed">{{ n.body }}</p>
                    <p class="text-xs text-slate-400 mt-2">{{ formatDate(n.createdAt) }}</p>
                  </div>
                </div>
                <span class="text-xs px-2 py-0.5 bg-slate-100 text-slate-500 rounded-full ml-3 shrink-0">{{ n.channel }}</span>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class NotificationsComponent implements OnInit {
  auth = inject(AuthService);
  private notificationService = inject(NotificationService);

  notifications = signal<NotificationResponse[]>([]);
  loading = signal(true);

  ngOnInit() {
    const userId = this.auth.userId;
    if (!userId) { this.loading.set(false); return; }
    this.notificationService.getForUser(userId).subscribe({
      next: (data) => { this.notifications.set(data ?? []); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  channelIconClass(channel: string) {
    return channel === 'EMAIL' ? 'bg-blue-50 text-blue-500' : 'bg-emerald-50 text-emerald-500';
  }

  statusClass(status: string) {
    switch (status) {
      case 'SENT': return 'bg-emerald-50 text-emerald-600';
      case 'FAILED': return 'bg-red-50 text-red-600';
      case 'PENDING': return 'bg-orange-50 text-orange-600';
      default: return 'bg-slate-100 text-slate-600';
    }
  }

  formatDate(dateStr: string) {
    if (!dateStr) return '';
    try {
      return new Date(dateStr).toLocaleString('en-IN', {
        day: 'numeric', month: 'short', year: 'numeric',
        hour: '2-digit', minute: '2-digit'
      });
    } catch { return dateStr; }
  }
}
