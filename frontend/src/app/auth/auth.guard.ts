import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    authService.login();
    return false;
  }

  const requiredRoles = route.data['roles'] as Array<string>;
  if (requiredRoles && requiredRoles.length > 0) {
    const userRoles = authService.userRoles;
    const hasRole = requiredRoles.some(role => userRoles.includes(role));
    if (!hasRole) {
      // In a real app, route to an unauthorized page
      console.warn('Unauthorized access attempt');
      router.navigate(['/']); 
      return false;
    }
  }

  return true;
};
