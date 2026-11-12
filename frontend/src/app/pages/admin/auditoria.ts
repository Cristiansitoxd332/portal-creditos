import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { RegistroAuditoria } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { FechaPipe } from '../../shared/pipes';

/** Bitacora de auditoria: quien hizo que y cuando. */
@Component({
  selector: 'app-auditoria',
  imports: [Icono, Encabezado, Cargando, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Auditoría" subtitulo="Registro de acciones realizadas en el sistema">
      <button type="button" class="btn btn-secundario" (click)="cargar()"><app-icono nombre="refresh" [tamano]="18" />Actualizar</button>
    </app-encabezado>

    <div class="buscador">
      <app-icono nombre="search" [tamano]="18" />
      <input type="search" placeholder="Filtrar por usuario, acción o detalle" aria-label="Filtrar auditoría" (input)="texto.set($any($event.target).value)">
    </div>

    @if (registros(); as _) {
      <div class="tabla-caja tarjeta sin-relleno">
        <table class="tabla">
          <thead><tr><th>Fecha</th><th>Usuario</th><th>Acción</th><th>Entidad</th><th>Detalle</th><th>IP</th></tr></thead>
          <tbody>
            @for (r of visibles(); track r.id) {
              <tr>
                <td class="fecha">{{ r.fecha | fecha: 'hora' }}</td>
                <td>{{ r.usuario ?? '—' }}</td>
                <td><span [class]="'chip ' + clase(r.accion)">{{ r.accion }}</span></td>
                <td><small>{{ r.entidad ?? '' }} {{ r.entidadId ?? '' }}</small></td>
                <td class="detalle">{{ r.detalle }}</td>
                <td><small class="texto-suave">{{ r.direccionIp }}</small></td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="vacio">Sin registros.</td></tr>
            }
          </tbody>
        </table>
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="6" [alto]="44" />
    }
  `,
  styles: [`
    .buscador { display: flex; align-items: center; gap: 10px; background: var(--superficie); border: 1.5px solid var(--borde-fuerte); border-radius: 14px;
      padding: 0 14px; height: 48px; margin-bottom: 16px; color: var(--texto-3); }
    .buscador:focus-within { border-color: var(--primario); }
    .buscador input { flex: 1; border: 0; background: transparent; font: inherit; color: var(--texto); outline: none; height: 100%; }
    .sin-relleno { padding: 0; }
    .fecha { white-space: nowrap; }
    .detalle { max-width: 420px; font-size: 13px; }
    .vacio { text-align: center; color: var(--texto-3); padding: 24px; }
  `],
})
export class AuditoriaPage {
  private readonly api = inject(ApiService);
  protected readonly registros = signal<RegistroAuditoria[] | null>(null);
  protected readonly error = signal('');
  protected readonly texto = signal('');
  protected readonly visibles = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return (this.registros() ?? []).filter((r) => !t || [r.usuario, r.accion, r.detalle, r.entidad].some((v) => (v ?? '').toLowerCase().includes(t)));
  });

  constructor() {
    this.cargar();
  }

  protected cargar(): void {
    this.registros.set(null);
    this.api.auditoria(200).subscribe({ next: (r) => this.registros.set(r), error: (e) => this.error.set(mensajeError(e)) });
  }

  protected clase(accion: string): string {
    if (/FALLIDO|BLOQUE|RECHAZ|ELIMIN/.test(accion)) return 'chip-mora';
    if (/PAGO|CREAR|APROB|IMPORT/.test(accion)) return 'chip-ok';
    if (/LOGIN|INGRESO/.test(accion)) return 'chip-neutro';
    return 'chip-alerta';
  }
}
