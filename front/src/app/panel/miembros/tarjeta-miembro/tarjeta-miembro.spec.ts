import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TarjetaMiembroResponse } from '../perfil.types';
import { TarjetaMiembro } from './tarjeta-miembro';

describe('TarjetaMiembro', () => {
  let fixture: ComponentFixture<TarjetaMiembro>;

  const base: TarjetaMiembroResponse = {
    id: 7,
    nombre: 'Ada',
    apellidos: 'Lovelace',
    mote: 'Condesa',
    sobreMi: 'Escribo algoritmos.',
    imagenUrl: '/api/v1/media/avatares/01_chica.png',
    parejaNombre: 'Charles Babbage',
    hijos: ['Byron', 'Anne'],
  };

  function crear(tarjeta: TarjetaMiembroResponse, esLaMia = false): void {
    fixture = TestBed.createComponent(TarjetaMiembro);
    fixture.componentRef.setInput('tarjeta', tarjeta);
    fixture.componentRef.setInput('esLaMia', esLaMia);
    fixture.detectChanges();
  }

  function texto(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TarjetaMiembro],
      providers: [provideRouter([])],
    });
  });

  it('pinta nombre, apellidos, mote, sobre mí, pareja e hijos', () => {
    crear(base);
    expect(texto()).toContain('Ada Lovelace');
    expect(texto()).toContain('Condesa');
    expect(texto()).toContain('Escribo algoritmos.');
    expect(texto()).toContain('Charles Babbage');
    expect(texto()).toContain('Byron, Anne');
  });

  it('sin sobre mí, sin pareja y sin hijos: no pinta esos bloques', () => {
    crear({ ...base, mote: null, sobreMi: null, parejaNombre: null, hijos: [] });
    expect(texto()).not.toContain('Pareja:');
    expect(texto()).not.toContain('Hijos:');
    expect(texto()).not.toContain('«');
  });

  it('el botón "Editar" solo aparece si es la tarjeta propia', () => {
    crear(base, false);
    expect(texto()).not.toContain('Editar');

    crear(base, true);
    const enlace = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/panel/miembros/editar"]',
    );
    expect(enlace).toBeTruthy();
    expect((enlace?.textContent ?? '').trim()).toBe('Editar');
  });

  it('sin imagen, muestra las iniciales', () => {
    crear({ ...base, imagenUrl: null });
    expect((fixture.nativeElement as HTMLElement).querySelector('img')).toBeNull();
    expect(texto()).toContain('AL');
  });
});
