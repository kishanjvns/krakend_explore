import { inject } from '@angular/core';
import { Router, type CanActivateFn, ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from './auth.service';

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    router.navigate(['/']);
    return false;
  }

  const requiredRole: string | undefined = route.data?.['role'];

  if (requiredRole) {
    const userRole = auth.role || auth.userType;
    if (userRole !== requiredRole) {
      router.navigate(['/dashboard']);
      return false;
    }
  } else {
    // Default admin guard behaviour when no specific role data provided
    if (!auth.isAdmin()) {
      router.navigate(['/dashboard']);
      return false;
    }
  }

  return true;
};
