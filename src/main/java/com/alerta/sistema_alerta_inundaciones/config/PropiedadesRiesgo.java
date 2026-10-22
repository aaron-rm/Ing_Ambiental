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

    public double getLluviaUmbralVigilanciaMm() {
        return lluviaUmbralVigilanciaMm;
    }

    public void setLluviaUmbralVigilanciaMm(double lluviaUmbralVigilanciaMm) {
        this.lluviaUmbralVigilanciaMm = lluviaUmbralVigilanciaMm;
    }

    public double getLluviaUmbralAlertaMm() {
        return lluviaUmbralAlertaMm;
    }

    public void setLluviaUmbralAlertaMm(double lluviaUmbralAlertaMm) {
        this.lluviaUmbralAlertaMm = lluviaUmbralAlertaMm;
    }

    public double getLluviaUmbralEmergenciaMm() {
        return lluviaUmbralEmergenciaMm;
    }

    public void setLluviaUmbralEmergenciaMm(double lluviaUmbralEmergenciaMm) {
        this.lluviaUmbralEmergenciaMm = lluviaUmbralEmergenciaMm;
    }

    public int getMaxEdadPronosticoHoras() {
        return maxEdadPronosticoHoras;
    }

    public void setMaxEdadPronosticoHoras(int maxEdadPronosticoHoras) {
        this.maxEdadPronosticoHoras = maxEdadPronosticoHoras;
    }

    public int getMaxEdadPrecipitacionHoras() {
        return maxEdadPrecipitacionHoras;
    }

    public void setMaxEdadPrecipitacionHoras(int maxEdadPrecipitacionHoras) {
        this.maxEdadPrecipitacionHoras = maxEdadPrecipitacionHoras;
    }
}
