import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { BandejaCliente, MensajeInfo, TipoMensaje } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { MensajesNoLeidos } from '../../core/mensajes-no-leidos';
import { FechaPipe } from '../../shared/pipes';

const ICONO: Record<TipoMensaje, string> = { MORA: 'alert', VENCE_HOY: 'calendar', RECORDATORIO: 'clock', GENERAL: 'chat' };

/** Mensajes que Rentar le envio al cliente: recordatorios de pago y avisos de mora. */
@Component({
  selector: 'app-mis-mensajes',
  imports: [Icono, Encabezado, Cargando, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Mensajes" subtitulo="Recordatorios y avisos de Rentar Inversiones sobre sus créditos" />
    @if (bandeja(); as b) {
      <div class="lista">
        @for (m of b.mensajes; track m.id) {
          <article class="tarjeta mensaje aparecer" [class.nuevo]="!m.leido" [class.es-mora]="m.tipo === 'MORA'">
            <span class="icono"><app-icono [nombre]="icono[m.tipo]" [tamano]="22" /></span>
            <div class="cuerpo">
              <header>
                <strong>{{ m.titulo }}</strong>
                @if (!m.leido) { <span class="chip chip-primario">Nuevo</span> }
              </header>
              <small class="texto-suave">{{ m.fechaEnvio | fecha: 'hora' }}@if (m.numeroCredito) { · Crédito {{ m.numeroCredito }} }</small>
              <p>{{ m.texto }}</p>
              @if (!m.leido) {
                <button type="button" class="btn btn-secundario btn-pequeno" (click)="leer(m)"><app-icono nombre="check" [tamano]="16" />Marcar como leído</button>
              }
            </div>
          </article>
        } @empty {
          <div class="tarjeta vacio"><app-icono nombre="bell" [tamano]="32" /><p>No tiene mensajes. Aquí verá los recordatorios de sus fechas de pago (días 5 y 20).</p></div>
        }
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="3" [alto]="110" />
    }
  `,
  styles: [`
    .lista { display: flex; flex-direction: column; gap: 12px; max-width: 760px; }
    .mensaje { display: flex; gap: 14px; align-items: flex-start; }
    .mensaje.nuevo { border: 1.5px solid var(--primario-borde); }
    .icono { width: 46px; height: 46px; border-radius: 14px; flex-shrink: 0; display: flex; align-items: center; justify-content: center;
      background: var(--primario-suave); color: var(--primario); }
    .es-mora .icono { background: var(--mora-fondo); color: var(--mora); }
    .cuerpo { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; align-items: flex-start; }
    header { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    small { font-size: 12px; }
    p { margin: 6px 0 4px; font-size: 14px; color: var(--texto-2); line-height: 1.5; }
    .vacio { display: flex; flex-direction: column; align-items: center; gap: 8px; color: var(--texto-3); padding: 36px; text-align: center; }
    .vacio p { margin: 0; max-width: 360px; }
  `],
})
export class MisMensajes {
  private readonly api = inject(ApiService);
  private readonly noLeidos = inject(MensajesNoLeidos);

  protected readonly bandeja = signal<BandejaCliente | null>(null);
  protected readonly error = signal('');
  protected readonly icono = ICONO;

  constructor() {
    this.api.misMensajes().subscribe({
      next: (b) => this.bandeja.set(b),
      error: (e) => this.error.set(mensajeError(e)),
    });
  }

  protected leer(m: MensajeInfo): void {
    this.api.marcarMensajeLeido(m.id).subscribe({
      next: (actualizado) => {
        this.bandeja.update((b) => b && {
          noLeidos: Math.max(0, b.noLeidos - 1),
          mensajes: b.mensajes.map((x) => (x.id === actualizado.id ? actualizado : x)),
        });
        this.noLeidos.actualizar();
      },
    });
  }
}
