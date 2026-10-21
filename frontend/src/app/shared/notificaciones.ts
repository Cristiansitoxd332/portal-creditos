import { ChangeDetectionStrategy, Component, Injectable, inject, signal } from '@angular/core';
import { Icono } from './icono';

export interface Notificacion {
  id: number;
  tipo: 'exito' | 'error' | 'info';
  texto: string;
}

/** Mensajes emergentes ("toasts") para confirmar acciones o mostrar errores. */
@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private secuencia = 0;
  readonly lista = signal<Notificacion[]>([]);

  exito(texto: string): void {
    this.mostrar('exito', texto);
  }

  error(texto: string): void {
    this.mostrar('error', texto, 6000);
  }

  info(texto: string): void {
    this.mostrar('info', texto);
  }

  cerrar(id: number): void {
    this.lista.update((l) => l.filter((n) => n.id !== id));
  }

  private mostrar(tipo: Notificacion['tipo'], texto: string, ms = 4000): void {
    const id = ++this.secuencia;
    this.lista.update((l) => [...l, { id, tipo, texto }]);
    setTimeout(() => this.cerrar(id), ms);
  }
}

@Component({
  selector: 'app-notificaciones',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="contenedor" aria-live="polite">
      @for (n of servicio.lista(); track n.id) {
        <div class="toast aparecer" [class.error]="n.tipo === 'error'" [class.exito]="n.tipo === 'exito'" role="status">
          <app-icono [nombre]="n.tipo === 'error' ? 'alert' : n.tipo === 'exito' ? 'check' : 'bell'" [tamano]="18" />
          <span>{{ n.texto }}</span>
          <button type="button" class="cerrar" aria-label="Cerrar mensaje" (click)="servicio.cerrar(n.id)">
            <app-icono nombre="x" [tamano]="16" />
          </button>
        </div>
      }
    </div>`,
  styles: [`
    .contenedor { position: fixed; top: 16px; left: 50%; transform: translateX(-50%); z-index: 1000;
      display: flex; flex-direction: column; gap: 8px; width: min(440px, calc(100vw - 32px)); }
    .toast { display: flex; align-items: center; gap: 10px; padding: 12px 12px 12px 16px; border-radius: 14px;
      background: var(--texto); color: var(--superficie); font-size: 14px; font-weight: 600; box-shadow: var(--sombra); }
    .toast.error { background: var(--mora); color: #fff; }
    .toast.exito { background: var(--ok-solido); color: #fff; }
    .toast span { flex: 1; }
    .cerrar { border: 0; background: transparent; color: inherit; cursor: pointer; padding: 6px; border-radius: 8px; display: flex; }
  `],
})
export class Notificaciones {
  protected readonly servicio = inject(NotificacionService);
}
