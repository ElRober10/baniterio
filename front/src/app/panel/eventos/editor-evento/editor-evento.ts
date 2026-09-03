import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Volver } from '../../../shared/volver/volver';
import { CodigoErrorEvento, GuardarEventoRequest } from '../eventos.types';
import { EventosService } from '../eventos.service';

const MENSAJES: Partial<Record<CodigoErrorEvento, string>> = {
  SIN_CREDITO_EVENTO: 'No tienes ningún evento autorizado sin crear.',
  SIN_PERMISO_EVENTO: 'No puedes editar este evento.',
  EVENTO_NO_ENCONTRADO: 'Ese evento ya no existe.',
};

/**
 * Editor de evento. Sin `id` en la ruta → crear (`POST`); con `id` → editar
 * (`PUT`, precarga con `GET /eventos/:id`). Campos: nombre, descripción, lugar,
 * fecha (obligatoria), fecha fin (opcional). La validación de "fecha fin no
 * anterior a fecha" también la hace el backend.
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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly idParam = this.route.snapshot.paramMap.get('id');
  protected readonly editando = this.idParam !== null;
  protected readonly guardando = signal(false);
  protected readonly error = signal('');

  protected readonly form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(120)]],
    descripcion: ['', Validators.maxLength(2000)],
    lugar: ['', Validators.maxLength(160)],
    fecha: ['', Validators.required],
    fechaFin: [''],
  });

  ngOnInit(): void {
    if (this.editando) {
      this.eventosService.detalle(Number(this.idParam)).subscribe({
        next: (e) =>
          this.form.patchValue({
            nombre: e.nombre,
            descripcion: e.descripcion ?? '',
            lugar: e.lugar ?? '',
            fecha: e.fecha,
            fechaFin: e.fechaFin ?? '',
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
    const body: GuardarEventoRequest = {
      nombre: v.nombre.trim(),
      descripcion: v.descripcion.trim() || null,
      lugar: v.lugar.trim() || null,
      fecha: v.fecha,
      fechaFin: v.fechaFin || null,
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
