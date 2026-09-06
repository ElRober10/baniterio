import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AdminAvisosService } from '../admin/admin-avisos.service';
import { AuthService } from '../auth/auth.service';
import { ModalRespuestaEvento } from './eventos/modal-respuesta-evento/modal-respuesta-evento';
import { AvisoPendientes } from '../shared/aviso-pendientes/aviso-pendientes';
import { SECCIONES } from './secciones';

/**
 * Layout del panel privado de la peña (tras iniciar sesión). Solo se llega aquí
 * con el authGuard pasado (ver app.routes.ts). Pinta la barra lateral + el
 * header/nav móvil y deja el contenido de cada sección en el `<router-outlet>`
 * (la ruta hija `''` es `PanelInicio`, la bienvenida).
 *
 * En el arranque llama a `asegurarYo()` para que el nav sepa las áreas del
 * usuario y pueda mostrar (o no) el enlace "Administración"; cuando el usuario
 * tiene áreas, pide además el recuento de pendientes para la campanita del nav.
 *
 * También monta `ModalRespuestaEvento`: si hay convocatorias sin contestar,
 * tapa la pantalla entera hasta que el usuario responda (voy/no voy/en duda,
 * y ficha de bebida si toca). Al estar aquí, sale nada más entrar a `/panel`.
 *
 * `salir()` cierra la sesión de verdad (borra el token) y vuelve a la home.
 */
@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet, AvisoPendientes, ModalRespuestaEvento],
  selector: 'app-panel',
  styleUrl: './panel.css',
  templateUrl: './panel.html',
})
export class Panel {
  protected readonly auth = inject(AuthService);
  protected readonly avisos = inject(AdminAvisosService);
  private readonly router = inject(Router);

  /** Nombres de las secciones "próximamente" para el nav; el detalle vive en PanelInicio. */
  protected readonly seccionesPronto = SECCIONES.map((s) => s.nombre);

  constructor() {
    this.auth.asegurarYo().subscribe((usuario) => {
      if (usuario?.areas?.length) {
        this.avisos.refrescar();
      }
    });
  }

  protected salir(): void {
    this.auth.cerrarSesion();
    this.router.navigateByUrl('/');
  }
}
