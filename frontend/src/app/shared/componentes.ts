import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EstadoCuota, Semaforo } from '../core/models';
import { CLASE_CUOTA, CLASE_SEMAFORO, ICONO_CUOTA, ICONO_SEMAFORO, TEXTO_CUOTA, TEXTO_SEMAFORO } from '../core/utilidades';
import { Icono } from './icono';
import { NumeroPipe } from './pipes';

/** Anillo de progreso: porcentaje del capital pagado. */
@Component({
  selector: 'app-anillo',
  imports: [NumeroPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="anillo" [style.width.px]="tamano()" [style.height.px]="tamano()">
      <svg [attr.viewBox]="'0 0 120 120'" role="img" [attr.aria-label]="(porcentaje() | numero) + ' % del capital pagado'">
        <circle cx="60" cy="60" r="52" fill="none" stroke="var(--primario-suave)" stroke-width="12" />
        <circle cx="60" cy="60" r="52" fill="none" stroke="var(--primario)" stroke-width="12" stroke-linecap="round"
                [attr.stroke-dasharray]="trazo()" transform="rotate(-90 60 60)" class="progreso" />
      </svg>
      <div class="centro">
        <strong>{{ porcentaje() | numero }} %</strong>
        <small>pagado</small>
      </div>
    </div>`,
  styles: [`
    .anillo { position: relative; flex-shrink: 0; }
    svg { width: 100%; height: 100%; }
    .progreso { transition: stroke-dasharray 0.8s ease; }
    .centro { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; line-height: 1.1; }
    strong { font-size: 22px; font-weight: 800; }
    small { font-size: 11px; color: var(--texto-3); }
  `],
})
export class Anillo {
  readonly porcentaje = input.required<number>();
  readonly tamano = input(120);
  private readonly circunferencia = 2 * Math.PI * 52;
  protected readonly trazo = computed(() => {
    const p = Math.max(0, Math.min(100, this.porcentaje()));
    return `${(this.circunferencia * p) / 100} ${this.circunferencia}`;
  });
}

/** Etiqueta del semaforo del credito: siempre icono + texto, nunca solo color. */
@Component({
  selector: 'app-chip-semaforo',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="'chip ' + clase()"><app-icono [nombre]="icono()" [tamano]="13" [grosor]="2.6" />{{ texto() }}</span>`,
})
export class ChipSemaforo {
  readonly estado = input.required<Semaforo>();
  readonly detalle = input<string>('');
  protected readonly clase = computed(() => CLASE_SEMAFORO[this.estado()]);
  protected readonly icono = computed(() => ICONO_SEMAFORO[this.estado()]);
  protected readonly texto = computed(() => TEXTO_SEMAFORO[this.estado()] + (this.detalle() ? ' · ' + this.detalle() : ''));
}

/** Etiqueta del estado de una cuota. */
@Component({
  selector: 'app-chip-cuota',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="'chip ' + clase()"><app-icono [nombre]="icono()" [tamano]="12" [grosor]="2.8" />{{ texto() }}</span>`,
})
export class ChipCuota {
  readonly estado = input.required<EstadoCuota>();
  protected readonly clase = computed(() => CLASE_CUOTA[this.estado()]);
  protected readonly icono = computed(() => ICONO_CUOTA[this.estado()]);
  protected readonly texto = computed(() => TEXTO_CUOTA[this.estado()]);
}

/** Encabezado de pagina con boton "volver". */
@Component({
  selector: 'app-encabezado',
  imports: [RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="encabezado">
      @if (volver()) {
        <a [routerLink]="volver()" class="btn btn-icono btn-secundario" aria-label="Volver"><app-icono nombre="chevron-left" /></a>
      }
      <div class="titulos">
        <h1>{{ titulo() }}</h1>
        @if (subtitulo()) { <p>{{ subtitulo() }}</p> }
      </div>
      <div class="extra"><ng-content /></div>
    </header>`,
  styles: [`
    .encabezado { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
    .titulos { min-width: 0; flex: 1; }
    h1 { font-size: 21px; font-weight: 800; }
    p { margin: 2px 0 0; font-size: 13px; color: var(--texto-3); }
    .extra { display: flex; gap: 8px; }
  `],
})
export class Encabezado {
  readonly titulo = input.required<string>();
  readonly subtitulo = input<string>('');
  readonly volver = input<string | null>(null);
}

/** Bloque de carga (skeleton) mientras llegan los datos. */
@Component({
  selector: 'app-cargando',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="cargando" aria-busy="true" aria-label="Cargando">
      @for (i of filas(); track i) { <div class="esqueleto" [style.height.px]="alto()"></div> }
    </div>`,
  styles: ['.cargando { display: flex; flex-direction: column; gap: 12px; }'],
})
export class Cargando {
  readonly cantidad = input(3);
  readonly alto = input(90);
  protected readonly filas = computed(() => Array.from({ length: this.cantidad() }, (_, i) => i));
}
