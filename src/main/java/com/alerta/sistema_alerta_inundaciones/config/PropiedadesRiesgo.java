package com.alerta.sistema_alerta_inundaciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros configurables de la evaluación de riesgo.
 *
 * Los umbrales de lluvia son PROVISIONALES/EXPERIMENTALES: no provienen de un
 * documento oficial de IMHPA, SINAPROC ni de otro organismo. Deben validarse
 * localmente antes de usar el sistema para decisiones reales.
 */
@ConfigurationProperties(prefix = "alerta.riesgo")
public class PropiedadesRiesgo {

    /** Lluvia acumulada en 24 h (mm) para nivel preventivo. Experimental. */
    private double lluviaUmbralVigilanciaMm = 25.0;

    /** Lluvia acumulada en 24 h (mm) para nivel de riesgo. Experimental. */
    private double lluviaUmbralAlertaMm = 50.0;

    /** Lluvia acumulada en 24 h (mm) para nivel crítico. Experimental. */
    private double lluviaUmbralEmergenciaMm = 100.0;

    /** Edad máxima del pronóstico de caudal antes de considerarlo desactualizado. */
    private int maxEdadPronosticoHoras = 24;

    /** Edad máxima de la consulta de precipitación antes de considerarla desactualizada. */
    private int maxEdadPrecipitacionHoras = 6;

    /**
     * Obtiene el umbral de lluvia acumulada en 24 horas (mm) para el nivel de vigilancia.
     *
     * @return umbral de vigilancia en milímetros
     */
    public double getLluviaUmbralVigilanciaMm() {
        return lluviaUmbralVigilanciaMm;
    }

    /**
     * Establece el umbral de lluvia acumulada en 24 horas (mm) para el nivel de vigilancia.
     *
     * @param lluviaUmbralVigilanciaMm umbral de vigilancia en milímetros
     */
    public void setLluviaUmbralVigilanciaMm(double lluviaUmbralVigilanciaMm) {
        this.lluviaUmbralVigilanciaMm = lluviaUmbralVigilanciaMm;
    }

    /**
     * Obtiene el umbral de lluvia acumulada en 24 horas (mm) para el nivel de alerta.
     *
     * @return umbral de alerta en milímetros
     */
    public double getLluviaUmbralAlertaMm() {
        return lluviaUmbralAlertaMm;
    }

    /**
     * Establece el umbral de lluvia acumulada en 24 horas (mm) para el nivel de alerta.
     *
     * @param lluviaUmbralAlertaMm umbral de alerta en milímetros
     */
    public void setLluviaUmbralAlertaMm(double lluviaUmbralAlertaMm) {
        this.lluviaUmbralAlertaMm = lluviaUmbralAlertaMm;
    }

    /**
     * Obtiene el umbral de lluvia acumulada en 24 horas (mm) para el nivel de emergencia.
     *
     * @return umbral de emergencia en milímetros
     */
    public double getLluviaUmbralEmergenciaMm() {
        return lluviaUmbralEmergenciaMm;
    }

    /**
     * Establece el umbral de lluvia acumulada en 24 horas (mm) para el nivel de emergencia.
     *
     * @param lluviaUmbralEmergenciaMm umbral de emergencia en milímetros
     */
    public void setLluviaUmbralEmergenciaMm(double lluviaUmbralEmergenciaMm) {
        this.lluviaUmbralEmergenciaMm = lluviaUmbralEmergenciaMm;
    }

    /**
     * Obtiene la edad máxima permitida en horas para considerar vigente un pronóstico de caudal.
     *
     * @return edad máxima del pronóstico en horas
     */
    public int getMaxEdadPronosticoHoras() {
        return maxEdadPronosticoHoras;
    }

    /**
     * Establece la edad máxima permitida en horas para considerar vigente un pronóstico de caudal.
     *
     * @param maxEdadPronosticoHoras edad máxima del pronóstico en horas
     */
    public void setMaxEdadPronosticoHoras(int maxEdadPronosticoHoras) {
        this.maxEdadPronosticoHoras = maxEdadPronosticoHoras;
    }

    /**
     * Obtiene la edad máxima permitida en horas para considerar vigente una consulta de precipitación.
     *
     * @return edad máxima de la precipitación en horas
     */
    public int getMaxEdadPrecipitacionHoras() {
        return maxEdadPrecipitacionHoras;
    }

    /**
     * Establece la edad máxima permitida en horas para considerar vigente una consulta de precipitación.
     *
     * @param maxEdadPrecipitacionHoras edad máxima de la precipitación en horas
     */
    public void setMaxEdadPrecipitacionHoras(int maxEdadPrecipitacionHoras) {
        this.maxEdadPrecipitacionHoras = maxEdadPrecipitacionHoras;
    }
}
