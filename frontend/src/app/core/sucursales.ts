/** Sucursales de Rentar Inversiones con su código (como en Sadmit: "01 NEIVA"). */
export interface Sucursal {
  codigo: string;
  nombre: string;
}

export const SUCURSALES: Sucursal[] = [
  { codigo: '01', nombre: 'Neiva' },
  { codigo: '02', nombre: 'Campoalegre' },
  { codigo: '03', nombre: 'Manizales' },
  { codigo: '04', nombre: 'Palmira' },
  { codigo: '05', nombre: 'Mocoa' },
  { codigo: '06', nombre: 'Puerto Asís' },
  { codigo: '07', nombre: 'La Hormiga' },
  { codigo: '08', nombre: 'Orito' },
  { codigo: '09', nombre: 'Villagarzón' },
  { codigo: '10', nombre: 'Sibundoy' },
  { codigo: '11', nombre: 'Puerto Leguízamo' },
  { codigo: '12', nombre: 'Armenia' },
  { codigo: '13', nombre: 'Santa María' },
  { codigo: '14', nombre: 'Guadalupe' },
];

export function etiquetaSucursal(codigo: string | null | undefined): string {
  const s = SUCURSALES.find((x) => x.codigo === codigo) ?? SUCURSALES[0];
  return `${s.codigo} ${s.nombre.toUpperCase()}`;
}
