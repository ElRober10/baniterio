import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

type Estado = 'idle' | 'enviando' | 'ok' | 'no_autorizado' | 'error';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  selector: 'app-registro',
  styleUrl: './registro.css',
  templateUrl: './registro.html',
})
export class Registro {
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly estado = signal<Estado>('idle');

  protected readonly form = this.formBuilder.group({
    nombre: ['', [Validators.required]],
    apellidos: ['', [Validators.required]],
    mote: [''],
    telefono: ['', [Validators.required, Validators.pattern(/^[67]\d{8}$/)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.estado.set('enviando');
    const v = this.form.getRawValue();

    this.auth
      .registro({
        nombre: v.nombre!,
        apellidos: v.apellidos!,
        mote: v.mote ?? '',
        telefono: v.telefono!,
        email: v.email!,
        password: v.password!,
      })
      .subscribe({
        next: () => {
          this.estado.set('ok');
          setTimeout(() => this.router.navigateByUrl('/login'), 1200);
        },
        error: (err: HttpErrorResponse) => {
          this.estado.set(err.error?.codigo === 'TELEFONO_NO_AUTORIZADO' ? 'no_autorizado' : 'error');
        },
      });
  }
}
