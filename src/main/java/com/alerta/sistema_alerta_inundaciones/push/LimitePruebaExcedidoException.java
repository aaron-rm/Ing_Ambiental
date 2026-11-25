package com.alerta.sistema_alerta_inundaciones.push;

/**
 * Se lanza cuando se intenta enviar otra notificación de prueba demasiado
 * pronto. Permite diferenciar el límite de frecuencia (HTTP 429) de otros
 * estados inválidos, como una suscripción inactiva (HTTP 409).
 */
public class LimitePruebaExcedidoException extends RuntimeException {

    public LimitePruebaExcedidoException(String mensaje) {
        super(mensaje);
    }
}
