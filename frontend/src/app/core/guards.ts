import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Rol } from './models';

/**
 * Guardas de ruta (proteccion en el frontend). La proteccion REAL esta en el backend:
 * aunque alguien manipule el navegador, la API rechaza las peticiones sin el rol correcto.
 */

/** Exige sesion iniciada; si debe cambiar la contrasena, lo lleva a esa pantalla. */
export const sesionGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.token) {
    return router.parseUrl('/login');
  }
  if (auth.usuario()?.debeCambiarPassword) {
    return router.parseUrl('/cambiar-password');
  }
  return true;
};

/** Exige un rol especifico. */
export const rolGuard = (rol: Rol): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.rol() === rol ? true : router.parseUrl(auth.rutaInicio());
};

/** Para la pantalla de login: si ya hay sesion, va directo al inicio. */
export const invitadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.token ? router.parseUrl(auth.rutaInicio()) : true;
};
