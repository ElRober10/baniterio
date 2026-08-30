import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { AdminIndice } from './indice';

/**
 * Tests de `AdminIndice`: el índice pinta como tarjetas solo las secciones para
 * las que el usuario tiene el área concedida; sin ninguna, muestra el aviso.
 */
describe('AdminIndice · tarjetas por área', () => {
  let areas: string[] = [];

  const authFalso: Partial<AuthService> = {
    tieneArea: (area: string) => areas.includes(area),
  };

  beforeEach(() => {
    areas = [];
    TestBed.configureTestingModule({
      imports: [AdminIndice],
      providers: [provideRouter([]), { provide: AuthService, useValue: authFalso }],
    });
  });

  function render(areasConcedidas: string[]): string {
    areas = areasConcedidas;
    const fixture = TestBed.createComponent(AdminIndice);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('sin áreas: muestra el aviso', () => {
    expect(render([])).toContain('No tienes ninguna sección de administración disponible.');
  });

  it('con ADMIN_SOLICITUDES: tarjeta "Solicitudes" presente, "Permisos" ausente', () => {
    const texto = render(['ADMIN_SOLICITUDES']);
    expect(texto).toContain('Solicitudes');
    expect(texto).not.toContain('Permisos');
  });

  it('con las dos áreas: las dos tarjetas presentes', () => {
    const texto = render(['ADMIN_SOLICITUDES', 'ADMIN_PERMISOS']);
    expect(texto).toContain('Solicitudes');
    expect(texto).toContain('Permisos');
  });
});
