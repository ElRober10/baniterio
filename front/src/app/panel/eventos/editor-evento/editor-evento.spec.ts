import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../auth/auth.service';
import { UsuarioDto } from '../../../auth/auth.types';
import { environment } from '../../../../environments/environment';
import { EditorEvento } from './editor-evento';

/**
 * Tests del editor de evento: modo crear (ruta sin id) hace POST; modo editar
 * (ruta con id) precarga con GET y luego hace PUT. El editor pide la lista de
 * cuentas y, si el usuario es admin, muestra el campo "Cuota máxima".
 */
describe('EditorEvento', () => {
  let fixture: ComponentFixture<EditorEvento>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;

  const CUENTAS = [
    { id: 1, nombre: 'Chuletas Santas', descripcion: null },
    { id: 2, nombre: 'San Miguel', descripcion: null },
  ];

  function usuario(rol: 'ADMIN' | 'MIEMBRO'): UsuarioDto {
    return {
      id: 1,
      nombre: 'Ada',
      apellidos: 'Lovelace',
      mote: null,
      esSuperadmin: false,
      rol,
      areas: [],
    };
  }

  function crear(id: string | null, rol: 'ADMIN' | 'MIEMBRO' = 'ADMIN') {
    TestBed.configureTestingModule({
      imports: [EditorEvento],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { asegurarYo: () => of(usuario(rol)) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } },
        },
      ],
    });
    fixture = TestBed.createComponent(EditorEvento);
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  function responderCuentas() {
    httpMock.expectOne(`${base}/cuentas`).flush(CUENTAS);
    fixture.detectChanges();
  }

  function escribir(control: string, valor: string) {
    const el = (fixture.nativeElement as HTMLElement).querySelector(
      `[formControlName="${control}"]`,
    ) as HTMLInputElement | HTMLSelectElement;
    el.value = valor;
    el.dispatchEvent(new Event('input'));
    el.dispatchEvent(new Event('change'));
  }

  it('modo crear: al enviar hace POST a /eventos con el cuerpo (cuenta existente)', () => {
    crear(null);
    fixture.detectChanges();
    responderCuentas();
    escribir('nombre', 'Cena de Navidad');
    escribir('fecha', '2027-12-24');
    escribir('cuenta', '2');
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = httpMock.expectOne(`${base}/eventos`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      nombre: 'Cena de Navidad',
      descripcion: null,
      lugar: null,
      fecha: '2027-12-24',
      fechaFin: null,
      cuentaId: 2,
      cuentaNueva: false,
      cuotaMaxima: null,
    });
    req.flush({ id: 3 });
  });

  it('admin: al rellenar "Cuota máxima" la manda en el cuerpo', () => {
    crear(null, 'ADMIN');
    fixture.detectChanges();
    responderCuentas();
    escribir('nombre', 'San Miguel 2028');
    escribir('fecha', '2028-09-25');
    escribir('cuenta', '2');
    escribir('cuotaMaxima', '26');
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = httpMock.expectOne(`${base}/eventos`);
    expect(req.request.body.cuotaMaxima).toBe(26);
    req.flush({ id: 5 });
  });

  it('miembro: no aparece el campo "Cuota máxima"', () => {
    crear(null, 'MIEMBRO');
    fixture.detectChanges();
    responderCuentas();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[formControlName="cuotaMaxima"]'),
    ).toBeNull();
  });

  it('modo crear: con "Otro evento" envía cuentaNueva', () => {
    crear(null);
    fixture.detectChanges();
    responderCuentas();
    escribir('nombre', 'Torneo de mus');
    escribir('fecha', '2027-05-01');
    escribir('cuenta', '__nueva__');
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = httpMock.expectOne(`${base}/eventos`);
    expect(req.request.body.cuentaNueva).toBe(true);
    expect(req.request.body.cuentaId).toBeNull();
    req.flush({ id: 4 });
  });

  it('modo editar: precarga cuenta y cuota, y al enviar hace PUT', () => {
    crear('7');
    fixture.detectChanges();
    responderCuentas();
    httpMock.expectOne(`${base}/eventos/7`).flush({
      id: 7,
      nombre: 'San Miguel',
      descripcion: null,
      lugar: 'La plaza',
      fecha: '2026-09-25',
      fechaFin: '2026-09-26',
      pasado: false,
      cuenta: { id: 2, nombre: 'San Miguel' },
      cuotaMaxima: 26,
      creadoPor: null,
      puedoEditar: true,
      puedoBorrar: true,
      borradoPendiente: false,
    });
    fixture.detectChanges();

    escribir('nombre', 'San Miguel 2026');
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));

    const put = httpMock.expectOne(`${base}/eventos/7`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.nombre).toBe('San Miguel 2026');
    expect(put.request.body.cuentaId).toBe(2);
    expect(put.request.body.cuotaMaxima).toBe(26);
    put.flush({ id: 7 });
  });

  it('muestra un mensaje si el backend responde CUENTA_YA_EXISTE', () => {
    crear(null);
    fixture.detectChanges();
    responderCuentas();
    escribir('nombre', 'San Miguel');
    escribir('fecha', '2027-01-01');
    escribir('cuenta', '__nueva__');
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector('form')!.dispatchEvent(new Event('submit'));

    httpMock
      .expectOne(`${base}/eventos`)
      .flush({ codigo: 'CUENTA_YA_EXISTE' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Ya existe una cuenta con ese nombre',
    );
  });
});
