import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { SECCIONES } from '../secciones';

/**
 * Contenido de bienvenida del panel: se pinta dentro del `<router-outlet>` de
 * `Panel` en la ruta `/panel` (hija `''`). Pinta las secciones "próximamente" de
 * la peña, cuya lista vive en `panel/secciones.ts` (compartida con el nav).
 *
 * Si el usuario tiene alguna área de administración concedida, se añade además
 * una tarjeta clicable "Administración" que lleva al índice `/panel/administracion`.
 */
@Component({
  selector: 'app-panel-inicio',
  imports: [RouterLink],
  styleUrl: './inicio.css',
  templateUrl: './inicio.html',
})
export class PanelInicio {
  protected readonly auth = inject(AuthService);
  protected readonly secciones = SECCIONES;
}
