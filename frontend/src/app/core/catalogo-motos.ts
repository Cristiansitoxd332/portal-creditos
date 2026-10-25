/**
 * Catalogo de referencias Yamaha con su precio de lista y el valor de la matricula.
 * - Matricula con reserva (con prenda): moto comprada a credito; incluye la inscripcion de la prenda.
 * - Matricula sin reserva (sin prenda): moto comprada de contado.
 * Las XTZ-250 y FZ250 ABS incluyen el impuesto al consumo del 8 %.
 * Valores tomados de la lista de precios entregada para el prototipo; se usan solo como referencia.
 */
export interface ReferenciaMoto {
  codigo: string;
  linea: string;
  precio: number;
  matriculaSinPrenda: number;
  matriculaConPrenda: number;
}

export const CATALOGO_YAMAHA: ReferenciaMoto[] = [
  { codigo: 'CRYPTON', linea: 'Crypton T115 FI', precio: 9_100_000, matriculaSinPrenda: 656_000, matriculaConPrenda: 692_000 },
  { codigo: 'NMAX', linea: 'NMAX GPD155-A Nueva versión', precio: 16_800_000, matriculaSinPrenda: 789_000, matriculaConPrenda: 825_000 },
  { codigo: 'AEROX', linea: 'Aerox GDR-155A', precio: 12_800_000, matriculaSinPrenda: 764_000, matriculaConPrenda: 800_000 },
  { codigo: 'FZ15', linea: 'FZ15A FZN150-A Nueva', precio: 11_800_000, matriculaSinPrenda: 758_000, matriculaConPrenda: 794_000 },
  { codigo: 'XTZ150', linea: 'XTZ150 Nueva', precio: 14_500_000, matriculaSinPrenda: 775_000, matriculaConPrenda: 811_000 },
  { codigo: 'MT155', linea: 'MT155', precio: 15_500_000, matriculaSinPrenda: 781_000, matriculaConPrenda: 817_000 },
  { codigo: 'XTZ250', linea: 'XTZ-250', precio: 25_900_000, matriculaSinPrenda: 1_265_000, matriculaConPrenda: 1_301_000 },
  { codigo: 'FZ250', linea: 'FZ250 ABS', precio: 15_300_000, matriculaSinPrenda: 1_198_000, matriculaConPrenda: 1_234_000 },
];
