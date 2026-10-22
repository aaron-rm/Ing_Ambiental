package com.alerta.sistema_alerta_inundaciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alerta.monitoreo")
public class PropiedadesMonitoreo {

    /** Activa la tarea programada que evalúa los ríos configurados. */
    private boolean habilitado = true;

    /** Intervalo entre ciclos de monitoreo. */
    private long intervaloMs = 900_000L; // 15 minutos

    /** Retraso inicial para no bloquear el arranque de la aplicación. */
    private long retrasoInicialMs = 60_000L; // 1 minuto

    /** Envía un aviso cuando un evento crítico vuelve a NORMAL. */
    private boolean notificarRecuperacion = true;

    public boolean isHabilitado() {
        return habilitado;
    }

    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    public long getIntervaloMs() {
        return intervaloMs;
    }

    public void setIntervaloMs(long intervaloMs) {
        this.intervaloMs = intervaloMs;
    }

    public long getRetrasoInicialMs() {
        return retrasoInicialMs;
    }

    public void setRetrasoInicialMs(long retrasoInicialMs) {
        this.retrasoInicialMs = retrasoInicialMs;
    }

    public boolean isNotificarRecuperacion() {
        return notificarRecuperacion;
    }

    public void setNotificarRecuperacion(boolean notificarRecuperacion) {
        this.notificarRecuperacion = notificarRecuperacion;
    }
}
