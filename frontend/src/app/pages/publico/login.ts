import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { mensajeError } from '../../core/utilidades';
import { Icono } from '../../shared/icono';

/** Pantalla de inicio de sesion (clientes y administrador). */
@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pantalla">
      <section class="portada">
        <div class="marca"><span class="logo">R</span><span><strong>Rentar</strong><small>Portal de clientes</small></span></div>
        <div class="mensaje">
          <h1>Tu crédito, claro y a la mano.</h1>
          <p>Consulta tus cuotas, paga y descarga tu paz y salvo en un solo lugar.</p>
        </div>
      </section>

      <form class="tarjeta tarjeta-elevada formulario aparecer" [formGroup]="form" (ngSubmit)="ingresar()" novalidate>
        <h2>Iniciar sesión</h2>
        <div class="campo">
          <label for="usuario">Número de documento</label>
          <input id="usuario" formControlName="usuario" autocomplete="username" inputmode="text"
                 [class.invalido]="invalido('usuario')" placeholder="Ej. 1075200101">
          @if (invalido('usuario')) { <span class="error">Ingrese su número de documento.</span> }
        </div>
        <div class="campo">
          <label for="clave">Contraseña</label>
          <div class="con-boton">
            <input id="clave" formControlName="clave" [type]="verClave() ? 'text' : 'password'" autocomplete="current-password"
                   [class.invalido]="invalido('clave')">
            <button type="button" class="ver" (click)="verClave.set(!verClave())"
                    [attr.aria-label]="verClave() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
              <app-icono [nombre]="verClave() ? 'x' : 'eye'" [tamano]="18" />
            </button>
          </div>
          @if (invalido('clave')) { <span class="error">Ingrese su contraseña.</span> }
        </div>
        @if (error()) {
          <div class="aviso aviso-mora" role="alert"><app-icono nombre="alert" [tamano]="18" /><span>{{ error() }}</span></div>
        }
        <button class="btn btn-primario btn-bloque" type="submit" [disabled]="cargando()">
          {{ cargando() ? 'Ingresando…' : 'Ingresar' }}
        </button>
        <a routerLink="/verificar" class="enlace">Verificar un paz y salvo</a>
      </form>

      <div class="aviso seguridad">
        <app-icono nombre="lock" [tamano]="18" />
        <span>Por tu seguridad, la cuenta se bloquea tras 5 intentos fallidos. Nunca compartas tu contraseña.</span>
      </div>

      <details class="tarjeta pruebas">
        <summary>Usuarios de prueba (datos ficticios)</summary>
        <ul>
          @for (u of usuariosPrueba; track u.usuario) {
            <li><button type="button" class="btn btn-secundario btn-pequeno" (click)="usar(u.usuario, u.clave)">{{ u.usuario }}</button> {{ u.descripcion }}</li>
          }
        </ul>
      </details>

      <p class="pie">Prototipo académico con datos ficticios. No está conectado a los sistemas de la empresa ni procesa pagos reales.</p>
    </div>
  `,
  styles: [`
    .pantalla { max-width: 460px; margin: 0 auto; min-height: 100vh; display: flex; flex-direction: column; }
    .portada { background: var(--rojo); color: #fff; padding: 48px 28px 76px; border-radius: 0 0 32px 32px; display: flex; flex-direction: column; gap: 28px; }
    .marca { display: flex; align-items: center; gap: 10px; }
    .marca span:last-child { display: flex; flex-direction: column; line-height: 1.2; }
    .marca strong { font-size: 20px; font-weight: 800; }
    .marca small { font-size: 12px; opacity: 0.85; }
    .logo { width: 42px; height: 42px; border-radius: 12px; background: #fff; color: var(--rojo); display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 22px; }
    h1 { font-size: 30px; line-height: 1.15; font-weight: 800; letter-spacing: -0.6px; }
    .mensaje p { margin: 8px 0 0; opacity: 0.92; }
    .formulario { margin: -44px 16px 0; display: flex; flex-direction: column; gap: 16px; padding: 24px; }
    h2 { font-size: 19px; }
    .con-boton { position: relative; display: flex; }
    .con-boton input { flex: 1; padding-right: 48px; }
    .ver { position: absolute; right: 4px; top: 4px; width: 40px; height: 40px; border: 0; background: transparent; color: var(--texto-3); cursor: pointer; border-radius: 10px; display: flex; align-items: center; justify-content: center; }
    .enlace { text-align: center; font-weight: 700; font-size: 14px; }
    .seguridad { margin: 16px 16px 0; }
    .pruebas { margin: 12px 16px 0; padding: 14px 16px; font-size: 13px; }
    .pruebas summary { cursor: pointer; font-weight: 700; }
    .pruebas ul { list-style: none; padding: 0; margin: 12px 0 0; display: flex; flex-direction: column; gap: 8px; }
    .pruebas li { display: flex; align-items: center; gap: 10px; color: var(--texto-2); }
    .pie { margin: auto 24px 24px; padding-top: 20px; text-align: center; font-size: 11.5px; color: var(--texto-3); }
    @media (min-width: 600px) { .pantalla { padding-top: 32px; } .portada { border-radius: 32px; } }
  `],
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly cargando = signal(false);
  protected readonly error = signal('');
  protected readonly verClave = signal(false);
  protected readonly enviado = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    usuario: ['', Validators.required],
    clave: ['', Validators.required],
  });

  protected readonly usuariosPrueba = [
    { usuario: '1075200101', clave: 'Cliente2026*', descripcion: 'Laura – crédito al día' },
    { usuario: '1075200202', clave: 'Cliente2026*', descripcion: 'Andrés – crédito en mora' },
    { usuario: '1075200303', clave: 'Cliente2026*', descripcion: 'Diana – crédito cancelado' },
    { usuario: '1075200505', clave: 'Cliente2026*', descripcion: 'Sofía – dos créditos' },
    { usuario: 'admin', clave: 'Admin2026*', descripcion: 'Administrador' },
  ];

  protected invalido(campo: 'usuario' | 'clave'): boolean {
    const c = this.form.controls[campo];
    return c.invalid && (c.touched || this.enviado());
  }

  protected usar(usuario: string, clave: string): void {
    this.form.setValue({ usuario, clave });
  }

  protected ingresar(): void {
    this.enviado.set(true);
    if (this.form.invalid) {
      return;
    }
    this.cargando.set(true);
    this.error.set('');
    const { usuario, clave } = this.form.getRawValue();
    this.auth.login(usuario.trim(), clave).subscribe({
      next: () => this.router.navigateByUrl(this.auth.rutaInicio()),
      error: (e) => {
        this.error.set(mensajeError(e));
        this.cargando.set(false);
      },
    });
  }
}
