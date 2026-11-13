import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, of, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { Comprobante, CreditoParaAbono, DeudorAbonos, MedioPago, TipoAbono, VistaPreviaAbono } from '../../core/models';
import { TEXTO_MEDIO, guardarArchivo, mensajeError } from '../../core/utilidades';
import { ChipSemaforo, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe, MonedaPipe } from '../../shared/pipes';
import { SUCURSALES, etiquetaSucursal } from '../../core/sucursales';

const TIPOS: { clave: TipoAbono; texto: string; ayuda: string }[] = [
  { clave: 'NORMAL', texto: 'Abono normal', ayuda: 'Paga la mora y las cuotas en orden: seguro de vida, FOGADE, interés y capital.' },
  { clave: 'CAPITAL_PLAZO', texto: 'Abonar capital - bajar tiempo', ayuda: 'Cubre lo vencido; el excedente va a capital. Misma cuota, menos cuotas.' },
  { clave: 'CAPITAL_CUOTA', texto: 'Abonar capital - bajar cuota', ayuda: 'Cubre lo vencido; el excedente va a capital. Mismas cuotas, cuota más baja.' },
  { clave: 'TOTAL', texto: 'Total pago', ayuda: 'Cancela el crédito hoy: capital, interés y seguros causados, lo vencido y la mora.' },
];
const TEXTO_LINEA: Record<string, string> = {
  MORA: 'Interés de mora', SEGURO_VIDA: 'Seguro de vida', FOGADE: 'FOGADE', INTERES: 'Interés préstamo',
  CAPITAL: 'Cuota capital', ABONO_CAPITAL: 'Abono a capital',
};

/**
 * ABONOS INDIVIDUALES: el administrador registra un pago recibido en la oficina, como en Sadmit
 * (sin conexion con Sadmit). Muestra la distribucion antes de grabar.
 */
@Component({
  selector: 'app-abonos',
  imports: [FormsModule, RouterLink, Icono, Encabezado, ChipSemaforo, MonedaPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Abonos individuales" subtitulo="Registra un pago recibido en la oficina y revisa cómo se aplica antes de grabarlo">
      <button type="button" class="btn btn-secundario" (click)="deshacer()"><app-icono nombre="refresh" [tamano]="18" />Deshacer</button>
      <button type="button" class="btn btn-primario" [disabled]="!puedeGrabar()" (click)="grabar()">
        <app-icono nombre="check" [tamano]="18" />{{ grabando() ? 'Grabando…' : 'Grabar' }}</button>
    </app-encabezado>

    <section class="tarjeta banda">
      <form class="buscar" (ngSubmit)="buscar()">
        <div class="campo">
          <label for="suc">Sucursal <span class="req">*</span></label>
          <select id="suc" name="suc" [ngModel]="sucursal()" (ngModelChange)="elegirSucursal($event)" [class.pendiente]="!sucursal()">
            <option value="" disabled>Seleccione la sucursal</option>
            @for (s of sucursales; track s.codigo) { <option [value]="s.codigo">{{ s.codigo }} · {{ s.nombre }}</option> }
          </select>
        </div>
        <div class="campo">
          <label for="doc">Identificación <span class="req">*</span></label>
          <div class="con-boton">
            <input id="doc" name="doc" inputmode="numeric" [(ngModel)]="documento" [placeholder]="sucursal() ? 'Cédula del deudor' : 'Primero elija la sucursal'"
                   [disabled]="!sucursal()" autocomplete="off">
            <button type="submit" class="btn btn-primario" [disabled]="buscando() || !sucursal()" aria-label="Buscar deudor"><app-icono nombre="search" [tamano]="18" /></button>
          </div>
        </div>
        <div class="campo">
          <label for="nom">Nombre</label>
          <input id="nom" readonly [value]="deudor()?.nombre ?? ''">
          @if (seleccionado(); as s) {
            <small class="texto-suave">CLIENTE · crédito originado en {{ etiquetaSucursal(s.sucursal) }} · pago en {{ etiquetaSucursal(sucursal()) }}</small>
          }
        </div>
      </form>
      @if (seleccionado(); as s) {
        <div class="valor-normal">
          <span>Valor normal pago</span>
          <strong>{{ s.valorNormalPago | moneda }}</strong>
          <small>Total para cancelar hoy: {{ s.valorTotalPago | moneda }}</small>
        </div>
      }
    </section>

    @if (error()) { <div class="aviso aviso-mora" role="alert">{{ error() }}</div> }

    @if (deudor(); as d) {
      @if (comprobante(); as c) {
        <section class="tarjeta exito aparecer" role="status">
          <app-icono nombre="check" [tamano]="28" />
          <div>
            <h2>Abono grabado · {{ c.referencia }}</h2>
            <p>{{ c.valor | moneda }} aplicado al crédito {{ c.numeroCredito }}. Saldo de capital: {{ c.saldoCapitalAnterior | moneda }} → <b>{{ c.saldoCapitalNuevo | moneda }}</b>.
              @if (c.estadoCredito === 'CANCELADO') { <b>El crédito quedó cancelado.</b> }</p>
            <div class="acciones">
              <button type="button" class="btn btn-secundario btn-pequeno" (click)="descargarComprobante(c)"><app-icono nombre="download" [tamano]="16" />Comprobante PDF</button>
              <a class="btn btn-secundario btn-pequeno" [routerLink]="['/admin/creditos', creditoId()]">Ver crédito</a>
              <button type="button" class="btn btn-primario btn-pequeno" (click)="otroAbono()">Registrar otro abono</button>
            </div>
          </div>
        </section>
      }

      <div class="dos">
        <section class="tarjeta">
          <h2>Datos del abono</h2>
          <div class="rejilla-form">
            <div class="campo"><label for="fa">Fecha abono</label><input id="fa" readonly [value]="d.fecha | fecha"></div>
            <div class="campo"><label for="fp">Forma de pago <span class="req">*</span></label>
              <select id="fp" [ngModel]="medio()" (ngModelChange)="medio.set($event)">
                @for (m of medios; track m) { <option [value]="m">{{ textoMedio[m] }}</option> }
              </select></div>
            <div class="campo"><label for="ta">Tipo abono</label>
              <select id="ta" [ngModel]="tipo()" (ngModelChange)="cambiarTipo($event)">
                @for (t of tipos; track t.clave) { <option [value]="t.clave">{{ t.texto }}</option> }
              </select></div>
            <div class="campo"><label for="va">Valor de abono <span class="req">*</span></label>
              <input id="va" type="number" min="1" step="1" [readonly]="tipo() === 'TOTAL'" [ngModel]="valor()" (ngModelChange)="valor.set($event); cambios.next()"></div>
          </div>
          <p class="ayuda-tipo"><app-icono nombre="calculator" [tamano]="16" />{{ ayudaTipo() }}</p>
          <div class="campo"><label for="co">Concepto de</label>
            <input id="co" maxlength="200" [ngModel]="concepto()" (ngModelChange)="concepto.set($event)" placeholder="Ej.: pago en efectivo en caja"></div>
        </section>

        <section class="tarjeta">
          <h2>Créditos activos <small class="texto-suave">· toque el crédito al que aplica el abono</small></h2>
          @if (d.creditos.length) {
            <div class="tabla-caja">
              <table class="tabla">
                <thead><tr><th>Crédito</th><th>Estado</th><th class="num">Cuota</th><th class="num">Saldo capital</th></tr></thead>
                <tbody>
                  @for (c of d.creditos; track c.credito.id) {
                    <tr class="clic" (click)="elegir(c)" [class.sel]="creditoId() === c.credito.id">
                      <td><strong>{{ c.credito.numeroCredito }}</strong><br><small class="texto-suave">RENTAR_MOTOCICLETAS · {{ c.credito.placa }}</small></td>
                      <td><app-chip-semaforo [estado]="c.credito.semaforo" /></td>
                      <td class="num">{{ c.credito.valorCuota | moneda }}</td>
                      <td class="num">{{ c.credito.saldoCapital | moneda }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <p class="texto-suave">Este cliente no tiene créditos activos.</p>
          }
        </section>
      </div>

      <section class="tarjeta">
        <div class="cab"><h2>Detalle del abono</h2>@if (calculando()) { <small class="texto-suave">Calculando…</small> }</div>
        @if (previa(); as p) {
          <div class="resumen">
            <div><span>Mora</span><strong>{{ p.aMora | moneda }}</strong></div>
            <div><span>Cuotas</span><strong>{{ p.aCuotas - p.aCapital | moneda }}</strong></div>
            <div><span>Abono a capital</span><strong>{{ p.aCapital | moneda }}</strong></div>
            <div><span>Saldo capital</span><strong>{{ p.saldoCapitalAnterior | moneda }} → {{ p.saldoCapitalNuevo | moneda }}</strong></div>
            <div><span>Cuotas pendientes</span><strong>{{ p.cuotasPendientesAntes }} → {{ p.cuotasPendientesDespues }}</strong></div>
            <div><span>Valor cuota</span><strong>{{ p.valorCuotaAntes | moneda }} → {{ p.valorCuotaDespues | moneda }}</strong></div>
          </div>
          <p class="aviso" [class.aviso-ok]="p.cancelaCredito">{{ p.resumen }}</p>
          <div class="tabla-caja">
            <table class="tabla detalle">
              <thead><tr><th>Tipo</th><th>Crédito</th><th>Detalle</th><th class="num">Saldo ant.</th><th class="num">Valor abono</th><th class="num">Saldo</th></tr></thead>
              <tbody>
                @for (l of p.lineas; track $index) {
                  <tr [class.mora]="l.tipo === 'MORA'" [class.capital]="l.tipo === 'ABONO_CAPITAL'">
                    <td class="nowrap">{{ textoLinea[l.tipo] ?? l.tipo }}</td>
                    <td class="nowrap">{{ p.numeroCredito }}</td>
                    <td>{{ l.detalle }}</td>
                    <td class="num">{{ l.saldoAnterior | moneda }}</td>
                    <td class="num"><strong>{{ l.valor | moneda }}</strong></td>
                    <td class="num">{{ l.saldo | moneda }}</td>
                  </tr>
                }
              </tbody>
              <tfoot><tr><td colspan="4">Subtotal</td><td class="num"><strong>{{ p.valor | moneda }}</strong></td><td></td></tr></tfoot>
            </table>
          </div>
        } @else if (errorPrevia()) {
          <div class="aviso aviso-mora" role="alert">{{ errorPrevia() }}</div>
        } @else {
          <p class="texto-suave">Seleccione un crédito y escriba el valor para ver cómo se aplica.</p>
        }
      </section>
    } @else if (!error()) {
      <div class="tarjeta vacio"><app-icono nombre="search" [tamano]="32" /><p>Escriba la identificación del deudor y presione buscar.</p></div>
    }
  `,
  styles: [`
    .banda { background: var(--primario-suave); border-color: var(--primario-borde); display: flex; gap: 20px; align-items: flex-end; flex-wrap: wrap; justify-content: space-between; margin-bottom: 16px; }
    .buscar { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr)); flex: 1; min-width: min(100%, 520px); }
    .con-boton { display: flex; gap: 6px; }
    .con-boton input { flex: 1; min-width: 0; }
    .req { color: var(--mora); }
    .valor-normal { text-align: right; display: flex; flex-direction: column; gap: 2px; color: var(--primario-oscuro); }
    .valor-normal span { font-size: 15px; }
    .valor-normal strong { font-size: 30px; font-weight: 800; letter-spacing: -0.02em; }
    .valor-normal small { font-size: 12.5px; color: var(--texto-2); }
    .dos { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 440px), 1fr)); margin-bottom: 16px; align-items: start; }
    h2 { font-size: 16px; font-weight: 800; margin-bottom: 14px; }
    .ayuda-tipo { display: flex; gap: 8px; align-items: flex-start; font-size: 13px; color: var(--texto-2); margin: 4px 0 12px; }
    .clic { cursor: pointer; }
    .sel { background: var(--primario-suave); }
    .cab { display: flex; justify-content: space-between; align-items: baseline; }
    .resumen { display: grid; gap: 10px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 180px), 1fr)); margin-bottom: 12px; }
    .resumen div { background: var(--superficie-2); border-radius: 12px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
    .resumen span { font-size: 12px; color: var(--texto-3); }
    .resumen strong { font-size: 14.5px; }
    .detalle tr.mora td:first-child { color: var(--mora); font-weight: 700; }
    .detalle tr.capital { background: var(--primario-suave); }
    .detalle tfoot td { font-weight: 800; border-top: 2px solid var(--borde-fuerte); padding: 12px; }
    .nowrap { white-space: nowrap; }
    .exito { display: flex; gap: 14px; align-items: flex-start; border: 1.5px solid var(--ok); margin-bottom: 16px; color: var(--ok); }
    .exito h2 { margin-bottom: 4px; color: var(--texto); }
    .exito p { margin: 0 0 10px; color: var(--texto-2); }
    .acciones { display: flex; gap: 8px; flex-wrap: wrap; }
    .vacio { display: flex; flex-direction: column; align-items: center; gap: 8px; color: var(--texto-3); padding: 40px; text-align: center; }
    .vacio p { margin: 0; }
  `],
})
export class Abonos implements OnInit {
  /** Permite llegar con el deudor ya buscado (?doc=cedula), por ejemplo desde el historial. */
  readonly doc = input<string>('');

  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);

  protected readonly tipos = TIPOS;
  protected readonly textoLinea = TEXTO_LINEA;
  protected readonly textoMedio = TEXTO_MEDIO;
  protected readonly medios: MedioPago[] = ['EFECTIVO', 'TRANSFERENCIA', 'CONSIGNACION', 'PSE_SIMULADO', 'TARJETA_SIMULADA'];

  protected documento = '';
  protected readonly deudor = signal<DeudorAbonos | null>(null);
  protected readonly error = signal('');
  protected readonly buscando = signal(false);
  protected readonly creditoId = signal<number | null>(null);
  protected readonly tipo = signal<TipoAbono>('NORMAL');
  protected readonly valor = signal<number | null>(null);
  protected readonly medio = signal<MedioPago>('EFECTIVO');
  protected readonly sucursales = SUCURSALES;
  protected readonly etiquetaSucursal = etiquetaSucursal;
  /** Sucursal donde se recibe el pago (por defecto, la del crédito). */
  protected readonly sucursal = signal('');
  protected readonly concepto = signal('');
  protected readonly previa = signal<VistaPreviaAbono | null>(null);
  protected readonly errorPrevia = signal('');
  protected readonly calculando = signal(false);
  protected readonly grabando = signal(false);
  protected readonly comprobante = signal<Comprobante | null>(null);

  protected readonly seleccionado = computed<CreditoParaAbono | null>(() =>
    this.deudor()?.creditos.find((c) => c.credito.id === this.creditoId()) ?? null);
  protected readonly ayudaTipo = computed(() => TIPOS.find((t) => t.clave === this.tipo())?.ayuda ?? '');
  protected readonly puedeGrabar = computed(() => !!this.sucursal() && !!this.previa() && !this.grabando() && !this.calculando() && !this.comprobante());

  protected readonly cambios = new Subject<void>();

  constructor() {
    // Vista previa en tiempo real cada vez que cambia el credito, el tipo o el valor.
    this.cambios.pipe(
      debounceTime(350),
      tap(() => { this.calculando.set(true); this.errorPrevia.set(''); }),
      switchMap(() => {
        const id = this.creditoId();
        const v = this.valor();
        if (!id || (this.tipo() !== 'TOTAL' && (!v || v <= 0))) return of(null);
        return this.api.vistaPreviaAbono({ creditoId: id, tipoAbono: this.tipo(), valor: this.tipo() === 'TOTAL' ? null : Number(v),
          medioPago: this.medio(), concepto: null, sucursal: this.sucursal() }).pipe(
          catchError((e) => { this.errorPrevia.set(mensajeError(e)); return of(null); }));
      }),
      takeUntilDestroyed(),
    ).subscribe((p) => { this.previa.set(p); this.calculando.set(false); });
  }

  ngOnInit(): void {
    if (this.doc()) { this.documento = this.doc(); }
  }

  /** Primero se elige la sucursal; si ya hay una cedula escrita, se busca de una vez. */
  protected elegirSucursal(codigo: string): void {
    this.sucursal.set(codigo);
    if (this.documento.trim() && !this.deudor()) this.buscar();
    else this.cambios.next();
  }

  protected buscar(): void {
    const doc = this.documento.trim();
    if (!doc || !this.sucursal()) return;
    this.buscando.set(true);
    this.error.set('');
    this.deshacer(false);
    this.api.deudorAbonos(doc).subscribe({
      next: (d) => {
        this.deudor.set(d);
        this.buscando.set(false);
        if (d.creditos.length) this.elegir(d.creditos[0]);
      },
      error: (e) => { this.error.set(mensajeError(e)); this.deudor.set(null); this.buscando.set(false); },
    });
  }

  protected elegir(c: CreditoParaAbono): void {
    this.creditoId.set(c.credito.id);
    this.comprobante.set(null);
    this.valor.set(this.tipo() === 'TOTAL' ? c.valorTotalPago : Math.round(c.valorNormalPago));
    this.cambios.next();
  }

  protected cambiarTipo(t: TipoAbono): void {
    this.tipo.set(t);
    const s = this.seleccionado();
    if (s && t === 'TOTAL') this.valor.set(s.valorTotalPago);
    else if (s && this.valor() === s.valorTotalPago) this.valor.set(Math.round(s.valorNormalPago));
    this.cambios.next();
  }

  protected deshacer(todo = true): void {
    if (todo) { this.documento = ''; this.deudor.set(null); this.error.set(''); this.sucursal.set(''); }
    this.creditoId.set(null);
    this.tipo.set('NORMAL');
    this.valor.set(null);
    this.concepto.set('');
    this.previa.set(null);
    this.errorPrevia.set('');
    this.comprobante.set(null);
  }

  protected otroAbono(): void {
    this.buscar();
  }

  protected grabar(): void {
    const id = this.creditoId();
    if (!id) return;
    this.grabando.set(true);
    this.api.registrarAbono({ creditoId: id, tipoAbono: this.tipo(), valor: this.tipo() === 'TOTAL' ? null : Number(this.valor()),
      medioPago: this.medio(), concepto: this.concepto().trim() || null, sucursal: this.sucursal() }).subscribe({
      next: (c) => {
        this.comprobante.set(c);
        this.grabando.set(false);
        this.previa.set(null);
        this.notificaciones.exito(`Abono ${c.referencia} grabado.`);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.grabando.set(false); },
    });
  }

  protected descargarComprobante(c: Comprobante): void {
    const id = this.creditoId();
    if (!id) return;
    this.api.comprobanteAdminPdf(id, c.pagoId).subscribe({
      next: (b) => guardarArchivo(b, `comprobante-${c.referencia}.pdf`),
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }
}
