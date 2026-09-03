import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Volver } from '../../../shared/volver/volver';
import { SelectorAvatar } from '../selector-avatar/selector-avatar';
import { PerfilService, urlMedia } from '../perfil.service';
import {
  AvatarResumen,
  CodigoErrorPerfil,
  GuardarPerfilRequest,
  HijoEnPerfil,
  HijoRequest,
  PerfilResponse,
} from '../perfil.types';

/** Mensajes en castellano para los códigos de error propios del editor. */
const MENSAJES: Partial<Record<CodigoErrorPerfil, string>> = {
  AVATAR_INEXISTENTE: 'Elige una foto o un avatar válido.',
  IMAGEN_REF_INVALIDA: 'Elige una foto o un avatar válido.',
  IMAGEN_NO_SOPORTADA: 'Ese archivo no es una foto válida (usa JPEG o PNG).',
  IMAGEN_DEMASIADO_GRANDE: 'La foto pesa demasiado.',
  TELEFONO_PAREJA_INVALIDO: 'Revisa el teléfono de tu pareja: no parece un móvil español.',
  NOMBRE_PAREJA_REQUERIDO: 'Escribe el nombre de tu pareja.',
  TELEFONO_YA_EMPAREJADO: 'Ese teléfono ya tiene pareja en la peña.',
  YA_TIENE_PAREJA: 'Ya tienes un vínculo de pareja activo.',
  TELEFONO_HIJO_INVALIDO: 'Revisa el teléfono de tu hijo: no parece un móvil español.',
  VALIDACION: 'Revisa los datos del formulario: hay algún campo incorrecto.',
};

/** El texto fijo que explica por qué pedimos el teléfono de la pareja/un hijo. */
export const AVISO_TELEFONO_FAMILIA =
  'Los teléfonos sirven para que un adulto pueda apuntar a toda la familia a un evento de una vez.';

/** Forma de una fila del FormArray de hijos (el FormGroup interno no está tipado). */
interface FilaHijo {
  id: number | null;
  nombre: string | null;
  mayorDeEdad: boolean | null;
  telefono: string | null;
  visible: boolean | null;
}

/**
 * Editor de perfil: datos, foto o avatar, "sobre mí", pareja e hijos. Un
 * mismo componente para dos casos, sin distinguirlos: el alta obligatoria del
 * primer login (a la que trae `perfilCompletoGuard`) y "Editar" desde la
 * propia tarjeta — los dos cargan `GET /perfil` y guardan con `PUT /perfil`.
 *
 * La imagen (avatar elegido / foto pendiente de subir) se lleva fuera del
 * `FormGroup` en signals propios: la foto no se sube hasta pulsar "Guardar"
 * (para no dejar ficheros huérfanos si el usuario no llega a guardar), así
 * que su `imagenRef` no existe todavía mientras se rellena el resto del
 * formulario — meterla como control de formulario obligaría a pelear con su
 * validez a destiempo.
 *
 * Ver el spec ("Landmines de backend") sobre por qué, con un vínculo de
 * pareja `ACEPTADO`, los campos de nombre/teléfono de la pareja se cargan
 * deshabilitados: cambiarlos en el `PUT` rompería el vínculo sin querer.
 */
@Component({
  selector: 'app-editor-perfil',
  imports: [ReactiveFormsModule, Volver, SelectorAvatar],
  templateUrl: './editor-perfil.html',
  styleUrl: './editor-perfil.css',
})
export class EditorPerfil implements OnInit, OnDestroy {
  private readonly formBuilder = inject(FormBuilder);
  private readonly perfilService = inject(PerfilService);
  private readonly router = inject(Router);

  protected readonly estado = signal<'cargando' | 'listo' | 'guardando' | 'error'>('cargando');
  protected readonly mensajeError = signal('');
  protected readonly intentoSinImagen = signal(false);
  protected readonly avisoTelefonoFamilia = AVISO_TELEFONO_FAMILIA;

  protected readonly avataresDisponibles = signal<AvatarResumen[]>([]);
  protected readonly modoImagen = signal<'FOTO' | 'AVATAR'>('AVATAR');
  protected readonly imagenRefAvatar = signal<string | null>(null);
  protected readonly imagenRefFotoActual = signal<string | null>(null);
  protected readonly fotoPendiente = signal<File | null>(null);
  protected readonly previsualizacionFoto = signal<string | null>(null);
  protected readonly parejaEstado = signal<string | null>(null);
  /** El diálogo con la rejilla de avatares (se abre desde el botón "Elegir avatar"). */
  protected readonly dialogoAvatarAbierto = signal(false);
  private readonly dialogoAvatar = viewChild<ElementRef<HTMLDialogElement>>('dialogoAvatar');

  /**
   * Sincroniza el `<dialog>` nativo con el signal: `showModal()` da foco
   * atrapado, cierre con Escape y fondo oscuro gratis. El `(close)` de la
   * plantilla devuelve el signal a `false` cuando el usuario pulsa Escape.
   */
  private readonly sincronizarDialogo = effect(() => {
    const dlg = this.dialogoAvatar()?.nativeElement;
    if (!dlg || typeof dlg.showModal !== 'function') return;
    if (this.dialogoAvatarAbierto()) {
      if (!dlg.open) dlg.showModal();
    } else if (dlg.open) {
      dlg.close();
    }
  });

  /** Hay una imagen lista para guardar: un avatar elegido, o una foto (nueva o ya subida). */
  protected readonly imagenElegida = computed(() =>
    this.modoImagen() === 'AVATAR'
      ? this.imagenRefAvatar() !== null
      : this.fotoPendiente() !== null || this.imagenRefFotoActual() !== null,
  );

  /** URL a mostrar en el hueco de la foto: la previsualización de una elegida ahora, o la ya guardada. */
  protected readonly urlFotoMostrada = computed(() => {
    const previa = this.previsualizacionFoto();
    if (previa) return previa;
    const ref = this.imagenRefFotoActual();
    return ref ? urlMedia(`/api/v1/media/fotos/${ref}`) : null;
  });

  /** Lo que se pinta en la carta: la foto en modo FOTO, o el avatar elegido en modo AVATAR. */
  protected readonly urlTarjeta = computed(() => {
    if (this.modoImagen() === 'FOTO') return this.urlFotoMostrada();
    const ref = this.imagenRefAvatar();
    return ref ? urlMedia(`/api/v1/media/avatares/${ref}.png`) : null;
  });

  protected readonly form = this.formBuilder.group({
    nombre: ['', Validators.required],
    apellidos: ['', Validators.required],
    mote: [''],
    sobreMi: ['', Validators.maxLength(500)],
    tienePareja: [false],
    parejaNombre: [''],
    parejaTelefono: [''],
    hijos: this.formBuilder.array<FormGroup>([]),
  });

  ngOnInit(): void {
    this.cargar();
  }

  ngOnDestroy(): void {
    this.revocarPrevisualizacion();
  }

  protected get hijosArray(): FormArray {
    return this.form.get('hijos') as FormArray;
  }

  protected anadirHijo(): void {
    this.hijosArray.push(this.nuevoHijoGroup());
  }

  protected quitarHijo(indice: number): void {
    this.hijosArray.removeAt(indice);
  }

  protected elegirAvatar(id: string): void {
    this.modoImagen.set('AVATAR');
    this.imagenRefAvatar.set(id);
    this.intentoSinImagen.set(false);
    this.dialogoAvatarAbierto.set(false);
  }

  protected abrirDialogoAvatar(): void {
    this.dialogoAvatarAbierto.set(true);
  }

  protected cerrarDialogoAvatar(): void {
    this.dialogoAvatarAbierto.set(false);
  }

  /** Iniciales para el hueco de la carta mientras no hay imagen elegida. */
  protected iniciales(): string {
    const { nombre, apellidos } = this.form.getRawValue();
    return ((nombre?.[0] ?? '') + (apellidos?.[0] ?? '')).toUpperCase() || '—';
  }

  protected onFotoElegida(evento: Event): void {
    const archivo = (evento.target as HTMLInputElement).files?.[0] ?? null;
    if (!archivo) {
      return;
    }
    this.revocarPrevisualizacion();
    this.fotoPendiente.set(archivo);
    this.previsualizacionFoto.set(URL.createObjectURL(archivo));
    this.modoImagen.set('FOTO');
    this.intentoSinImagen.set(false);
  }

  protected romperVinculo(): void {
    this.perfilService.romperPareja().subscribe({
      next: () => this.cargar(),
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  protected guardar(): void {
    this.form.markAllAsTouched();
    this.intentoSinImagen.set(!this.imagenElegida());
    if (this.form.invalid || !this.imagenElegida()) {
      if (this.form.invalid) {
        this.mensajeError.set('Revisa los campos obligatorios (nombre y apellidos).');
      }
      return;
    }

    this.mensajeError.set('');
    this.estado.set('guardando');
    if (this.modoImagen() === 'FOTO' && this.fotoPendiente()) {
      this.perfilService.subirFoto(this.fotoPendiente()!).subscribe({
        next: ({ imagenRef }) => {
          this.imagenRefFotoActual.set(imagenRef);
          this.fotoPendiente.set(null);
          this.enviarGuardar('FOTO', imagenRef);
        },
        error: (e: HttpErrorResponse) => this.avisarError(e),
      });
      return;
    }

    const tipo = this.modoImagen();
    const ref = tipo === 'AVATAR' ? this.imagenRefAvatar() : this.imagenRefFotoActual();
    this.enviarGuardar(tipo, ref!);
  }

  private cargar(): void {
    this.estado.set('cargando');
    forkJoin({
      perfil: this.perfilService.miPerfil(),
      avatares: this.perfilService.avatares(),
    }).subscribe({
      next: ({ perfil, avatares }) => {
        this.avataresDisponibles.set(avatares);
        this.precargar(perfil);
        this.estado.set('listo');
      },
      error: () => this.estado.set('error'),
    });
  }

  private precargar(p: PerfilResponse): void {
    this.revocarPrevisualizacion();
    this.fotoPendiente.set(null);
    this.modoImagen.set(p.imagenTipo ?? 'AVATAR');
    this.imagenRefAvatar.set(p.imagenTipo === 'AVATAR' ? p.imagenRef : null);
    this.imagenRefFotoActual.set(p.imagenTipo === 'FOTO' ? p.imagenRef : null);
    this.parejaEstado.set((p.pareja?.estado ?? null) as never);
    this.intentoSinImagen.set(false);

    this.form.patchValue({
      nombre: p.nombre,
      apellidos: p.apellidos,
      mote: p.mote ?? '',
      sobreMi: p.sobreMi ?? '',
      tienePareja: p.pareja !== null,
      parejaNombre: p.pareja?.nombre ?? '',
      parejaTelefono: p.pareja?.telefono ?? '',
    });

    // Vínculo ACEPTADO: nombre/teléfono de la pareja quedan de solo lectura.
    // Cambiarlos en el PUT rompería el vínculo y declararía uno nuevo.
    if (p.pareja?.estado === 'ACEPTADO') {
      this.form.get('parejaNombre')!.disable();
      this.form.get('parejaTelefono')!.disable();
    } else {
      this.form.get('parejaNombre')!.enable();
      this.form.get('parejaTelefono')!.enable();
    }

    this.hijosArray.clear();
    for (const h of p.hijos) {
      this.hijosArray.push(this.nuevoHijoGroup(h));
    }
  }

  private nuevoHijoGroup(h?: HijoEnPerfil): FormGroup {
    return this.formBuilder.group({
      id: [h?.id ?? null],
      nombre: [h?.nombre ?? '', Validators.required],
      mayorDeEdad: [h?.mayorDeEdad ?? false],
      telefono: [h?.telefono ?? null],
      visible: [h?.visible ?? false],
    });
  }

  private enviarGuardar(imagenTipo: 'FOTO' | 'AVATAR', imagenRef: string): void {
    const v = this.form.getRawValue();
    const body: GuardarPerfilRequest = {
      nombre: v.nombre ?? '',
      apellidos: v.apellidos ?? '',
      mote: v.mote || null,
      sobreMi: v.sobreMi || null,
      imagenTipo,
      imagenRef,
      tienePareja: !!v.tienePareja,
      parejaNombre: v.tienePareja ? v.parejaNombre || null : null,
      parejaTelefono: v.tienePareja ? v.parejaTelefono || null : null,
      hijos: ((v.hijos ?? []) as FilaHijo[]).map(
        (h): HijoRequest => ({
          id: h.id ?? null,
          nombre: h.nombre ?? '',
          mayorDeEdad: !!h.mayorDeEdad,
          telefono: h.telefono || null,
          visible: !!h.visible,
        }),
      ),
    };

    this.perfilService.guardar(body).subscribe({
      next: () => this.router.navigateByUrl('/panel/miembros'),
      error: (e: HttpErrorResponse) => this.avisarError(e),
    });
  }

  private avisarError(e: HttpErrorResponse): void {
    const codigo = e.error?.codigo as CodigoErrorPerfil | undefined;
    if (codigo && MENSAJES[codigo]) {
      this.mensajeError.set(MENSAJES[codigo] as string);
    } else if (e.status === 0) {
      this.mensajeError.set('Sin conexión con el servidor.');
    } else {
      this.mensajeError.set('No se pudo guardar el perfil.');
    }
    this.estado.set('listo');
  }

  private revocarPrevisualizacion(): void {
    const url = this.previsualizacionFoto();
    if (url) {
      URL.revokeObjectURL(url);
      this.previsualizacionFoto.set(null);
    }
  }
}
