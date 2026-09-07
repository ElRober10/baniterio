import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { Volver } from '../../shared/volver/volver';
import { MetodoPago, PagoDeclaradoPendiente } from '../../panel/eventos/eventos.types';
import { EventosService } from '../../panel/eventos/eventos.service';

const METODO_TEXTO: Record<MetodoPago, string> = {
  BIZUM: 'Bizum',
  TRANSFERENCIA: 'Transferencia',
  EFECTIVO: 'Efectivo',
};

/**
 * Cola de "Confirmar pagos": las declaraciones de pago que la gente ha hecho desde
 * el detalle del evento y esperan a que un administrador confirme que el dinero ha
 * llegado. Solo admin/superadmin (rebota a `/panel` si no lo eres). Confirmar →
 * marca las cuotas cubiertas como pagadas; rechazar → avisa al declarante.
 */
@Component({
  selector: 'app-admin-pagos',
  imports: [Volver, DatePipe],
  templateUrl: './pagos.html',
})
export class AdminPagos implements OnInit {
  private readonly eventos = inject(EventosService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly metodoTexto = METODO_TEXTO;

  protected readonly esAdmin = computed(() => {
    const u = this.auth.usuarioActual();
    return u?.rol === 'ADMIN' || u?.esSuperadmin === true;
  });

  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly pendientes = signal<PagoDeclaradoPendiente[]>([]);
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
    this.eventos.pagosDeclaradosPendientes().subscribe({
      next: (l) => {
        this.pendientes.set(l);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected confirmar(p: PagoDeclaradoPendiente): void {
    this.eventos.confirmarPagoDeclarado(p.id).subscribe({
      next: () => {
        this.pendientes.update((l) => l.filter((x) => x.id !== p.id));
        this.aviso.set(`Pago de ${p.declaradoPor} confirmado.`);
      },
      error: () => this.aviso.set('No se pudo confirmar.'),
    });
  }

  protected rechazar(p: PagoDeclaradoPendiente): void {
    this.eventos.rechazarPagoDeclarado(p.id).subscribe({
      next: () => {
        this.pendientes.update((l) => l.filter((x) => x.id !== p.id));
        this.aviso.set(`Pago de ${p.declaradoPor} rechazado.`);
      },
      error: () => this.aviso.set('No se pudo rechazar.'),
    });
  }

  protected cubiertos(p: PagoDeclaradoPendiente): string {
    return p.cubre.map((c) => c.nombre).join(', ');
  }
}
