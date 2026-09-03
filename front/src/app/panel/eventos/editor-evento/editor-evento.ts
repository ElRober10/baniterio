import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../auth/auth.service';
import { Volver } from '../../../shared/volver/volver';
import { CuentaResumen } from '../../cuentas/cuentas.types';
import { CuentasService } from '../../cuentas/cuentas.service';
import { CodigoErrorEvento, GuardarEventoRequest } from '../eventos.types';
import { EventosService } from '../eventos.service';

const MENSAJES: Partial<Record<CodigoErrorEvento, string>> = {
  SIN_CREDITO_EVENTO: 'No tienes ningún evento autorizado sin crear.',
  SIN_PERMISO_EVENTO: 'No puedes editar este evento.',
  EVENTO_NO_ENCONTRADO: 'Ese evento ya no existe.',
  CUENTA_YA_EXISTE: 'Ya existe una cuenta con ese nombre. Elígela de la lista.',
  CUENTA_NO_ENCONTRADA: 'Esa cuenta ya no existe.',
};

/** Valor del `<select>` de cuenta que significa "crear una cuenta nueva". */
const CUENTA_NUEVA = '__nueva__';

/**
 * Editor de evento. Sin `id` en la ruta → crear (`POST`); con `id` → editar
 * (`PUT`, precarga con `GET /eventos/:id`). Campos: nombre, descripción, lugar,
 * fecha (obligatoria), fecha fin (opcional) y cuenta (obligatoria). La cuota
 * máxima solo se ve/edita si el usuario es admin/superadmin (el backend también
 * lo comprueba). La validación de "fecha fin no anterior a fecha" la hace el
 * backend además del cliente.
 */
@Component({
  selector: 'app-editor-evento',
  imports: [ReactiveFormsModule, Volver],
  templateUrl: './editor-evento.html',
  styleUrl: './editor-evento.css',
})
export class EditorEvento implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly eventosService = inject(EventosService);
  private readonly cuentasService = inject(CuentasService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly CUENTA_NUEVA = CUENTA_NUEVA;
  protected readonly idParam = this.route.snapshot.paramMap.get('id');
  protected readonly editando = this.idParam !== null;
  protected readonly guardando = signal(false);
  protected readonly error = signal('');
  protected readonly cuentas = signal<CuentaResumen[]>([]);
  protected readonly esAdmin = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    descripcion: ['', Validators.maxLength(2000)],
    lugar: ['', Validators.maxLength(160)],
    fecha: ['', Validators.required],
    fechaFin: [''],
    cuenta: ['', Validators.required],
    cuotaMaxima: [''],
  });

  ngOnInit(): void {
    this.auth.asegurarYo().subscribe((u) =>
      this.esAdmin.set(u?.rol === 'ADMIN' || u?.esSuperadmin === true),
    );
    this.cuentasService.listar().subscribe({
      next: (cuentas) => this.cuentas.set(cuentas),
    });
    if (this.editando) {
      this.eventosService.detalle(Number(this.idParam)).subscribe({
        next: (e) =>
          this.form.patchValue({
            nombre: e.nombre,
            descripcion: e.descripcion ?? '',
            lugar: e.lugar ?? '',
            fecha: e.fecha,
            fechaFin: e.fechaFin ?? '',
            cuenta: String(e.cuenta.id),
            cuotaMaxima: e.cuotaMaxima != null ? String(e.cuotaMaxima) : '',
          }),
        error: () => this.error.set('No se ha podido cargar el evento.'),
      });
    }
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const cuentaNueva = v.cuenta === CUENTA_NUEVA;

    // El input es type="number": el value accessor puede dar number, string o null.
    const cuotaRaw = String(v.cuotaMaxima ?? '').trim();
    let cuotaMaxima: number | null = null;
    if (this.esAdmin() && cuotaRaw) {
      const n = Number(cuotaRaw.replace(',', '.'));
      if (Number.isNaN(n) || n < 0) {
        this.error.set('La cuota máxima tiene que ser un número mayor o igual que 0.');
        return;
      }
      cuotaMaxima = n;
    }

    const body: GuardarEventoRequest = {
      nombre: v.nombre.trim(),
      descripcion: v.descripcion.trim() || null,
      lugar: v.lugar.trim() || null,
      fecha: v.fecha,
      fechaFin: v.fechaFin || null,
      cuentaId: cuentaNueva ? null : Number(v.cuenta),
      cuentaNueva,
      cuotaMaxima,
    };
    if (body.fechaFin && body.fechaFin < body.fecha) {
      this.error.set('La fecha de fin no puede ser anterior a la de inicio.');
      return;
    }
    this.guardando.set(true);
    const peticion = this.editando
      ? this.eventosService.editar(Number(this.idParam), body)
      : this.eventosService.crear(body);
    peticion.subscribe({
      next: (e) => this.router.navigate(['/panel/eventos', e.id]),
      error: (err: HttpErrorResponse) => {
        this.guardando.set(false);
        const codigo = err.error?.codigo as CodigoErrorEvento | undefined;
        this.error.set((codigo && MENSAJES[codigo]) || 'No se pudo guardar el evento.');
      },
    });
  }
}
