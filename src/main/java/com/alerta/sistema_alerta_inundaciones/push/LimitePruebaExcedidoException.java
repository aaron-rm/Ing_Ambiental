package com.alerta.sistema_alerta_inundaciones.push;

/**
 * Se lanza cuando se intenta enviar otra notificación de prueba demasiado
 * pronto. Permite diferenciar el límite de frecuencia (HTTP 429) de otros
 * estados inválidos, como una suscripción inactiva (HTTP 409).
 */
public class LimitePruebaExcedidoException extends RuntimeException {

    /**
     * Crea la excepción con el mensaje que se devolverá al cliente.
     *
     * @param mensaje descripción del límite excedido
     */
    public LimitePruebaExcedidoException(String mensaje) {
        super(mensaje);
    }
}
