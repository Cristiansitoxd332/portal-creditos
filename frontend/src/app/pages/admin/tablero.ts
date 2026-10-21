import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CreditoResumen, Semaforo, Tablero } from '../../core/models';
import { TEXTO_SEMAFORO, mensajeError } from '../../core/utilidades';
import { Cargando, ChipSemaforo, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { FechaPipe, MonedaPipe, NumeroPipe } from '../../shared/pipes';

const COLOR_SEMAFORO: Record<Semaforo, string> = {
  AL_DIA: 'var(--ok)', PROXIMA_A_VENCER: 'var(--alerta)', EN_MORA: 'var(--rojo)', CANCELADO: 'var(--texto-3)',
};

/** Tablero del administrador: indicadores de cartera, distribucion y pagos recientes. */
@Component({
  selector: 'app-tablero',
  imports: [RouterLink, Icono, Encabezado, Cargando, ChipSemaforo, MonedaPipe, NumeroPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (tablero(); as t) {
      <app-encabezado titulo="Tablero" [subtitulo]="'Corte al ' + (t.fechaCorte | fecha: 'larga')">
        <a routerLink="/admin/creditos/nuevo" class="btn btn-primario"><app-icono nombre="plus" [tamano]="18" />Nuevo crédito</a>
      </app-encabezado>

      <div class="kpis">
        <article class="tarjeta kpi destacado aparecer">
          <span class="etiqueta">Cartera total</span>
          <strong>{{ t.carteraTotal | moneda }}</strong>
          <small>{{ t.totalCreditos }} créditos · {{ t.totalClientes }} clientes</small>
        </article>
        <article class="tarjeta kpi aparecer">
          <span class="etiqueta">Cartera vencida</span>
          <strong class="mora">{{ t.carteraVencida | moneda }}</strong>
          <small>Índice de mora {{ t.indiceMora | numero }} %</small>
        </article>
        <article class="tarjeta kpi aparecer">
          <span class="etiqueta">Pagos del mes</span>
          <strong>{{ t.pagosMesValor | moneda }}</strong>
          <small>{{ t.pagosMesCantidad }} pagos aprobados</small>
        </article>
        <a class="tarjeta kpi enlace aparecer" routerLink="/admin/solicitudes">
          <span class="etiqueta">Solicitudes pendientes</span>
          <strong [class.mora]="t.solicitudesPendientes > 0">{{ t.solicitudesPendientes }}</strong>
          <small>Levantamientos de prenda <app-icono nombre="chevron-right" [tamano]="14" /></small>
        </a>
      </div>

      <div class="paneles">
        <section class="tarjeta">
          <h2>Estado de la cartera</h2>
          <div class="barra-apilada" role="img" [attr.aria-label]="resumenSemaforo()">
            @for (s of semaforos(); track s.clave) {
              @if (s.cantidad > 0) { <span [style.flex]="s.cantidad" [style.background]="s.color" [title]="s.texto + ': ' + s.cantidad"></span> }
            }
          </div>
          <ul class="leyenda">
            @for (s of semaforos(); track s.clave) {
              <li><span class="punto" [style.background]="s.color"></span>{{ s.texto }}<strong>{{ s.cantidad }}</strong></li>
            }
          </ul>
        </section>

        <section class="tarjeta">
          <h2>Pagos últimos meses</h2>
          <div class="columnas">
            @for (m of t.pagosUltimosMeses; track m.mes) {
              <div class="col">
                <span class="valor">{{ abreviar(m.total) }}</span>
                <div class="pista"><div class="relleno" [style.height.%]="altura(m.total)"></div></div>
                <span class="mes">{{ m.mes }}</span>
              </div>
            }
          </div>
        </section>
      </div>

      <section class="tarjeta">
        <div class="cab"><h2>Créditos que requieren atención</h2><a routerLink="/admin/creditos">Ver todos</a></div>
        <div class="tabla-caja">
          <table class="tabla">
            <thead><tr><th>Crédito</th><th>Cliente</th><th>Estado</th><th class="num">Vencido</th><th class="num">Días mora</th></tr></thead>
            <tbody>
              @for (c of atencion(); track c.id) {
                <tr class="clic" [routerLink]="['/admin/creditos', c.id]">
                  <td><strong>{{ c.numeroCredito }}</strong><br><small class="texto-suave">{{ c.placa }}</small></td>
                  <td>{{ c.cliente }}</td>
                  <td><app-chip-semaforo [estado]="c.semaforo" /></td>
                  <td class="num">{{ c.valorVencido | moneda }}</td>
                  <td class="num">{{ c.diasMora }}</td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="vacio">Todos los créditos están al día.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    } @else if (error()) {
      <app-encabezado titulo="Tablero" />
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-encabezado titulo="Tablero" />
      <app-cargando [cantidad]="3" [alto]="130" />
    }
  `,
  styles: [`
    .kpis { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr)); margin-bottom: 16px; }
    .kpi { display: flex; flex-direction: column; gap: 6px; text-decoration: none; color: inherit; }
    .kpi strong { font-size: 26px; font-weight: 800; letter-spacing: -0.02em; }
    .kpi small { font-size: 12.5px; color: var(--texto-3); display: flex; align-items: center; gap: 4px; }
    .destacado { background: linear-gradient(135deg, var(--rojo), var(--rojo-oscuro)); color: #fff; }
    .destacado .etiqueta, .destacado small { color: rgba(255,255,255,0.85); }
    .enlace:hover { border-color: var(--rojo); }
    .mora { color: var(--mora); }
    .paneles { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 380px), 1fr)); margin-bottom: 16px; }
    h2 { font-size: 16px; font-weight: 800; margin-bottom: 14px; }
    .barra-apilada { display: flex; height: 18px; border-radius: 9px; overflow: hidden; gap: 3px; background: var(--superficie-2); }
    .leyenda { list-style: none; margin: 16px 0 0; padding: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 10px 18px; }
    .leyenda li { display: flex; align-items: center; gap: 8px; font-size: 13.5px; }
    .leyenda strong { margin-left: auto; }
    .punto { width: 10px; height: 10px; border-radius: 5px; }
    .columnas { display: flex; gap: 10px; align-items: flex-end; height: 170px; }
    .col { flex: 1; height: 100%; display: flex; flex-direction: column; align-items: center; gap: 6px; }
    .pista { flex: 1; width: 100%; max-width: 46px; display: flex; align-items: flex-end; }
    .relleno { width: 100%; background: var(--rojo); border-radius: 8px 8px 3px 3px; min-height: 4px; transition: height 0.6s ease; }
    .col:last-child .relleno { background: var(--rojo-oscuro); }
    .valor { font-size: 11.5px; font-weight: 700; color: var(--texto-2); }
    .mes { font-size: 12px; color: var(--texto-3); text-transform: capitalize; }
    .cab { display: flex; justify-content: space-between; align-items: baseline; }
    .cab a { font-weight: 700; font-size: 14px; }
    .clic { cursor: pointer; }
    .vacio { text-align: center; color: var(--texto-3); padding: 24px; }
  `],
})
export class TableroPage {
  protected readonly tablero = signal<Tablero | null>(null);
  protected readonly creditos = signal<CreditoResumen[]>([]);
  protected readonly error = signal('');

  protected readonly semaforos = computed(() => {
    const t = this.tablero();
    const claves: Semaforo[] = ['AL_DIA', 'PROXIMA_A_VENCER', 'EN_MORA', 'CANCELADO'];
    return claves.map((clave) => ({ clave, texto: TEXTO_SEMAFORO[clave], color: COLOR_SEMAFORO[clave], cantidad: t?.creditosPorSemaforo?.[clave] ?? 0 }));
  });
  protected readonly resumenSemaforo = computed(() => this.semaforos().map((s) => `${s.texto}: ${s.cantidad}`).join(', '));
  protected readonly atencion = computed(() =>
    this.creditos().filter((c) => c.semaforo === 'EN_MORA' || c.semaforo === 'PROXIMA_A_VENCER')
      .sort((a, b) => b.diasMora - a.diasMora || b.valorVencido - a.valorVencido).slice(0, 8));
  private readonly maximo = computed(() => Math.max(1, ...(this.tablero()?.pagosUltimosMeses ?? []).map((m) => m.total)));

  constructor() {
    const api = inject(ApiService);
    forkJoin({ t: api.tablero(), c: api.creditosAdmin() }).subscribe({
      next: ({ t, c }) => { this.tablero.set(t); this.creditos.set(c); },
      error: (e) => this.error.set(mensajeError(e)),
    });
  }

  protected altura(v: number): number {
    return (v / this.maximo()) * 100;
  }

  protected abreviar(v: number): string {
    if (v >= 1_000_000) return '$' + (v / 1_000_000).toLocaleString('es-CO', { maximumFractionDigits: 1 }) + ' M';
    if (v >= 1_000) return '$' + Math.round(v / 1_000) + ' mil';
    return '$' + v;
  }
}
