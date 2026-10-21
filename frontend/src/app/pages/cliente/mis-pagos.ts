import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CreditoResumen, PagoHistorial } from '../../core/models';
import { TEXTO_MEDIO, guardarArchivo, mensajeError } from '../../core/utilidades';
import { Cargando, ChipSemaforo, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe, MonedaPipe } from '../../shared/pipes';

interface Grupo {
  credito: CreditoResumen;
  pagos: PagoHistorial[];
}

/** Pagos: acceso rapido a pagar cada credito e historial de pagos con comprobantes. */
@Component({
  selector: 'app-mis-pagos',
  imports: [RouterLink, Icono, Encabezado, Cargando, ChipSemaforo, MonedaPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Pagos" subtitulo="Paga tus cuotas y consulta tus comprobantes" />
    @if (grupos(); as lista) {
      <div class="lista">
        @for (g of lista; track g.credito.id) {
          <section class="tarjeta aparecer">
            <header class="cab">
              <div><strong>{{ g.credito.numeroCredito }}</strong><span>{{ g.credito.vehiculo }} · {{ g.credito.placa }}</span></div>
              <app-chip-semaforo [estado]="g.credito.semaforo" />
            </header>
            @if (g.credito.estado !== 'CANCELADO') {
              <a class="btn btn-primario btn-bloque" [routerLink]="['/cliente/creditos', g.credito.id, 'pagar']">
                <app-icono nombre="money" [tamano]="18" />{{ g.credito.estado === 'EN_MORA' ? 'Ponerme al día · ' : 'Pagar · ' }}{{ (g.credito.estado === 'EN_MORA' ? g.credito.valorVencido : g.credito.proximaCuota?.valor) | moneda }}
              </a>
            }
            <ul class="pagos">
              @for (p of g.pagos.slice(0, 5); track p.pagoId) {
                <li>
                  <span class="icono" [class.rechazado]="p.estado !== 'APROBADO'"><app-icono [nombre]="p.estado === 'APROBADO' ? 'receipt' : 'x'" [tamano]="18" /></span>
                  <div class="info"><strong>{{ p.valor | moneda }}</strong><span>{{ p.fecha | fecha }} · {{ textoMedio[p.medioPago] }}{{ p.estado !== 'APROBADO' ? ' · Rechazado' : '' }}</span></div>
                  @if (p.estado === 'APROBADO') {
                    <button type="button" class="btn btn-fantasma btn-icono" (click)="descargar(g.credito.id, p)" [attr.aria-label]="'Descargar comprobante ' + p.referencia">
                      <app-icono nombre="download" [tamano]="18" />
                    </button>
                  }
                </li>
              } @empty {
                <li class="vacio">Sin pagos registrados.</li>
              }
            </ul>
            @if (g.pagos.length > 5) {
              <a class="ver-todos" [routerLink]="['/cliente/creditos', g.credito.id]">Ver los {{ g.pagos.length }} pagos</a>
            }
          </section>
        }
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="2" [alto]="220" />
    }
  `,
  styles: [`
    .lista { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 400px), 1fr)); }
    section { display: flex; flex-direction: column; gap: 14px; }
    .cab { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
    .cab div { display: flex; flex-direction: column; }
    .cab span { font-size: 13px; color: var(--texto-3); }
    .pagos { list-style: none; margin: 0; padding: 0; }
    .pagos li { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--borde); }
    .pagos li:last-child { border-bottom: 0; }
    .icono { width: 38px; height: 38px; border-radius: 12px; background: var(--ok-fondo); color: var(--ok); display: flex; align-items: center; justify-content: center; }
    .icono.rechazado { background: var(--mora-fondo); color: var(--mora); }
    .info { flex: 1; display: flex; flex-direction: column; }
    .info span { font-size: 12.5px; color: var(--texto-3); }
    .vacio { justify-content: center; color: var(--texto-3); }
    .ver-todos { font-weight: 700; font-size: 14px; text-align: center; }
  `],
})
export class MisPagos {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);
  protected readonly grupos = signal<Grupo[] | null>(null);
  protected readonly error = signal('');
  protected readonly textoMedio = TEXTO_MEDIO;

  constructor() {
    this.api.misCreditos().pipe(
      switchMap((creditos) => creditos.length === 0 ? of([]) :
        forkJoin(creditos.map((c) => this.api.misPagos(c.id).pipe(map((pagos) => ({ credito: c, pagos }))))),
      ),
    ).subscribe({
      next: (g) => this.grupos.set(g),
      error: (e) => this.error.set(mensajeError(e)),
    });
  }

  protected descargar(creditoId: number, p: PagoHistorial): void {
    this.api.comprobantePdf(creditoId, p.pagoId).subscribe({
      next: (b) => guardarArchivo(b, `comprobante-${p.referencia}.pdf`),
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }
}
