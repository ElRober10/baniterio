import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { CuentaDetalle } from '../cuentas.types';
import { CuentasService } from '../cuentas.service';

/**
 * Vista de una cuenta: saldo, estimación (lo que habrá cuando todos paguen) y el
 * libro de movimientos con su saldo corriente, al estilo del Excel del tesorero.
 * Si quien mira es admin y hay dinero cobrado sin ingresar, sale el botón "He
 * transferido el dinero a la peña".
 */
@Component({
  selector: 'app-cuenta-detalle',
  imports: [Volver, DatePipe],
  templateUrl: './cuenta-detalle.html',
  styleUrl: './cuenta-detalle.css',
})
export class CuentaDetalleComponent implements OnInit {
  private readonly cuentasService = inject(CuentasService);
  private readonly route = inject(ActivatedRoute);

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly cuenta = signal<CuentaDetalle | null>(null);
  protected readonly modalTransferir = signal(false);
  protected readonly enviando = signal(false);
  protected readonly aviso = signal<string | null>(null);
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

  protected confirmarTransferencia(): void {
    this.enviando.set(true);
    this.cuentasService.marcarTransferido(this.id).subscribe({
      next: (c) => {
        this.cuenta.set(c);
        this.modalTransferir.set(false);
        this.enviando.set(false);
        this.aviso.set('Hecho, el dinero consta ingresado en la cuenta de la peña.');
      },
      error: () => {
        this.enviando.set(false);
        this.aviso.set('No se ha podido registrar. Inténtalo de nuevo.');
      },
    });
  }
}
