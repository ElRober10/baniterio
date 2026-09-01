/**
 * Secciones "próximamente" de la peña, en un único sitio. `PanelInicio` pinta el
 * nombre + la descripción de cada una; el nav de `Panel` solo usa los nombres
 * (`SECCIONES.map((s) => s.nombre)`). Antes esta lista estaba duplicada en los
 * dos componentes y se desincronizaba.
 */
export interface Seccion {
  nombre: string;
  descripcion: string;
}

export const SECCIONES: Seccion[] = [
  {
    nombre: 'Eventos',
    descripcion: 'Calendario y organización de las quedadas y fiestas de la peña.',
  },
  { nombre: 'Cuentas', descripcion: 'Ingresos, gastos y balance de la peña.' },
  { nombre: 'Inventario', descripcion: 'Material y enseres que tiene la peña.' },
  { nombre: 'Ropa', descripcion: 'Pedidos y tallas del vestuario de la peña.' },
];
