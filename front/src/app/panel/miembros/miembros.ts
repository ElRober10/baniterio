import { Component, OnInit, inject, signal } from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { forkJoin } from 'rxjs';
import { TarjetaMiembro } from './tarjeta-miembro/tarjeta-miembro';
import { PerfilService } from './perfil.service';
import { TarjetaMiembroResponse, VinculoPendiente } from './perfil.types';

/**
 * Sección Miembros: la lista de tarjetas de la peña (ya ordenada por el
 * backend — el front no reordena) y, si otra persona ha declarado que sois
 * pareja, un aviso arriba para confirmar o rechazar el vínculo.
 *
 * La tarjeta propia (la que coincide con el usuario en sesión) lleva el botón
 * "Editar" que va al editor de perfil.
 */
@Component({
  selector: 'app-miembros',
  imports: [TarjetaMiembro],
  templateUrl: './miembros.html',
  styleUrl: './miembros.css',
})
export class Miembros implements OnInit {
  private readonly perfilService = inject(PerfilService);
  protected readonly auth = inject(AuthService);

  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly tarjetas = signal<TarjetaMiembroResponse[]>([]);
  protected readonly vinculoPendiente = signal<VinculoPendiente | null>(null);
  protected readonly procesandoVinculo = signal(false);
  protected readonly mensaje = signal('');

  ngOnInit(): void {
    this.cargar();
  }

  protected esLaMia(t: TarjetaMiembroResponse): boolean {
    return t.id === this.auth.usuarioActual()?.id;
  }

  protected confirmarVinculo(): void {
    this.procesandoVinculo.set(true);
    this.perfilService.aceptarPareja().subscribe({
      next: () => this.trasResponderVinculo(),
      error: () => this.trasResponderVinculo('No se pudo confirmar el vínculo. Recargo la lista.'),
    });
  }

  protected rechazarVinculo(): void {
    this.procesandoVinculo.set(true);
    this.perfilService.rechazarPareja().subscribe({
      next: () => this.trasResponderVinculo(),
      error: () => this.trasResponderVinculo('No se pudo rechazar el vínculo. Recargo la lista.'),
    });
  }

  private trasResponderVinculo(mensaje = ''): void {
    this.mensaje.set(mensaje);
    this.procesandoVinculo.set(false);
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    forkJoin({
      tarjetas: this.perfilService.miembros(),
      perfil: this.perfilService.miPerfil(),
    }).subscribe({
      next: ({ tarjetas, perfil }) => {
        this.tarjetas.set(tarjetas);
        this.vinculoPendiente.set(perfil.vinculoPendiente);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }
}
