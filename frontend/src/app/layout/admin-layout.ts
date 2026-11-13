import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { TemaService } from '../core/tema.service';
import { Icono } from '../shared/icono';

/** Estructura del panel administrativo: menu lateral (en celular se abre con un boton). */
@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="marco">
      <aside class="lateral" [class.abierto]="menuAbierto()">
        <div class="marca">
          <span class="logo">R</span>
          <span class="marca-texto"><strong>Rentar</strong><small>Administración</small></span>
        </div>
        <nav aria-label="Menú del administrador">
          @for (item of menu; track item.ruta) {
            <a [routerLink]="item.ruta" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: item.exacto }">
              <app-icono [nombre]="item.icono" [tamano]="18" />
              <span class="texto">{{ item.texto }}</span>
              @if (item.ruta === '/admin/solicitudes' && pendientes() > 0) {
                <span class="insignia" [attr.aria-label]="pendientes() + ' pendientes'">{{ pendientes() }}</span>
              }
              @if (item.ruta === '/admin/mensajes' && avisos() > 0) {
                <span class="insignia" [attr.aria-label]="avisos() + ' avisos por enviar hoy'">{{ avisos() }}</span>
              }
            </a>
          }
        </nav>
        <div class="pie-lateral">
          <button type="button" class="btn btn-fantasma btn-bloque" (click)="tema.alternar()">
            <app-icono [nombre]="tema.oscuro() ? 'sun' : 'moon'" [tamano]="18" />{{ tema.oscuro() ? 'Modo claro' : 'Modo oscuro' }}
          </button>
          <button type="button" class="btn btn-fantasma btn-bloque" (click)="auth.logout()">
            <app-icono nombre="logout" [tamano]="18" />Cerrar sesión
          </button>
        </div>
      </aside>
      @if (menuAbierto()) {
        <button type="button" class="velo" aria-label="Cerrar menú" (click)="menuAbierto.set(false)"></button>
      }

      <div class="principal">
        <header class="barra-movil">
          <button type="button" class="btn btn-icono btn-fantasma" aria-label="Abrir menú" (click)="menuAbierto.set(true)">
            <app-icono nombre="grid" />
          </button>
          <strong>Rentar · Administración</strong>
        </header>
        <main class="contenido">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: [`
    .marco { display: flex; min-height: 100vh; }
    .lateral { width: 248px; flex-shrink: 0; background: var(--superficie); border-right: 1px solid var(--borde); padding: 22px 14px;
      display: flex; flex-direction: column; gap: 4px; position: fixed; top: 0; bottom: 0; left: 0; z-index: 40;
      transform: translateX(-100%); transition: transform 0.2s ease; overflow-y: auto; }
    .lateral.abierto { transform: none; box-shadow: var(--sombra); }
    .velo { position: fixed; inset: 0; z-index: 30; background: rgba(0, 0, 0, 0.35); border: 0; }
    .marca { display: flex; align-items: center; gap: 10px; padding: 0 8px 20px; }
    .logo { width: 36px; height: 36px; border-radius: 10px; background: var(--primario); color: #fff; display: flex; align-items: center;
      justify-content: center; font-weight: 800; font-size: 19px; }
    .marca-texto { display: flex; flex-direction: column; line-height: 1.15; }
    .marca-texto strong { font-size: 16px; font-weight: 800; }
    .marca-texto small { font-size: 11.5px; color: var(--texto-3); }
    nav { display: flex; flex-direction: column; gap: 2px; }
    nav a { display: flex; align-items: center; gap: 10px; min-height: 44px; padding: 0 12px; border-radius: 10px; color: var(--texto-2);
      text-decoration: none; font-weight: 600; font-size: 14px; }
    nav a:hover { background: var(--superficie-2); }
    nav a.activo { background: var(--primario-suave); color: var(--primario-oscuro); font-weight: 700; }
    .texto { flex: 1; }
    .insignia { min-width: 22px; height: 22px; padding: 0 6px; border-radius: 11px; background: var(--primario); color: #fff; font-size: 12px;
      font-weight: 800; display: flex; align-items: center; justify-content: center; }
    .pie-lateral { margin-top: auto; padding-top: 16px; display: flex; flex-direction: column; gap: 4px; }
    .pie-lateral .btn { justify-content: flex-start; font-size: 14px; height: 42px; }
    .principal { flex: 1; min-width: 0; }
    .barra-movil { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; gap: 10px; padding: 8px 12px;
      background: var(--superficie); border-bottom: 1px solid var(--borde); }
    .contenido { padding: 20px 16px 48px; max-width: 1320px; margin: 0 auto; }
    @media (min-width: 1000px) {
      .lateral { transform: none; position: sticky; height: 100vh; }
      .barra-movil, .velo { display: none; }
      .contenido { padding: 28px 32px 48px; }
    }
  `],
})
export class AdminLayout {
  protected readonly auth = inject(AuthService);
  protected readonly tema = inject(TemaService);
  private readonly api = inject(ApiService);
  protected readonly menuAbierto = signal(false);
  protected readonly pendientes = signal(0);
  /** Avisos de hoy (mora y cuotas que vencen hoy) que aun no se han enviado. */
  protected readonly avisos = signal(0);

  protected readonly menu = [
    { ruta: '/admin', texto: 'Tablero', icono: 'grid', exacto: true },
    { ruta: '/admin/clientes', texto: 'Clientes', icono: 'users', exacto: false },
    { ruta: '/admin/creditos', texto: 'Créditos', icono: 'card', exacto: false },
    { ruta: '/admin/abonos', texto: 'Abonos individuales', icono: 'money', exacto: false },
    { ruta: '/admin/historial', texto: 'Historial de créditos', icono: 'file', exacto: false },
    { ruta: '/admin/solicitudes', texto: 'Solicitudes', icono: 'doc-check', exacto: false },
    { ruta: '/admin/mensajes', texto: 'Mensajes', icono: 'bell', exacto: false },
    { ruta: '/admin/importar', texto: 'Importar archivos', icono: 'upload', exacto: false },
    { ruta: '/admin/configuracion', texto: 'Contacto y requisitos', icono: 'settings', exacto: false },
    { ruta: '/admin/auditoria', texto: 'Auditoría', icono: 'shield', exacto: false },
  ];

  constructor() {
    this.actualizarPendientes();
    // Al navegar: cierra el menu en celular y refresca el numero de solicitudes pendientes.
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.menuAbierto.set(false);
      this.actualizarPendientes();
    });
  }

  private actualizarPendientes(): void {
    this.api.tablero().subscribe({ next: (t) => this.pendientes.set(t.solicitudesPendientes), error: () => {} });
    this.api.avisosMensajes().subscribe({
      next: (p) => this.avisos.set([...p.mora, ...p.venceHoy].filter((a) => !a.enviadoHoy).length),
      error: () => {},
    });
  }
}
