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
  { nombre: 'Ropa', descripcion: 'Pedidos y tallas del vestuario de la peña.' },
];
