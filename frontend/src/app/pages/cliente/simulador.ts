import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { CreditoDetalle, SimulacionAbono } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { FechaPipe, MonedaPipe } from '../../shared/pipes';

/** Simulador de abono extraordinario a capital: compara reducir el plazo vs. reducir la cuota. */
@Component({
  selector: 'app-simulador',
  imports: [Icono, Encabezado, Cargando, MonedaPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Simulador de abono" subtitulo="¿Cuánto ahorras si abonas a capital?" [volver]="'/cliente/creditos/' + id()" />

    @if (detalle(); as d) {
      <div class="rejilla">
        <section class="tarjeta entrada">
          <div class="fila-dato"><span class="texto-suave">Saldo de capital actual</span><strong>{{ d.resumen.saldoCapital | moneda }}</strong></div>
          <div class="campo">
            <label for="abono">Valor del abono</label>
            <input id="abono" class="grande" inputmode="numeric" [value]="texto()" (input)="escribir($any($event.target).value)" placeholder="$ 0">
          </div>
          <div class="sugeridos">
            @for (v of sugeridos; track v) {
              <button type="button" class="sugerido" [class.activo]="valor() === v" (click)="elegir(v)">{{ v | moneda }}</button>
            }
          </div>
          <button type="button" class="btn btn-primario btn-bloque" [disabled]="!valor() || cargando()" (click)="simular()">
            <app-icono nombre="calculator" [tamano]="18" />{{ cargando() ? 'Calculando…' : 'Simular' }}
          </button>
          @if (error()) { <div class="aviso aviso-mora" role="alert">{{ error() }}</div> }
        </section>

        <section class="resultados" aria-live="polite">
          @if (resultado(); as r) {
            <span class="titulo-seccion">Elige cómo aplicarlo</span>
            <article class="tarjeta opcion recomendada aparecer">
              <header><h2>Reducir el plazo</h2>
                @if (r.reducirPlazo.ahorroIntereses >= r.reducirCuota.ahorroIntereses) { <span class="chip chip-primario">RECOMENDADO</span> }
              </header>
              <div class="cifras">
                <div><span>Ahorro en intereses</span><strong class="ahorro">{{ r.reducirPlazo.ahorroIntereses | moneda }}</strong></div>
                <div><span>Terminas antes</span><strong>{{ r.reducirPlazo.cuotasMenos }} {{ r.reducirPlazo.cuotasMenos === 1 ? 'cuota' : 'cuotas' }}</strong></div>
              </div>
              <p>Sigues pagando {{ r.reducirPlazo.valorCuota | moneda }} al mes, ahora por {{ r.reducirPlazo.cuotasRestantes }} meses en vez de {{ r.sinAbono.cuotasRestantes }}
                @if (r.reducirPlazo.fechaUltimaCuota) { (última cuota: {{ r.reducirPlazo.fechaUltimaCuota | fecha }}) }.</p>
            </article>
            <article class="tarjeta opcion aparecer">
              <header><h2>Reducir la cuota</h2></header>
              <div class="cifras">
                <div><span>Ahorro en intereses</span><strong class="ahorro">{{ r.reducirCuota.ahorroIntereses | moneda }}</strong></div>
                <div><span>Nueva cuota</span><strong>{{ r.reducirCuota.valorCuota | moneda }}</strong></div>
              </div>
              <p>Mantienes las {{ r.reducirCuota.cuotasRestantes }} cuotas restantes con un valor mensual {{ (r.sinAbono.valorCuota - r.reducirCuota.valorCuota) | moneda }} más bajo.</p>
            </article>
            <div class="comparacion tarjeta aparecer">
              <span class="titulo-seccion">Intereses por pagar</span>
              @for (b of barras(r); track b.texto) {
                <div class="barra">
                  <span>{{ b.texto }}</span>
                  <div class="pista"><div class="relleno" [style.width.%]="b.porcentaje" [class.base]="b.base"></div></div>
                  <strong>{{ b.valor | moneda }}</strong>
                </div>
              }
            </div>
            <p class="aviso"><app-icono nombre="shield" [tamano]="18" /><span>{{ r.aviso }}</span></p>
          } @else {
            <div class="tarjeta vacio">
              <app-icono nombre="calculator" [tamano]="34" />
              <p>Escribe un valor y presiona <strong>Simular</strong> para comparar las opciones.</p>
            </div>
          }
        </section>
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="2" [alto]="160" />
    }
  `,
  styles: [`
    .rejilla { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr)); align-items: start; }
    .entrada { display: flex; flex-direction: column; gap: 14px; }
    .fila-dato { display: flex; justify-content: space-between; font-size: 14px; }
    .grande { height: 58px !important; font-size: 24px !important; font-weight: 800; border: 2px solid var(--primario) !important; }
    .sugeridos { display: flex; gap: 8px; flex-wrap: wrap; }
    .sugerido { height: 38px; padding: 0 14px; border-radius: 999px; border: 1.5px solid var(--borde-fuerte); background: var(--superficie); color: var(--texto);
      font: inherit; font-size: 13px; font-weight: 700; cursor: pointer; }
    .sugerido.activo { border-color: var(--primario); background: var(--primario-suave); color: var(--primario-oscuro); }
    .resultados { display: flex; flex-direction: column; gap: 12px; }
    .opcion { display: flex; flex-direction: column; gap: 12px; border: 1.5px solid var(--borde); }
    .recomendada { border: 2px solid var(--primario); }
    .opcion header { display: flex; justify-content: space-between; align-items: center; }
    h2 { font-size: 16.5px; font-weight: 800; }
    .cifras { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .cifras div { display: flex; flex-direction: column; }
    .cifras span { font-size: 12px; color: var(--texto-3); }
    .cifras strong { font-size: 20px; font-weight: 800; }
    .ahorro { color: var(--ok); }
    .opcion p { margin: 0; font-size: 13.5px; color: var(--texto-2); }
    .comparacion { display: flex; flex-direction: column; gap: 10px; }
    .barra { display: grid; grid-template-columns: 110px 1fr auto; gap: 10px; align-items: center; font-size: 13px; }
    .pista { height: 12px; border-radius: 6px; background: var(--superficie-2); overflow: hidden; }
    .relleno { height: 100%; border-radius: 6px; background: var(--primario); transition: width 0.6s ease; }
    .relleno.base { background: var(--texto-3); }
    .vacio { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 10px; padding: 34px; color: var(--texto-3); }
    .vacio p { margin: 0; }
    .aviso { margin: 0; }
  `],
})
export class Simulador {
  private readonly api = inject(ApiService);
  readonly id = input.required<string>();

  protected readonly detalle = signal<CreditoDetalle | null>(null);
  protected readonly resultado = signal<SimulacionAbono | null>(null);
  protected readonly valor = signal<number>(5_000_000);
  protected readonly texto = signal('5.000.000');
  protected readonly cargando = signal(false);
  protected readonly error = signal('');
  protected readonly sugeridos = [1_000_000, 5_000_000, 10_000_000];

  constructor() {
    effect(() => {
      this.api.miCredito(Number(this.id())).subscribe({
        next: (d) => {
          this.detalle.set(d);
          if (d.resumen.saldoCapital < this.valor()) {
            this.elegir(Math.floor(d.resumen.saldoCapital / 2));
          }
        },
        error: (e) => this.error.set(mensajeError(e)),
      });
    });
  }

  protected escribir(t: string): void {
    const limpio = t.replace(/[^0-9]/g, '');
    const v = limpio ? Number(limpio) : 0;
    this.valor.set(v);
    this.texto.set(v ? v.toLocaleString('es-CO') : '');
  }

  protected elegir(v: number): void {
    this.valor.set(v);
    this.texto.set(v.toLocaleString('es-CO'));
  }

  protected simular(): void {
    this.cargando.set(true);
    this.error.set('');
    this.api.simularAbono(Number(this.id()), this.valor()).subscribe({
      next: (r) => { this.resultado.set(r); this.cargando.set(false); },
      error: (e) => { this.error.set(mensajeError(e)); this.cargando.set(false); },
    });
  }

  protected barras(r: SimulacionAbono): { texto: string; valor: number; porcentaje: number; base: boolean }[] {
    const max = Math.max(r.sinAbono.totalIntereses, 1);
    return [
      { texto: 'Sin abono', valor: r.sinAbono.totalIntereses, porcentaje: 100, base: true },
      { texto: 'Reducir plazo', valor: r.reducirPlazo.totalIntereses, porcentaje: (r.reducirPlazo.totalIntereses / max) * 100, base: false },
      { texto: 'Reducir cuota', valor: r.reducirCuota.totalIntereses, porcentaje: (r.reducirCuota.totalIntereses / max) * 100, base: false },
    ];
  }
}
