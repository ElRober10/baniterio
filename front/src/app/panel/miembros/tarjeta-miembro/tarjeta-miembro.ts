import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { urlMedia } from '../perfil.service';
import { TarjetaMiembroResponse } from '../perfil.types';

/**
 * Tarjeta de un miembro en la sección Miembros. Presentacional: recibe la
 * tarjeta ya montada por el backend (sin teléfono ni email) y si es la del
 * propio usuario (`esLaMia`), en cuyo caso muestra el botón "Editar" que
 * lleva a `/panel/miembros/editar`.
 */
@Component({
  selector: 'app-tarjeta-miembro',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './tarjeta-miembro.html',
  styleUrl: './tarjeta-miembro.css',
  host: { class: 'block h-full' },
})
export class TarjetaMiembro {
  readonly tarjeta = input.required<TarjetaMiembroResponse>();
  readonly esLaMia = input(false);

  protected readonly urlImagen = computed(() => urlMedia(this.tarjeta().imagenUrl));

  /** Iniciales para el hueco de la imagen cuando el miembro no tiene foto ni avatar. */
  protected readonly iniciales = computed(() => {
    const t = this.tarjeta();
    return ((t.nombre[0] ?? '') + (t.apellidos[0] ?? '')).toUpperCase();
  });
}
