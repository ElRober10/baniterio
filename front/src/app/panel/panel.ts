import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/**
 * Layout del panel privado de la peña (tras iniciar sesión). Solo se llega aquí
 * con el authGuard pasado (ver app.routes.ts). Pinta la barra lateral + el
 * header/nav móvil y deja el contenido de cada sección en el `<router-outlet>`
 * (la ruta hija `''` es `PanelInicio`, la bienvenida).
 *
 * En el arranque llama a `asegurarYo()` para que el nav sepa las áreas del
 * usuario y pueda mostrar (o no) el grupo "Administración".
 *
 * `salir()` cierra la sesión de verdad (borra el token) y vuelve a la home.
 */
@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-panel',
  styleUrl: './panel.css',
  templateUrl: './panel.html',
})
export class Panel {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Nombres de las secciones "próximamente" (solo para el nav; el detalle vive en PanelInicio). */
  protected readonly seccionesPronto: string[] = ['Miembros', 'Eventos', 'Cuentas', 'Inventario', 'Ropa'];

  constructor() {
    this.auth.asegurarYo().subscribe();
  }

  protected salir(): void {
    this.auth.cerrarSesion();
    this.router.navigateByUrl('/');
  }
}
