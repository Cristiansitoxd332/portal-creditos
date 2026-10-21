import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { CreditoDetalle, EstadoCuota, PagoHistorial, PlanPagos } from '../../core/models';
import { TEXTO_MEDIO, guardarArchivo, mensajeError } from '../../core/utilidades';
import { Anillo, Cargando, ChipCuota, ChipSemaforo, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe, MonedaPipe, NumeroPipe } from '../../shared/pipes';

type Pestana = 'plan' | 'pagos' | 'vehiculo' | 'datos';
type Filtro = 'TODAS' | EstadoCuota;

/** Detalle de un credito del cliente: plan de pagos, historial de pagos, vehiculo y condiciones. */
@Component({
  selector: 'app-credito-detalle',
  imports: [RouterLink, Icono, Anillo, ChipSemaforo, ChipCuota, Encabezado, Cargando, MonedaPipe, FechaPipe, NumeroPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './credito-detalle.html',
  styleUrl: './credito-detalle.scss',
})
export class CreditoDetallePage {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);
  readonly id = input.required<string>();

  protected readonly detalle = signal<CreditoDetalle | null>(null);
  protected readonly plan = signal<PlanPagos | null>(null);
  protected readonly pagos = signal<PagoHistorial[]>([]);
  protected readonly error = signal('');
  protected readonly pestana = signal<Pestana>('plan');
  protected readonly filtro = signal<Filtro>('TODAS');
  protected readonly textoMedio = TEXTO_MEDIO;

  protected readonly filtros: { valor: Filtro; texto: string }[] = [
    { valor: 'TODAS', texto: 'Todas' },
    { valor: 'VENCIDA', texto: 'Vencidas' },
    { valor: 'PENDIENTE', texto: 'Pendientes' },
    { valor: 'PAGADA', texto: 'Pagadas' },
  ];

  protected readonly cuotas = computed(() => {
    const lista = this.plan()?.cuotas ?? [];
    const f = this.filtro();
    if (f === 'TODAS') {
      return lista;
    }
    if (f === 'PENDIENTE') {
      return lista.filter((c) => c.estado === 'PENDIENTE' || c.estado === 'PARCIAL');
    }
    return lista.filter((c) => c.estado === f);
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      forkJoin({ detalle: this.api.miCredito(id), plan: this.api.misCuotas(id), pagos: this.api.misPagos(id) }).subscribe({
        next: (r) => {
          this.detalle.set(r.detalle);
          this.plan.set(r.plan);
          this.pagos.set(r.pagos);
          if (r.plan.cuotasVencidas > 0) {
            this.filtro.set('VENCIDA');
          }
        },
        error: (e) => this.error.set(mensajeError(e)),
      });
    });
  }

  protected descargarComprobante(p: PagoHistorial): void {
    this.api.comprobantePdf(Number(this.id()), p.pagoId).subscribe({
      next: (b) => guardarArchivo(b, `comprobante-${p.referencia}.pdf`),
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }
}
