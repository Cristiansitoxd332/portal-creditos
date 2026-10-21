import { Pipe, PipeTransform } from '@angular/core';

const MONEDA = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const NUMERO = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });

/** 43339201 -> "$ 43.339.201" */
@Pipe({ name: 'moneda' })
export class MonedaPipe implements PipeTransform {
  transform(valor: number | null | undefined): string {
    return valor === null || valor === undefined ? '—' : MONEDA.format(valor);
  }
}

/** 27.8 -> "27,8" */
@Pipe({ name: 'numero' })
export class NumeroPipe implements PipeTransform {
  transform(valor: number | null | undefined): string {
    return valor === null || valor === undefined ? '—' : NUMERO.format(valor);
  }
}

/**
 * Fechas del backend ("2026-10-25" o "2026-10-17T21:31:00") en espanol de Colombia.
 * formato: 'corta' (25 oct 2026), 'larga' (25 de octubre de 2026), 'hora' (25 oct 2026, 9:31 p. m.)
 */
@Pipe({ name: 'fecha' })
export class FechaPipe implements PipeTransform {
  transform(valor: string | null | undefined, formato: 'corta' | 'larga' | 'hora' = 'corta'): string {
    if (!valor) {
      return '—';
    }
    const fecha = valor.length === 10 ? new Date(valor + 'T00:00:00') : new Date(valor);
    if (isNaN(fecha.getTime())) {
      return valor;
    }
    const opciones: Intl.DateTimeFormatOptions =
      formato === 'larga' ? { day: 'numeric', month: 'long', year: 'numeric' }
        : formato === 'hora' ? { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' }
          : { day: 'numeric', month: 'short', year: 'numeric' };
    return new Intl.DateTimeFormat('es-CO', opciones).format(fecha).replace('.', '');
  }
}
