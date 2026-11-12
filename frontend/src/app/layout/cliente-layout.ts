import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { MensajesNoLeidos } from '../core/mensajes-no-leidos';
import { AuthService } from '../core/auth.service';
import { TemaService } from '../core/tema.service';
import { Icono } from '../shared/icono';

/**
 * Estructura del portal del cliente:
 * - En celular: menu inferior fijo (como una app movil).
 * - En computador: barra superior con el menu.
 */
@Component({
  selector: 'app-cliente-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barra">
      <a routerLink="/cliente" class="marca" aria-label="Ir al inicio">
        <span class="logo">R</span>
        <span class="marca-texto"><strong>Rentar</strong><small>Portal de clientes</small></span>
      </a>
      <nav class="menu-escritorio" aria-label="Menú principal">
        @for (item of menu; track item.ruta) {
          <a [routerLink]="item.ruta" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: item.exacto }">
            <app-icono [nombre]="item.icono" [tamano]="18" />{{ item.texto }}
          </a>
        }
      </nav>
      <div class="acciones">
        <a routerLink="/cliente/mensajes" routerLinkActive="activa" class="btn btn-icono btn-fantasma campana"
           [attr.aria-label]="noLeidos.cantidad() ? 'Mensajes: ' + noLeidos.cantidad() + ' sin leer' : 'Mensajes'">
          <app-icono nombre="bell" />
          @if (noLeidos.cantidad() > 0) { <span class="globo">{{ noLeidos.cantidad() }}</span> }
        </a>
        <button type="button" class="btn btn-icono btn-fantasma" (click)="tema.alternar()"
                [attr.aria-label]="tema.oscuro() ? 'Activar modo claro' : 'Activar modo oscuro'">
          <app-icono [nombre]="tema.oscuro() ? 'sun' : 'moon'" />
        </button>
        <button type="button" class="btn btn-icono btn-fantasma" (click)="auth.logout()" aria-label="Cerrar sesión">
          <app-icono nombre="logout" />
        </button>
      </div>
    </header>

    <main class="contenido">
      <router-outlet />
      <p class="pie">Prototipo académico con datos ficticios. No está conectado a los sistemas de la empresa ni procesa pagos reales.</p>
    </main>

    <nav class="menu-movil" aria-label="Menú principal">
      @for (item of menu; track item.ruta) {
        <a [routerLink]="item.ruta" routerLinkActive="activo" [routerLinkActiveOptions]="{ exact: item.exacto }">
          <app-icono [nombre]="item.icono" [tamano]="22" />
          <span>{{ item.texto }}</span>
        </a>
      }
    </nav>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; }
    .campana { position: relative; }
    .campana.activa { color: var(--primario); }
    .globo { position: absolute; top: 2px; right: 2px; min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px;
      background: var(--mora); color: #fff; font-size: 11px; font-weight: 800; display: flex; align-items: center; justify-content: center; }
    .barra { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; gap: 16px; padding: 10px 20px;
      background: color-mix(in srgb, var(--superficie) 92%, transparent); backdrop-filter: blur(10px); border-bottom: 1px solid var(--borde); }
    .marca { display: flex; align-items: center; gap: 10px; text-decoration: none; color: var(--texto); }
    .logo { width: 38px; height: 38px; border-radius: 11px; background: var(--primario); color: #fff; display: flex; align-items: center;
      justify-content: center; font-weight: 800; font-size: 20px; }
    .marca-texto { display: flex; flex-direction: column; line-height: 1.15; }
    .marca-texto strong { font-size: 17px; font-weight: 800; }
    .marca-texto small { font-size: 11.5px; color: var(--texto-3); }
    .menu-escritorio { display: none; gap: 4px; margin-left: 24px; flex: 1; }
    .menu-escritorio a { display: flex; align-items: center; gap: 8px; height: 40px; padding: 0 14px; border-radius: 10px;
      text-decoration: none; color: var(--texto-2); font-weight: 600; font-size: 14px; }
    .menu-escritorio a:hover { background: var(--superficie-2); }
    .menu-escritorio a.activo { background: var(--primario-suave); color: var(--primario-oscuro); }
    .acciones { display: flex; gap: 4px; margin-left: auto; }
    .contenido { max-width: 1040px; margin: 0 auto; padding: 18px 16px 110px; }
    .pie { margin: 32px auto 0; max-width: 520px; text-align: center; font-size: 11.5px; color: var(--texto-3); }
    .menu-movil { position: fixed; bottom: 0; left: 0; right: 0; z-index: 20; display: grid; grid-template-columns: repeat(4, 1fr);
      background: var(--superficie); border-top: 1px solid var(--borde); padding: 8px 6px calc(10px + env(safe-area-inset-bottom)); }
    .menu-movil a { display: flex; flex-direction: column; align-items: center; gap: 3px; font-size: 11px; font-weight: 600;
      color: var(--texto-3); text-decoration: none; padding: 4px 0; min-height: 44px; }
    .menu-movil a.activo { color: var(--primario); font-weight: 800; }
    @media (min-width: 900px) {
      .menu-escritorio { display: flex; }
      .menu-movil { display: none; }
      .contenido { padding: 28px 24px 48px; }
    }
  `],
})
export class ClienteLayout {
  protected readonly auth = inject(AuthService);
  protected readonly tema = inject(TemaService);
  protected readonly noLeidos = inject(MensajesNoLeidos);

  constructor() {
    // Mensajes sin leer en tiempo real: al entrar, al navegar y cada minuto.
    this.noLeidos.actualizar();
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.noLeidos.actualizar());
    const id = setInterval(() => this.noLeidos.actualizar(), 60_000);
    inject(DestroyRef).onDestroy(() => clearInterval(id));
  }

  protected readonly menu = [
    { ruta: '/cliente', texto: 'Inicio', icono: 'home', exacto: true },
    { ruta: '/cliente/pagos', texto: 'Pagos', icono: 'money', exacto: false },
    { ruta: '/cliente/contacto', texto: 'Contacto', icono: 'chat', exacto: false },
    { ruta: '/cliente/perfil', texto: 'Perfil', icono: 'user', exacto: false },
  ];
}
