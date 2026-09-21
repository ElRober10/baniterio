import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { PrecioBebidaService } from '../precio-bebida.service';
import { Tienda } from '../precio-bebida.types';

/** Tamaños de botella de refresco; el primero es el de por defecto. */
const TAMANOS_LITROS = [2, 1.5, 1];

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

  protected readonly tamanosLitros = TAMANOS_LITROS;
  protected readonly porKilo = signal<{ nombreArticulo: string; precioKilo: number | null; pesoKg: number | null }[]>([]);
  protected readonly tamanos = signal<{ nombreArticulo: string; litros: number }[]>([]);

  /** Los artículos que se comparan por tiendas: todos menos los que se compran por kilo en Jamones Duriber. */
  protected readonly articulosTienda = computed(() => {
    const porKilo = new Set(this.porKilo().map((k) => k.nombreArticulo));
    return this.articulos().filter((a) => !porKilo.has(a));
  });

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
        this.tamanos.set(g.tamanos ?? []);
        this.porKilo.set(g.porKilo ?? []);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  protected precioDe(nombreArticulo: string, tiendaId: number): number | null {
    const celda = this.precios().find((p) => p.nombreArticulo === nombreArticulo && p.tiendaId === tiendaId);
    return celda ? celda.precio : null;
  }

  /** El tamaño de botella se apunta en todos los refrescos y en el tinto de verano (que va en cerveza). */
  protected llevaTamano(nombreArticulo: string): boolean {
    return this.categoria === 'REFRESCOS' || (this.categoria === 'CERVEZA' && nombreArticulo === 'Tinto de verano');
  }

  protected kiloDe(nombreArticulo: string): { precioKilo: number | null; pesoKg: number | null } | undefined {
    return this.porKilo().find((k) => k.nombreArticulo === nombreArticulo);
  }

  /** Precio de la pieza = precio/kg x peso estimado, o null si falta alguno. */
  protected precioPieza(nombreArticulo: string): number | null {
    const k = this.kiloDe(nombreArticulo);
    return k?.precioKilo != null && k.pesoKg != null ? Math.round(k.precioKilo * k.pesoKg * 100) / 100 : null;
  }

  protected guardarKilo(nombreArticulo: string, campo: 'precioKilo' | 'pesoKg', valor: string): void {
    const numero = Number(valor.replace(',', '.'));
    if (valor.trim() === '' || Number.isNaN(numero) || numero < 0) {
      return;
    }
    const actual = this.kiloDe(nombreArticulo);
    const nuevo = {
      nombreArticulo,
      precioKilo: actual?.precioKilo ?? null,
      pesoKg: actual?.pesoKg ?? null,
      [campo]: numero,
    };
    this.porKilo.update((lista) => lista.map((k) => (k.nombreArticulo === nombreArticulo ? nuevo : k)));
    // Se guarda cuando ya están los dos datos; hasta entonces solo se recuerda en pantalla.
    if (nuevo.precioKilo !== null && nuevo.pesoKg !== null) {
      this.service
        .guardarProductoKilo(this.eventoId, {
          nombreArticulo,
          precioKilo: nuevo.precioKilo,
          pesoKg: nuevo.pesoKg,
        })
        .subscribe({ error: () => this.estado.set('error') });
    }
  }

  protected tamanoDe(nombreArticulo: string): number {
    return this.tamanos().find((t) => t.nombreArticulo === nombreArticulo)?.litros ?? TAMANOS_LITROS[0];
  }

  protected guardarTamano(nombreArticulo: string, valor: string): void {
    const litros = Number(valor);
    if (!TAMANOS_LITROS.includes(litros)) {
      return;
    }
    this.service.guardarTamanoArticulo(this.eventoId, this.categoria, { nombreArticulo, litros }).subscribe({
      next: () =>
        this.tamanos.update((lista) => [...lista.filter((t) => t.nombreArticulo !== nombreArticulo), { nombreArticulo, litros }]),
      error: () => this.estado.set('error'),
    });
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
