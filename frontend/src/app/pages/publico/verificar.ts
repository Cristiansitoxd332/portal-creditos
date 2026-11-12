import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { VerificacionPazYSalvo } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Icono } from '../../shared/icono';
import { FechaPipe } from '../../shared/pipes';

/**
 * Pagina PUBLICA de verificacion del paz y salvo (a la que apunta el codigo QR del PDF).
 * Muestra los datos del titular enmascarados para proteger su privacidad.
 */
@Component({
  selector: 'app-verificar',
  imports: [FormsModule, RouterLink, Icono, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pagina">
      <a routerLink="/login" class="marca"><span class="logo">R</span><strong>Rentar</strong></a>
      <div class="tarjeta tarjeta-elevada caja aparecer">
        <h1>Verificar paz y salvo</h1>
        <p class="texto-suave">Ingrese el código que aparece en el documento o escanee su código QR.</p>
        <form class="buscar" (ngSubmit)="consultar()">
          <label class="sr-only" for="codigo">Código de verificación</label>
          <input id="codigo" name="codigo" [(ngModel)]="texto" placeholder="PYS-2026-XXXXXXXX" autocomplete="off">
          <button class="btn btn-primario" type="submit" [disabled]="!texto.trim() || cargando()">Verificar</button>
        </form>

        @if (cargando()) {
          <div class="esqueleto" style="height: 140px"></div>
        } @else if (resultado(); as r) {
          @if (r.valido) {
            <div class="resultado valido aparecer">
              <span class="sello"><app-icono nombre="check" [tamano]="26" [grosor]="3" /></span>
              <div>
                <strong>Documento válido</strong>
                <p>Este paz y salvo fue emitido por el portal.</p>
              </div>
            </div>
            <dl class="datos">
              <div><dt>Código</dt><dd>{{ r.codigoVerificacion }}</dd></div>
              <div><dt>Crédito</dt><dd>{{ r.numeroCredito }}</dd></div>
              <div><dt>Titular</dt><dd>{{ r.titular }}</dd></div>
              <div><dt>Documento</dt><dd>{{ r.documento }}</dd></div>
              <div><dt>Placa</dt><dd>{{ r.placa }}</dd></div>
              <div><dt>Cancelado el</dt><dd>{{ r.fechaCancelacion | fecha: 'larga' }}</dd></div>
              <div><dt>Emitido el</dt><dd>{{ r.fechaEmision | fecha: 'larga' }}</dd></div>
            </dl>
          } @else {
            <div class="resultado invalido aparecer">
              <span class="sello"><app-icono nombre="x" [tamano]="26" [grosor]="3" /></span>
              <div>
                <strong>Código no encontrado</strong>
                <p>No corresponde a ningún paz y salvo emitido.</p>
              </div>
            </div>
          }
          <p class="aviso">{{ r.aviso }}</p>
        }
        @if (error()) { <div class="aviso aviso-mora">{{ error() }}</div> }
      </div>
    </div>
  `,
  styles: [`
    .pagina { max-width: 560px; margin: 0 auto; padding: 32px 16px; display: flex; flex-direction: column; gap: 20px; }
    .marca { display: flex; align-items: center; gap: 10px; text-decoration: none; color: var(--texto); font-size: 18px; }
    .logo { width: 38px; height: 38px; border-radius: 11px; background: var(--primario); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 800; font-size: 20px; }
    .caja { display: flex; flex-direction: column; gap: 16px; padding: 24px; }
    h1 { font-size: 22px; font-weight: 800; }
    p { margin: 0; font-size: 14px; }
    .buscar { display: flex; gap: 8px; }
    .buscar input { flex: 1; min-width: 0; height: 48px; border: 1.5px solid var(--borde-fuerte); border-radius: 12px; padding: 0 14px; font: inherit;
      font-size: 15px; text-transform: uppercase; background: var(--superficie); color: var(--texto); }
    .resultado { display: flex; gap: 14px; align-items: center; padding: 16px; border-radius: 16px; }
    .resultado strong { font-size: 17px; }
    .valido { background: var(--ok-fondo); color: var(--ok); }
    .invalido { background: var(--mora-fondo); color: var(--mora); }
    .sello { width: 50px; height: 50px; border-radius: 25px; background: currentColor; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
    .sello app-icono { color: var(--superficie); }
    .datos { margin: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .datos div { display: flex; flex-direction: column; }
    dt { font-size: 12px; color: var(--texto-3); }
    dd { margin: 0; font-weight: 700; }
  `],
})
export class Verificar {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly codigo = input<string>();

  protected texto = '';
  protected readonly cargando = signal(false);
  protected readonly resultado = signal<VerificacionPazYSalvo | null>(null);
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      const c = this.codigo();
      if (c) {
        this.texto = c;
        this.buscar(c);
      }
    });
  }

  protected consultar(): void {
    const c = this.texto.trim().toUpperCase();
    if (c) {
      this.router.navigate(['/verificar', c]);
    }
  }

  private buscar(codigo: string): void {
    this.cargando.set(true);
    this.error.set('');
    this.api.verificarPazYSalvo(codigo).subscribe({
      next: (r) => { this.resultado.set(r); this.cargando.set(false); },
      error: (e) => { this.error.set(mensajeError(e)); this.cargando.set(false); },
    });
  }
}
