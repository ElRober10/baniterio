package com.baniterio.api.web;

import java.util.HashMap;
import java.util.Map;

import com.baniterio.api.auth.CredencialesInvalidasException;
import com.baniterio.api.auth.RegistroConflictoException;
import com.baniterio.api.auth.TelefonoNoAutorizadoException;
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

    @ExceptionHandler(TelefonoNoAutorizadoException.class)
    ResponseEntity<Map<String, Object>> telefonoNoAutorizado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("codigo", "TELEFONO_NO_AUTORIZADO"));
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<Map<String, Object>> credenciales() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("codigo", "CREDENCIALES_INVALIDAS"));
    }

    @ExceptionHandler(RegistroConflictoException.class)
    ResponseEntity<Map<String, Object>> conflicto() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "YA_REGISTRADO"));
    }

    /**
     * Carrera entre dos registros simultáneos con el mismo teléfono/email: ambos pasan las
     * comprobaciones previas y uno choca contra `uk_usuario_telefono` / `uk_usuario_email`
     * al confirmar. Se devuelve el mismo 409 que la comprobación previa, no un 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> integridad() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "YA_REGISTRADO"));
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
