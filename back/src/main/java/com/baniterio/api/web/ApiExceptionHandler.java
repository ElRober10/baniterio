package com.baniterio.api.web;

import java.util.HashMap;
import java.util.Map;

import com.baniterio.api.auth.CredencialesInvalidasException;
import com.baniterio.api.auth.RegistroConflictoException;
import com.baniterio.api.auth.TelefonoNoAutorizadoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
