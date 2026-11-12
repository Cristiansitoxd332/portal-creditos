import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from './api.service';

/** Cantidad de mensajes sin leer del cliente (globo de la campana). Se consulta en tiempo real cada minuto. */
@Injectable({ providedIn: 'root' })
export class MensajesNoLeidos {
  private readonly api = inject(ApiService);
  readonly cantidad = signal(0);

  actualizar(): void {
    this.api.mensajesNoLeidos().subscribe({ next: (r) => this.cantidad.set(r.noLeidos), error: () => {} });
  }
}
