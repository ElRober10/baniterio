import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { urlMedia } from '../perfil.service';
import { AvatarResumen } from '../perfil.types';

type Filtro = 'TODOS' | 'CHICO' | 'CHICA';

/**
 * Rejilla del catálogo de avatares, con filtro Todos/Chicos/Chicas (todos
 * visibles por defecto). Presentacional: recibe el catálogo y el id ya
 * elegido (si hay), y emite el id al hacer click en uno.
 */
@Component({
  selector: 'app-selector-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './selector-avatar.html',
  styleUrl: './selector-avatar.css',
})
export class SelectorAvatar {
  readonly avatares = input.required<AvatarResumen[]>();
  readonly seleccionado = input<string | null>(null);
  readonly elegido = output<string>();

  protected readonly filtro = signal<Filtro>('TODOS');

  protected readonly visibles = computed(() => {
    const f = this.filtro();
    return f === 'TODOS' ? this.avatares() : this.avatares().filter((a) => a.genero === f);
  });

  protected urlAvatar(id: string): string | null {
    return urlMedia(`/api/v1/media/avatares/${id}.png`);
  }

  protected ponerFiltro(f: Filtro): void {
    this.filtro.set(f);
  }
}
