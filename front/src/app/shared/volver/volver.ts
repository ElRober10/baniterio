import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Enlace "← Volver" reutilizable. Se usa igual en todas las pantallas para que el
 * botón de atrás tenga siempre el mismo aspecto y comportamiento.
 *
 * - `destino`: ruta a la que vuelve (obligatoria), p. ej. `/panel/administracion`.
 * - `etiqueta`: texto tras la flecha; por defecto "Volver".
 *
 * No usa el historial del navegador (`location.back()`) a propósito: navega
 * siempre al padre lógico de la pantalla, así el destino es predecible aunque se
 * haya llegado por un enlace directo o recargando.
 */
@Component({
  selector: 'app-volver',
  imports: [RouterLink],
  template: `
    <a
      [routerLink]="destino"
      class="inline-flex items-center gap-1.5 text-sm font-medium text-muted transition hover:text-ink"
    >
      <span aria-hidden="true">←</span> {{ etiqueta }}
    </a>
  `,
})
export class Volver {
  @Input({ required: true }) destino!: string;
  @Input() etiqueta = 'Volver';
}
