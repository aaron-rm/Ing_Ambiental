package com.alerta.sistema_alerta_inundaciones.push;

/**
 * Resultado del envío de una notificación a un dispositivo.
 *
 * @param exito {@code true} si FCM aceptó el mensaje
 * @param tokenInvalido {@code true} si el token está vencido o no registrado
 * @param error descripción del error; {@code null} si hubo éxito
 */
public record ResultadoEnvioPush(boolean exito, boolean tokenInvalido, String error) {

    /**
     * Crea un resultado exitoso.
     *
     * @return resultado con éxito y sin error
     */
    public static ResultadoEnvioPush ok() {
        return new ResultadoEnvioPush(true, false, null);
    }

    /**
     * Crea un resultado de fallo que no implica token inválido.
     *
     * @param error descripción del fallo
     * @return resultado fallido
     */
    public static ResultadoEnvioPush fallo(String error) {
        return new ResultadoEnvioPush(false, false, error);
    }

    /**
     * Crea un resultado de fallo por token inválido o no registrado.
     *
     * @param error descripción del fallo
     * @return resultado fallido que marca el token como inválido
     */
    public static ResultadoEnvioPush tokenInvalido(String error) {
        return new ResultadoEnvioPush(false, true, error);
    }
}
