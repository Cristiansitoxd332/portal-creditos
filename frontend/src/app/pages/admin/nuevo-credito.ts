import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SUCURSALES } from '../../core/sucursales';
import { debounceTime, filter, map, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CATALOGO_YAMAHA, ReferenciaMoto } from '../../core/catalogo-motos';
import { ClaseVehiculo, ClienteInfo, CodeudorInfo, CredencialesTemporales, CreditoResumen, PlanAmortizacion, TipoDocumento } from '../../core/models';
import { hoyIso, mensajeError } from '../../core/utilidades';
import { Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe, MonedaPipe, NumeroPipe } from '../../shared/pipes';

/** Registro de un credito con vista previa en vivo del plan de amortizacion (sistema frances). */
@Component({
  selector: 'app-nuevo-credito',
  imports: [ReactiveFormsModule, FormsModule, RouterLink, Icono, Encabezado, MonedaPipe, NumeroPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Nuevo crédito" subtitulo="Registra la moto y las condiciones financieras" volver="/admin/creditos" />

    <form [formGroup]="form" (ngSubmit)="guardar()" class="distribucion">
      <div class="columna">
        <section class="tarjeta">
          <h2><span class="paso">1</span>Cliente</h2>
          <div class="campo" [class.invalido]="invalido('clienteId')">
            <label for="cli">Titular del crédito</label>
            <select id="cli" formControlName="clienteId">
              <option [ngValue]="null" disabled>Selecciona un cliente…</option>
              @for (c of clientes(); track c.id) { <option [ngValue]="c.id">{{ c.nombres }} {{ c.apellidos }} · {{ c.numeroDocumento }}</option> }
            </select>
            <span class="error">Selecciona el cliente.</span>
          </div>
          @if (activosDelCliente(); as activos) {
            @if (activos.length) {
              <div class="aviso aviso-existente aparecer" role="alert">
                <app-icono nombre="alert" [tamano]="20" />
                <div>
                  <strong>Este cliente ya tiene {{ activos.length === 1 ? 'un crédito activo' : activos.length + ' créditos activos' }}:</strong>
                  <ul>
                    @for (a of activos; track a.id) {
                      <li><a [routerLink]="['/admin/creditos', a.id]">{{ a.numeroCredito }}</a> · moto {{ a.placa }} · saldo {{ a.saldoCapital | moneda }}</li>
                    }
                  </ul>
                  <p>Si quiere <b>cambiar datos</b>, no registre otro crédito:
                    <a [routerLink]="['/admin/creditos', activos[0].id]">abra el crédito</a> o
                    <a routerLink="/admin/clientes">modifique los datos del cliente</a>.</p>
                  <label class="check-adicional">
                    <input type="checkbox" [checked]="adicional()" (change)="adicional.set($any($event.target).checked)">
                    <span>Confirmo que es un crédito <b>NUEVO por otra moto</b> (cada crédito lleva su propia moto y su propio plan de pagos).</span>
                  </label>
                </div>
              </div>
            }
          }
          @if (!nuevoCliente()) {
            <button type="button" class="btn btn-secundario btn-pequeno nuevo" (click)="abrirNuevoCliente()">
              <app-icono nombre="plus" [tamano]="16" />¿No está en la lista? Crear cliente nuevo
            </button>
          } @else {
            <div class="mini-form aparecer">
              <strong>Nuevo cliente</strong>
              <div class="rejilla-form">
                <div class="campo"><label for="nc-td">Tipo de documento</label>
                  <select id="nc-td" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cli.tipoDocumento">
                    @for (t of tiposDoc; track t) { <option [value]="t">{{ t }}</option> }</select></div>
                <div class="campo"><label for="nc-nd">Número de documento</label>
                  <input id="nc-nd" inputmode="numeric" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cli.numeroDocumento"
                         (ngModelChange)="docNuevo.set($event)" [class.invalido]="!!clienteExistente()"></div>
                <div class="campo"><label for="nc-no">Nombres</label><input id="nc-no" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cli.nombres"></div>
                <div class="campo"><label for="nc-ap">Apellidos</label><input id="nc-ap" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cli.apellidos"></div>
                <div class="campo"><label for="nc-co">Correo (opcional)</label><input id="nc-co" type="email" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cli.correo"></div>
                <div class="campo"><label for="nc-te">Teléfono (opcional)</label><input id="nc-te" inputmode="tel" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cli.telefono"></div>
              </div>
              @if (clienteExistente(); as e) {
                <div class="aviso aviso-mora existente aparecer" role="alert">
                  <app-icono nombre="alert" [tamano]="18" />
                  <div>
                    <strong>Ya existe un cliente con el documento {{ e.numeroDocumento }}:</strong> {{ e.nombres }} {{ e.apellidos }}
                    ({{ e.creditos === 1 ? '1 crédito' : e.creditos + ' créditos' }}). Un cliente no se puede registrar dos veces.
                    <div class="acciones-existente">
                      <button type="button" class="btn btn-primario btn-pequeno" (click)="usarExistente(e)">Usar este cliente</button>
                      <a class="btn btn-secundario btn-pequeno" routerLink="/admin/clientes">Modificar sus datos</a>
                    </div>
                  </div>
                </div>
              }
              <div class="acciones-mini">
                <button type="button" class="btn btn-fantasma btn-pequeno" (click)="nuevoCliente.set(false)">Cancelar</button>
                <button type="button" class="btn btn-primario btn-pequeno" [disabled]="creandoCliente() || !!clienteExistente()" (click)="crearCliente()">
                  {{ creandoCliente() ? 'Creando…' : 'Crear y seleccionar' }}</button>
              </div>
            </div>
          }
          @if (credenciales(); as c) {
            <div class="aviso aviso-ok credenciales aparecer" role="status">
              <app-icono nombre="key" [tamano]="18" />
              <span>Cliente creado. Usuario <b>{{ c.usuario }}</b> · clave temporal <b>{{ c.passwordTemporal }}</b>. Entrégala al cliente; deberá cambiarla en su primer ingreso.</span>
            </div>
          }
        </section>

        <section class="tarjeta">
          <h2><span class="paso">2</span>Moto (garantía prendaria)</h2>
          <div class="campo referencia">
            <label for="ref">Referencia Yamaha</label>
            <select id="ref" [value]="referencia()?.codigo ?? ''" (change)="elegirReferencia($any($event.target).value)">
              <option value="">Otra (escribir a mano)</option>
              @for (r of catalogo; track r.codigo) { <option [value]="r.codigo">{{ r.linea }} · {{ r.precio | moneda }}</option> }
            </select>
            <span class="ayuda">Llena marca, línea y valor (precio de la moto + matrícula con prenda).</span>
          </div>
          @if (referencia(); as r) {
            <div class="desglose">
              <div><span>Precio moto</span><strong>{{ r.precio | moneda }}</strong></div>
              <div><span>Matrícula con prenda</span><strong>{{ r.matriculaConPrenda | moneda }}</strong></div>
              <div><span>Total</span><strong class="primario">{{ r.precio + r.matriculaConPrenda | moneda }}</strong></div>
            </div>
            <p class="texto-suave nota">De contado (sin prenda) la matrícula sería {{ r.matriculaSinPrenda | moneda }}.</p>
          }
          <div class="rejilla-form">
            <div class="campo" [class.invalido]="invalido('placa')"><label for="pl">Placa</label><input id="pl" formControlName="placa" placeholder="ABC12D" class="mayus"><span class="error">Placa de moto: ABC12D.</span></div>
            <div class="campo"><label for="cl">Clase</label><input id="cl" value="Motocicleta" readonly><span class="ayuda">Rentar financia únicamente motos.</span></div>
            <div class="campo" [class.invalido]="invalido('marca')"><label for="ma">Marca</label><input id="ma" formControlName="marca"><span class="error">Obligatorio.</span></div>
            <div class="campo" [class.invalido]="invalido('linea')"><label for="li">Línea</label><input id="li" formControlName="linea"><span class="error">Obligatorio.</span></div>
            <div class="campo" [class.invalido]="invalido('modelo')"><label for="mo">Modelo (año)</label><input id="mo" type="number" formControlName="modelo"><span class="error">Año entre 1990 y 2027.</span></div>
            <div class="campo"><label for="co">Color</label><input id="co" formControlName="color"></div>
            <div class="campo"><label for="nm">N.º de motor</label><input id="nm" formControlName="numeroMotor"></div>
            <div class="campo"><label for="nc">N.º de chasis</label><input id="nc" formControlName="numeroChasis"></div>
          </div>
        </section>

        <section class="tarjeta">
          <h2><span class="paso">3</span>Condiciones financieras</h2>
          <div class="rejilla-form">
            <div class="campo" [class.invalido]="invalido('valorVehiculo')"><label for="vv">Valor total (moto + matrícula)</label><input id="vv" type="number" formControlName="valorVehiculo" min="1"><span class="error">Valor mayor a cero.</span></div>
            <div class="campo" [class.invalido]="invalido('cuotaInicial')"><label for="ci">Cuota inicial</label><input id="ci" type="number" formControlName="cuotaInicial" min="0"><span class="error">Valor no válido.</span></div>
            <fieldset class="tasa">
              <legend>Tasa de interés mensual</legend>
              <label class="opcion-tasa" [class.activa]="!especial()">
                <input type="radio" name="tipoTasa" [checked]="!especial()" (change)="elegirTasa(false)">
                <span><strong>2,84 %</strong> Tasa general</span>
              </label>
              <label class="opcion-tasa" [class.activa]="especial()">
                <input type="radio" name="tipoTasa" [checked]="especial()" (change)="elegirTasa(true)">
                <span><strong>1,80 %</strong> Caso especial: empleado de Rentar Inversiones o de Yamaha</span>
              </label>
            </fieldset>
            <div class="campo" [class.invalido]="invalido('plazoMeses')"><label for="pz">Plazo (meses)</label><input id="pz" type="number" formControlName="plazoMeses"><span class="error">Entre 6 y 120 meses.</span></div>
            <div class="campo"><label for="fd">Fecha de desembolso</label><input id="fd" type="date" formControlName="fechaDesembolso" [max]="fechaSistema()"><span class="ayuda">No puede ser posterior al {{ fechaSistema() | fecha }} (fecha del sistema).</span></div>
            <div class="campo"><label for="fp">Primera cuota</label>
              <select id="fp" formControlName="fechaPrimeraCuota">
                <option value="">Automática · {{ corteSugerido() | fecha }}</option>
                @for (c of cortes(); track c) { <option [value]="c">{{ c | fecha }}</option> }
              </select>
              <span class="ayuda">Fechas de corte de Rentar: día 5 y día 20 de cada mes.</span></div>
            <div class="campo" [class.invalido]="errorOficial()"><label for="cof">Cuota oficial (cotizador)</label>
              <input id="cof" type="number" min="0" step="1" formControlName="cuotaOficial" placeholder="Ej.: 1053000">
              @if (errorOficial()) { <span class="error activo">Debe ser mayor o igual a la cuota financiera ({{ plan()?.valorCuota | moneda }}).</span> }
              @else { <span class="ayuda">La diferencia con la cuota financiera se divide en 2: seguro de vida y FOGADE.</span> }</div>
            <div class="campo"><label for="su">Sucursal</label>
              <select id="su" formControlName="sucursal">
                @for (s of sucursales; track s.codigo) { <option [value]="s.codigo">{{ s.codigo }} · {{ s.nombre }}</option> }
              </select></div>
            <div class="campo"><label for="as">Asesor de ventas (opcional)</label><input id="as" formControlName="asesor" maxlength="100"></div>
          </div>
          @if (seguros(); as sg) {
            <div class="desglose seguros aparecer">
              <div><span>Cuota financiera</span><strong>{{ sg.financiera | moneda }}</strong></div>
              <div><span>Seguro de vida</span><strong>{{ sg.vida | moneda }}</strong></div>
              <div><span>FOGADE</span><strong>{{ sg.fogade | moneda }}</strong></div>
              <div><span>Cuota oficial</span><strong class="primario">{{ sg.total | moneda }}</strong></div>
            </div>
          }
        </section>

        <section class="tarjeta">
          <h2><span class="paso">4</span>Codeudor (fiador)</h2>
          <label class="check-adicional">
            <input type="checkbox" [checked]="conCodeudor()" (change)="conCodeudor.set($any($event.target).checked)">
            <span>Este crédito tiene codeudor (máximo uno)</span>
          </label>
          @if (conCodeudor()) {
            <div class="rejilla-form aparecer codeudor">
              <div class="campo"><label for="cd-td">Tipo de documento</label>
                <select id="cd-td" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cod.tipoDocumento">
                  @for (t of tiposDoc; track t) { <option [value]="t">{{ t }}</option> }</select></div>
              <div class="campo"><label for="cd-nd">Número de documento</label>
                <div class="con-boton">
                  <input id="cd-nd" inputmode="numeric" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cod.numeroDocumento" (blur)="buscarCodeudor()">
                  <button type="button" class="btn btn-secundario" aria-label="Buscar codeudor" (click)="buscarCodeudor()"><app-icono nombre="search" [tamano]="18" /></button>
                </div>
                @if (codeudorEncontrado(); as ce) { <span class="ayuda ok">Ya registrado: {{ ce.nombre }}. Se usarán sus datos.</span> }</div>
              <div class="campo"><label for="cd-no">Nombres</label><input id="cd-no" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cod.nombres"></div>
              <div class="campo"><label for="cd-ap">Apellidos</label><input id="cd-ap" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cod.apellidos"></div>
              <div class="campo"><label for="cd-te">Teléfono</label><input id="cd-te" inputmode="tel" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cod.telefono"></div>
              <div class="campo"><label for="cd-co">Correo (opcional)</label><input id="cd-co" type="email" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cod.correo"></div>
              <div class="campo ancho"><label for="cd-di">Dirección (opcional)</label><input id="cd-di" [ngModelOptions]="{ standalone: true }" [(ngModel)]="cod.direccion"></div>
            </div>
          }
        </section>
      </div>

      <aside class="columna resumen">
        <section class="tarjeta pegajosa">
          <span class="etiqueta">Valor financiado</span>
          <strong class="valor-grande">{{ financiado() | moneda }}</strong>
          @if (plan(); as p) {
            <div class="datos">
              <div><span>Cuota mensual</span><strong class="primario">{{ (seguros()?.total ?? p.valorCuota) | moneda }}</strong></div>
              <div><span>Total intereses</span><strong>{{ p.totalIntereses | moneda }}</strong></div>
              <div><span>Total a pagar</span><strong>{{ p.totalPagar | moneda }}</strong></div>
              <div><span>Cuotas</span><strong>{{ p.filas.length }}</strong></div>
            </div>
            <details>
              <summary>Ver plan de amortización</summary>
              <div class="tabla-caja mini">
                <table class="tabla">
                  <thead><tr><th>#</th><th>Fecha</th><th class="num">Capital</th><th class="num">Interés</th><th class="num">Seguros</th><th class="num">Total</th><th class="num">Saldo</th></tr></thead>
                  <tbody>
                    @for (f of p.filas; track f.numeroCuota) {
                      <tr><td>{{ f.numeroCuota }}</td><td>{{ f.fechaVencimiento | fecha }}</td><td class="num">{{ f.valorCapital | moneda }}</td>
                        <td class="num">{{ f.valorInteres | moneda }}</td><td class="num">{{ (seguros()?.vida ?? 0) + (seguros()?.fogade ?? 0) | moneda }}</td>
                        <td class="num">{{ f.valorCuota + (seguros()?.vida ?? 0) + (seguros()?.fogade ?? 0) | moneda }}</td><td class="num">{{ f.saldoCapitalFinal | moneda }}</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            </details>
          } @else {
            <p class="texto-suave">Completa las condiciones financieras para ver la cuota.</p>
          }
          <button type="submit" class="btn btn-primario btn-bloque" [disabled]="guardando() || faltaConfirmar()">
            <app-icono nombre="check" [tamano]="18" />{{ guardando() ? 'Registrando…' : 'Registrar crédito' }}
          </button>
          <small class="texto-suave">Tasa {{ form.controls.tasaInteresMensual.value | numero }} % mensual · sistema de cuota fija.</small>
        </section>
      </aside>
    </form>
  `,
  styles: [`
    .distribucion { display: grid; gap: 16px; grid-template-columns: minmax(0, 1fr) 360px; align-items: start; }
    @media (max-width: 960px) { .distribucion { grid-template-columns: 1fr; } }
    .columna { display: flex; flex-direction: column; gap: 16px; }
    h2 { font-size: 16px; font-weight: 800; margin-bottom: 14px; display: flex; align-items: center; gap: 10px; }
    .paso { width: 26px; height: 26px; border-radius: 13px; background: var(--primario); color: #fff; font-size: 13px; display: flex; align-items: center; justify-content: center; }
    .mayus { text-transform: uppercase; }
    .nuevo { margin-top: 12px; }
    .mini-form { margin-top: 12px; padding: 14px; border-radius: 14px; background: var(--superficie-2); display: flex; flex-direction: column; gap: 12px; }
    .acciones-mini { display: flex; justify-content: flex-end; gap: 8px; }
    .aviso-existente { margin-top: 12px; display: flex; gap: 10px; align-items: flex-start; background: var(--alerta-fondo); color: var(--alerta); }
    .aviso-existente ul { margin: 6px 0; padding-left: 18px; }
    .aviso-existente p { margin: 6px 0; }
    .aviso-existente a, .existente a { color: inherit; font-weight: 700; }
    .check-adicional { display: flex; gap: 8px; align-items: flex-start; margin-top: 8px; cursor: pointer; color: var(--texto); }
    .check-adicional input { width: 18px; height: 18px; margin-top: 2px; accent-color: var(--primario); flex-shrink: 0; }
    .desglose.seguros { margin-top: 12px; grid-template-columns: repeat(4, 1fr); }
    @media (max-width: 600px) { .desglose.seguros { grid-template-columns: 1fr 1fr; } }
    .codeudor { margin-top: 12px; }
    .con-boton { display: flex; gap: 6px; }
    .con-boton input { flex: 1; min-width: 0; }
    .ancho { grid-column: 1 / -1; }
    .ayuda.ok { color: var(--ok); font-weight: 700; }
    .existente { display: flex; gap: 10px; align-items: flex-start; margin-top: 10px; }
    .acciones-existente { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
    .acciones-existente a { color: var(--texto); }
    .credenciales { margin-top: 12px; display: flex; gap: 10px; align-items: flex-start; }
    .referencia { margin-bottom: 12px; }
    .desglose { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; background: var(--primario-suave); border-radius: 12px; padding: 12px; }
    .desglose div { display: flex; flex-direction: column; }
    .desglose span { font-size: 12px; color: var(--texto-3); }
    .nota { margin: 6px 0 14px; font-size: 12.5px; }
    .pegajosa { position: sticky; top: 16px; display: flex; flex-direction: column; gap: 12px; }
    .datos { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 12px 0; border-top: 1px solid var(--borde); border-bottom: 1px solid var(--borde); }
    .datos div { display: flex; flex-direction: column; }
    .datos span { font-size: 12px; color: var(--texto-3); }
    .primario { color: var(--primario); font-size: 18px; }
    .tasa { border: 0; padding: 0; margin: 0; min-width: 0; grid-column: 1 / -1; display: grid; gap: 10px;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); }
    .tasa legend { padding: 0; margin-bottom: 8px; font-size: 13.5px; font-weight: 700; }
    .opcion-tasa { display: flex; gap: 10px; align-items: flex-start; padding: 12px 14px; border: 1.5px solid var(--borde-fuerte);
      border-radius: 14px; background: var(--superficie); cursor: pointer; font-size: 13px; color: var(--texto-2); }
    .opcion-tasa.activa { border-color: var(--primario); background: var(--primario-suave); }
    .opcion-tasa strong { display: block; font-size: 18px; color: var(--primario-oscuro); }
    .opcion-tasa input { margin-top: 5px; accent-color: var(--primario); }
    summary { cursor: pointer; font-weight: 700; font-size: 14px; color: var(--primario-oscuro); }
    .mini { max-height: 300px; overflow: auto; margin-top: 10px; font-size: 12.5px; }
    p { margin: 0; font-size: 13.5px; }
  `],
})
export class NuevoCredito {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notificaciones = inject(NotificacionService);

  protected readonly catalogo = CATALOGO_YAMAHA;
  protected readonly referencia = signal<ReferenciaMoto | null>(null);
  /** Tasas de Rentar: 2,84 % mensual general y 1,80 % para empleados de Rentar Inversiones o de Yamaha. */
  protected readonly especial = signal(false);
  protected readonly guardando = signal(false);
  protected readonly clientes = signal<ClienteInfo[]>([]);
  protected readonly nuevoCliente = signal(false);
  protected readonly creandoCliente = signal(false);
  protected readonly credenciales = signal<CredencialesTemporales | null>(null);
  protected readonly tiposDoc: TipoDocumento[] = ['CC', 'CE', 'NIT', 'PAS'];
  /** Documento escrito en el mini formulario de cliente nuevo (para detectar duplicados al instante). */
  protected readonly docNuevo = signal('');
  protected readonly clienteExistente = computed(() => {
    const doc = this.docNuevo().trim();
    return doc ? this.clientes().find((c) => c.numeroDocumento === doc) ?? null : null;
  });
  /** Creditos de la cartera, para avisar si el cliente elegido ya tiene uno activo. */
  private readonly cartera = signal<CreditoResumen[] | null>(null);
  protected readonly adicional = signal(false);
  protected cli = { tipoDocumento: 'CC' as TipoDocumento, numeroDocumento: '', nombres: '', apellidos: '', correo: '', telefono: '' };

  protected readonly form = inject(FormBuilder).group({
    clienteId: [null as number | null, Validators.required],
    placa: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{3}[0-9]{2}[0-9A-Za-z]$/)]],
    clase: ['MOTOCICLETA' as ClaseVehiculo],
    marca: ['Yamaha', Validators.required],
    linea: ['', Validators.required],
    modelo: [2026, [Validators.required, Validators.min(1990), Validators.max(2027)]],
    color: [''],
    numeroMotor: [''],
    numeroChasis: [''],
    valorVehiculo: [0, [Validators.required, Validators.min(1)]],
    cuotaInicial: [0, [Validators.required, Validators.min(0)]],
    tasaInteresMensual: [TASA_GENERAL, Validators.required],
    plazoMeses: [36, [Validators.required, Validators.min(6), Validators.max(120)]],
    fechaDesembolso: [hoyIso(), Validators.required],
    fechaPrimeraCuota: [''],
    cuotaOficial: [null as number | null],
    asesor: [''],
    sucursal: ['01'],
  });

  private readonly valores = toSignal(this.form.valueChanges, { initialValue: this.form.value });
  protected financiado(): number {
    const v = this.valores();
    return Math.max(0, (Number(v.valorVehiculo) || 0) - (Number(v.cuotaInicial) || 0));
  }

  protected readonly plan = toSignal<PlanAmortizacion | null>(
    this.form.valueChanges.pipe(
      debounceTime(400),
      map(() => this.form.getRawValue()),
      filter((v) => !!v.valorVehiculo && v.tasaInteresMensual !== null && !!v.plazoMeses && v.plazoMeses >= 6 && (v.valorVehiculo ?? 0) > (v.cuotaInicial ?? 0)),
      switchMap((v) => this.api.simularPlan((v.valorVehiculo ?? 0) - (v.cuotaInicial ?? 0), Number(v.tasaInteresMensual), Number(v.plazoMeses),
        v.fechaPrimeraCuota || siguienteCorte(sumarDias(v.fechaDesembolso || hoyIso(), 30)))),
    ),
    { initialValue: null },
  );

  /** Creditos activos (no cancelados) del cliente seleccionado. */
  protected readonly activosDelCliente = computed(() => {
    const id = this.valores().clienteId;
    const cliente = this.clientes().find((c) => c.id === id);
    const cartera = this.cartera();
    if (!cliente || !cartera) return null;
    return cartera.filter((c) => c.documentoCliente === cliente.numeroDocumento && c.estado !== 'CANCELADO');
  });
  /** Seguro de vida y FOGADE: (cuota oficial - cuota financiera) / 2 cada uno. */
  protected readonly seguros = computed(() => {
    const oficial = Number(this.valores().cuotaOficial) || 0;
    const p = this.plan();
    if (!p || oficial <= 0 || oficial < p.valorCuota) return null;
    const diferencia = Math.round(oficial - p.valorCuota);
    const fogade = Math.floor(diferencia / 2);
    return { financiera: p.valorCuota, vida: diferencia - fogade, fogade, total: p.valorCuota + diferencia };
  });
  protected readonly errorOficial = computed(() => {
    const oficial = Number(this.valores().cuotaOficial) || 0;
    const p = this.plan();
    return !!p && oficial > 0 && oficial < p.valorCuota;
  });

  protected readonly sucursales = SUCURSALES;
  protected readonly conCodeudor = signal(false);
  protected readonly codeudorEncontrado = signal<CodeudorInfo | null>(null);
  protected cod = { tipoDocumento: 'CC' as TipoDocumento, numeroDocumento: '', nombres: '', apellidos: '', telefono: '', correo: '', direccion: '' };

  protected readonly faltaConfirmar = computed(() => !!this.activosDelCliente()?.length && !this.adicional());

  /** Fecha del sistema (la del servidor, en tiempo real). */
  protected readonly fechaSistema = signal(hoyIso());

  /** Primera cuota sugerida: la fecha de corte (5 o 20) siguiente a 30 días del desembolso. */
  protected readonly corteSugerido = computed(() => siguienteCorte(sumarDias(this.valores().fechaDesembolso || hoyIso(), 30)));
  /** Fechas de corte disponibles para la primera cuota (desde 15 días después del desembolso). */
  protected readonly cortes = computed(() => {
    const lista: string[] = [];
    let f = siguienteCorte(sumarDias(this.valores().fechaDesembolso || hoyIso(), 15));
    while (lista.length < 6) { lista.push(f); f = siguienteCorte(sumarDias(f, 1)); }
    return lista;
  });

  constructor() {
    this.cargarClientes();
    this.api.creditosAdmin().subscribe({ next: (c) => this.cartera.set(c), error: () => this.cartera.set([]) });
    // al cambiar de cliente se debe volver a confirmar el credito adicional
    this.form.controls.clienteId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.adicional.set(false));
    this.api.tablero().subscribe((t) => {
      this.fechaSistema.set(t.fechaCorte);
      this.form.patchValue({ fechaDesembolso: t.fechaCorte });
    });
    // si cambia el desembolso, la primera cuota vuelve a la sugerida
    this.form.controls.fechaDesembolso.valueChanges.pipe(takeUntilDestroyed()).subscribe(() =>
      this.form.controls.fechaPrimeraCuota.setValue('', { emitEvent: false }));
    // arranca con la NMAX seleccionada para mostrar la cuota de inmediato
    queueMicrotask(() => this.elegirReferencia('NMAX'));
  }

  protected elegirTasa(especial: boolean): void {
    this.especial.set(especial);
    this.form.patchValue({ tasaInteresMensual: especial ? TASA_ESPECIAL : TASA_GENERAL });
  }

  /** Llena la moto y sugiere una cuota inicial del 15 % (redondeada a miles). */
  protected elegirReferencia(codigo: string): void {
    const r = this.catalogo.find((x) => x.codigo === codigo) ?? null;
    this.referencia.set(r);
    if (!r) return;
    const total = r.precio + r.matriculaConPrenda;
    this.form.patchValue({
      marca: 'Yamaha', linea: r.linea, clase: 'MOTOCICLETA',
      valorVehiculo: total, cuotaInicial: Math.round((total * 0.15) / 1000) * 1000,
    });
  }

  private cargarClientes(seleccionar?: number): void {
    this.api.clientes().subscribe((l) => {
      this.clientes.set(l);
      if (seleccionar) this.form.patchValue({ clienteId: seleccionar });
    });
  }

  protected abrirNuevoCliente(): void {
    this.cli = { tipoDocumento: 'CC', numeroDocumento: '', nombres: '', apellidos: '', correo: '', telefono: '' };
    this.docNuevo.set('');
    this.credenciales.set(null);
    this.nuevoCliente.set(true);
  }

  /** Crea el cliente sin salir de la pantalla y lo deja seleccionado como titular. */
  protected crearCliente(): void {
    const c = this.cli;
    if (!/^[0-9A-Za-z]{5,20}$/.test(c.numeroDocumento.trim()) || !c.nombres.trim() || !c.apellidos.trim()) {
      this.notificaciones.error('Escribe el documento (5 a 20 caracteres), los nombres y los apellidos.');
      return;
    }
    this.creandoCliente.set(true);
    this.api.crearCliente({ tipoDocumento: c.tipoDocumento, numeroDocumento: c.numeroDocumento.trim(), nombres: c.nombres.trim(),
      apellidos: c.apellidos.trim(), correo: c.correo.trim() || null, telefono: c.telefono.trim() || null, direccion: null, ciudad: 'Neiva' }).subscribe({
      next: (r) => {
        this.creandoCliente.set(false);
        this.nuevoCliente.set(false);
        this.credenciales.set(r);
        this.notificaciones.exito('Cliente creado y seleccionado.');
        this.cargarClientes(r.clienteId);
      },
      error: (e) => { this.creandoCliente.set(false); this.notificaciones.error(mensajeError(e)); },
    });
  }

  /** Si el codeudor ya existe, trae sus datos para no escribirlos dos veces. */
  protected buscarCodeudor(): void {
    const doc = this.cod.numeroDocumento.trim();
    this.codeudorEncontrado.set(null);
    if (doc.length < 5) return;
    this.api.codeudor(doc).subscribe({
      next: (c) => {
        this.codeudorEncontrado.set(c);
        const partes = c.nombre.split(' ');
        const mitad = Math.ceil(partes.length / 2);
        this.cod = { ...this.cod, nombres: this.cod.nombres || partes.slice(0, mitad).join(' '),
          apellidos: this.cod.apellidos || partes.slice(mitad).join(' '), telefono: this.cod.telefono || (c.telefono ?? ''),
          correo: this.cod.correo || (c.correo ?? ''), direccion: this.cod.direccion || (c.direccion ?? '') };
      },
      error: () => this.codeudorEncontrado.set(null),
    });
  }

  /** El documento ya existe: en lugar de duplicarlo se selecciona el cliente registrado. */
  protected usarExistente(c: ClienteInfo): void {
    this.form.patchValue({ clienteId: c.id });
    this.nuevoCliente.set(false);
    this.notificaciones.exito(`Se seleccionó a ${c.nombres} ${c.apellidos}, que ya estaba registrado.`);
  }

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.notificaciones.error('Revisa los campos marcados.');
      return;
    }
    if (this.errorOficial()) {
      this.notificaciones.error('La cuota oficial no puede ser menor que la cuota financiera.');
      return;
    }
    if (this.conCodeudor() && (!this.cod.numeroDocumento.trim() || (!this.codeudorEncontrado()
        && (!this.cod.nombres.trim() || !this.cod.apellidos.trim() || !this.cod.telefono.trim())))) {
      this.notificaciones.error('Complete el documento, nombres, apellidos y teléfono del codeudor.');
      return;
    }
    if (this.faltaConfirmar()) {
      this.notificaciones.error('El cliente ya tiene un crédito activo. Confirme que es un crédito nuevo por otra moto o modifique el existente.');
      return;
    }
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.api.crearCredito({
      clienteId: v.clienteId!, placa: v.placa!.toUpperCase(), marca: v.marca!, linea: v.linea!, modelo: Number(v.modelo), color: v.color || null,
      clase: v.clase!, numeroMotor: v.numeroMotor || null, numeroChasis: v.numeroChasis || null, valorVehiculo: Number(v.valorVehiculo),
      cuotaInicial: Number(v.cuotaInicial), tasaInteresMensual: Number(v.tasaInteresMensual), plazoMeses: Number(v.plazoMeses),
      fechaDesembolso: v.fechaDesembolso!, fechaPrimeraCuota: v.fechaPrimeraCuota || null,
      creditoAdicional: this.adicional(),
      cuotaOficial: Number(v.cuotaOficial) > 0 ? Number(v.cuotaOficial) : null,
      asesor: v.asesor?.trim() || null,
      sucursal: v.sucursal || '01',
      codeudor: this.conCodeudor() ? {
        tipoDocumento: this.cod.tipoDocumento, numeroDocumento: this.cod.numeroDocumento.trim(),
        nombres: this.cod.nombres.trim() || null, apellidos: this.cod.apellidos.trim() || null,
        telefono: this.cod.telefono.trim() || null, correo: this.cod.correo.trim() || null, direccion: this.cod.direccion.trim() || null,
      } : null,
    }).subscribe({
      next: (d) => {
        this.notificaciones.exito(`Crédito ${d.resumen.numeroCredito} registrado.`);
        this.router.navigate(['/admin/creditos', d.resumen.id]);
      },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.guardando.set(false); },
    });
  }
}

const TASA_GENERAL = 2.84;
const TASA_ESPECIAL = 1.8;

/** Fechas de corte de Rentar: las cuotas vencen el día 5 o el día 20. */
const CORTES = [5, 20];

function aIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function sumarDias(iso: string, dias: number): string {
  const [a, m, d] = iso.split('-').map(Number);
  return aIso(new Date(a, m - 1, d + dias));
}

/** Primera fecha de corte (5 o 20) igual o posterior a la fecha dada. */
function siguienteCorte(iso: string): string {
  const [a, m, d] = iso.split('-').map(Number);
  const dia = CORTES.find((c) => c >= d);
  return dia ? aIso(new Date(a, m - 1, dia)) : aIso(new Date(a, m, CORTES[0]));
}
