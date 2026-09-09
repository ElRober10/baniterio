import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CATEGORIAS } from './inventario.types';

/**
 * Portada de la sección Inventario: una rejilla con un botón por categoría
 * (Alcohol, Cerveza, Refrescos, Limpieza, Comida). Al pulsar se abre
 * `/panel/inventario/:slug`, que muestra el listado de esa categoría
 * (`InventarioCategoria`). No pide nada al backend.
 */
@Component({
  selector: 'app-inventario',
  imports: [RouterLink],
  templateUrl: './inventario.html',
  styleUrl: './inventario.css',
})
export class Inventario {
  protected readonly categorias = CATEGORIAS;
}
