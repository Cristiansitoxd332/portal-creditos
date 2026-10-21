import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { mensajeError } from '../../core/utilidades';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';

/** Cambio de contrasena (obligatorio en el primer ingreso; tambien disponible desde el perfil). */
@Component({
  selector: 'app-cambiar-password',
  imports: [ReactiveFormsModule, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <form class="tarjeta tarjeta-elevada aparecer" [formGroup]="form" (ngSubmit)="guardar()" novalidate>
        <span class="icono"><app-icono nombre="key" [tamano]="26" /></span>
        <h1>Crea una nueva contraseña</h1>
        @if (obligatorio()) {
          <p class="texto-suave">Por seguridad, debes cambiar la contraseña temporal antes de continuar.</p>
        }
        <div class="campo">
          <label for="actual">Contraseña actual</label>
          <input id="actual" type="password" formControlName="actual" autocomplete="current-password">
        </div>
        <div class="campo">
          <label for="nueva">Nueva contraseña</label>
          <input id="nueva" type="password" formControlName="nueva" autocomplete="new-password">
        </div>
        <ul class="reglas" aria-label="Requisitos de la contraseña">
          @for (r of reglas(); track r.texto) {
            <li [class.cumple]="r.cumple"><app-icono [nombre]="r.cumple ? 'check' : 'x'" [tamano]="14" [grosor]="3" />{{ r.texto }}</li>
          }
        </ul>
        <div class="campo">
          <label for="confirmacion">Confirma la nueva contraseña</label>
          <input id="confirmacion" type="password" formControlName="confirmacion" autocomplete="new-password">
          @if (valores().confirmacion && valores().confirmacion !== valores().nueva) {
            <span class="error">Las contraseñas no coinciden.</span>
          }
        </div>
        @if (error()) {
          <div class="aviso aviso-mora" role="alert"><app-icono nombre="alert" [tamano]="18" /><span>{{ error() }}</span></div>
        }
        <button class="btn btn-primario btn-bloque" type="submit" [disabled]="!valido() || cargando()">
          {{ cargando() ? 'Guardando…' : 'Guardar contraseña' }}
        </button>
        @if (!obligatorio()) {
          <button type="button" class="btn btn-fantasma btn-bloque" (click)="volver()">Cancelar</button>
        } @else {
          <button type="button" class="btn btn-fantasma btn-bloque" (click)="auth.logout()">Salir</button>
        }
      </form>
    </div>
  `,
  styles: [`
    .pagina { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px 16px; }
    form { width: min(440px, 100%); display: flex; flex-direction: column; gap: 14px; padding: 26px; }
    .icono { width: 52px; height: 52px; border-radius: 16px; background: var(--rojo-suave); color: var(--rojo); display: flex; align-items: center; justify-content: center; }
    h1 { font-size: 22px; font-weight: 800; }
    p { margin: 0; font-size: 14px; }
    .reglas { list-style: none; margin: -4px 0 0; padding: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 6px; font-size: 12.5px; color: var(--texto-3); }
    .reglas li { display: flex; align-items: center; gap: 6px; }
    .reglas li.cumple { color: var(--ok); font-weight: 700; }
  `],
})
export class CambiarPassword {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notificaciones = inject(NotificacionService);

  protected readonly cargando = signal(false);
  protected readonly error = signal('');
  protected readonly obligatorio = computed(() => this.auth.usuario()?.debeCambiarPassword ?? false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    actual: ['', Validators.required],
    nueva: ['', Validators.required],
    confirmacion: ['', Validators.required],
  });
  protected readonly valores = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  protected readonly reglas = computed(() => {
    const p = this.valores().nueva ?? '';
    return [
      { texto: 'Entre 8 y 64 caracteres', cumple: p.length >= 8 && p.length <= 64 },
      { texto: 'Una mayúscula', cumple: /[A-Z]/.test(p) },
      { texto: 'Una minúscula', cumple: /[a-z]/.test(p) },
      { texto: 'Un número', cumple: /\d/.test(p) },
      { texto: 'Un carácter especial', cumple: /[^A-Za-z0-9]/.test(p) },
    ];
  });

  protected readonly valido = computed(() => {
    const v = this.valores();
    return !!v.actual && this.reglas().every((r) => r.cumple) && v.nueva === v.confirmacion;
  });

  constructor() {
    if (!this.auth.token) {
      this.router.navigateByUrl('/login');
    }
  }

  protected guardar(): void {
    if (!this.valido()) {
      return;
    }
    const v = this.form.getRawValue();
    this.cargando.set(true);
    this.error.set('');
    this.auth.cambiarPassword(v.actual, v.nueva, v.confirmacion).subscribe({
      next: () => {
        this.notificaciones.exito('Contraseña actualizada correctamente.');
        this.router.navigateByUrl(this.auth.rutaInicio());
      },
      error: (e) => {
        this.error.set(mensajeError(e));
        this.cargando.set(false);
      },
    });
  }

  protected volver(): void {
    this.router.navigateByUrl(this.auth.rutaInicio());
  }
}
