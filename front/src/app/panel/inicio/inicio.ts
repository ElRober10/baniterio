import { Component } from '@angular/core';
import { SECCIONES } from '../secciones';

/**
 * Contenido de bienvenida del panel: se pinta dentro del `<router-outlet>` de
 * `Panel` en la ruta `/panel` (hija `''`). Pinta las secciones "próximamente" de
 * la peña, cuya lista vive en `panel/secciones.ts` (compartida con el nav).
 */
@Component({
  selector: 'app-panel-inicio',
  styleUrl: './inicio.css',
  templateUrl: './inicio.html',
})
export class PanelInicio {
  protected readonly secciones = SECCIONES;
}
