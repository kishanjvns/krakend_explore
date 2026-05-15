import { Injectable, signal, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { OAuthService } from 'angular-oauth2-oidc';
import { authConfig } from './auth.config';
import { UserResponse } from '../core/models';
import { API_BASE } from '../core/api.config';

@Injectable({ providedIn: 'root' })
export class AuthService {
  public isAuthenticated = signal(false);
  public currentUser = signal<UserResponse | null>(null);

  private oauthService = inject(OAuthService);
  private router = inject(Router);
  private http = inject(HttpClient);

  constructor() {
    this.oauthService.configure(authConfig);
    this.oauthService.setupAutomaticSilentRefresh();
    this.oauthService.loadDiscoveryDocumentAndTryLogin().then(() => {
      if (this.oauthService.hasValidAccessToken()) {
        this.isAuthenticated.set(true);
        this.loadCurrentUser().then(() => this.router.navigate(['/dashboard']));
      }
    }).catch(err => console.error('OAuth Error:', err));
  }

  private async loadCurrentUser() {
    try {
      const user = await this.http.get<UserResponse>(`${API_BASE}/users/me`).toPromise();
      this.currentUser.set(user ?? null);
    } catch {
      // seed users (dr.mehta etc.) won't have a user-service record — that's fine
    }
  }

  public login() { this.oauthService.initCodeFlow(); }

  public register() {
    const issuer = authConfig.issuer!.replace(/\/$/, '');
    const redirectUri = encodeURIComponent(window.location.origin);
    window.location.href =
      `${issuer}/protocol/openid-connect/registrations` +
      `?client_id=${authConfig.clientId}&response_type=code&scope=openid&redirect_uri=${redirectUri}`;
  }

  public logout() {
    this.oauthService.logOut();
    this.isAuthenticated.set(false);
    this.currentUser.set(null);
    this.router.navigate(['/']);
  }

  public get claims(): any { return this.oauthService.getIdentityClaims() ?? {}; }
  public get userName(): string { return this.claims['name'] || this.claims['preferred_username'] || 'User'; }
  public get userEmail(): string { return this.claims['email'] || ''; }
  public get role(): string { return this.claims['role'] || (this.claims['realm_access']?.roles?.[0]) || ''; }
  public get userId(): string { return this.claims['userId'] || this.currentUser()?.id || ''; }
  public get userSub(): string { return this.claims['sub'] || ''; }
  public get userType(): string { return this.claims['userType'] || this.role; }
  public get permissions(): string[] {
    const raw = this.claims['permissions'];
    if (!raw) return [];
    return (raw as string).split(',').map((p: string) => p.trim()).filter(Boolean);
  }
  public get doctorProfileId(): string { return this.currentUser()?.doctorProfile?.id || ''; }

  public hasPermission(permission: string): boolean { return this.permissions.includes(permission); }
  public isPatient() { return this.role === 'PATIENT'; }
  public isDoctor() { return this.role === 'DOCTOR'; }
  public isAdmin() { return this.role === 'ADMIN'; }
}
