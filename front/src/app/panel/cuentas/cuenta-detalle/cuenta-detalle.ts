import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { CuentaResumen } from '../cuentas.types';
import { CuentasService } from '../cuentas.service';

/**
 * Vista de una cuenta. De momento solo el nombre y la descripción: los
 * movimientos (ingresos, gastos y balance) son una tarea posterior, así que
 * abajo hay un aviso de "sección en construcción".
 */
@Component({
  selector: 'app-cuenta-detalle',
  imports: [Volver],
  templateUrl: './cuenta-detalle.html',
  styleUrl: './cuenta-detalle.css',
})
export class CuentaDetalleComponent implements OnInit {
  private readonly cuentasService = inject(CuentasService);
  private readonly route = inject(ActivatedRoute);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly cuenta = signal<CuentaResumen | null>(null);
  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.cuentasService.detalle(this.id).subscribe({
      next: (c) => {
        this.cuenta.set(c);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }
}
