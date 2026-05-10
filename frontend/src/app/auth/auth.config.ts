import { AuthConfig } from 'angular-oauth2-oidc';

export const authConfig: AuthConfig = {
  issuer: 'http://localhost:8090/realms/mediq', // Based on the properties we saw in user-service
  redirectUri: window.location.origin,
  clientId: 'mediq-frontend-spa',
  responseType: 'code',
  scope: 'openid profile email roles',
  showDebugInformation: true,
  requireHttps: false, // Allow HTTP for local development
  // Ensures PKCE is used
  strictDiscoveryDocumentValidation: false,
};
