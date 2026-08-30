import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Campanita con un número: "tienes N cosas sin atender". Presentacional puro —
 * el número se lo pasa quien lo usa (`[cuenta]`). Con `cuenta` 0 (o menos) no
 * pinta nada: el aviso solo existe cuando hay trabajo. Se usa en la tarjeta
 * "Administración" de la home del panel (con el total) y en cada tarjeta del
 * índice de administración (con el pendiente de esa área).
 */
@Component({
  selector: 'app-aviso-pendientes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (cuenta() > 0) {
      <span
        role="status"
        [attr.aria-label]="cuenta() + ' pendientes'"
        class="inline-flex items-center gap-1 rounded-full bg-brand/20 px-2 py-0.5 text-xs font-bold text-gold-soft"
      >
        <svg viewBox="0 0 16 16" class="h-3.5 w-3.5" fill="currentColor" aria-hidden="true">
          <path
            d="M8 1.5a.9.9 0 0 1 .9.9v.43a4.25 4.25 0 0 1 3.35 4.15v2.2l.86 1.44A.7.7 0 0 1 12.4 11.7H3.6a.7.7 0 0 1-.6-1.08l.86-1.44v-2.2A4.25 4.25 0 0 1 7.1 2.83V2.4a.9.9 0 0 1 .9-.9ZM6.6 12.7h2.8a1.4 1.4 0 0 1-2.8 0Z"
          />
        </svg>
        {{ mostrada() }}
      </span>
    }
  `,
})
export class AvisoPendientes {
  /** Nº de cosas sin atender. 0 o menos → no se pinta nada. */
  readonly cuenta = input.required<number>();

  /** Texto del globo: el número, o `99+` si se pasa. */
  protected readonly mostrada = computed(() =>
    this.cuenta() > 99 ? '99+' : String(this.cuenta()),
  );
}
