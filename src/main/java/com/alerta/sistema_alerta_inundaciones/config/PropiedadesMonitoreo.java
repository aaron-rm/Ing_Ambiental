package com.alerta.sistema_alerta_inundaciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración bajo el prefijo {@code alerta.monitoreo} que
 * controlan la ejecución periódica del monitoreo automático de ríos y alertas.
 */
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

    /**
     * Indica si el monitoreo periódico automático está habilitado.
     *
     * @return {@code true} si la tarea programada está activa
     */
    public boolean isHabilitado() {
        return habilitado;
    }

    /**
     * Activa o desactiva el monitoreo periódico automático.
     *
     * @param habilitado {@code true} para activar el monitoreo
     */
    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
    }

    /**
     * Obtiene el intervalo en milisegundos entre ciclos de monitoreo.
     *
     * @return intervalo en milisegundos
     */
    public long getIntervaloMs() {
        return intervaloMs;
    }

    /**
     * Establece el intervalo en milisegundos entre ciclos de monitoreo.
     *
     * @param intervaloMs intervalo en milisegundos
     */
    public void setIntervaloMs(long intervaloMs) {
        this.intervaloMs = intervaloMs;
    }

    /**
     * Obtiene el retraso inicial en milisegundos antes del primer ciclo de monitoreo.
     *
     * @return retraso inicial en milisegundos
     */
    public long getRetrasoInicialMs() {
        return retrasoInicialMs;
    }

    /**
     * Establece el retraso inicial en milisegundos antes del primer ciclo de monitoreo.
     *
     * @param retrasoInicialMs retraso inicial en milisegundos
     */
    public void setRetrasoInicialMs(long retrasoInicialMs) {
        this.retrasoInicialMs = retrasoInicialMs;
    }

    /**
     * Indica si debe enviarse una notificación cuando un río vuelve al nivel normal.
     *
     * @return {@code true} si se notifican recuperaciones
     */
    public boolean isNotificarRecuperacion() {
        return notificarRecuperacion;
    }

    /**
     * Configura si debe enviarse una notificación cuando un río vuelve al nivel normal.
     *
     * @param notificarRecuperacion {@code true} para notificar la recuperación
     */
    public void setNotificarRecuperacion(boolean notificarRecuperacion) {
        this.notificarRecuperacion = notificarRecuperacion;
    }
}
