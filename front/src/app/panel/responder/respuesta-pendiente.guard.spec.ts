import { TestBed } from '@angular/core/testing';
import { CanActivateFn, UrlTree } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of, throwError } from 'rxjs';
import { EventoResumen } from '../eventos/eventos.types';
import { EventosService } from '../eventos/eventos.service';
import { respuestaPendienteGuard } from './respuesta-pendiente.guard';

/**
 * Tests de respuestaPendienteGuard: con pendientes -> /panel/responder; sin
 * pendientes -> true; fallo de red -> true (no atrapa al usuario).
 */
describe('respuestaPendienteGuard', () => {
  let respuesta: Observable<{ eventos: EventoResumen[] }>;

  const eventosFalso: Partial<EventosService> = {
    pendientesRespuesta: () => respuesta,
  };

  function ejecutar(guard: CanActivateFn): Promise<boolean | UrlTree> {
    const resultado = TestBed.runInInjectionContext(() => guard({} as never, {} as never));
    if (isObservable(resultado)) {
      return firstValueFrom(resultado as Observable<boolean | UrlTree>);
    }
    return Promise.resolve(resultado as boolean | UrlTree);
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: EventosService, useValue: eventosFalso }],
    });
  });

  it('con eventos pendientes redirige a /panel/responder', async () => {
    respuesta = of({ eventos: [{ id: 1 } as EventoResumen] });
    const resultado = await ejecutar(respuestaPendienteGuard);
    expect(resultado).toBeInstanceOf(UrlTree);
    expect((resultado as UrlTree).toString()).toBe('/panel/responder');
  });

  it('sin pendientes deja pasar (true)', async () => {
    respuesta = of({ eventos: [] });
    const resultado = await ejecutar(respuestaPendienteGuard);
    expect(resultado).toBe(true);
  });

  it('un fallo al pedir los pendientes deja pasar (true)', async () => {
    respuesta = throwError(() => new Error('boom'));
    const resultado = await ejecutar(respuestaPendienteGuard);
    expect(resultado).toBe(true);
  });
});
