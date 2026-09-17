import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PrecioBebidaService } from './precio-bebida.service';

const base = environment.apiBaseUrl;

describe('PrecioBebidaService', () => {
  let service: PrecioBebidaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PrecioBebidaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('eventos() hace GET a /precio-bebida/eventos', () => {
    service.eventos().subscribe();
    const req = http.expectOne(`${base}/precio-bebida/eventos`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
