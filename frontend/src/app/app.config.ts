import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling, withViewTransitions } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),                                 // parametros de ruta como inputs (ej. id)
      withViewTransitions(),                                       // transiciones suaves entre pantallas
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }), // cada pantalla inicia arriba
    ),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
  ],
};
