import { Injectable, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { authConfig } from './auth.config';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  public identityClaims = signal<any>(null);
  public isAuthenticated = signal<boolean>(false);

  constructor(private oauthService: OAuthService) {
    this.oauthService.configure(authConfig);
    this.oauthService.setupAutomaticSilentRefresh();
    this.oauthService.loadDiscoveryDocumentAndTryLogin().then(() => {
      if (this.oauthService.hasValidAccessToken()) {
        this.isAuthenticated.set(true);
        this.identityClaims.set(this.oauthService.getIdentityClaims());
      }
    }).catch(err => console.error('OAuth Error:', err));
  }

  public login() {
    this.oauthService.initCodeFlow();
  }

  public logout() {
    this.oauthService.logOut();
    this.isAuthenticated.set(false);
    this.identityClaims.set(null);
  }

  public get userName(): string {
    const claims = this.identityClaims();
    if (!claims) return 'Guest';
    return claims['name'] || claims['preferred_username'] || 'User';
  }

  public get userRoles(): string[] {
    const claims = this.identityClaims();
    if (!claims || !claims['realm_access']) return [];
    return claims['realm_access']['roles'] || [];
  }
}
