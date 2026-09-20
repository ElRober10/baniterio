import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { PrecioBebidaService } from '../precio-bebida.service';
import { Tienda } from '../precio-bebida.types';

const ETIQUETAS: Record<string, string> = {
  REFRESCOS: 'Refrescos',
  CERVEZA: 'Cerveza',
  LIMPIEZA: 'Limpieza y utensilios',
  COMIDA: 'Comida',
};

/**
 * Rejilla de precios de una sección sin tamaños (refrescos, cerveza, limpieza
 * y utensilios, comida), `/panel/precio-bebidas/:anio/:id/articulos/:categoria`.
 * Filas = artículos, columnas = tiendas, un precio por celda. Solo edita quien
 * puede (`puedoEditar`). Misma idea que la rejilla de alcohol pero sin
 * pestañas de tamaño: aquí cada artículo se compra en un único formato.
 */
@Component({
  selector: 'app-precio-articulo',
  imports: [Volver],
  templateUrl: './precio-articulo.html',
})
export class PrecioArticulo implements OnInit {
  private readonly service = inject(PrecioBebidaService);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly anio = Number(this.ruta.snapshot.paramMap.get('anio'));
  protected readonly eventoId = Number(this.ruta.snapshot.paramMap.get('id'));
  protected readonly categoria = (this.ruta.snapshot.paramMap.get('categoria') ?? '').toUpperCase();
  protected readonly etiqueta = ETIQUETAS[this.categoria] ?? this.categoria;

  protected readonly estado = signal<'cargando' | 'listo' | 'error'>('cargando');
  protected readonly puedoEditar = signal(false);
  protected readonly tiendas = signal<Tienda[]>([]);
  protected readonly articulos = signal<string[]>([]);
  protected readonly precios = signal<{ nombreArticulo: string; tiendaId: number; precio: number | null }[]>([]);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.estado.set('cargando');
    this.service.articulos(this.eventoId, this.categoria).subscribe({
      next: (g) => {
        this.puedoEditar.set(g.puedoEditar);
        this.tiendas.set(g.tiendas);
        this.articulos.set(g.articulos);
        this.precios.set(g.precios);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected precioDe(nombreArticulo: string, tiendaId: number): number | null {
    const celda = this.precios().find((p) => p.nombreArticulo === nombreArticulo && p.tiendaId === tiendaId);
    return celda ? celda.precio : null;
  }

  protected guardarPrecio(nombreArticulo: string, tiendaId: number, valor: string): void {
    const precio = valor.trim() === '' ? null : Number(valor.replace(',', '.'));
    if (precio !== null && (Number.isNaN(precio) || precio < 0)) {
      return;
    }
    this.service.guardarPrecioArticulo(this.eventoId, this.categoria, { nombreArticulo, tiendaId, precio }).subscribe({
      next: () => {
        this.precios.update((lista) => {
          const resto = lista.filter((p) => !(p.nombreArticulo === nombreArticulo && p.tiendaId === tiendaId));
          return precio === null ? resto : [...resto, { nombreArticulo, tiendaId, precio }];
        });
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

  protected trackTienda = (_: number, t: Tienda) => t.id;
  protected trackArticulo = (_: number, a: string) => a;
}
