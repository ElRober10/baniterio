import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { PrecioBebidaService } from '../precio-bebida.service';
import { BebidaRef, PrecioCelda, Tienda } from '../precio-bebida.types';

/**
 * Rejilla de precios de bebidas alcohólicas de un evento,
 * `/panel/precio-bebidas/:anio/:id/alcohol`. Filas = marcas del catálogo,
 * columnas = tiendas; una pestaña por tamaño (70 cl / 1 L de serie, más las
 * que añada el admin). Solo edita quien puede (`puedoEditar`).
 */
@Component({
  selector: 'app-precio-bebida-alcohol',
  imports: [Volver],
  templateUrl: './precio-bebida-alcohol.html',
})
export class PrecioBebidaAlcohol implements OnInit {
  private readonly service = inject(PrecioBebidaService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly anio = Number(this.ruta.snapshot.paramMap.get('anio'));
  protected readonly eventoId = Number(this.ruta.snapshot.paramMap.get('id'));

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly puedoEditar = signal(false);
  protected readonly tiendas = signal<Tienda[]>([]);
  protected readonly tamanos = signal<string[]>([]);
  protected readonly bebidas = signal<BebidaRef[]>([]);
  protected readonly precios = signal<PrecioCelda[]>([]);
  protected readonly tamanoActivo = signal('');
  protected readonly ocupado = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.service.alcohol(this.eventoId).subscribe({
      next: (g) => {
        this.puedoEditar.set(g.puedoEditar);
        this.tiendas.set(g.tiendas);
        this.tamanos.set(g.tamanos);
        this.bebidas.set(g.bebidas);
        this.precios.set(g.precios);
        if (!this.tamanos().includes(this.tamanoActivo())) {
          this.tamanoActivo.set(g.tamanos[0] ?? '');
        }
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected precioDe(bebidaId: number, tiendaId: number): number | null {
    const celda = this.precios().find(
      (p) => p.bebidaId === bebidaId && p.tiendaId === tiendaId && p.tamano === this.tamanoActivo(),
    );
    return celda ? celda.precio : null;
  }

  protected guardarPrecio(bebidaId: number, tiendaId: number, valor: string): void {
    const tamano = this.tamanoActivo();
    const precio = valor.trim() === '' ? null : Number(valor.replace(',', '.'));
    if (precio !== null && (Number.isNaN(precio) || precio < 0)) {
      return;
    }
    this.service.guardarPrecio(this.eventoId, { bebidaId, tamano, tiendaId, precio }).subscribe({
      next: () => {
        this.precios.update((lista) => {
          const resto = lista.filter(
            (p) => !(p.bebidaId === bebidaId && p.tiendaId === tiendaId && p.tamano === tamano),
          );
          return precio === null ? resto : [...resto, { bebidaId, tamano, tiendaId, precio }];
        });
      },
      error: () => this.estado.set('error'),
    });
  }

  protected anadirTamano(): void {
    const tamano = prompt('Nombre del tamaño (ej. "3 L"):');
    if (!tamano?.trim()) return;
    this.service.anadirTamano(this.eventoId, tamano.trim()).subscribe({
      next: (lista) => {
        this.tamanos.set(lista);
        this.tamanoActivo.set(tamano.trim());
      },
      error: () => this.estado.set('error'),
    });
  }

  protected anadirTienda(): void {
    const nombre = prompt('Nombre de la tienda:');
    if (!nombre?.trim()) return;
    this.service.crearTienda(nombre.trim()).subscribe({
      next: (t) => this.tiendas.update((lista) => [...lista, t]),
      error: () => this.estado.set('error'),
    });
  }

  protected trackTamano = (_: number, t: string) => t;
  protected trackTienda = (_: number, t: Tienda) => t.id;
  protected trackBebida = (_: number, b: BebidaRef) => b.id;
}
