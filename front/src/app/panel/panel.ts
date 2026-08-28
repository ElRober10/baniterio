import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface Seccion {
  nombre: string;
  descripcion: string;
}

@Component({
  imports: [RouterLink],
  selector: 'app-panel',
  styleUrl: './panel.css',
  templateUrl: './panel.html',
})
export class Panel {
  protected readonly secciones: Seccion[] = [
    { nombre: 'Miembros', descripcion: 'Socios de la peña y sus datos de contacto.' },
    { nombre: 'Eventos', descripcion: 'Calendario y organización de las quedadas y fiestas de la peña.' },
    { nombre: 'Cuentas', descripcion: 'Ingresos, gastos y balance de la peña.' },
    { nombre: 'Inventario', descripcion: 'Material y enseres que tiene la peña.' },
    { nombre: 'Ropa', descripcion: 'Pedidos y tallas del vestuario de la peña.' },
  ];
}
