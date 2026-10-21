import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { Comprobante, MedioPago, OpcionesPago } from '../../core/models';
import { TEXTO_MEDIO, guardarArchivo, mensajeError } from '../../core/utilidades';
import { Cargando, ChipCuota, Encabezado } from '../../shared/componentes';
import { Icono } from '../../shared/icono';
import { NotificacionService } from '../../shared/notificaciones';
import { FechaPipe, MonedaPipe } from '../../shared/pipes';

type Opcion = 'vencido' | 'proxima' | 'total' | 'otro';

/**
 * Pago SIMULADO en 2 pasos: (1) elegir valor y medio, (2) comprobante.
 * No se solicitan datos de tarjetas ni cuentas: el prototipo no procesa dinero real.
 */
@Component({
  selector: 'app-pagar',
  imports: [RouterLink, Icono, Encabezado, Cargando, ChipCuota, MonedaPipe, FechaPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pagar.html',
  styleUrl: './pagar.scss',
})
export class Pagar {
  private readonly api = inject(ApiService);
  private readonly notificaciones = inject(NotificacionService);
  readonly id = input.required<string>();

  protected readonly opciones = signal<OpcionesPago | null>(null);
  protected readonly error = signal('');
  protected readonly opcion = signal<Opcion>('proxima');
  protected readonly otroValor = signal<number | null>(null);
  protected readonly medio = signal<MedioPago>('PSE_SIMULADO');
  protected readonly simularRechazo = signal(false);
  protected readonly procesando = signal(false);
  protected readonly comprobante = signal<Comprobante | null>(null);
  protected readonly textoMedio = TEXTO_MEDIO;

  protected readonly valor = computed(() => {
    const o = this.opciones();
    if (!o) {
      return 0;
    }
    switch (this.opcion()) {
      case 'vencido': return o.valorVencido;
      case 'proxima': return o.valorHastaProximaCuota;
      case 'total': return o.valorMaximo;
      case 'otro': return this.otroValor() ?? 0;
    }
  });

  protected readonly errorValor = computed(() => {
    const o = this.opciones();
    const v = this.valor();
    if (!o || this.opcion() !== 'otro' || this.otroValor() === null) {
      return '';
    }
    if (v > o.valorMaximo) {
      return 'El valor supera el saldo pendiente del crédito.';
    }
    if (v < o.valorMinimo) {
      return 'El valor mínimo de un pago es $10.000.';
    }
    return '';
  });

  protected readonly puedePagar = computed(() => this.valor() > 0 && !this.errorValor() && !this.procesando());

  constructor() {
    effect(() => this.cargar(Number(this.id())));
  }

  private cargar(id: number): void {
    this.api.opcionesPago(id).subscribe({
      next: (o) => {
        this.opciones.set(o);
        this.opcion.set(o.valorVencido > 0 ? 'vencido' : 'proxima');
      },
      error: (e) => this.error.set(mensajeError(e)),
    });
  }

  protected cambiarOtro(texto: string): void {
    const limpio = texto.replace(/[^0-9]/g, '');
    this.otroValor.set(limpio ? Number(limpio) : null);
    this.opcion.set('otro');
  }

  protected pagar(): void {
    if (!this.puedePagar()) {
      return;
    }
    this.procesando.set(true);
    this.api.pagar(Number(this.id()), { valor: this.valor(), medioPago: this.medio(), simularRechazo: this.simularRechazo() }).subscribe({
      next: (c) => {
        this.comprobante.set(c);
        this.procesando.set(false);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      },
      error: (e) => {
        this.notificaciones.error(mensajeError(e));
        this.procesando.set(false);
      },
    });
  }

  protected descargar(): void {
    const c = this.comprobante();
    if (!c) {
      return;
    }
    this.api.comprobantePdf(Number(this.id()), c.pagoId).subscribe({
      next: (b) => guardarArchivo(b, `comprobante-${c.referencia}.pdf`),
      error: (e) => this.notificaciones.error(mensajeError(e)),
    });
  }

  protected otroPago(): void {
    this.comprobante.set(null);
    this.simularRechazo.set(false);
    this.otroValor.set(null);
    this.cargar(Number(this.id()));
  }
}
