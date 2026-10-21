import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { CreditoResumen } from '../../core/models';
import { mensajeError } from '../../core/utilidades';
import { Anillo, Cargando, ChipSemaforo } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { FechaPipe, MonedaPipe } from '../../shared/pipes';

/** Inicio del cliente: tarjeta por cada credito con su progreso, semaforo y proxima cuota. */
@Component({
  selector: 'app-inicio',
  imports: [RouterLink, Icono, Anillo, ChipSemaforo, Cargando, MonedaPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly creditos = signal<CreditoResumen[] | null>(null);
  protected readonly error = signal('');

  protected readonly saludo = computed(() => {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  });

  protected readonly resumen = computed(() => {
    const lista = this.creditos() ?? [];
    const activos = lista.filter((c) => c.estado !== 'CANCELADO');
    return {
      activos: activos.length,
      saldo: activos.reduce((s, c) => s + c.saldoCapital, 0),
      enMora: activos.some((c) => c.estado === 'EN_MORA'),
    };
  });

  constructor() {
    this.api.misCreditos().subscribe({
      next: (c) => this.creditos.set(c),
      error: (e) => this.error.set(mensajeError(e)),
    });
  }
}
