import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { ImportacionInfo, ResultadoImportacion, TipoCarga } from '../../core/models';
import { guardarArchivo, mensajeError } from '../../core/utilidades';
import { Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe } from '../../shared/pipes';

const TIPOS: { clave: TipoCarga; texto: string; descripcion: string }[] = [
  { clave: 'CLIENTES', texto: 'Clientes', descripcion: 'Crea clientes y su usuario de acceso.' },
  { clave: 'CREDITOS', texto: 'Créditos', descripcion: 'Crea créditos con su vehículo y plan de pagos.' },
  { clave: 'PAGOS', texto: 'Pagos', descripcion: 'Aplica pagos a créditos existentes.' },
];

/** Importacion de datos desde Excel (.xlsx) o CSV, con vista previa y validacion por fila. */
@Component({
  selector: 'app-importar',
  imports: [Icono, Encabezado, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Importar datos" subtitulo="Carga masiva desde Excel o CSV con validación previa" />

    <div class="tipos">
      @for (t of tipos; track t.clave) {
        <button type="button" class="tarjeta tipo" [class.activo]="tipo() === t.clave" (click)="elegirTipo(t.clave)" [attr.aria-pressed]="tipo() === t.clave">
          <strong>{{ t.texto }}</strong><span>{{ t.descripcion }}</span>
        </button>
      }
    </div>

    <section class="tarjeta pasos">
      <div class="paso">
        <span class="numero">1</span>
        <div><strong>Descarga la plantilla</strong><p>Respeta los encabezados de las columnas.</p></div>
        <button type="button" class="btn btn-secundario btn-pequeno" (click)="descargarPlantilla()"><app-icono nombre="download" [tamano]="16" />Plantilla {{ tipoTexto() }}</button>
      </div>
      <div class="paso">
        <span class="numero">2</span>
        <div><strong>Sube el archivo</strong><p>Formatos .xlsx o .csv (máx. 5 MB).</p></div>
      </div>
      <label class="zona" [class.arrastrando]="arrastrando()" (dragover)="$event.preventDefault(); arrastrando.set(true)" (dragleave)="arrastrando.set(false)" (drop)="soltar($event)">
        <input type="file" accept=".xlsx,.csv" (change)="elegirArchivo($any($event.target).files)" class="sr-only">
        <app-icono nombre="upload" [tamano]="30" />
        @if (archivo(); as a) { <strong>{{ a.name }}</strong><span>{{ (a.size / 1024).toFixed(1) }} KB · clic para cambiar</span> }
        @else { <strong>Arrastra el archivo aquí</strong><span>o haz clic para seleccionarlo</span> }
      </label>
      <div class="paso">
        <span class="numero">3</span>
        <div><strong>Valida y confirma</strong><p>La vista previa no guarda nada; revisa los errores antes de importar.</p></div>
        <div class="botones">
          <button type="button" class="btn btn-secundario btn-pequeno" [disabled]="!archivo() || procesando()" (click)="previsualizar()"><app-icono nombre="eye" [tamano]="16" />Vista previa</button>
          <button type="button" class="btn btn-primario btn-pequeno" [disabled]="!archivo() || procesando() || (resultado()?.filasValidas === 0)" (click)="importar()"><app-icono nombre="upload" [tamano]="16" />Importar</button>
        </div>
      </div>
    </section>

    @if (resultado(); as r) {
      <section class="tarjeta resultado aparecer">
        <div class="cab">
          <h2>{{ r.guardado ? 'Importación finalizada' : 'Vista previa' }} · {{ r.nombreArchivo }}</h2>
          <span [class]="'chip ' + (r.filasConError === 0 ? 'chip-ok' : r.filasValidas === 0 ? 'chip-mora' : 'chip-alerta')">{{ estadoTexto(r) }}</span>
        </div>
        <div class="contadores">
          <div><strong>{{ r.totalFilas }}</strong><span>Filas leídas</span></div>
          <div class="ok"><strong>{{ r.filasValidas }}</strong><span>{{ r.guardado ? 'Importadas' : 'Válidas' }}</span></div>
          <div class="mal"><strong>{{ r.filasConError }}</strong><span>Con error</span></div>
        </div>
        @if (r.errores.length) {
          <h3>Errores encontrados</h3>
          <div class="tabla-caja">
            <table class="tabla">
              <thead><tr><th>Fila</th><th>Campo</th><th>Detalle</th></tr></thead>
              <tbody>@for (e of r.errores; track $index) { <tr><td>{{ e.fila }}</td><td>{{ e.campo ?? '—' }}</td><td>{{ e.mensaje }}</td></tr> }</tbody>
            </table>
          </div>
        }
        @if (r.muestra.length) {
          <h3>Muestra de datos</h3>
          <div class="tabla-caja">
            <table class="tabla">
              <thead><tr>@for (c of columnas(); track c) { <th>{{ c }}</th> }</tr></thead>
              <tbody>@for (f of r.muestra; track $index) { <tr>@for (c of columnas(); track c) { <td>{{ f[c] }}</td> }</tr> }</tbody>
            </table>
          </div>
        }
      </section>
    }

    <section class="tarjeta">
      <h2>Historial de importaciones</h2>
      <div class="tabla-caja">
        <table class="tabla">
          <thead><tr><th>Fecha</th><th>Tipo</th><th>Archivo</th><th>Usuario</th><th class="num">Filas</th><th class="num">Exitosas</th><th class="num">Errores</th></tr></thead>
          <tbody>
            @for (h of historial(); track h.id) {
              <tr><td>{{ h.fecha | fecha: 'hora' }}</td><td>{{ h.tipo }}</td><td>{{ h.nombreArchivo }}</td><td>{{ h.usuario }}</td>
                <td class="num">{{ h.totalFilas }}</td><td class="num">{{ h.filasExitosas }}</td><td class="num">{{ h.filasConError }}</td></tr>
            } @empty {
              <tr><td colspan="7" class="vacio">Aún no hay importaciones.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: [`
    .tipos { display: grid; gap: 12px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr)); margin-bottom: 16px; }
    .tipo { text-align: left; font: inherit; color: inherit; cursor: pointer; display: flex; flex-direction: column; gap: 4px; border: 2px solid var(--borde); }
    .tipo span { font-size: 13px; color: var(--texto-3); }
    .tipo.activo { border-color: var(--primario); background: var(--primario-suave); }
    .pasos { display: flex; flex-direction: column; gap: 16px; margin-bottom: 16px; }
    .paso { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
    .paso > div:first-of-type { flex: 1; min-width: 200px; }
    .paso p { margin: 0; font-size: 13px; color: var(--texto-3); }
    .numero { width: 30px; height: 30px; border-radius: 15px; background: var(--primario); color: #fff; font-weight: 800; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .botones { display: flex; gap: 8px; }
    .zona { border: 2px dashed var(--borde-fuerte); border-radius: 16px; padding: 28px; display: flex; flex-direction: column; align-items: center; gap: 6px;
      cursor: pointer; color: var(--texto-3); text-align: center; transition: border-color 0.2s, background 0.2s; }
    .zona:hover, .zona.arrastrando { border-color: var(--primario); background: var(--primario-suave); color: var(--primario-oscuro); }
    .zona strong { color: var(--texto); }
    .zona span { font-size: 13px; }
    .resultado { margin-bottom: 16px; display: flex; flex-direction: column; gap: 14px; }
    .cab { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
    h2 { font-size: 16px; font-weight: 800; margin-bottom: 12px; }
    .cab h2 { margin: 0; }
    h3 { font-size: 14px; font-weight: 800; margin: 0; }
    .contadores { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
    .contadores div { background: var(--superficie-2); border-radius: 12px; padding: 12px; display: flex; flex-direction: column; }
    .contadores strong { font-size: 24px; font-weight: 800; }
    .contadores span { font-size: 12px; color: var(--texto-3); }
    .contadores .ok strong { color: var(--ok); }
    .contadores .mal strong { color: var(--mora); }
    .vacio { text-align: center; color: var(--texto-3); padding: 20px; }
  `],
})
export class Importar {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);

  protected readonly tipos = TIPOS;
  protected readonly tipo = signal<TipoCarga>('CLIENTES');
  protected readonly archivo = signal<File | null>(null);
  protected readonly resultado = signal<ResultadoImportacion | null>(null);
  protected readonly historial = signal<ImportacionInfo[]>([]);
  protected readonly procesando = signal(false);
  protected readonly arrastrando = signal(false);
  protected readonly tipoTexto = computed(() => TIPOS.find((t) => t.clave === this.tipo())!.texto.toLowerCase());
  protected readonly columnas = computed(() => Object.keys(this.resultado()?.muestra[0] ?? {}));

  constructor() {
    this.cargarHistorial();
  }

  private cargarHistorial(): void {
    this.api.importaciones().subscribe({ next: (h) => this.historial.set(h), error: () => this.historial.set([]) });
  }

  protected elegirTipo(t: TipoCarga): void {
    this.tipo.set(t);
    this.resultado.set(null);
  }

  protected elegirArchivo(lista: FileList | null): void {
    const a = lista?.item(0) ?? null;
    if (a && !/\.(xlsx|csv)$/i.test(a.name)) {
      this.notificaciones.error('Solo se permiten archivos .xlsx o .csv.');
      return;
    }
    if (a && a.size > 5 * 1024 * 1024) {
      this.notificaciones.error('El archivo supera 5 MB.');
      return;
    }
    this.archivo.set(a);
    this.resultado.set(null);
  }

  protected soltar(e: DragEvent): void {
    e.preventDefault();
    this.arrastrando.set(false);
    this.elegirArchivo(e.dataTransfer?.files ?? null);
  }

  protected descargarPlantilla(): void {
    this.api.plantilla(this.tipo()).subscribe({
      next: (b) => guardarArchivo(b, `plantilla-${this.tipo().toLowerCase()}.xlsx`),
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }

  protected previsualizar(): void {
    this.ejecutar(false);
  }

  protected importar(): void {
    this.ejecutar(true);
  }

  private ejecutar(guardar: boolean): void {
    const a = this.archivo();
    if (!a) return;
    this.procesando.set(true);
    const peticion = guardar ? this.api.importar(this.tipo(), a) : this.api.vistaPrevia(this.tipo(), a);
    peticion.subscribe({
      next: (r) => {
        this.resultado.set(r);
        this.procesando.set(false);
        if (guardar) {
          this.notificaciones.exito(`${r.filasValidas} filas importadas.`);
          this.archivo.set(null);
          this.cargarHistorial();
        }
      },
      error: (e) => { this.notificaciones.error(mensajeError(e)); this.procesando.set(false); },
    });
  }

  protected estadoTexto(r: ResultadoImportacion): string {
    if (r.filasConError === 0) return 'Sin errores';
    if (r.filasValidas === 0) return 'Todas con error';
    return 'Con errores parciales';
  }
}
