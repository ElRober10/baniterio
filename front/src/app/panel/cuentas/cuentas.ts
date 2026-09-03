import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CuentaResumen } from './cuentas.types';
import { CuentasService } from './cuentas.service';

/**
 * Listado de las cuentas de la peña. Cada cuenta es un botón que lleva a su
 * detalle (`/panel/cuentas/:id`). Las cuentas viven en BBDD (una por evento
 * recurrente: Chuletas Santas, Migas Santas, San Miguel) y se pueden crear
 * más; el CRUD y los movimientos llegan en tareas siguientes.
 */
@Component({
  selector: 'app-cuentas',
  templateUrl: './cuentas.html',
  styleUrl: './cuentas.css',
})
export class Cuentas implements OnInit {
  private readonly cuentasService = inject(CuentasService);
  private readonly router = inject(Router);

  protected readonly estado = signal<'cargando' | 'lista' | 'error'>('cargando');
  protected readonly cuentas = signal<CuentaResumen[]>([]);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.cuentasService.listar().subscribe({
      next: (cuentas) => {
        this.cuentas.set(cuentas);
        this.estado.set('lista');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected abrir(c: CuentaResumen): void {
    this.router.navigate(['/panel/cuentas', c.id]);
  }
}
