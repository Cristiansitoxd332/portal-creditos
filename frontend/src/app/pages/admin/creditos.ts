import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { CreditoResumen, Semaforo } from '../../core/models';
import { TEXTO_SEMAFORO, mensajeError } from '../../core/utilidades';
import { Cargando, ChipSemaforo, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { MonedaPipe, NumeroPipe } from '../../shared/pipes';

/** Listado de creditos con busqueda y filtro por semaforo. */
@Component({
  selector: 'app-creditos',
  imports: [RouterLink, Icono, Encabezado, Cargando, ChipSemaforo, MonedaPipe, NumeroPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Créditos" subtitulo="Cartera de créditos vehiculares">
      <a routerLink="/admin/creditos/nuevo" class="btn btn-primario"><app-icono nombre="plus" [tamano]="18" />Nuevo crédito</a>
    </app-encabezado>

    <div class="filtros">
      <div class="buscador">
        <app-icono nombre="search" [tamano]="18" />
        <input type="search" placeholder="Número, cliente, documento o placa" aria-label="Buscar créditos" [value]="texto()" (input)="texto.set($any($event.target).value)">
      </div>
      <div class="pestanas" role="tablist">
        @for (f of filtros; track f.clave) {
          <button type="button" role="tab" [attr.aria-selected]="filtro() === f.clave" [class.activa]="filtro() === f.clave" (click)="filtro.set(f.clave)">
            {{ f.texto }} <span>{{ conteo(f.clave) }}</span></button>
        }
      </div>
    </div>

    @if (creditos(); as _) {
      <div class="tabla-caja tarjeta sin-relleno">
        <table class="tabla">
          <thead><tr><th>Crédito</th><th>Cliente</th><th>Vehículo</th><th>Estado</th><th class="num">Saldo capital</th><th class="num">Pagado</th><th class="num">Vencido</th></tr></thead>
          <tbody>
            @for (c of visibles(); track c.id) {
              <tr class="clic" [routerLink]="['/admin/creditos', c.id]">
                <td><strong>{{ c.numeroCredito }}</strong></td>
                <td>{{ c.cliente }}<br><small class="texto-suave">{{ c.documentoCliente }}</small></td>
                <td>{{ c.vehiculo }}<br><small class="texto-suave">{{ c.placa }}</small></td>
                <td><app-chip-semaforo [estado]="c.semaforo" /></td>
                <td class="num">{{ c.saldoCapital | moneda }}</td>
                <td class="num"><div class="progreso"><span [style.width.%]="c.porcentajePagado"></span></div><small>{{ c.porcentajePagado | numero }} %</small></td>
                <td class="num" [class.mora]="c.valorVencido > 0">{{ c.valorVencido | moneda }}</td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="vacio">No hay créditos que coincidan.</td></tr>
            }
          </tbody>
        </table>
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="5" [alto]="56" />
    }
  `,
  styles: [`
    .filtros { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; align-items: center; }
    .buscador { flex: 1 1 280px; display: flex; align-items: center; gap: 10px; background: var(--superficie); border: 1.5px solid var(--borde-fuerte);
      border-radius: 14px; padding: 0 14px; height: 48px; color: var(--texto-3); }
    .buscador:focus-within { border-color: var(--primario); }
    .buscador input { flex: 1; border: 0; background: transparent; font: inherit; color: var(--texto); outline: none; height: 100%; }
    .pestanas { display: flex; gap: 6px; flex-wrap: wrap; }
    .pestanas button { height: 40px; padding: 0 14px; border-radius: 999px; border: 1.5px solid var(--borde-fuerte); background: var(--superficie);
      font: inherit; font-size: 13px; font-weight: 700; color: var(--texto-2); cursor: pointer; display: flex; gap: 6px; align-items: center; }
    .pestanas button span { background: var(--superficie-2); border-radius: 999px; padding: 1px 8px; font-size: 12px; }
    .pestanas .activa { background: var(--texto); color: var(--superficie); border-color: var(--texto); }
    .pestanas .activa span { background: rgba(255,255,255,0.2); }
    .sin-relleno { padding: 0; }
    .clic { cursor: pointer; }
    .progreso { width: 80px; height: 6px; border-radius: 3px; background: var(--superficie-2); overflow: hidden; margin-left: auto; margin-bottom: 3px; }
    .progreso span { display: block; height: 100%; background: var(--primario); }
    .mora { color: var(--mora); font-weight: 700; }
    .vacio { text-align: center; color: var(--texto-3); padding: 24px; }
  `],
})
export class Creditos {
  /** Permite llegar filtrado desde Clientes (?buscar=documento). */
  readonly buscar = input<string>('');

  protected readonly creditos = signal<CreditoResumen[] | null>(null);
  protected readonly error = signal('');
  protected readonly texto = signal('');
  protected readonly filtro = signal<Semaforo | 'TODOS'>('TODOS');
  protected readonly filtros: { clave: Semaforo | 'TODOS'; texto: string }[] = [
    { clave: 'TODOS', texto: 'Todos' },
    ...(['AL_DIA', 'PROXIMA_A_VENCER', 'EN_MORA', 'CANCELADO'] as Semaforo[]).map((s) => ({ clave: s, texto: TEXTO_SEMAFORO[s] })),
  ];

  protected readonly visibles = computed(() => {
    const t = (this.texto() || '').trim().toLowerCase();
    const f = this.filtro();
    return (this.creditos() ?? []).filter((c) =>
      (f === 'TODOS' || c.semaforo === f) &&
      (!t || [c.numeroCredito, c.cliente, c.documentoCliente, c.placa].some((v) => v.toLowerCase().includes(t))));
  });

  constructor() {
    inject(ApiService).creditosAdmin().subscribe({
      next: (c) => { this.creditos.set(c); if (this.buscar()) this.texto.set(this.buscar()); },
      error: (e) => this.error.set(mensajeError(e)),
    });
  }

  protected conteo(clave: Semaforo | 'TODOS'): number {
    const lista = this.creditos() ?? [];
    return clave === 'TODOS' ? lista.length : lista.filter((c) => c.semaforo === clave).length;
  }
}
