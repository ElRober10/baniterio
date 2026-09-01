import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AvatarResumen } from '../perfil.types';
import { SelectorAvatar } from './selector-avatar';

describe('SelectorAvatar', () => {
  let fixture: ComponentFixture<SelectorAvatar>;

  const catalogo: AvatarResumen[] = [
    { id: '01_chica', genero: 'CHICA' },
    { id: '01_chico', genero: 'CHICO' },
    { id: '02_chico', genero: 'CHICO' },
  ];

  function crear(avatares = catalogo, seleccionado: string | null = null): void {
    fixture = TestBed.createComponent(SelectorAvatar);
    fixture.componentRef.setInput('avatares', avatares);
    fixture.componentRef.setInput('seleccionado', seleccionado);
    fixture.detectChanges();
  }

  function botonesAvatar(): HTMLButtonElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button[aria-pressed]'),
    ) as HTMLButtonElement[];
  }

  it('pinta los avatares recibidos, todos por defecto', () => {
    crear();
    expect(botonesAvatar().length).toBe(3);
  });

  it('el filtro "Chicos" solo deja los de género CHICO', () => {
    crear();
    (
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) =>
        (b.textContent ?? '').trim().includes('Chicos'),
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(botonesAvatar().length).toBe(2);
  });

  it('el filtro "Chicas" solo deja los de género CHICA', () => {
    crear();
    (
      Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((b) =>
        (b.textContent ?? '').trim().includes('Chicas'),
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    expect(botonesAvatar().length).toBe(1);
  });

  it('click en un avatar emite su id', () => {
    crear();
    let emitido: string | undefined;
    fixture.componentInstance.elegido.subscribe((id) => (emitido = id));

    botonesAvatar()[1].click();

    expect(emitido).toBe('01_chico');
  });

  it('el avatar seleccionado se marca con aria-pressed', () => {
    crear(catalogo, '02_chico');

    const marcado = botonesAvatar().find((b) => b.getAttribute('aria-pressed') === 'true');
    expect(marcado).toBeTruthy();
    expect(botonesAvatar().filter((b) => b.getAttribute('aria-pressed') === 'true').length).toBe(1);
  });
});
