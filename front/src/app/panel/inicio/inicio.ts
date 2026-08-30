import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminAvisosService } from '../../admin/admin-avisos.service';
import { AuthService } from '../../auth/auth.service';
import { AvisoPendientes } from '../../shared/aviso-pendientes/aviso-pendientes';
import { SECCIONES } from '../secciones';

/**
 * Contenido de bienvenida del panel: se pinta dentro del `<router-outlet>` de
 * `Panel` en la ruta `/panel` (hija `''`). Pinta las secciones "próximamente" de
 * la peña, cuya lista vive en `panel/secciones.ts` (compartida con el nav).
 *
 * Si el usuario tiene alguna área de administración concedida, se añade además
 * una tarjeta clicable "Administración" que lleva al índice `/panel/administracion`,
 * con una campanita del total de cosas sin atender (`AdminAvisosService`).
 */
@Component({
  selector: 'app-panel-inicio',
  imports: [RouterLink, AvisoPendientes],
  styleUrl: './inicio.css',
  templateUrl: './inicio.html',
})
export class PanelInicio implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly avisos = inject(AdminAvisosService);
  protected readonly secciones = SECCIONES;

  ngOnInit(): void {
    if (this.auth.usuarioActual()?.areas?.length) {
      this.avisos.refrescar();
    }
  }
}
