import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Volver } from './volver';

/**
 * Tests de `Volver`: pinta un enlace al `destino` indicado y usa "Volver" como
 * texto por defecto, sustituible con `etiqueta`.
 */
@Component({
  imports: [Volver],
  template: `<app-volver [destino]="destino" [etiqueta]="etiqueta" />`,
})
class Anfitrion {
  destino = '/panel';
  etiqueta = 'Volver';
}

describe('Volver', () => {
  function crear(): { enlace: HTMLAnchorElement; host: Anfitrion } {
    TestBed.configureTestingModule({
      imports: [Anfitrion],
      providers: [provideRouter([])],
    });
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return { enlace: el.querySelector('a')!, host: fixture.componentInstance };
  }

  it('enlaza al destino y usa "Volver" por defecto', () => {
    const { enlace } = crear();
    expect(enlace.getAttribute('href')).toBe('/panel');
    expect(enlace.textContent?.trim()).toContain('Volver');
  });
});
