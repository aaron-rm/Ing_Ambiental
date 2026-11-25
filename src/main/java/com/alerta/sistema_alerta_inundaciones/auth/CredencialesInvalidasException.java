package com.alerta.sistema_alerta_inundaciones.auth;

/**
 * Excepción de negocio lanzada cuando el identificador o la contraseña
 * no son válidos, o cuando una sesión deja de existir.
 */
public class CredencialesInvalidasException extends RuntimeException {

    /**
     * Crea la excepción con un mensaje descriptivo.
     *
     * @param mensaje detalle del motivo del fallo de autenticación
     */
    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
