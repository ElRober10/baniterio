package com.baniterio.api.evento;

/**
 * Se intenta responder o convocar un evento cuya fecha de inicio ya pasó. La
 * traduce {@code ApiExceptionHandler} a 409 {@code EVENTO_YA_PASADO}.
 */
public class EventoYaPasadoException extends RuntimeException {
}
