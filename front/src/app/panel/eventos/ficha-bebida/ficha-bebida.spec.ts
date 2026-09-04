import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CatalogoBebidas, FichaBebidaBody } from '../eventos.types';
import { FichaBebida } from './ficha-bebida';

/** Tests del formulario de ficha: días, embarazada, "Otra…" y el cuerpo que emite. */
describe('FichaBebida', () => {
  let fixture: ComponentFixture<FichaBebida>;
  let emitido: FichaBebidaBody | undefined;

  const catalogo: CatalogoBebidas = {
    alcohol: [{ id: 1, nombre: 'Barceló' }],
    refresco: [
      { id: 10, nombre: 'Coca-Cola' },
      { id: 11, nombre: 'Fanta Naranja' },
    ],
  };

  function crear(dias: string[]) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ imports: [FichaBebida] });
    fixture = TestBed.createComponent(FichaBebida);
    fixture.componentRef.setInput('catalogo', catalogo);
    fixture.componentRef.setInput('dias', dias);
    emitido = undefined;
    fixture.componentInstance.guardar.subscribe((b) => (emitido = b));
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;

  function elegir(selector: string, valor: string) {
    const s = el().querySelector(selector) as HTMLSelectElement;
    s.value = valor;
    s.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function enviar() {
    (el().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('con 2 días pinta el selector de día; con 1 no', () => {
    crear(['2026-09-25', '2026-09-26']);
    expect(el().textContent).toContain('¿Qué días vas?');
    crear(['2026-09-25']);
    expect(el().textContent).not.toContain('¿Qué días vas?');
  });

  it('marcar "Embarazada" oculta alcohol y alternativa', () => {
    crear(['2026-09-25', '2026-09-26']);
    expect(el().querySelector('#fb-alcohol')).toBeTruthy();
    const chk = el().querySelector('input[type="checkbox"]') as HTMLInputElement;
    chk.checked = true;
    chk.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(el().querySelector('#fb-alcohol')).toBeNull();
    expect(el().querySelector('#fb-alt')).toBeNull();
  });

  it('elegir "Otra…" en alcohol muestra el input y el cuerpo lleva alcoholOtra', () => {
    crear(['2026-09-25', '2026-09-26']);
    elegir('#fb-refresco', '10');
    elegir('#fb-alcohol', '__otra__');
    const inp = el().querySelector('#fb-alcohol + input') as HTMLInputElement;
    expect(inp).toBeTruthy();
    inp.value = 'Ron del abuelo';
    inp.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    enviar();
    expect(emitido?.alcoholOtra).toBe('Ron del abuelo');
    expect(emitido?.alcoholBebidaId).toBeNull();
  });

  it('emite el cuerpo con los ids elegidos', () => {
    crear(['2026-09-25', '2026-09-26']);
    elegir('#fb-alcohol', '1');
    elegir('#fb-refresco', '11');
    elegir('#fb-alt', 'CERVEZA');
    enviar();
    expect(emitido).toEqual({
      estado: 'APUNTADO',
      alcoholBebidaId: 1,
      alcoholOtra: null,
      refrescoBebidaId: 11,
      refrescoOtra: null,
      alternativa: 'CERVEZA',
      cervezaEspecial: null,
      embarazada: false,
      asisteDia1: true,
      asisteDia2: true,
    });
  });

  it('sin refresco no emite y muestra error', () => {
    crear(['2026-09-25']);
    enviar();
    expect(emitido).toBeUndefined();
    expect(el().textContent).toContain('Elige un refresco');
  });

  it('precarga fichaActual', () => {
    crear(['2026-09-25', '2026-09-26']);
    fixture.componentRef.setInput('fichaActual', {
      alcoholBebidaId: 1,
      alcohol: 'Barceló',
      refrescoBebidaId: 10,
      refresco: 'Coca-Cola',
      alternativa: 'CERVEZA',
      cervezaEspecial: null,
      embarazada: false,
      asisteDia1: true,
      asisteDia2: false,
      modalidad: 'UN_DIA',
      cuota: 14,
      cuotaPendiente: false,
      bebidaPendiente: false,
    });
    fixture.componentInstance.ngOnInit();
    fixture.detectChanges();
    enviar();
    expect(emitido?.alcoholBebidaId).toBe(1);
    expect(emitido?.asisteDia2).toBe(false);
  });
});
