import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { Volver } from '../../shared/volver/volver';
import { BebidaPendiente } from '../../panel/eventos/eventos.types';
import { EventosService } from '../../panel/eventos/eventos.service';

/**
 * Pantalla de administración de bebidas propuestas con "Otra…" en la ficha de
 * San Miguel. Cualquier admin/superadmin (no un área del panel): si el usuario
 * no lo es, se le devuelve a `/panel`. Aceptar → la bebida sale ya en los
 * desplegables; rechazar → se avisa a quien la propuso.
 */
@Component({
  selector: 'app-admin-bebidas',
  imports: [Volver],
  templateUrl: './bebidas.html',
})
export class AdminBebidas implements OnInit {
  private readonly eventos = inject(EventosService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly esAdmin = computed(() => {
    const u = this.auth.usuarioActual();
    return u?.rol === 'ADMIN' || u?.esSuperadmin === true;
  });

  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly pendientes = signal<BebidaPendiente[]>([]);
  protected readonly aviso = signal('');

  ngOnInit(): void {
    this.auth.asegurarYo().subscribe(() => {
      if (!this.esAdmin()) {
        this.router.navigateByUrl('/panel');
        return;
      }
      this.cargar();
    });
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.eventos.bebidasPendientes().subscribe({
      next: (l) => {
        this.pendientes.set(l);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected aceptar(b: BebidaPendiente): void {
    this.eventos.aceptarBebida(b.id).subscribe({
      next: () => {
        this.pendientes.update((l) => l.filter((x) => x.id !== b.id));
        this.aviso.set(`«${b.nombre}» aceptada.`);
      },
      error: () => this.aviso.set('No se pudo aceptar.'),
    });
  }

  protected rechazar(b: BebidaPendiente): void {
    this.eventos.rechazarBebida(b.id).subscribe({
      next: () => {
        this.pendientes.update((l) => l.filter((x) => x.id !== b.id));
        this.aviso.set(`«${b.nombre}» rechazada.`);
      },
      error: () => this.aviso.set('No se pudo rechazar.'),
    });
  }

  protected tipoTexto(t: string): string {
    return t === 'ALCOHOL' ? 'alcohol' : 'refresco';
  }
}
