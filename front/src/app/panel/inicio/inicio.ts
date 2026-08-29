import { Component } from '@angular/core';

interface Seccion {
  nombre: string;
  descripcion: string;
}

/**
 * Contenido de bienvenida del panel: se pinta dentro del `<router-outlet>` de
 * `Panel` en la ruta `/panel` (hija `''`). Dueño del array `secciones` con las
 * secciones "próximamente" de la peña.
 */
@Component({
  selector: 'app-panel-inicio',
  styleUrl: './inicio.css',
  templateUrl: './inicio.html',
})
export class PanelInicio {
  protected readonly secciones: Seccion[] = [
    { nombre: 'Miembros', descripcion: 'Socios de la peña y sus datos de contacto.' },
    { nombre: 'Eventos', descripcion: 'Calendario y organización de las quedadas y fiestas de la peña.' },
    { nombre: 'Cuentas', descripcion: 'Ingresos, gastos y balance de la peña.' },
    { nombre: 'Inventario', descripcion: 'Material y enseres que tiene la peña.' },
    { nombre: 'Ropa', descripcion: 'Pedidos y tallas del vestuario de la peña.' },
  ];
}
