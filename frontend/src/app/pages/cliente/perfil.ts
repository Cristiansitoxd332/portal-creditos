import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Perfil } from '../../core/models';
import { TemaService } from '../../core/tema.service';
import { mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { FechaPipe } from '../../shared/pipes';

/** Perfil del cliente: datos de la cuenta, seguridad y preferencias. */
@Component({
  selector: 'app-perfil',
  imports: [RouterLink, Icono, Encabezado, Cargando, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Mi perfil" />
    @if (perfil(); as p) {
      <div class="contenido">
        <section class="tarjeta cabecera aparecer">
          <span class="avatar">{{ iniciales(p.nombre) }}</span>
          <div><h2>{{ p.nombre }}</h2><span class="texto-suave">Documento {{ p.username }}</span></div>
        </section>
        <section class="tarjeta">
          <dl class="datos">
            <div><dt>Correo</dt><dd>{{ p.correo ?? 'Sin registrar' }}</dd></div>
            <div><dt>Último acceso</dt><dd>{{ p.ultimoAcceso | fecha: 'hora' }}</dd></div>
          </dl>
        </section>
        <section class="tarjeta opciones">
          <a routerLink="/cambiar-password" class="opcion"><app-icono nombre="key" /><span>Cambiar contraseña</span><app-icono nombre="chevron-right" [tamano]="18" /></a>
          <button type="button" class="opcion" (click)="tema.alternar()">
            <app-icono [nombre]="tema.oscuro() ? 'sun' : 'moon'" /><span>{{ tema.oscuro() ? 'Modo claro' : 'Modo oscuro' }}</span>
            <span class="interruptor" [class.on]="tema.oscuro()" aria-hidden="true"></span>
          </button>
          <button type="button" class="opcion salir" (click)="auth.logout()"><app-icono nombre="logout" /><span>Cerrar sesión</span></button>
        </section>
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="3" [alto]="80" />
    }
  `,
  styles: [`
    .contenido { display: flex; flex-direction: column; gap: 14px; max-width: 560px; }
    .cabecera { display: flex; align-items: center; gap: 16px; }
    .avatar { width: 60px; height: 60px; border-radius: 30px; background: var(--primario); color: #fff; font-weight: 800; font-size: 22px; display: flex; align-items: center; justify-content: center; }
    h2 { font-size: 18px; font-weight: 800; }
    .datos { margin: 0; display: grid; gap: 12px; }
    .datos div { display: flex; flex-direction: column; }
    dt { font-size: 12px; color: var(--texto-3); }
    dd { margin: 0; font-weight: 700; }
    .opciones { padding: 6px; display: flex; flex-direction: column; }
    .opcion { display: flex; align-items: center; gap: 12px; min-height: 52px; padding: 0 12px; border: 0; background: transparent; font: inherit;
      font-weight: 600; color: var(--texto); text-decoration: none; border-radius: 12px; cursor: pointer; text-align: left; }
    .opcion:hover { background: var(--superficie-2); }
    .opcion span:not(.interruptor) { flex: 1; }
    .salir { color: var(--mora); }
    .interruptor { width: 44px; height: 26px; border-radius: 13px; background: var(--borde-fuerte); position: relative; transition: background 0.2s; }
    .interruptor::after { content: ''; position: absolute; top: 3px; left: 3px; width: 20px; height: 20px; border-radius: 10px; background: #fff; transition: transform 0.2s; }
    .interruptor.on { background: var(--primario); }
    .interruptor.on::after { transform: translateX(18px); }
  `],
})
export class PerfilPage {
  protected readonly auth = inject(AuthService);
  protected readonly tema = inject(TemaService);
  protected readonly perfil = signal<Perfil | null>(null);
  protected readonly error = signal('');

  constructor() {
    inject(ApiService).perfil().subscribe({
      next: (p) => this.perfil.set(p),
      error: (e) => this.error.set(mensajeError(e)),
    });
  }

  protected iniciales(nombre: string): string {
    return nombre.split(' ').filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase();
  }
}
