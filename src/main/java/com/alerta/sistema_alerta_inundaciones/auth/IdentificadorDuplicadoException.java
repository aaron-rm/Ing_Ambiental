package com.alerta.sistema_alerta_inundaciones.auth;

public class IdentificadorDuplicadoException extends RuntimeException {

    public IdentificadorDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
