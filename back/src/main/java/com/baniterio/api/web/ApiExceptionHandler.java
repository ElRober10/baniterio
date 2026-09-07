package com.baniterio.api.web;

import java.util.HashMap;
import java.util.Map;

import com.baniterio.api.auth.CredencialesInvalidasException;
import com.baniterio.api.auth.RegistroConflictoException;
import com.baniterio.api.auth.SolicitudYaPendienteException;
import com.baniterio.api.auth.TelefonoNoAutorizadoException;
import com.baniterio.api.auth.TelefonoYaAutorizadoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.baniterio.api.perfil.AvatarInexistenteException;
import com.baniterio.api.perfil.ImagenNoSoportadaException;
import com.baniterio.api.perfil.ImagenRefInvalidaException;
import com.baniterio.api.perfil.NombreParejaRequeridoException;
import com.baniterio.api.perfil.TelefonoHijoInvalidoException;
import com.baniterio.api.perfil.TelefonoParejaInvalidoException;
import com.baniterio.api.perfil.VinculoConflictoException;
import com.baniterio.api.perfil.VinculoNoEncontradoException;

/**
 * Traductor central de excepciones → respuestas HTTP JSON. Con
 * {@code @RestControllerAdvice}, Spring envía aquí cualquier excepción que
 * escape de un controlador y aplica el {@code @ExceptionHandler} que coincida.
 *
 * <p>Así los controladores y servicios solo tienen que lanzar la excepción de
 * dominio ({@code TelefonoNoAutorizadoException}, etc.) sin preocuparse del
 * código de estado ni del formato del cuerpo. El cuerpo siempre lleva un
 * {@code "codigo"} estable que el front usa para distinguir casos.
 *
 * <p>Nota: los 401 de "no has enviado token" NO pasan por aquí; los genera
 * antes Spring Security ({@code SecurityConfig}), porque la petición ni siquiera
 * llega al controlador.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** {@code { "codigo": <codigo> }} con el estado dado. */
    private static ResponseEntity<Map<String, Object>> error(HttpStatus estado, String codigo) {
        return ResponseEntity.status(estado).body(Map.of("codigo", codigo));
    }

    @ExceptionHandler(TelefonoNoAutorizadoException.class)
    ResponseEntity<Map<String, Object>> telefonoNoAutorizado() {
        return error(HttpStatus.FORBIDDEN, "TELEFONO_NO_AUTORIZADO");
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<Map<String, Object>> credenciales() {
        return error(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS");
    }

    @ExceptionHandler(RegistroConflictoException.class)
    ResponseEntity<Map<String, Object>> conflicto() {
        return error(HttpStatus.CONFLICT, "YA_REGISTRADO");
    }

    @ExceptionHandler(SolicitudYaPendienteException.class)
    ResponseEntity<Map<String, Object>> solicitudPendiente() {
        return error(HttpStatus.CONFLICT, "SOLICITUD_YA_PENDIENTE");
    }

    @ExceptionHandler(TelefonoYaAutorizadoException.class)
    ResponseEntity<Map<String, Object>> telefonoYaAutorizado() {
        return error(HttpStatus.CONFLICT, "TELEFONO_YA_AUTORIZADO");
    }

    /**
     * Carrera entre dos registros simultáneos con el mismo teléfono/email: ambos pasan las
     * comprobaciones previas y uno choca contra `uk_usuario_telefono` / `uk_usuario_email`
     * al confirmar. Se devuelve el mismo 409 que la comprobación previa, no un 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> integridad() {
        return error(HttpStatus.CONFLICT, "YA_REGISTRADO");
    }

    @ExceptionHandler(com.baniterio.api.admin.SinPermisoException.class)
    ResponseEntity<Map<String, Object>> sinPermiso() {
        return error(HttpStatus.FORBIDDEN, "SIN_PERMISO");
    }

    @ExceptionHandler(com.baniterio.api.admin.SolicitudYaResueltaException.class)
    ResponseEntity<Map<String, Object>> solicitudYaResuelta() {
        return error(HttpStatus.CONFLICT, "SOLICITUD_YA_RESUELTA");
    }

    @ExceptionHandler(com.baniterio.api.admin.MiembroNoEncontradoException.class)
    ResponseEntity<Map<String, Object>> miembroNoEncontrado() {
        return error(HttpStatus.NOT_FOUND, "MIEMBRO_NO_ENCONTRADO");
    }

    @ExceptionHandler(com.baniterio.api.admin.UltimoAdminException.class)
    ResponseEntity<Map<String, Object>> ultimoAdmin() {
        return error(HttpStatus.CONFLICT, "ULTIMO_ADMIN");
    }

    @ExceptionHandler(com.baniterio.api.admin.SoloSuperadminException.class)
    ResponseEntity<Map<String, Object>> soloSuperadmin() {
        return error(HttpStatus.CONFLICT, "SOLO_EL_SUPERADMIN");
    }

    @ExceptionHandler(com.baniterio.api.admin.AutoModificacionException.class)
    ResponseEntity<Map<String, Object>> autoModificacion(com.baniterio.api.admin.AutoModificacionException ex) {
        return error(HttpStatus.CONFLICT, ex.getCodigo());
    }

    /**
     * Entrada que no llega a validarse por tipo:
     * <ul>
     *   <li>{@code HttpMessageNotReadableException} — cuerpo JSON mal formado, o
     *       con un valor que no encaja en el tipo esperado (p. ej. un string que
     *       no es ningún valor de un enum). Jackson lo lanza al deserializar.
     *   <li>{@code MethodArgumentTypeMismatchException} — un {@code @RequestParam}
     *       (p. ej. {@code ?estado=pendiente} en minúscula) que no convierte al
     *       tipo del parámetro.
     * </ul>
     * Devuelve la misma forma que la validación de bean
     * ({@code {"codigo":"VALIDACION","errores":{}}}) para que el cliente no tenga
     * que distinguir casos.
     */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> entradaInvalida() {
        return ResponseEntity.badRequest()
                .body(Map.of("codigo", "VALIDACION", "errores", Map.of()));
    }

    @ExceptionHandler(AvatarInexistenteException.class)
    ResponseEntity<Map<String, Object>> avatarInexistente() {
        return error(HttpStatus.BAD_REQUEST, "AVATAR_INEXISTENTE");
    }

    @ExceptionHandler(ImagenRefInvalidaException.class)
    ResponseEntity<Map<String, Object>> imagenRefInvalida() {
        return error(HttpStatus.BAD_REQUEST, "IMAGEN_REF_INVALIDA");
    }

    @ExceptionHandler(VinculoConflictoException.class)
    ResponseEntity<Map<String, Object>> vinculoConflicto(VinculoConflictoException ex) {
        return error(HttpStatus.CONFLICT, ex.getCodigo());
    }

    @ExceptionHandler(VinculoNoEncontradoException.class)
    ResponseEntity<Map<String, Object>> vinculoNoEncontrado() {
        return error(HttpStatus.NOT_FOUND, "VINCULO_NO_ENCONTRADO");
    }

    @ExceptionHandler(TelefonoParejaInvalidoException.class)
    ResponseEntity<Map<String, Object>> telefonoParejaInvalido() {
        return error(HttpStatus.BAD_REQUEST, "TELEFONO_PAREJA_INVALIDO");
    }

    @ExceptionHandler(NombreParejaRequeridoException.class)
    ResponseEntity<Map<String, Object>> nombreParejaRequerido() {
        return error(HttpStatus.BAD_REQUEST, "NOMBRE_PAREJA_REQUERIDO");
    }

    @ExceptionHandler(TelefonoHijoInvalidoException.class)
    ResponseEntity<Map<String, Object>> telefonoHijoInvalido() {
        return error(HttpStatus.BAD_REQUEST, "TELEFONO_HIJO_INVALIDO");
    }

    @ExceptionHandler(ImagenNoSoportadaException.class)
    ResponseEntity<Map<String, Object>> imagenNoSoportada() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IMAGEN_NO_SOPORTADA");
    }

    @ExceptionHandler(com.baniterio.api.evento.EventoNoEncontradoException.class)
    ResponseEntity<Map<String, Object>> eventoNoEncontrado() {
        return error(HttpStatus.NOT_FOUND, "EVENTO_NO_ENCONTRADO");
    }

    @ExceptionHandler(com.baniterio.api.evento.EventoYaPasadoException.class)
    ResponseEntity<Map<String, Object>> eventoYaPasado() {
        return error(HttpStatus.CONFLICT, "EVENTO_YA_PASADO");
    }

    @ExceptionHandler(com.baniterio.api.evento.NotificacionReenvioProntoException.class)
    ResponseEntity<Map<String, Object>> notificacionReenvioPronto() {
        return error(HttpStatus.CONFLICT, "NOTIFICACION_REENVIO_PRONTO");
    }

    @ExceptionHandler(com.baniterio.api.evento.AsistenciaNoEncontradaException.class)
    ResponseEntity<Map<String, Object>> asistenciaNoEncontrada() {
        return error(HttpStatus.NOT_FOUND, "ASISTENCIA_NO_ENCONTRADA");
    }

    @ExceptionHandler(com.baniterio.api.evento.AsistenciaNoManualException.class)
    ResponseEntity<Map<String, Object>> asistenciaNoManual() {
        return error(HttpStatus.CONFLICT, "ASISTENCIA_NO_MANUAL");
    }

    @ExceptionHandler(com.baniterio.api.evento.BebidaNoEncontradaException.class)
    ResponseEntity<Map<String, Object>> bebidaNoEncontrada() {
        return error(HttpStatus.NOT_FOUND, "BEBIDA_NO_ENCONTRADA");
    }

    @ExceptionHandler(com.baniterio.api.evento.EventoSinFichaException.class)
    ResponseEntity<Map<String, Object>> eventoSinFicha() {
        return error(HttpStatus.CONFLICT, "EVENTO_SIN_FICHA");
    }

    @ExceptionHandler(com.baniterio.api.evento.FichaSinCuotaException.class)
    ResponseEntity<Map<String, Object>> fichaSinCuota() {
        return error(HttpStatus.CONFLICT, "FICHA_SIN_CUOTA");
    }

    @ExceptionHandler(com.baniterio.api.evento.PagoDeclaradoYaPendienteException.class)
    ResponseEntity<Map<String, Object>> pagoDeclaradoYaPendiente() {
        return error(HttpStatus.CONFLICT, "PAGO_DECLARADO_YA_PENDIENTE");
    }

    @ExceptionHandler(com.baniterio.api.evento.PagoDeclaradoNoEncontradoException.class)
    ResponseEntity<Map<String, Object>> pagoDeclaradoNoEncontrado() {
        return error(HttpStatus.NOT_FOUND, "PAGO_DECLARADO_NO_ENCONTRADO");
    }

    @ExceptionHandler(com.baniterio.api.evento.PagoDeclaradoYaResueltoException.class)
    ResponseEntity<Map<String, Object>> pagoDeclaradoYaResuelto() {
        return error(HttpStatus.CONFLICT, "PAGO_DECLARADO_YA_RESUELTO");
    }

    @ExceptionHandler(com.baniterio.api.cuenta.CuentaNoEncontradaException.class)
    ResponseEntity<Map<String, Object>> cuentaNoEncontrada() {
        return error(HttpStatus.NOT_FOUND, "CUENTA_NO_ENCONTRADA");
    }

    @ExceptionHandler(com.baniterio.api.cuenta.CuentaConflictoException.class)
    ResponseEntity<Map<String, Object>> cuentaConflicto() {
        return error(HttpStatus.CONFLICT, "CUENTA_YA_EXISTE");
    }

    @ExceptionHandler(com.baniterio.api.cuenta.MovimientoNoEncontradoException.class)
    ResponseEntity<Map<String, Object>> movimientoNoEncontrado() {
        return error(HttpStatus.NOT_FOUND, "MOVIMIENTO_NO_ENCONTRADO");
    }

    @ExceptionHandler(com.baniterio.api.cuenta.MovimientoNoManualException.class)
    ResponseEntity<Map<String, Object>> movimientoNoManual() {
        return error(HttpStatus.CONFLICT, "MOVIMIENTO_NO_MANUAL");
    }

    @ExceptionHandler(com.baniterio.api.evento.SinPrecioRopaException.class)
    ResponseEntity<Map<String, Object>> sinPrecioRopa() {
        return error(HttpStatus.CONFLICT, "SIN_PRECIO_ROPA");
    }

    @ExceptionHandler(com.baniterio.api.cuenta.ReciboNoValidoException.class)
    ResponseEntity<Map<String, Object>> reciboNoValido() {
        return error(HttpStatus.BAD_REQUEST, "RECIBO_NO_VALIDO");
    }

    @ExceptionHandler(com.baniterio.api.evento.SinCreditoEventoException.class)
    ResponseEntity<Map<String, Object>> sinCreditoEvento() {
        return error(HttpStatus.CONFLICT, "SIN_CREDITO_EVENTO");
    }

    @ExceptionHandler(com.baniterio.api.evento.SinPermisoEventoException.class)
    ResponseEntity<Map<String, Object>> sinPermisoEvento() {
        return error(HttpStatus.FORBIDDEN, "SIN_PERMISO_EVENTO");
    }

    @ExceptionHandler(com.baniterio.api.evento.SolicitudEventoConflictoException.class)
    ResponseEntity<Map<String, Object>> solicitudEventoConflicto(
            com.baniterio.api.evento.SolicitudEventoConflictoException ex) {
        return error(HttpStatus.CONFLICT, ex.getCodigo());
    }

    @ExceptionHandler(com.baniterio.api.evento.SolicitudEventoYaResueltaException.class)
    ResponseEntity<Map<String, Object>> solicitudEventoYaResuelta() {
        return error(HttpStatus.CONFLICT, "SOLICITUD_EVENTO_YA_RESUELTA");
    }

    /** El multipart supera {@code spring.servlet.multipart.max-file-size}. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> imagenDemasiadoGrande() {
        return error(HttpStatus.CONTENT_TOO_LARGE, "IMAGEN_DEMASIADO_GRANDE");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(Map.of("codigo", "VALIDACION", "errores", errores));
    }
}
