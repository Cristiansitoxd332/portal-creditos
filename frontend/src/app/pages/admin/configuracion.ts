import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { CanalInfo, RequisitoInfo, TipoCanal } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';

type CanalEdicion = Omit<CanalInfo, 'id'> & { id: number | null };
type RequisitoEdicion = Omit<RequisitoInfo, 'id'> & { id: number | null };

/** Configuracion de contenido visible al cliente: canales de atencion y requisitos de levantamiento. */
@Component({
  selector: 'app-configuracion',
  imports: [FormsModule, Icono, Encabezado],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Configuración" subtitulo="Contenido que ven los clientes en el portal" />

    <div class="rejilla">
      <section class="tarjeta">
        <div class="cab"><h2>Canales de atención</h2>
          <button type="button" class="btn btn-secundario btn-pequeno" (click)="nuevoCanal()"><app-icono nombre="plus" [tamano]="16" />Agregar</button></div>

        @if (canal(); as c) {
          <form class="editor aparecer" (ngSubmit)="guardarCanal(c)">
            <div class="rejilla-form">
              <div class="campo"><label for="ct">Tipo</label>
                <select id="ct" name="tipo" [(ngModel)]="c.tipo">@for (t of tiposCanal; track t) { <option [value]="t">{{ t }}</option> }</select></div>
              <div class="campo"><label for="cn">Nombre</label><input id="cn" name="nombre" [(ngModel)]="c.nombre" required></div>
              <div class="campo"><label for="cv">Valor visible</label><input id="cv" name="valor" [(ngModel)]="c.valor" required></div>
              <div class="campo"><label for="ce">Enlace (opcional)</label><input id="ce" name="enlace" [(ngModel)]="c.enlace" placeholder="https://…, tel:…, mailto:…"></div>
              <div class="campo"><label for="ch">Horario</label><input id="ch" name="horario" [(ngModel)]="c.horario"></div>
              <div class="campo"><label for="co">Orden</label><input id="co" name="orden" type="number" [(ngModel)]="c.orden"></div>
            </div>
            <label class="check"><input type="checkbox" name="activo" [(ngModel)]="c.activo"> Visible para los clientes</label>
            <div class="acciones">
              <button type="button" class="btn btn-secundario btn-pequeno" (click)="canal.set(null)">Cancelar</button>
              <button type="submit" class="btn btn-primario btn-pequeno">Guardar</button>
            </div>
          </form>
        }

        <ul class="lista">
          @for (c of canales(); track c.id) {
            <li [class.inactivo]="!c.activo">
              <div><strong>{{ c.nombre }}</strong><span>{{ c.tipo }} · {{ c.valor }}</span></div>
              @if (!c.activo) { <span class="chip chip-neutro">Oculto</span> }
              <button type="button" class="btn btn-fantasma btn-icono" (click)="editarCanal(c)" aria-label="Editar canal"><app-icono nombre="edit" [tamano]="17" /></button>
              <button type="button" class="btn btn-fantasma btn-icono" (click)="eliminarCanal(c)" aria-label="Eliminar canal"><app-icono nombre="trash" [tamano]="17" /></button>
            </li>
          }
        </ul>
      </section>

      <section class="tarjeta">
        <div class="cab"><h2>Requisitos de levantamiento</h2>
          <button type="button" class="btn btn-secundario btn-pequeno" (click)="nuevoRequisito()"><app-icono nombre="plus" [tamano]="16" />Agregar</button></div>

        @if (requisito(); as r) {
          <form class="editor aparecer" (ngSubmit)="guardarRequisito(r)">
            <div class="campo"><label for="rt">Título</label><input id="rt" name="titulo" [(ngModel)]="r.titulo" required></div>
            <div class="campo"><label for="rd">Descripción</label><textarea id="rd" name="descripcion" [(ngModel)]="r.descripcion" required></textarea></div>
            <div class="campo"><label for="ro">Orden</label><input id="ro" name="orden" type="number" [(ngModel)]="r.orden"></div>
            <label class="check"><input type="checkbox" name="activo" [(ngModel)]="r.activo"> Visible para los clientes</label>
            <div class="acciones">
              <button type="button" class="btn btn-secundario btn-pequeno" (click)="requisito.set(null)">Cancelar</button>
              <button type="submit" class="btn btn-primario btn-pequeno">Guardar</button>
            </div>
          </form>
        }

        <ol class="lista">
          @for (r of requisitos(); track r.id) {
            <li [class.inactivo]="!r.activo">
              <div><strong>{{ r.titulo }}</strong><span>{{ r.descripcion }}</span></div>
              <button type="button" class="btn btn-fantasma btn-icono" (click)="editarRequisito(r)" aria-label="Editar requisito"><app-icono nombre="edit" [tamano]="17" /></button>
              <button type="button" class="btn btn-fantasma btn-icono" (click)="eliminarRequisito(r)" aria-label="Eliminar requisito"><app-icono nombre="trash" [tamano]="17" /></button>
            </li>
          }
        </ol>
      </section>
    </div>
  `,
  styles: [`
    .rejilla { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr)); align-items: start; }
    .cab { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    h2 { font-size: 16px; font-weight: 800; }
    .editor { display: flex; flex-direction: column; gap: 12px; background: var(--superficie-2); padding: 14px; border-radius: 14px; margin-bottom: 12px; }
    .check { display: flex; gap: 8px; align-items: center; font-size: 14px; font-weight: 600; }
    .check input { width: 18px; height: 18px; accent-color: var(--primario); }
    .acciones { display: flex; justify-content: flex-end; gap: 8px; }
    .lista { list-style: none; margin: 0; padding: 0; }
    .lista li { display: flex; align-items: center; gap: 8px; padding: 12px 0; border-bottom: 1px solid var(--borde); }
    .lista li:last-child { border-bottom: 0; }
    .lista li > div { flex: 1; min-width: 0; display: flex; flex-direction: column; }
    .lista span:not(.chip) { font-size: 12.5px; color: var(--texto-3); overflow-wrap: anywhere; }
    .inactivo { opacity: 0.55; }
  `],
})
export class Configuracion {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);

  protected readonly canales = signal<CanalInfo[]>([]);
  protected readonly requisitos = signal<RequisitoInfo[]>([]);
  protected readonly canal = signal<CanalEdicion | null>(null);
  protected readonly requisito = signal<RequisitoEdicion | null>(null);
  protected readonly tiposCanal: TipoCanal[] = ['WHATSAPP', 'TELEFONO', 'CORREO', 'SITIO_WEB', 'OFICINA', 'REDES_SOCIALES'];

  constructor() {
    this.cargarCanales();
    this.cargarRequisitos();
  }

  private cargarCanales(): void {
    this.api.canalesAdmin().subscribe({ next: (c) => this.canales.set(c), error: (e) => this.notificaciones.error(mensajeError(e)) });
  }

  private cargarRequisitos(): void {
    this.api.requisitosAdmin().subscribe({ next: (r) => this.requisitos.set(r), error: (e) => this.notificaciones.error(mensajeError(e)) });
  }

  protected nuevoCanal(): void {
    this.canal.set({ id: null, tipo: 'TELEFONO', nombre: '', valor: '', enlace: '', horario: '', orden: this.canales().length + 1, activo: true });
  }

  protected editarCanal(c: CanalInfo): void {
    this.canal.set({ ...c });
  }

  protected guardarCanal(c: CanalEdicion): void {
    const { id, ...datos } = c;
    this.api.guardarCanal(id, { ...datos, enlace: datos.enlace || null, horario: datos.horario || null, orden: Number(datos.orden) }).subscribe({
      next: () => { this.notificaciones.exito('Canal guardado.'); this.canal.set(null); this.cargarCanales(); },
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }

  protected eliminarCanal(c: CanalInfo): void {
    this.api.eliminarCanal(c.id).subscribe({
      next: () => { this.notificaciones.exito('Canal eliminado.'); this.cargarCanales(); },
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }

  protected nuevoRequisito(): void {
    this.requisito.set({ id: null, titulo: '', descripcion: '', orden: this.requisitos().length + 1, activo: true });
  }

  protected editarRequisito(r: RequisitoInfo): void {
    this.requisito.set({ ...r });
  }

  protected guardarRequisito(r: RequisitoEdicion): void {
    const { id, ...datos } = r;
    this.api.guardarRequisito(id, { ...datos, orden: Number(datos.orden) }).subscribe({
      next: () => { this.notificaciones.exito('Requisito guardado.'); this.requisito.set(null); this.cargarRequisitos(); },
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }

  protected eliminarRequisito(r: RequisitoInfo): void {
    this.api.eliminarRequisito(r.id).subscribe({
      next: () => { this.notificaciones.exito('Requisito eliminado.'); this.cargarRequisitos(); },
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }
}
