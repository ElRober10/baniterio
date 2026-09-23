import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { Volver } from '../../shared/volver/volver';
import { AdminService } from '../admin.service';
import { LogEvento } from '../admin.types';

/**
 * Registro de eventos y errores: toda petición de escritura de la API y los
 * errores de móvil/web que nunca llegaron a golpear el backend. Cualquier
 * admin/superadmin (no un área del panel); si el usuario no lo es, se le
 * devuelve a `/panel`, igual que `AdminBebidas`/`AdminPagos`.
 */
@Component({
  selector: 'app-admin-logs',
  imports: [Volver, DatePipe],
  templateUrl: './logs.html',
})
export class AdminLogs implements OnInit {
  private readonly adminService = inject(AdminService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly esAdmin = computed(() => {
    const u = this.auth.usuarioActual();
    return u?.rol === 'ADMIN' || u?.esSuperadmin === true;
  });

  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly filas = signal<LogEvento[]>([]);
  protected readonly total = signal(0);
  protected readonly pagina = signal(0);
  protected readonly tamano = 50;

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
    this.adminService.listarLogs({ pagina: this.pagina(), tamano: this.tamano }).subscribe({
      next: (p) => {
        this.filas.set(p.contenido);
        this.total.set(p.total);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected anterior(): void {
    if (this.pagina() === 0) return;
    this.pagina.update((p) => p - 1);
    this.cargar();
  }

  protected siguiente(): void {
    if ((this.pagina() + 1) * this.tamano >= this.total()) return;
    this.pagina.update((p) => p + 1);
    this.cargar();
  }
}
