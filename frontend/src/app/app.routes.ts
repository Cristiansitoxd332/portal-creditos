import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthService } from './core/auth.service';
import { invitadoGuard, rolGuard, sesionGuard } from './core/guards';

/**
 * Rutas de la aplicacion. Cada pantalla se carga "bajo demanda" (lazy loading):
 * el navegador solo descarga el codigo de la pantalla cuando el usuario la abre.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: () => inject(AuthService).rutaInicio() },
  { path: 'login', canActivate: [invitadoGuard], loadComponent: () => import('./pages/publico/login').then((m) => m.Login) },
  { path: 'cambiar-password', loadComponent: () => import('./pages/publico/cambiar-password').then((m) => m.CambiarPassword) },
  { path: 'verificar', loadComponent: () => import('./pages/publico/verificar').then((m) => m.Verificar) },
  { path: 'verificar/:codigo', loadComponent: () => import('./pages/publico/verificar').then((m) => m.Verificar) },

  {
    path: 'cliente',
    canActivate: [sesionGuard, rolGuard('CLIENTE')],
    loadComponent: () => import('./layout/cliente-layout').then((m) => m.ClienteLayout),
    children: [
      { path: '', title: 'Inicio · Rentar', loadComponent: () => import('./pages/cliente/inicio').then((m) => m.Inicio) },
      { path: 'creditos/:id', title: 'Mi crédito · Rentar', loadComponent: () => import('./pages/cliente/credito-detalle').then((m) => m.CreditoDetallePage) },
      { path: 'creditos/:id/pagar', title: 'Pagar · Rentar', loadComponent: () => import('./pages/cliente/pagar').then((m) => m.Pagar) },
      { path: 'creditos/:id/simulador', title: 'Simulador · Rentar', loadComponent: () => import('./pages/cliente/simulador').then((m) => m.Simulador) },
      { path: 'creditos/:id/documentos', title: 'Paz y salvo · Rentar', loadComponent: () => import('./pages/cliente/documentos').then((m) => m.Documentos) },
      { path: 'pagos', title: 'Mis pagos · Rentar', loadComponent: () => import('./pages/cliente/mis-pagos').then((m) => m.MisPagos) },
      { path: 'contacto', title: 'Contacto · Rentar', loadComponent: () => import('./pages/cliente/contacto').then((m) => m.Contacto) },
      { path: 'perfil', title: 'Mi perfil · Rentar', loadComponent: () => import('./pages/cliente/perfil').then((m) => m.PerfilPage) },
    ],
  },

  {
    path: 'admin',
    canActivate: [sesionGuard, rolGuard('ADMIN')],
    loadComponent: () => import('./layout/admin-layout').then((m) => m.AdminLayout),
    children: [
      { path: '', title: 'Tablero · Administración', loadComponent: () => import('./pages/admin/tablero').then((m) => m.TableroPage) },
      { path: 'clientes', title: 'Clientes · Administración', loadComponent: () => import('./pages/admin/clientes').then((m) => m.Clientes) },
      { path: 'creditos', title: 'Créditos · Administración', loadComponent: () => import('./pages/admin/creditos').then((m) => m.Creditos) },
      { path: 'creditos/nuevo', title: 'Nuevo crédito · Administración', loadComponent: () => import('./pages/admin/nuevo-credito').then((m) => m.NuevoCredito) },
      { path: 'creditos/:id', title: 'Crédito · Administración', loadComponent: () => import('./pages/admin/credito-admin').then((m) => m.CreditoAdmin) },
      { path: 'solicitudes', title: 'Solicitudes · Administración', loadComponent: () => import('./pages/admin/solicitudes').then((m) => m.Solicitudes) },
      { path: 'importar', title: 'Importar · Administración', loadComponent: () => import('./pages/admin/importar').then((m) => m.Importar) },
      { path: 'configuracion', title: 'Configuración · Administración', loadComponent: () => import('./pages/admin/configuracion').then((m) => m.Configuracion) },
      { path: 'auditoria', title: 'Auditoría · Administración', loadComponent: () => import('./pages/admin/auditoria').then((m) => m.AuditoriaPage) },
    ],
  },

  { path: '**', redirectTo: '' },
];
