import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CreditoDetalle, MedioPago, OpcionesPago, PagoHistorial, PlanPagos } from '../../core/models';
import { TEXTO_MEDIO, guardarArchivo, mensajeError } from '../../core/utilidades';
import { Anillo, Cargando, ChipCuota, ChipSemaforo, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe, MonedaPipe, NumeroPipe } from '../../shared/pipes';

/** Detalle de un credito para el administrador: plan, pagos y registro de pagos recibidos por otros canales. */
@Component({
  selector: 'app-credito-admin',
  imports: [FormsModule, Icono, Encabezado, Cargando, Anillo, ChipSemaforo, ChipCuota, MonedaPipe, NumeroPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (detalle(); as d) {
      <app-encabezado [titulo]="'Crédito ' + d.resumen.numeroCredito" [subtitulo]="d.resumen.cliente + ' · ' + d.resumen.documentoCliente" volver="/admin/creditos">
        @if (d.resumen.estado === 'CANCELADO') {
          <button type="button" class="btn btn-secundario" (click)="pazYSalvo()"><app-icono nombre="download" [tamano]="18" />Paz y salvo</button>
        } @else {
          <button type="button" class="btn btn-primario" (click)="abrirPago()"><app-icono nombre="money" [tamano]="18" />Registrar pago</button>
        }
      </app-encabezado>

      <div class="cabecera">
        <section class="tarjeta resumen">
          <app-anillo [porcentaje]="d.resumen.porcentajePagado" [tamano]="118" />
          <div class="cifras">
            <div><span class="etiqueta">Saldo capital</span><strong class="valor-grande">{{ d.resumen.saldoCapital | moneda }}</strong></div>
            <app-chip-semaforo [estado]="d.resumen.semaforo" [detalle]="d.resumen.diasMora > 0 ? d.resumen.diasMora + ' días' : ''" />
          </div>
        </section>
        <section class="tarjeta datos">
          <div><span>Valor financiado</span><strong>{{ d.resumen.valorFinanciado | moneda }}</strong></div>
          <div><span>Cuota</span><strong>{{ d.resumen.valorCuota | moneda }}</strong></div>
          <div><span>Plazo / tasa</span><strong>{{ d.resumen.plazoMeses }} m · {{ d.resumen.tasaInteresMensual | numero }} %</strong></div>
          <div><span>Cuotas pagadas</span><strong>{{ d.resumen.cuotasPagadas }} de {{ d.resumen.plazoMeses }}</strong></div>
          <div><span>Valor vencido</span><strong [class.mora]="d.resumen.valorVencido > 0">{{ d.resumen.valorVencido | moneda }}</strong></div>
          <div><span>Mora ({{ d.resumen.moraPorDia | moneda }}/día)</span><strong [class.mora]="d.resumen.valorMora > 0">{{ d.resumen.valorMora | moneda }}</strong></div>
          <div><span>Vehículo</span><strong>{{ d.resumen.vehiculo }} · {{ d.resumen.placa }}</strong></div>
          <div><span>Desembolso</span><strong>{{ d.fechaDesembolso | fecha }}</strong></div>
          <div><span>Prenda</span><strong>{{ d.resumen.estadoPrenda === 'LEVANTADA' ? 'Levantada' : d.resumen.estadoPrenda === 'EN_TRAMITE' ? 'En trámite' : 'Vigente' }}</strong></div>
        </section>
      </div>

      @if (formPago(); as o) {
        <section class="tarjeta pago aparecer">
          <div class="cab"><h2>Registrar pago recibido</h2>
            <button type="button" class="btn btn-fantasma btn-icono" (click)="formPago.set(null)" aria-label="Cerrar"><app-icono nombre="x" [tamano]="18" /></button></div>
          <p class="texto-suave">Para pagos recibidos en oficina, transferencia o consignación. Vencido: <strong>{{ o.valorVencido | moneda }}</strong> ·
            Mora: <strong>{{ o.valorMora | moneda }}</strong> ({{ o.diasMoraCobrados }} días) ·
            Hasta próxima cuota: <strong>{{ o.valorHastaProximaCuota | moneda }}</strong> · Máximo (saldo total): <strong>{{ o.valorMaximo | moneda }}</strong></p>
          <form class="rejilla-form" (ngSubmit)="registrar()">
            <div class="campo"><label for="va">Valor</label><input id="va" name="va" type="number" [(ngModel)]="valorPago" [min]="o.valorMinimo" [max]="o.valorMaximo" required></div>
            <div class="campo"><label for="me">Medio</label>
              <select id="me" name="me" [(ngModel)]="medioPago">@for (m of mediosAdmin; track m) { <option [value]="m">{{ textoMedio[m] }}</option> }</select></div>
            <div class="campo ancho"><label for="ob">Observación</label><input id="ob" name="ob" [(ngModel)]="observacion" maxlength="250" placeholder="Ej. Recibo de caja 0045"></div>
            <div class="acciones">
              <button type="button" class="btn btn-secundario" (click)="valorPago = o.valorVencido > 0 ? o.valorVencido + o.valorMora : o.valorHastaProximaCuota">Usar valor sugerido</button>
              <button type="submit" class="btn btn-primario" [disabled]="registrando() || !valorPago">{{ registrando() ? 'Registrando…' : 'Registrar pago' }}</button>
            </div>
          </form>
        </section>
      }

      <div class="pestanas" role="tablist">
        <button type="button" role="tab" [class.activa]="pestana() === 'plan'" [attr.aria-selected]="pestana() === 'plan'" (click)="pestana.set('plan')">Plan de pagos</button>
        <button type="button" role="tab" [class.activa]="pestana() === 'pagos'" [attr.aria-selected]="pestana() === 'pagos'" (click)="pestana.set('pagos')">Pagos ({{ pagos().length }})</button>
      </div>

      @if (pestana() === 'plan') {
        <div class="tabla-caja tarjeta sin-relleno">
          <table class="tabla">
            <thead><tr><th>#</th><th>Vence</th><th class="num">Cuota</th><th class="num">Capital</th><th class="num">Interés</th><th class="num">Pagado</th><th class="num">Pendiente</th><th>Estado</th></tr></thead>
            <tbody>
              @for (c of plan()?.cuotas ?? []; track c.numeroCuota) {
                <tr [class.vencida]="c.estado === 'VENCIDA'">
                  <td>{{ c.numeroCuota }}</td><td>{{ c.fechaVencimiento | fecha }}</td>
                  <td class="num">{{ c.valorCuota | moneda }}</td><td class="num">{{ c.valorCapital | moneda }}</td><td class="num">{{ c.valorInteres | moneda }}</td>
                  <td class="num">{{ c.valorPagado | moneda }}</td><td class="num">{{ c.saldoPendiente | moneda }}</td>
                  <td><app-chip-cuota [estado]="c.estado" /></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      } @else {
        <div class="tabla-caja tarjeta sin-relleno">
          <table class="tabla">
            <thead><tr><th>Referencia</th><th>Fecha</th><th>Medio</th><th>Canal</th><th>Cuotas</th><th class="num">Valor</th><th>Estado</th></tr></thead>
            <tbody>
              @for (p of pagos(); track p.pagoId) {
                <tr>
                  <td><strong>{{ p.referencia }}</strong></td><td>{{ p.fecha | fecha: 'hora' }}</td><td>{{ textoMedio[p.medioPago] }}</td>
                  <td>{{ p.canal }}</td><td>{{ p.cuotasAplicadas || '—' }}</td><td class="num">{{ p.valor | moneda }}</td>
                  <td><span class="chip" [class]="p.estado === 'APROBADO' ? 'chip chip-ok' : 'chip chip-mora'">{{ p.estado === 'APROBADO' ? 'Aprobado' : 'Rechazado' }}</span></td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="vacio">Sin pagos registrados.</td></tr>
              }
            </tbody>
          </table>
        </div>
      }
    } @else if (error()) {
      <app-encabezado titulo="Crédito" volver="/admin/creditos" />
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-encabezado titulo="Crédito" volver="/admin/creditos" />
      <app-cargando [cantidad]="3" [alto]="140" />
    }
  `,
  styles: [`
    .cabecera { display: grid; gap: 16px; grid-template-columns: minmax(0, 360px) minmax(0, 1fr); margin-bottom: 16px; }
    @media (max-width: 900px) { .cabecera { grid-template-columns: 1fr; } }
    .resumen { display: flex; align-items: center; gap: 18px; }
    .cifras { display: flex; flex-direction: column; gap: 10px; align-items: flex-start; }
    .datos { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 14px; }
    .datos div { display: flex; flex-direction: column; }
    .datos span { font-size: 12px; color: var(--texto-3); }
    .datos strong { font-size: 14.5px; }
    .mora { color: var(--mora); }
    .pago { margin-bottom: 16px; border: 2px solid var(--rojo); display: flex; flex-direction: column; gap: 12px; }
    .cab { display: flex; justify-content: space-between; align-items: center; }
    h2 { font-size: 16px; font-weight: 800; }
    p { margin: 0; font-size: 13.5px; }
    .ancho { grid-column: span 2; }
    @media (max-width: 600px) { .ancho { grid-column: auto; } }
    .acciones { grid-column: 1 / -1; display: flex; justify-content: flex-end; gap: 10px; flex-wrap: wrap; }
    .pestanas { display: flex; gap: 4px; border-bottom: 2px solid var(--borde); margin-bottom: 14px; }
    .pestanas button { border: 0; background: none; font: inherit; font-weight: 700; color: var(--texto-3); padding: 10px 16px; cursor: pointer; border-bottom: 3px solid transparent; margin-bottom: -2px; }
    .pestanas .activa { color: var(--rojo); border-bottom-color: var(--rojo); }
    .sin-relleno { padding: 0; }
    .vencida td { background: var(--mora-fondo); }
    .vacio { text-align: center; color: var(--texto-3); padding: 24px; }
  `],
})
export class CreditoAdmin {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);
  readonly id = input.required<string>();

  protected readonly detalle = signal<CreditoDetalle | null>(null);
  protected readonly plan = signal<PlanPagos | null>(null);
  protected readonly pagos = signal<PagoHistorial[]>([]);
  protected readonly error = signal('');
  protected readonly pestana = signal<'plan' | 'pagos'>('plan');
  protected readonly formPago = signal<OpcionesPago | null>(null);
  protected readonly registrando = signal(false);
  protected readonly textoMedio = TEXTO_MEDIO;
  protected readonly mediosAdmin: MedioPago[] = ['EFECTIVO', 'TRANSFERENCIA', 'CONSIGNACION'];
  protected valorPago = 0;
  protected medioPago: MedioPago = 'EFECTIVO';
  protected observacion = '';

  constructor() {
    effect(() => this.cargar(Number(this.id())));
  }

  private cargar(id: number): void {
    forkJoin({ d: this.api.creditoAdmin(id), p: this.api.cuotasAdmin(id), g: this.api.pagosAdmin(id) }).subscribe({
      next: ({ d, p, g }) => { this.detalle.set(d); this.plan.set(p); this.pagos.set(g); },
      error: (e) => this.error.set(mensajeError(e)),
    });
  }

  protected abrirPago(): void {
    this.api.opcionesPagoAdmin(Number(this.id())).subscribe({
      next: (o) => { this.formPago.set(o); this.valorPago = o.valorVencido > 0 ? o.valorVencido + o.valorMora : o.valorHastaProximaCuota; this.observacion = ''; },
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }

  protected registrar(): void {
    this.registrando.set(true);
    this.api.registrarPagoAdmin(Number(this.id()), { valor: Number(this.valorPago), medioPago: this.medioPago, observacion: this.observacion || undefined }).subscribe({
      next: (c) => {
        this.notificaciones.exito(`Pago ${c.referencia} registrado.`);
        this.registrando.set(false);
        this.formPago.set(null);
        this.pestana.set('pagos');
        this.cargar(Number(this.id()));
      },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.registrando.set(false); },
    });
  }

  protected pazYSalvo(): void {
    this.api.pazYSalvoAdminPdf(Number(this.id())).subscribe({
      next: (b) => guardarArchivo(b, `paz-y-salvo-${this.detalle()?.resumen.numeroCredito ?? this.id()}.pdf`),
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }
}
