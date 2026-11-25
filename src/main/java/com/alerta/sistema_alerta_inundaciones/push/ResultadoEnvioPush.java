package com.alerta.sistema_alerta_inundaciones.push;

public record ResultadoEnvioPush(boolean exito, boolean tokenInvalido, String error) {

    public static ResultadoEnvioPush ok() {
        return new ResultadoEnvioPush(true, false, null);
    }

    public static ResultadoEnvioPush fallo(String error) {
        return new ResultadoEnvioPush(false, false, error);
    }

    public static ResultadoEnvioPush tokenInvalido(String error) {
        return new ResultadoEnvioPush(false, true, error);
    }
}
