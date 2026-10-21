package com.portalcreditos.exception;

import org.springframework.http.HttpStatus;

/**
 * Error esperado de la logica de negocio (credenciales invalidas, recurso inexistente,
 * operacion no permitida...). Lleva el codigo HTTP que debe recibir el cliente.
 */
public class NegocioException extends RuntimeException {

    private final HttpStatus estado;

    public NegocioException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public static NegocioException noEncontrado(String mensaje) {
        return new NegocioException(HttpStatus.NOT_FOUND, mensaje);
    }

    public static NegocioException solicitudInvalida(String mensaje) {
        return new NegocioException(HttpStatus.BAD_REQUEST, mensaje);
    }
}
