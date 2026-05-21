import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const oauthService = inject(OAuthService);
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = oauthService.getAccessToken();

  const isGateway = req.url.includes('localhost:8080/api') || req.url.startsWith('/api');

  const isAuthEndpoint = req.url.includes('/auth/logout') ||
    req.url.includes('/auth/login') ||
    req.url.includes('/openid-connect') ||
    req.url.includes('/users/patients/register') ||
    req.url.includes('/users/doctors/register');

  const outReq = (token && isGateway)
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(outReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && isGateway && !isAuthEndpoint) {
        auth.isAuthenticated.set(false);
        auth.currentUser.set(null);
        router.navigate(['/'], { queryParams: { sessionExpired: 'true' } });
      }
      return throwError(() => error);
    })
  );
};
