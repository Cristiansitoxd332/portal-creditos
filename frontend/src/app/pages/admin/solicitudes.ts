import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { EstadoSolicitud, SolicitudInfo } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe } from '../../shared/pipes';

const TEXTO_ESTADO: Record<EstadoSolicitud, string> = {
  RADICADA: 'Radicada', EN_REVISION: 'En revisión', APROBADA: 'Aprobada', RECHAZADA: 'Rechazada',
};
const CLASE_ESTADO: Record<EstadoSolicitud, string> = {
  RADICADA: 'chip-rojo', EN_REVISION: 'chip-alerta', APROBADA: 'chip-ok', RECHAZADA: 'chip-mora',
};

/** Bandeja de solicitudes de levantamiento de prenda. */
@Component({
  selector: 'app-solicitudes',
  imports: [FormsModule, RouterLink, Icono, Encabezado, Cargando, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Solicitudes" subtitulo="Levantamiento de prenda radicado por los clientes" />

    <div class="pestanas">
      @for (f of filtros; track f.texto) {
        <button type="button" [class.activa]="filtro() === f.clave" (click)="filtrar(f.clave)">{{ f.texto }}</button>
      }
    </div>

    @if (solicitudes(); as lista) {
      <div class="lista">
        @for (s of lista; track s.id) {
          <article class="tarjeta solicitud aparecer">
            <header>
              <div><strong>{{ s.numeroRadicado }}</strong><span class="texto-suave">{{ s.fechaSolicitud | fecha: 'hora' }}</span></div>
              <span [class]="'chip ' + claseEstado[s.estado]">{{ textoEstado[s.estado] }}</span>
            </header>
            <dl>
              <div><dt>Cliente</dt><dd>{{ s.cliente }} · {{ s.documentoCliente }}</dd></div>
              <div><dt>Crédito / placa</dt><dd><a [routerLink]="['/admin/creditos', s.creditoId]">{{ s.numeroCredito }}</a> · {{ s.placa }}</dd></div>
            </dl>
            @if (s.observacionCliente) { <p class="cita">“{{ s.observacionCliente }}”</p> }
            @if (s.respuestaAdmin) { <p class="respuesta"><app-icono nombre="chat" [tamano]="16" />{{ s.respuestaAdmin }}</p> }

            @if (s.estado === 'RADICADA' || s.estado === 'EN_REVISION') {
              @if (abierta() === s.id) {
                <form class="responder" (ngSubmit)="responder(s)">
                  <div class="campo"><label [for]="'es' + s.id">Nuevo estado</label>
                    <select [id]="'es' + s.id" name="estado" [(ngModel)]="nuevoEstado">
                      @if (s.estado === 'RADICADA') { <option value="EN_REVISION">En revisión</option> }
                      <option value="APROBADA">Aprobada (prenda levantada)</option>
                      <option value="RECHAZADA">Rechazada</option>
                    </select></div>
                  <div class="campo"><label [for]="'re' + s.id">Respuesta al cliente</label>
                    <textarea [id]="'re' + s.id" name="respuesta" [(ngModel)]="respuesta" maxlength="500" required></textarea></div>
                  <div class="acciones">
                    <button type="button" class="btn btn-secundario btn-pequeno" (click)="abierta.set(null)">Cancelar</button>
                    <button type="submit" class="btn btn-primario btn-pequeno" [disabled]="!respuesta.trim() || enviando()">Enviar</button>
                  </div>
                </form>
              } @else {
                <button type="button" class="btn btn-secundario btn-pequeno" (click)="abrir(s)"><app-icono nombre="edit" [tamano]="16" />Gestionar</button>
              }
            }
          </article>
        } @empty {
          <div class="tarjeta vacio"><app-icono nombre="doc-check" [tamano]="32" /><p>No hay solicitudes en este estado.</p></div>
        }
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="3" [alto]="150" />
    }
  `,
  styles: [`
    .pestanas { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 16px; }
    .pestanas button { height: 40px; padding: 0 16px; border-radius: 999px; border: 1.5px solid var(--borde-fuerte); background: var(--superficie);
      font: inherit; font-size: 13px; font-weight: 700; color: var(--texto-2); cursor: pointer; }
    .pestanas .activa { background: var(--texto); color: var(--superficie); border-color: var(--texto); }
    .lista { display: grid; gap: 14px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 380px), 1fr)); align-items: start; }
    .solicitud { display: flex; flex-direction: column; gap: 12px; }
    header { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
    header div { display: flex; flex-direction: column; }
    header span.texto-suave { font-size: 12.5px; }
    dl { margin: 0; display: grid; gap: 8px; }
    dt { font-size: 12px; color: var(--texto-3); }
    dd { margin: 0; font-weight: 600; font-size: 14px; }
    .cita { margin: 0; font-size: 13.5px; font-style: italic; color: var(--texto-2); border-left: 3px solid var(--borde-fuerte); padding-left: 10px; }
    .respuesta { margin: 0; font-size: 13px; display: flex; gap: 8px; color: var(--texto-2); background: var(--superficie-2); padding: 10px; border-radius: 10px; }
    .responder { display: flex; flex-direction: column; gap: 10px; border-top: 1px solid var(--borde); padding-top: 12px; }
    .acciones { display: flex; justify-content: flex-end; gap: 8px; }
    .vacio { display: flex; flex-direction: column; align-items: center; gap: 8px; color: var(--texto-3); padding: 36px; grid-column: 1 / -1; }
    .vacio p { margin: 0; }
  `],
})
export class Solicitudes {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);

  protected readonly solicitudes = signal<SolicitudInfo[] | null>(null);
  protected readonly error = signal('');
  protected readonly filtro = signal<EstadoSolicitud | undefined>(undefined);
  protected readonly abierta = signal<number | null>(null);
  protected readonly enviando = signal(false);
  protected readonly textoEstado = TEXTO_ESTADO;
  protected readonly claseEstado = CLASE_ESTADO;
  protected readonly filtros: { clave: EstadoSolicitud | undefined; texto: string }[] = [
    { clave: undefined, texto: 'Todas' },
    ...(Object.keys(TEXTO_ESTADO) as EstadoSolicitud[]).map((k) => ({ clave: k, texto: TEXTO_ESTADO[k] })),
  ];
  protected nuevoEstado: EstadoSolicitud = 'EN_REVISION';
  protected respuesta = '';

  constructor() {
    this.cargar();
  }

  private cargar(): void {
    this.api.solicitudes(this.filtro()).subscribe({
      next: (s) => this.solicitudes.set(s),
      error: (e) => this.error.set(mensajeError(e)),
    });
  }

  protected filtrar(clave: EstadoSolicitud | undefined): void {
    this.filtro.set(clave);
    this.solicitudes.set(null);
    this.cargar();
  }

  protected abrir(s: SolicitudInfo): void {
    this.abierta.set(s.id);
    this.nuevoEstado = s.estado === 'RADICADA' ? 'EN_REVISION' : 'APROBADA';
    this.respuesta = s.estado === 'RADICADA'
      ? 'Su solicitud está en revisión. Le informaremos cuando el trámite ante tránsito finalice.'
      : 'El levantamiento de prenda fue aprobado. Puede reclamar los documentos en la oficina.';
  }

  protected responder(s: SolicitudInfo): void {
    this.enviando.set(true);
    this.api.responderSolicitud(s.id, this.nuevoEstado, this.respuesta.trim()).subscribe({
      next: () => {
        this.notificaciones.exito(`Solicitud ${s.numeroRadicado} actualizada.`);
        this.enviando.set(false);
        this.abierta.set(null);
        this.cargar();
      },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.enviando.set(false); },
    });
  }
}
