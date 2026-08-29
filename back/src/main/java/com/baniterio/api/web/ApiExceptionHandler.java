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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
