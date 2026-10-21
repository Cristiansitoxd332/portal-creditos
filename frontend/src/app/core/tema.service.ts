import { Injectable, signal } from '@angular/core';

const CLAVE = 'portal-creditos.tema';

/** Modo claro / oscuro. La preferencia se recuerda en este navegador. */
@Injectable({ providedIn: 'root' })
export class TemaService {
  readonly oscuro = signal(false);

  constructor() {
    let guardado: string | null = null;
    try {
      guardado = localStorage.getItem(CLAVE);
    } catch {
      /* sin almacenamiento */
    }
    const sistema = typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches;
    this.aplicar(guardado ? guardado === 'oscuro' : sistema);
  }

  alternar(): void {
    this.aplicar(!this.oscuro());
    try {
      localStorage.setItem(CLAVE, this.oscuro() ? 'oscuro' : 'claro');
    } catch {
      /* sin almacenamiento */
    }
  }

  private aplicar(oscuro: boolean): void {
    this.oscuro.set(oscuro);
    document.documentElement.dataset['tema'] = oscuro ? 'oscuro' : 'claro';
  }
}
