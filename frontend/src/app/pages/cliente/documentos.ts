import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { EstadoLevantamiento, PazYSalvoEstado, RequisitoInfo } from '../../core/models';
import { guardarArchivo, mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe } from '../../shared/pipes';

/** Paz y salvo (descarga con QR) y solicitud/seguimiento del levantamiento de prenda. */
@Component({
  selector: 'app-documentos',
  imports: [FormsModule, Icono, Encabezado, Cargando, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Paz y salvo y levantamiento" [subtitulo]="lev() ? 'Crédito ' + lev()!.numeroCredito : ''" [volver]="'/cliente/creditos/' + id()" />

    @if (paz() && lev()) {
      <div class="rejilla">
        <div class="columna">
          @if (paz()!.disponible) {
            <section class="tarjeta listo aparecer">
              <div class="titulo"><span class="sello"><app-icono nombre="check" [tamano]="24" [grosor]="3" /></span>
                <div><h2>¡Crédito cancelado!</h2><p>Pagado en su totalidad.</p></div></div>
              <button type="button" class="btn btn-bloque blanco" (click)="descargar()" [disabled]="descargando()">
                <app-icono nombre="download" [tamano]="18" />{{ descargando() ? 'Generando…' : 'Descargar paz y salvo (PDF)' }}
              </button>
              @if (paz()!.codigoVerificacion) { <small>Código de verificación: <strong>{{ paz()!.codigoVerificacion }}</strong></small> }
            </section>
          } @else {
            <section class="tarjeta pendiente aparecer">
              <app-icono nombre="lock" [tamano]="26" />
              <h2>Paz y salvo aún no disponible</h2>
              <p>{{ paz()!.mensaje }}</p>
            </section>
          }

          <section class="tarjeta">
            <h2>Requisitos para el levantamiento</h2>
            <ol class="requisitos">
              @for (r of requisitos(); track r.id; let i = $index) {
                <li><span class="num">{{ i + 1 }}</span><div><strong>{{ r.titulo }}</strong><p>{{ r.descripcion }}</p></div></li>
              }
            </ol>
          </section>
        </div>

        <div class="columna">
          <section class="tarjeta">
            <div class="cab"><h2>Levantamiento de prenda</h2>
              <span class="chip" [class]="lev()!.estadoPrenda === 'LEVANTADA' ? 'chip chip-ok' : lev()!.estadoPrenda === 'EN_TRAMITE' ? 'chip chip-alerta' : 'chip chip-neutro'">
                {{ lev()!.estadoPrenda === 'LEVANTADA' ? 'Prenda levantada' : lev()!.estadoPrenda === 'EN_TRAMITE' ? 'En trámite' : 'Prenda vigente' }}
              </span>
            </div>

            @for (s of lev()!.solicitudes; track s.id) {
              <div class="solicitud">
                <div class="radicado"><span>Radicado</span><strong>{{ s.numeroRadicado }}</strong></div>
                <ol class="linea" aria-label="Seguimiento de la solicitud">
                  @for (p of s.seguimiento; track p.nombre; let ultimo = $last) {
                    <li [class.hecho]="p.completado" [class.actual]="!p.completado && (($index === 0) || s.seguimiento[$index - 1].completado)">
                      <span class="punto">@if (p.completado) { <app-icono nombre="check" [tamano]="14" [grosor]="3" /> }</span>
                      <div><strong>{{ p.nombre }}</strong>
                        <small>{{ p.fecha ? (p.fecha | fecha: 'hora') : (p.completado ? 'Completado' : 'Pendiente') }}</small></div>
                    </li>
                  }
                </ol>
                @if (s.respuestaAdmin) { <div class="aviso"><app-icono nombre="chat" [tamano]="18" /><span>{{ s.respuestaAdmin }}</span></div> }
              </div>
            } @empty {
              <p class="texto-suave">No has radicado solicitudes de levantamiento.</p>
            }

            @if (lev()!.puedeSolicitar) {
              <form class="formulario" (ngSubmit)="solicitar()">
                <div class="campo">
                  <label for="obs">Observación (opcional)</label>
                  <textarea id="obs" name="obs" [(ngModel)]="observacion" maxlength="500" placeholder="Ej. Solicito el levantamiento de la prenda de mi vehículo."></textarea>
                </div>
                <button class="btn btn-primario btn-bloque" type="submit" [disabled]="enviando()">
                  <app-icono nombre="doc-check" [tamano]="18" />{{ enviando() ? 'Radicando…' : 'Radicar solicitud' }}
                </button>
              </form>
            } @else {
              <div class="aviso"><app-icono nombre="alert" [tamano]="18" /><span>{{ lev()!.mensaje }}</span></div>
            }
          </section>
          <p class="aviso">{{ lev()!.aviso }}</p>
        </div>
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="2" [alto]="180" />
    }
  `,
  styles: [`
    .rejilla { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr)); align-items: start; }
    .columna { display: flex; flex-direction: column; gap: 16px; }
    h2 { font-size: 16.5px; font-weight: 800; }
    p { margin: 4px 0 0; font-size: 13.5px; }
    .listo { background: var(--ok-solido); color: #fff; display: flex; flex-direction: column; gap: 14px; }
    .titulo { display: flex; gap: 12px; align-items: center; }
    .sello { width: 46px; height: 46px; border-radius: 14px; background: rgba(255,255,255,0.2); display: flex; align-items: center; justify-content: center; }
    .blanco { background: #fff; color: var(--ok-solido); }
    .listo small { font-size: 12.5px; opacity: 0.95; }
    .pendiente { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 8px; color: var(--texto-3); }
    .pendiente h2 { color: var(--texto); }
    .requisitos { list-style: none; margin: 12px 0 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
    .requisitos li { display: flex; gap: 12px; }
    .requisitos p { color: var(--texto-3); font-size: 12.5px; margin: 2px 0 0; }
    .num { width: 26px; height: 26px; border-radius: 13px; background: var(--rojo-suave); color: var(--rojo-oscuro); font-weight: 800; font-size: 13px;
      display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .cab { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 12px; }
    .solicitud { display: flex; flex-direction: column; gap: 12px; padding-bottom: 14px; margin-bottom: 14px; border-bottom: 1px solid var(--borde); }
    .radicado { display: flex; justify-content: space-between; font-size: 13px; color: var(--texto-3); }
    .radicado strong { color: var(--texto); }
    .linea { list-style: none; margin: 0; padding: 0; }
    .linea li { display: flex; gap: 12px; position: relative; padding-bottom: 18px; }
    .linea li:last-child { padding-bottom: 0; }
    .linea li:not(:last-child)::before { content: ''; position: absolute; left: 13px; top: 28px; bottom: 0; width: 3px; background: var(--borde); }
    .linea li.hecho:not(:last-child)::before { background: var(--rojo); }
    .punto { width: 29px; height: 29px; border-radius: 15px; background: var(--superficie-2); border: 2px solid var(--borde-fuerte); flex-shrink: 0;
      display: flex; align-items: center; justify-content: center; color: #fff; }
    .hecho .punto { background: var(--rojo); border-color: var(--rojo); }
    .actual .punto { border: 3px solid var(--rojo); background: var(--superficie); }
    .linea div { display: flex; flex-direction: column; padding-top: 3px; }
    .linea small { font-size: 12px; color: var(--texto-3); }
    .actual small { color: var(--rojo-oscuro); font-weight: 700; }
    .formulario { display: flex; flex-direction: column; gap: 12px; }
  `],
})
export class Documentos {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);
  readonly id = input.required<string>();

  protected readonly paz = signal<PazYSalvoEstado | null>(null);
  protected readonly lev = signal<EstadoLevantamiento | null>(null);
  protected readonly requisitos = signal<RequisitoInfo[]>([]);
  protected readonly error = signal('');
  protected readonly descargando = signal(false);
  protected readonly enviando = signal(false);
  protected observacion = '';

  constructor() {
    effect(() => this.cargar(Number(this.id())));
  }

  private cargar(id: number): void {
    forkJoin({ paz: this.api.estadoPazYSalvo(id), lev: this.api.estadoLevantamiento(id), req: this.api.requisitos() }).subscribe({
      next: (r) => { this.paz.set(r.paz); this.lev.set(r.lev); this.requisitos.set(r.req); },
      error: (e) => this.error.set(mensajeError(e)),
    });
  }

  protected descargar(): void {
    this.descargando.set(true);
    this.api.pazYSalvoPdf(Number(this.id())).subscribe({
      next: (b) => {
        guardarArchivo(b, `paz-y-salvo-${this.lev()?.numeroCredito ?? this.id()}.pdf`);
        this.descargando.set(false);
        this.cargar(Number(this.id()));
      },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.descargando.set(false); },
    });
  }

  protected solicitar(): void {
    this.enviando.set(true);
    this.api.solicitarLevantamiento(Number(this.id()), this.observacion).subscribe({
      next: (s) => {
        this.notificaciones.exito(`Solicitud ${s.numeroRadicado} radicada.`);
        this.enviando.set(false);
        this.observacion = '';
        this.cargar(Number(this.id()));
      },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.enviando.set(false); },
    });
  }
}
