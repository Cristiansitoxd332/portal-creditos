import { HttpErrorResponse } from '@angular/common/http';
import { EstadoCuota, MedioPago, Semaforo } from './models';

/** Extrae un mensaje claro de un error de la API (todas las respuestas de error tienen el mismo formato). */
export function mensajeError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'No hay conexión con el servidor. Verifique que el backend esté encendido.';
    }
    const cuerpo = err.error;
    if (cuerpo && typeof cuerpo === 'object' && 'mensaje' in cuerpo) {
      const detalles = (cuerpo as { detalles?: Record<string, string> | null }).detalles;
      const extra = detalles ? ' ' + Object.values(detalles).join('. ') : '';
      return String((cuerpo as { mensaje: string }).mensaje) + extra;
    }
  }
  return 'Ocurrió un error inesperado. Intente de nuevo.';
}

/** Descarga un archivo recibido como Blob con el nombre indicado. */
export function guardarArchivo(blob: Blob, nombre: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export const TEXTO_SEMAFORO: Record<Semaforo, string> = {
  AL_DIA: 'Al día',
  PROXIMA_A_VENCER: 'Por vencer',
  EN_MORA: 'En mora',
  CANCELADO: 'Cancelado',
};

export const CLASE_SEMAFORO: Record<Semaforo, string> = {
  AL_DIA: 'chip-ok',
  PROXIMA_A_VENCER: 'chip-alerta',
  EN_MORA: 'chip-mora',
  CANCELADO: 'chip-neutro',
};

export const ICONO_SEMAFORO: Record<Semaforo, string> = {
  AL_DIA: 'check',
  PROXIMA_A_VENCER: 'clock',
  EN_MORA: 'alert',
  CANCELADO: 'doc-check',
};

export const TEXTO_CUOTA: Record<EstadoCuota, string> = {
  PAGADA: 'Pagada',
  PARCIAL: 'Abono parcial',
  PENDIENTE: 'Pendiente',
  VENCIDA: 'Vencida',
};

export const CLASE_CUOTA: Record<EstadoCuota, string> = {
  PAGADA: 'chip-ok',
  PARCIAL: 'chip-alerta',
  PENDIENTE: 'chip-neutro',
  VENCIDA: 'chip-mora',
};

export const ICONO_CUOTA: Record<EstadoCuota, string> = {
  PAGADA: 'check',
  PARCIAL: 'clock',
  PENDIENTE: 'clock',
  VENCIDA: 'alert',
};

export const TEXTO_MEDIO: Record<MedioPago, string> = {
  PSE_SIMULADO: 'PSE (simulado)',
  TARJETA_SIMULADA: 'Tarjeta (simulada)',
  EFECTIVO: 'Efectivo',
  TRANSFERENCIA: 'Transferencia',
  CONSIGNACION: 'Consignación',
};

/** Fecha de hoy en formato aaaa-mm-dd (para valores por defecto de formularios). */
export function hoyIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
