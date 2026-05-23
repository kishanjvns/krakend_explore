import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const oauth = inject(OAuthService);
  const router = inject(Router);

  // Signal is set on returning visits; direct storage check handles the immediate
  // page load after the OAuth callback redirect (async init not yet complete).
  if (auth.isAuthenticated() || oauth.hasValidAccessToken()) {
    if (!auth.isAuthenticated()) auth.isAuthenticated.set(true);
    return true;
  }

  router.navigate(['/']);
  return false;
};
