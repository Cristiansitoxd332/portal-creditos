import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, filter, map, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ClaseVehiculo, PlanAmortizacion } from '../../core/models';
import { hoyIso, mensajeError } from '../../core/utilidades';
import { Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe, MonedaPipe, NumeroPipe } from '../../shared/pipes';

/** Registro de un credito con vista previa en vivo del plan de amortizacion (sistema frances). */
@Component({
  selector: 'app-nuevo-credito',
  imports: [ReactiveFormsModule, Icono, Encabezado, MonedaPipe, NumeroPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Nuevo crédito" subtitulo="Registra el vehículo y las condiciones financieras" volver="/admin/creditos" />

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
        </section>

        <section class="tarjeta">
          <h2><span class="paso">2</span>Vehículo (garantía prendaria)</h2>
          <div class="rejilla-form">
            <div class="campo" [class.invalido]="invalido('placa')"><label for="pl">Placa</label><input id="pl" formControlName="placa" placeholder="ABC123" class="mayus"><span class="error">Formato ABC123 o ABC12D.</span></div>
            <div class="campo"><label for="cl">Clase</label><select id="cl" formControlName="clase">@for (c of clases; track c) { <option [value]="c">{{ c }}</option> }</select></div>
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
            <div class="campo" [class.invalido]="invalido('valorVehiculo')"><label for="vv">Valor del vehículo</label><input id="vv" type="number" formControlName="valorVehiculo" min="1"><span class="error">Valor mayor a cero.</span></div>
            <div class="campo" [class.invalido]="invalido('cuotaInicial')"><label for="ci">Cuota inicial</label><input id="ci" type="number" formControlName="cuotaInicial" min="0"><span class="error">Valor no válido.</span></div>
            <div class="campo" [class.invalido]="invalido('tasaInteresMensual')"><label for="ta">Tasa mensual (%)</label><input id="ta" type="number" step="0.01" formControlName="tasaInteresMensual"><span class="error">Entre 0 y 5 %.</span></div>
            <div class="campo" [class.invalido]="invalido('plazoMeses')"><label for="pz">Plazo (meses)</label><input id="pz" type="number" formControlName="plazoMeses"><span class="error">Entre 6 y 120 meses.</span></div>
            <div class="campo"><label for="fd">Fecha de desembolso</label><input id="fd" type="date" formControlName="fechaDesembolso"></div>
            <div class="campo"><label for="fp">Primera cuota</label><input id="fp" type="date" formControlName="fechaPrimeraCuota"><span class="ayuda">Vacío = un mes después del desembolso.</span></div>
          </div>
        </section>
      </div>

      <aside class="columna resumen">
        <section class="tarjeta pegajosa">
          <span class="etiqueta">Valor financiado</span>
          <strong class="valor-grande">{{ financiado() | moneda }}</strong>
          @if (plan(); as p) {
            <div class="datos">
              <div><span>Cuota mensual</span><strong class="rojo">{{ p.valorCuota | moneda }}</strong></div>
              <div><span>Total intereses</span><strong>{{ p.totalIntereses | moneda }}</strong></div>
              <div><span>Total a pagar</span><strong>{{ p.totalPagar | moneda }}</strong></div>
              <div><span>Cuotas</span><strong>{{ p.filas.length }}</strong></div>
            </div>
            <details>
              <summary>Ver plan de amortización</summary>
              <div class="tabla-caja mini">
                <table class="tabla">
                  <thead><tr><th>#</th><th>Fecha</th><th class="num">Capital</th><th class="num">Interés</th><th class="num">Saldo</th></tr></thead>
                  <tbody>
                    @for (f of p.filas; track f.numeroCuota) {
                      <tr><td>{{ f.numeroCuota }}</td><td>{{ f.fechaVencimiento | fecha }}</td><td class="num">{{ f.valorCapital | moneda }}</td>
                        <td class="num">{{ f.valorInteres | moneda }}</td><td class="num">{{ f.saldoCapitalFinal | moneda }}</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            </details>
          } @else {
            <p class="texto-suave">Completa las condiciones financieras para ver la cuota.</p>
          }
          <button type="submit" class="btn btn-primario btn-bloque" [disabled]="guardando()">
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
    .paso { width: 26px; height: 26px; border-radius: 13px; background: var(--rojo); color: #fff; font-size: 13px; display: flex; align-items: center; justify-content: center; }
    .mayus { text-transform: uppercase; }
    .pegajosa { position: sticky; top: 16px; display: flex; flex-direction: column; gap: 12px; }
    .datos { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; padding: 12px 0; border-top: 1px solid var(--borde); border-bottom: 1px solid var(--borde); }
    .datos div { display: flex; flex-direction: column; }
    .datos span { font-size: 12px; color: var(--texto-3); }
    .rojo { color: var(--rojo); font-size: 18px; }
    summary { cursor: pointer; font-weight: 700; font-size: 14px; color: var(--rojo-oscuro); }
    .mini { max-height: 300px; overflow: auto; margin-top: 10px; font-size: 12.5px; }
    p { margin: 0; font-size: 13.5px; }
  `],
})
export class NuevoCredito {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notificaciones = inject(NotificacionService);

  protected readonly clases: ClaseVehiculo[] = ['AUTOMOVIL', 'CAMIONETA', 'CAMPERO', 'MOTOCICLETA', 'CAMION', 'OTRO'];
  protected readonly guardando = signal(false);
  protected readonly clientes = toSignal(this.api.clientes(), { initialValue: [] });

  protected readonly form = inject(FormBuilder).group({
    clienteId: [null as number | null, Validators.required],
    placa: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{3}[0-9]{2}[0-9A-Za-z]$/)]],
    clase: ['AUTOMOVIL' as ClaseVehiculo],
    marca: ['', Validators.required],
    linea: ['', Validators.required],
    modelo: [2025, [Validators.required, Validators.min(1990), Validators.max(2027)]],
    color: [''],
    numeroMotor: [''],
    numeroChasis: [''],
    valorVehiculo: [80_000_000, [Validators.required, Validators.min(1)]],
    cuotaInicial: [20_000_000, [Validators.required, Validators.min(0)]],
    tasaInteresMensual: [1.5, [Validators.required, Validators.min(0), Validators.max(5)]],
    plazoMeses: [48, [Validators.required, Validators.min(6), Validators.max(120)]],
    fechaDesembolso: [hoyIso(), Validators.required],
    fechaPrimeraCuota: [''],
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
        v.fechaPrimeraCuota || this.unMesDespues(v.fechaDesembolso ?? hoyIso()))),
    ),
    { initialValue: null },
  );

  constructor() {
    // dispara la primera simulacion con los valores por defecto
    queueMicrotask(() => this.form.updateValueAndValidity({ emitEvent: true }));
  }

  private unMesDespues(fecha: string): string {
    const [a, m, d] = fecha.split('-').map(Number);
    const r = new Date(a, m, Math.min(d, 28));
    return `${r.getFullYear()}-${String(r.getMonth() + 1).padStart(2, '0')}-${String(r.getDate()).padStart(2, '0')}`;
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
    const v = this.form.getRawValue();
    this.guardando.set(true);
    this.api.crearCredito({
      clienteId: v.clienteId!, placa: v.placa!.toUpperCase(), marca: v.marca!, linea: v.linea!, modelo: Number(v.modelo), color: v.color || null,
      clase: v.clase!, numeroMotor: v.numeroMotor || null, numeroChasis: v.numeroChasis || null, valorVehiculo: Number(v.valorVehiculo),
      cuotaInicial: Number(v.cuotaInicial), tasaInteresMensual: Number(v.tasaInteresMensual), plazoMeses: Number(v.plazoMeses),
      fechaDesembolso: v.fechaDesembolso!, fechaPrimeraCuota: v.fechaPrimeraCuota || null,
    }).subscribe({
      next: (d) => {
        this.notificaciones.exito(`Crédito ${d.resumen.numeroCredito} registrado.`);
        this.router.navigate(['/admin/creditos', d.resumen.id]);
      },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.guardando.set(false); },
    });
  }
}
