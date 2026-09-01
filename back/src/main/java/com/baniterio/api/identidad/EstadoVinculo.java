package com.baniterio.api.identidad;

/**
 * Estado de un {@link VinculoPareja}. {@code SIN_CUENTA}: la pareja aún no está
 * registrada, se da por buena. {@code PENDIENTE}: la pareja tiene cuenta y debe
 * aceptar. {@code ACEPTADO}: vínculo mutuo confirmado. {@code RECHAZADO}:
 * terminal e histórico; el solicitante puede crear otro.
 */
public enum EstadoVinculo { SIN_CUENTA, PENDIENTE, ACEPTADO, RECHAZADO }
