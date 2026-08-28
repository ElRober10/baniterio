import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';

interface Seccion {
  nombre: string;
  descripcion: string;
}

/**
 * Panel privado de la peña (tras iniciar sesión). Solo se llega aquí con el
 * authGuard pasado (ver app.routes.ts). De momento solo lista las secciones
 * futuras; cuando existan, cada una tendrá su propia ruta hija.
 *
 * `salir()` cierra la sesión de verdad (borra el token) y vuelve a la home.
 */
@Component({
  imports: [RouterLink],
  selector: 'app-panel',
  styleUrl: './panel.css',
  templateUrl: './panel.html',
})
export class Panel {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly secciones: Seccion[] = [
    { nombre: 'Miembros', descripcion: 'Socios de la peña y sus datos de contacto.' },
    { nombre: 'Eventos', descripcion: 'Calendario y organización de las quedadas y fiestas de la peña.' },
    { nombre: 'Cuentas', descripcion: 'Ingresos, gastos y balance de la peña.' },
    { nombre: 'Inventario', descripcion: 'Material y enseres que tiene la peña.' },
    { nombre: 'Ropa', descripcion: 'Pedidos y tallas del vestuario de la peña.' },
  ];

  protected salir(): void {
    this.auth.cerrarSesion();
    this.router.navigateByUrl('/');
  }
}
