import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ClienteInfo, ClienteRequest, CredencialesTemporales, TipoDocumento } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Cargando, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe } from '../../shared/pipes';

/** Gestion de clientes: busqueda, creacion, edicion, bloqueo y restablecimiento de clave. */
@Component({
  selector: 'app-clientes',
  imports: [ReactiveFormsModule, RouterLink, Icono, Encabezado, Cargando, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-encabezado titulo="Clientes" subtitulo="Registro y acceso de los clientes al portal">
      <button type="button" class="btn btn-primario" (click)="nuevo()"><app-icono nombre="plus" [tamano]="18" />Nuevo cliente</button>
    </app-encabezado>

    <div class="buscador">
      <app-icono nombre="search" [tamano]="18" />
      <input type="search" placeholder="Buscar por nombre o documento" aria-label="Buscar clientes" (input)="buscar$.next($any($event.target).value)">
    </div>

    @if (credenciales(); as c) {
      <div class="aviso aviso-ok credenciales aparecer" role="status">
        <app-icono nombre="key" [tamano]="20" />
        <div><strong>{{ c.mensaje }}</strong><br>Usuario: <code>{{ c.usuario }}</code> · Clave temporal: <code>{{ c.passwordTemporal }}</code>
          <br><small>Entréguela al cliente; deberá cambiarla en su primer ingreso.</small></div>
        <button type="button" class="btn btn-fantasma btn-icono" (click)="credenciales.set(null)" aria-label="Cerrar"><app-icono nombre="x" [tamano]="18" /></button>
      </div>
    }

    @if (formAbierto()) {
      <section class="tarjeta formulario aparecer">
        <h2>{{ editando() ? 'Editar cliente' : 'Nuevo cliente' }}</h2>
        <form [formGroup]="form" (ngSubmit)="guardar()" class="rejilla-form">
          <div class="campo"><label for="td">Tipo de documento</label>
            <select id="td" formControlName="tipoDocumento">@for (t of tipos; track t) { <option [value]="t">{{ t }}</option> }</select></div>
          <div class="campo" [class.invalido]="invalido('numeroDocumento')"><label for="nd">Número de documento</label>
            <input id="nd" formControlName="numeroDocumento" inputmode="numeric"><span class="error">Entre 5 y 20 caracteres.</span></div>
          <div class="campo" [class.invalido]="invalido('nombres')"><label for="no">Nombres</label><input id="no" formControlName="nombres"><span class="error">Obligatorio.</span></div>
          <div class="campo" [class.invalido]="invalido('apellidos')"><label for="ap">Apellidos</label><input id="ap" formControlName="apellidos"><span class="error">Obligatorio.</span></div>
          <div class="campo" [class.invalido]="invalido('correo')"><label for="co">Correo</label><input id="co" type="email" formControlName="correo"><span class="error">Correo no válido.</span></div>
          <div class="campo"><label for="te">Teléfono</label><input id="te" formControlName="telefono" inputmode="tel"></div>
          <div class="campo"><label for="di">Dirección</label><input id="di" formControlName="direccion"></div>
          <div class="campo"><label for="ci">Ciudad</label><input id="ci" formControlName="ciudad"></div>
          <div class="acciones">
            <button type="button" class="btn btn-secundario" (click)="cerrar()">Cancelar</button>
            <button type="submit" class="btn btn-primario" [disabled]="guardando()">{{ guardando() ? 'Guardando…' : 'Guardar' }}</button>
          </div>
        </form>
      </section>
    }

    @if (clientes(); as lista) {
      <div class="tabla-caja tarjeta sin-relleno">
        <table class="tabla">
          <thead><tr><th>Cliente</th><th>Contacto</th><th class="num">Créditos</th><th>Acceso</th><th>Último acceso</th><th class="num">Acciones</th></tr></thead>
          <tbody>
            @for (c of lista; track c.id) {
              <tr>
                <td><strong>{{ c.nombres }} {{ c.apellidos }}</strong><br><small class="texto-suave">{{ c.tipoDocumento }} {{ c.numeroDocumento }}</small></td>
                <td><small>{{ c.correo ?? '—' }}<br>{{ c.telefono ?? '' }}</small></td>
                <td class="num"><a [routerLink]="['/admin/creditos']" [queryParams]="{ buscar: c.numeroDocumento }">{{ c.creditos }}</a></td>
                <td>
                  @if (c.usuarioBloqueado) { <span class="chip chip-mora">Bloqueado</span> }
                  @else if (!c.usuarioActivo) { <span class="chip chip-neutro">Inactivo</span> }
                  @else if (c.debeCambiarPassword) { <span class="chip chip-alerta">Clave temporal</span> }
                  @else { <span class="chip chip-ok">Activo</span> }
                </td>
                <td><small>{{ c.ultimoAcceso ? (c.ultimoAcceso | fecha: 'hora') : 'Nunca' }}</small></td>
                <td class="num acciones-fila">
                  <button type="button" class="btn btn-fantasma btn-icono" title="Editar" aria-label="Editar" (click)="editar(c)"><app-icono nombre="edit" [tamano]="17" /></button>
                  @if (c.usuarioBloqueado) {
                    <button type="button" class="btn btn-fantasma btn-icono" title="Desbloquear" aria-label="Desbloquear" (click)="desbloquear(c)"><app-icono nombre="lock" [tamano]="17" /></button>
                  }
                  <button type="button" class="btn btn-fantasma btn-icono" title="Restablecer clave" aria-label="Restablecer clave" (click)="restablecer(c)"><app-icono nombre="key" [tamano]="17" /></button>
                  <button type="button" class="btn btn-fantasma btn-icono" [title]="c.usuarioActivo ? 'Desactivar acceso' : 'Activar acceso'"
                          [attr.aria-label]="c.usuarioActivo ? 'Desactivar acceso' : 'Activar acceso'" (click)="activar(c)">
                    <app-icono [nombre]="c.usuarioActivo ? 'x' : 'check'" [tamano]="17" /></button>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="6" class="vacio">No se encontraron clientes.</td></tr>
            }
          </tbody>
        </table>
      </div>
    } @else if (error()) {
      <div class="aviso aviso-mora" role="alert">{{ error() }}</div>
    } @else {
      <app-cargando [cantidad]="4" [alto]="56" />
    }
  `,
  styles: [`
    .buscador { display: flex; align-items: center; gap: 10px; background: var(--superficie); border: 1.5px solid var(--borde-fuerte); border-radius: 14px;
      padding: 0 14px; height: 48px; margin-bottom: 16px; color: var(--texto-3); }
    .buscador:focus-within { border-color: var(--primario); }
    .buscador input { flex: 1; border: 0; background: transparent; font: inherit; color: var(--texto); outline: none; height: 100%; }
    .credenciales { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 16px; }
    .credenciales div { flex: 1; }
    code { background: rgba(0,0,0,0.06); padding: 2px 6px; border-radius: 6px; font-weight: 700; }
    .formulario { margin-bottom: 16px; }
    h2 { font-size: 16px; font-weight: 800; margin-bottom: 14px; }
    .acciones { grid-column: 1 / -1; display: flex; justify-content: flex-end; gap: 10px; }
    .sin-relleno { padding: 0; }
    .acciones-fila { white-space: nowrap; }
    .vacio { text-align: center; color: var(--texto-3); padding: 24px; }
  `],
})
export class Clientes {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly fb = inject(FormBuilder);

  protected readonly clientes = signal<ClienteInfo[] | null>(null);
  protected readonly error = signal('');
  protected readonly formAbierto = signal(false);
  protected readonly editando = signal<ClienteInfo | null>(null);
  protected readonly guardando = signal(false);
  protected readonly credenciales = signal<CredencialesTemporales | null>(null);
  protected readonly buscar$ = new Subject<string>();
  protected readonly tipos: TipoDocumento[] = ['CC', 'CE', 'NIT', 'PAS'];
  private termino = '';

  protected readonly form = this.fb.nonNullable.group({
    tipoDocumento: ['CC' as TipoDocumento, Validators.required],
    numeroDocumento: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(20)]],
    nombres: ['', Validators.required],
    apellidos: ['', Validators.required],
    correo: ['', Validators.email],
    telefono: [''],
    direccion: [''],
    ciudad: ['Neiva'],
  });

  constructor() {
    this.buscar$.pipe(debounceTime(300), distinctUntilChanged(), switchMap((t) => { this.termino = t; return this.api.clientes(t); }))
      .subscribe({ next: (c) => this.clientes.set(c), error: (e) => this.error.set(mensajeError(e)) });
    this.recargar();
  }

  private recargar(): void {
    this.api.clientes(this.termino).subscribe({ next: (c) => this.clientes.set(c), error: (e) => this.error.set(mensajeError(e)) });
  }

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  protected nuevo(): void {
    this.editando.set(null);
    this.form.reset();
    this.formAbierto.set(true);
  }

  protected editar(c: ClienteInfo): void {
    this.editando.set(c);
    this.form.reset({
      tipoDocumento: c.tipoDocumento, numeroDocumento: c.numeroDocumento, nombres: c.nombres, apellidos: c.apellidos,
      correo: c.correo ?? '', telefono: c.telefono ?? '', direccion: c.direccion ?? '', ciudad: c.ciudad ?? '',
    });
    this.formAbierto.set(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected cerrar(): void {
    this.formAbierto.set(false);
  }

  protected guardar(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const req: ClienteRequest = { ...v, correo: v.correo || null, telefono: v.telefono || null, direccion: v.direccion || null, ciudad: v.ciudad || null };
    this.guardando.set(true);
    const actual = this.editando();
    if (actual) {
      this.api.actualizarCliente(actual.id, req).subscribe({
        next: () => { this.notificaciones.exito('Cliente actualizado.'); this.fin(); },
        error: (e) => { this.notificaciones.error(mensajeError(e)); this.guardando.set(false); },
      });
    } else {
      this.api.crearCliente(req).subscribe({
        next: (c) => { this.credenciales.set(c); this.notificaciones.exito('Cliente creado.'); this.fin(); },
        error: (e) => { this.notificaciones.error(mensajeError(e)); this.guardando.set(false); },
      });
    }
  }

  private fin(): void {
    this.guardando.set(false);
    this.formAbierto.set(false);
    this.recargar();
  }

  protected desbloquear(c: ClienteInfo): void {
    this.api.desbloquearCliente(c.id).subscribe({
      next: () => { this.notificaciones.exito('Usuario desbloqueado.'); this.recargar(); },
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }

  protected activar(c: ClienteInfo): void {
    this.api.activarCliente(c.id, !c.usuarioActivo).subscribe({
      next: () => { this.notificaciones.exito(c.usuarioActivo ? 'Acceso desactivado.' : 'Acceso activado.'); this.recargar(); },
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }

  protected restablecer(c: ClienteInfo): void {
    this.api.restablecerPassword(c.id).subscribe({
      next: (r) => { this.credenciales.set(r); window.scrollTo({ top: 0, behavior: 'smooth' }); this.recargar(); },
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }
}
