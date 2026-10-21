import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { CanalInfo, TipoCanal } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';

const ICONO_CANAL: Record<TipoCanal, string> = {
  WHATSAPP: 'chat', TELEFONO: 'phone', CORREO: 'mail', SITIO_WEB: 'globe', OFICINA: 'pin', REDES_SOCIALES: 'users',
};

/** Canales de atencion (configurables por el administrador). */
@Component({
  selector: 'app-contacto',
  imports: [Icono, Encabezado, Cargando],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Contacto" subtitulo="Canales de atención de la entidad" />
    @if (canales(); as lista) {
      <div class="lista">
        @for (c of lista; track c.id) {
          <article class="tarjeta canal aparecer">
            <span class="icono" [class.wa]="c.tipo === 'WHATSAPP'"><app-icono [nombre]="icono[c.tipo]" [tamano]="24" /></span>
            <div class="info">
              <strong>{{ c.nombre }}</strong>
              <span class="valor">{{ c.valor }}</span>
              @if (c.horario) { <small>{{ c.horario }}</small> }
            </div>
            @if (c.enlace) {
              <a class="btn btn-secundario btn-pequeno" [href]="c.enlace" target="_blank" rel="noopener noreferrer"
                 [attr.aria-label]="'Abrir ' + c.nombre">Abrir<app-icono nombre="chevron-right" [tamano]="16" /></a>
            }
          </article>
        }
      </div>
      <p class="aviso"><app-icono nombre="alert" [tamano]="18" /><span>Datos de contacto de ejemplo del prototipo. El administrador puede actualizarlos desde su panel.</span></p>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="4" [alto]="76" />
    }
  `,
  styles: [`
    .lista { display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr)); margin-bottom: 16px; }
    .canal { display: flex; align-items: center; gap: 14px; }
    .icono { width: 50px; height: 50px; border-radius: 16px; background: var(--rojo-suave); color: var(--rojo); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .icono.wa { background: var(--ok-fondo); color: var(--ok); }
    .info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
    .valor { font-size: 14px; color: var(--texto-2); overflow-wrap: anywhere; }
    small { font-size: 12px; color: var(--texto-3); }
  `],
})
export class Contacto {
  protected readonly canales = signal<CanalInfo[] | null>(null);
  protected readonly error = signal('');
  protected readonly icono = ICONO_CANAL;

  constructor() {
    inject(ApiService).canales().subscribe({
      next: (c) => this.canales.set(c),
      error: (e) => this.error.set(mensajeError(e)),
    });
  }
}
