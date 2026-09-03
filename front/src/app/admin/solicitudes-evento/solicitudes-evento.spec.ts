import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { SolicitudesEvento } from './solicitudes-evento';

describe('SolicitudesEvento (bloque de administración)', () => {
  let fixture: ComponentFixture<SolicitudesEvento>;
  let httpMock: HttpTestingController;
  const base = environment.apiBaseUrl;
  const listaUrl = `${base}/admin/solicitudes-evento?estado=PENDIENTE`;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SolicitudesEvento],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(SolicitudesEvento);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function responder() {
    httpMock.expectOne(listaUrl).flush([
      {
        id: 1,
        tipo: 'CREAR',
        estado: 'PENDIENTE',
        solicitante: { id: 9, nombre: 'Ana', apellidos: 'Ruiz' },
        evento: null,
        mensaje: 'La cena de Navidad',
        createdAt: '2026-09-03T10:00:00Z',
      },
    ]);
  }

  it('carga la lista y pinta el tipo y el solicitante', () => {
    fixture.detectChanges();
    responder();
    fixture.detectChanges();
    const txt = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(txt).toContain('Crear evento');
    expect(txt).toContain('Ana Ruiz');
  });

  it('"Aprobar" hace POST a .../aprobar y recarga', () => {
    fixture.detectChanges();
    responder();
    fixture.detectChanges();

    const aprobar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'Aprobar');
    aprobar?.dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${base}/admin/solicitudes-evento/1/aprobar`);
    expect(post.request.method).toBe('POST');
    post.flush(null, { status: 204, statusText: 'No Content' });
    httpMock.expectOne(listaUrl).flush([]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Crédito concedido');
  });

  it('"Rechazar" abre el textarea y "Confirmar" manda el motivo', () => {
    fixture.detectChanges();
    responder();
    fixture.detectChanges();

    const rechazar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'Rechazar');
    rechazar?.dispatchEvent(new Event('click'));
    fixture.detectChanges();

    const area = (fixture.nativeElement as HTMLElement).querySelector('textarea') as HTMLTextAreaElement;
    area.value = 'demasiado tarde';
    area.dispatchEvent(new Event('input'));

    const confirmar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.trim() === 'Confirmar rechazo');
    confirmar?.dispatchEvent(new Event('click'));

    const post = httpMock.expectOne(`${base}/admin/solicitudes-evento/1/rechazar`);
    expect(post.request.body).toEqual({ motivo: 'demasiado tarde' });
    post.flush(null, { status: 204, statusText: 'No Content' });
    httpMock.expectOne(listaUrl).flush([]);
  });
});
