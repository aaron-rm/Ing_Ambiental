package com.alerta.sistema_alerta_inundaciones.auth;

/**
 * Excepción de negocio lanzada cuando se intenta registrar un identificador
 * de usuario que ya existe en la base de datos.
 */
public class IdentificadorDuplicadoException extends RuntimeException {

    /**
     * Crea la excepción con un mensaje descriptivo.
     *
     * @param mensaje detalle del conflicto de identificador
     */
    public IdentificadorDuplicadoException(String mensaje) {
        super(mensaje);
    }
}
