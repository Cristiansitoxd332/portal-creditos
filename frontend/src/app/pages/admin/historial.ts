import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { CalculoFecha, CreditoHistorial, Historial } from '../../core/models';
import { hoyIso, mensajeError } from '../../core/utilidades';
import { ChipSemaforo, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { FechaPipe, MonedaPipe, NumeroPipe } from '../../shared/pipes';

type Filtro = 'VIGENTES' | 'PAGADOS' | 'TODOS';

/**
 * HISTORIAL DE CREDITOS: consulta por identificacion, inspirada en Sadmit (sin conexion con Sadmit).
 * Muestra los creditos como deudor y como codeudor, y calcula el valor al corte o el total a una fecha.
 */
@Component({
  selector: 'app-historial',
  imports: [FormsModule, RouterLink, Icono, Encabezado, ChipSemaforo, MonedaPipe, FechaPipe, NumeroPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Historial de créditos" subtitulo="Consulta por identificación: créditos como deudor y como codeudor">
      @if (credito(); as c) {
        <a class="btn btn-secundario" [routerLink]="['/admin/creditos', c.credito.id]"><app-icono nombre="calendar" [tamano]="18" />Plan de pagos</a>
        @if (c.credito.estado !== 'CANCELADO' && historial()?.persona?.esCliente) {
          <a class="btn btn-primario" routerLink="/admin/abonos" [queryParams]="{ doc: historial()!.persona.documento }"><app-icono nombre="money" [tamano]="18" />Abonar</a>
        }
      }
    </app-encabezado>

    <section class="tarjeta banda">
      <form class="fila" (ngSubmit)="buscar()">
        <div class="campo">
          <label for="doc">Identificación</label>
          <div class="con-boton">
            <input id="doc" name="doc" inputmode="numeric" [(ngModel)]="documento" placeholder="Cédula" autocomplete="off">
            <button type="submit" class="btn btn-primario" aria-label="Buscar" [disabled]="buscando()"><app-icono nombre="search" [tamano]="18" /></button>
          </div>
        </div>
        <div class="campo"><label for="nom">Nombre</label><input id="nom" readonly [value]="historial()?.persona?.nombre ?? ''"></div>
        <div class="campo"><label for="ncr">Número crédito</label><input id="ncr" readonly [value]="credito()?.credito?.numeroCredito ?? ''"></div>
        <div class="campo"><label for="fin">Fecha inicial</label><input id="fin" readonly [value]="credito() ? (credito()!.fechaDesembolso | fecha) : ''"></div>
        <div class="campo"><label for="sal">Saldo capital</label><input id="sal" readonly [value]="credito() ? (credito()!.credito.saldoCapital | moneda) : ''"></div>
      </form>
      @if (historial(); as h) {
        <p class="contacto">
          @if (h.persona.correo) { <span><b>Correo:</b> {{ h.persona.correo }}</span> }
          @if (h.persona.telefono) { <span><b>Teléfono:</b> {{ h.persona.telefono }}</span> }
          @if (h.persona.direccion) { <span><b>Dirección:</b> {{ h.persona.direccion }}{{ h.persona.ciudad ? ' - ' + h.persona.ciudad : '' }}</span> }
          <span class="roles">
            @if (h.persona.esCliente) { <span class="chip chip-primario">Deudor</span> }
            @if (h.persona.esCodeudor) { <span class="chip chip-alerta">Codeudor</span> }
          </span>
        </p>
      }
    </section>

    @if (error()) { <div class="aviso aviso-mora" role="alert">{{ error() }}</div> }

    @if (historial(); as h) {
      <section class="tarjeta">
        <div class="dos">
          <div>
            <h2>Créditos deudor</h2>
            <div class="radios" role="radiogroup" aria-label="Filtrar créditos como deudor">
              @for (f of filtros; track f.clave) {
                <label><input type="radio" name="fd" [checked]="filtroDeudor() === f.clave" (change)="filtroDeudor.set(f.clave)">{{ f.texto }}</label>
              }
            </div>
            <div class="campo"><select aria-label="Crédito como deudor" [ngModel]="seleccion()" (ngModelChange)="elegir($event)">
              @if (!deudor().length) { <option [ngValue]="null">Sin créditos en este filtro</option> }
              @for (c of deudor(); track c.credito.id) {
                <option [ngValue]="c.credito.id">{{ c.credito.numeroCredito }} · {{ c.credito.placa }} · {{ c.credito.valorFinanciado | moneda }}</option>
              }
            </select></div>
          </div>
          @if (credito(); as c) {
            <dl class="datos">
              <div><dt>Tipo de crédito</dt><dd>{{ c.tipoCredito }}</dd></div>
              <div><dt>Periodicidad</dt><dd>{{ c.periodicidad }}</dd></div>
              <div><dt>Tasa interés</dt><dd>{{ c.credito.tasaInteresMensual | numero }} %</dd></div>
              <div><dt>Cuotas</dt><dd>{{ c.credito.plazoMeses }} ({{ c.credito.cuotasPagadas }} pagadas)</dd></div>
              <div><dt>Asesor de ventas</dt><dd>{{ c.asesor ?? '—' }}</dd></div>
              <div><dt>Sucursal</dt><dd>{{ c.sucursal }}</dd></div>
              <div><dt>Placa</dt><dd>{{ c.credito.placa }}</dd></div>
              <div><dt>Estado</dt><dd><app-chip-semaforo [estado]="c.credito.semaforo" /></dd></div>
              <div><dt>Cuota oficial</dt><dd>{{ c.credito.valorCuota | moneda }}</dd></div>
              <div><dt>Seguro de vida / FOGADE</dt><dd>{{ c.seguroVidaCuota | moneda }} / {{ c.fogadeCuota | moneda }}</dd></div>
              @if (c.fechaCancelacion) { <div><dt>Cancelado el</dt><dd>{{ c.fechaCancelacion | fecha }}</dd></div> }
            </dl>
          }
        </div>
      </section>

      <section class="tarjeta">
        <h2>Codeudor</h2>
        <div class="dos">
          <div>
            @if (credito()?.codeudor; as cd) {
              <dl class="datos">
                <div><dt>Codeudor</dt><dd>{{ cd.tipoDocumento }} {{ cd.numeroDocumento }}</dd></div>
                <div><dt>Nombre</dt><dd>{{ cd.nombre }}</dd></div>
                <div><dt>Teléfono</dt><dd>{{ cd.telefono ?? '—' }}</dd></div>
                <div><dt>Correo</dt><dd>{{ cd.correo ?? '—' }}</dd></div>
                <div class="ancho"><dt>Dirección</dt><dd>{{ cd.direccion ?? '—' }}</dd></div>
              </dl>
            } @else {
              <p class="texto-suave">{{ credito() ? 'Este crédito no tiene codeudor.' : 'Seleccione un crédito.' }}</p>
            }
          </div>
          <div>
            <h3>Créditos donde {{ h.persona.nombre.split(' ')[0] }} es codeudor</h3>
            @if (h.creditosCodeudor.length) {
              <ul class="lista-codeudor">
                @for (c of h.creditosCodeudor; track c.credito.id) {
                  <li><a [routerLink]="['/admin/creditos', c.credito.id]">{{ c.credito.numeroCredito }}</a> · titular {{ c.credito.cliente }}
                    · <app-chip-semaforo [estado]="c.credito.semaforo" /></li>
                }
              </ul>
            } @else {
              <p class="texto-suave">No respalda créditos de otras personas.</p>
            }
          </div>
        </div>
      </section>

      @if (credito(); as c) {
        <section class="tarjeta corte">
          <div class="fila-corte">
            <div class="campo"><label for="fc">Fecha corte</label><input id="fc" type="date" [min]="hoy" [ngModel]="fecha()" (ngModelChange)="fecha.set($event); calculo.set(null)"></div>
            <div class="valor"><span>Valor a pagar</span><strong>{{ (calculo()?.valorPagar ?? 0) | moneda }}</strong></div>
            <div class="botones">
              <button type="button" class="btn btn-secundario" (click)="calcular(false)" [disabled]="calculando()"><app-icono nombre="calculator" [tamano]="18" />Calc. corte</button>
              <button type="button" class="btn btn-secundario" (click)="calcular(true)" [disabled]="calculando()"><app-icono nombre="calculator" [tamano]="18" />Calc. total</button>
            </div>
          </div>
          @if (calculo(); as k) {
            <div class="desglose aparecer">
              <div><span>Capital</span><strong>{{ k.capital | moneda }}</strong></div>
              <div><span>Interés</span><strong>{{ k.interes | moneda }}</strong></div>
              <div><span>Seguro de vida</span><strong>{{ k.seguroVida | moneda }}</strong></div>
              <div><span>FOGADE</span><strong>{{ k.fogade | moneda }}</strong></div>
              <div><span>Mora ({{ k.diasMora }} días)</span><strong class="mora">{{ k.mora | moneda }}</strong></div>
            </div>
            <p class="texto-suave nota">{{ k.explicacion }}</p>
          }
        </section>
      }
    } @else if (!error()) {
      <div class="tarjeta vacio"><app-icono nombre="search" [tamano]="32" /><p>Escriba una identificación para ver el historial del deudor o codeudor.</p></div>
    }
  `,
  styles: [`
    .banda { background: var(--primario-suave); border-color: var(--primario-borde); margin-bottom: 16px; }
    .fila { display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 190px), 1fr)); }
    .con-boton { display: flex; gap: 6px; }
    .con-boton input { flex: 1; min-width: 0; }
    .contacto { margin: 12px 0 0; display: flex; gap: 8px 22px; flex-wrap: wrap; font-size: 13.5px; color: var(--primario-oscuro); align-items: center; }
    .roles { display: flex; gap: 6px; margin-left: auto; }
    section.tarjeta { margin-bottom: 16px; }
    h2 { font-size: 16px; font-weight: 800; margin-bottom: 12px; }
    h3 { font-size: 14px; font-weight: 800; margin: 0 0 10px; }
    .dos { display: grid; gap: 20px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 380px), 1fr)); align-items: start; }
    .radios { display: flex; gap: 14px; flex-wrap: wrap; margin-bottom: 10px; font-size: 14px; }
    .radios label { display: flex; gap: 6px; align-items: center; cursor: pointer; }
    .radios input { accent-color: var(--primario); }
    select { width: 100%; }
    .datos { margin: 0; display: grid; gap: 10px 16px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 160px), 1fr)); }
    .datos dt { font-size: 12px; color: var(--texto-3); }
    .datos dd { margin: 2px 0 0; font-weight: 700; font-size: 14px; }
    .ancho { grid-column: 1 / -1; }
    .lista-codeudor { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 8px; font-size: 14px; }
    .corte { background: var(--primario-suave); border-color: var(--primario-borde); }
    .fila-corte { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; }
    .fila-corte .campo { min-width: 180px; }
    .valor { display: flex; flex-direction: column; flex: 1; min-width: 200px; color: var(--primario-oscuro); }
    .valor span { font-size: 14px; }
    .valor strong { font-size: 28px; font-weight: 800; }
    .botones { display: flex; gap: 8px; flex-wrap: wrap; }
    .desglose { display: grid; gap: 10px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 150px), 1fr)); margin-top: 14px; }
    .desglose div { background: var(--superficie); border-radius: 12px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
    .desglose span { font-size: 12px; color: var(--texto-3); }
    .mora { color: var(--mora); }
    .nota { margin: 10px 0 0; font-size: 12.5px; }
    .vacio { display: flex; flex-direction: column; align-items: center; gap: 8px; color: var(--texto-3); padding: 40px; text-align: center; }
    .vacio p { margin: 0; }
  `],
})
export class HistorialCreditos implements OnInit {
  /** Permite llegar con la identificacion ya buscada (?doc=cedula). */
  readonly doc = input<string>('');
  private readonly api = inject(ApiService);

  protected documento = '';
  protected readonly hoy = hoyIso();
  protected readonly historial = signal<Historial | null>(null);
  protected readonly error = signal('');
  protected readonly buscando = signal(false);
  protected readonly filtroDeudor = signal<Filtro>('VIGENTES');
  protected readonly seleccion = signal<number | null>(null);
  protected readonly fecha = signal(hoyIso());
  protected readonly calculo = signal<CalculoFecha | null>(null);
  protected readonly calculando = signal(false);
  protected readonly filtros: { clave: Filtro; texto: string }[] = [
    { clave: 'VIGENTES', texto: 'Vigentes' }, { clave: 'PAGADOS', texto: 'Pagados' }, { clave: 'TODOS', texto: 'Todos' },
  ];

  protected readonly deudor = computed<CreditoHistorial[]>(() => {
    const lista = this.historial()?.creditosDeudor ?? [];
    const f = this.filtroDeudor();
    return f === 'TODOS' ? lista : lista.filter((c) => (c.credito.estado === 'CANCELADO') === (f === 'PAGADOS'));
  });
  protected readonly credito = computed<CreditoHistorial | null>(() => {
    const id = this.seleccion();
    return this.deudor().find((c) => c.credito.id === id) ?? this.deudor()[0] ?? null;
  });

  ngOnInit(): void {
    if (this.doc()) { this.documento = this.doc(); this.buscar(); }
  }

  protected buscar(): void {
    const doc = this.documento.trim();
    if (!doc) return;
    this.buscando.set(true);
    this.error.set('');
    this.calculo.set(null);
    this.api.historial(doc).subscribe({
      next: (h) => {
        this.historial.set(h);
        this.buscando.set(false);
        this.filtroDeudor.set(h.creditosDeudor.some((c) => c.credito.estado !== 'CANCELADO') ? 'VIGENTES' : 'TODOS');
        this.seleccion.set(this.deudor()[0]?.credito.id ?? null);
      },
      error: (e) => { this.error.set(mensajeError(e)); this.historial.set(null); this.buscando.set(false); },
    });
  }

  protected elegir(id: number | null): void {
    this.seleccion.set(id);
    this.calculo.set(null);
  }

  protected calcular(total: boolean): void {
    const c = this.credito();
    if (!c) return;
    this.calculando.set(true);
    this.api.calculoCredito(c.credito.id, this.fecha() || this.hoy, total).subscribe({
      next: (k) => { this.calculo.set(k); this.calculando.set(false); },
      error: (e) => { this.error.set(mensajeError(e)); this.calculando.set(false); },
    });
  }
}
