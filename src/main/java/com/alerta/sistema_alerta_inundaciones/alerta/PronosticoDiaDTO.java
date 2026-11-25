package com.alerta.sistema_alerta_inundaciones.alerta;

import java.time.LocalDate;

/**
 * Resumen diario del pronóstico de caudal de un tramo para su visualización en la API.
 */
public class PronosticoDiaDTO {

    private int diaNumero;
    private LocalDate fecha;
    private double caudalMedio;
    private double caudalMinimo;
    private double caudalMaximo;
    private String estado;

    /** Crea un resumen diario vacío. */
    public PronosticoDiaDTO() {}

    /**
     * Crea un resumen diario de pronóstico hidrológico.
     *
     * @param diaNumero número secuencial del día dentro del horizonte de pronóstico
     * @param fecha fecha calendario correspondiente en UTC
     * @param caudalMedio caudal promedio del día en m³/s
     * @param caudalMinimo caudal mínimo del día en m³/s
     * @param caudalMaximo caudal máximo del día en m³/s
     * @param estado etiqueta de riesgo diario calculada frente a los umbrales
     */
    public PronosticoDiaDTO(int diaNumero, LocalDate fecha, double caudalMedio, double caudalMinimo, double caudalMaximo, String estado) {
        this.diaNumero = diaNumero;
        this.fecha = fecha;
        this.caudalMedio = caudalMedio;
        this.caudalMinimo = caudalMinimo;
        this.caudalMaximo = caudalMaximo;
        this.estado = estado;
    }

    /**
     * Obtiene el número de día dentro del pronóstico.
     *
     * @return número de día secuencial
     */
    public int getDiaNumero() {
        return diaNumero;
    }

    /**
     * Establece el número de día dentro del pronóstico.
     *
     * @param diaNumero número de día secuencial
     */
    public void setDiaNumero(int diaNumero) {
        this.diaNumero = diaNumero;
    }

    /**
     * Obtiene la fecha del día pronosticado.
     *
     * @return fecha del pronóstico
     */
    public LocalDate getFecha() {
        return fecha;
    }

    /**
     * Establece la fecha del día pronosticado.
     *
     * @param fecha fecha del pronóstico
     */
    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    /**
     * Obtiene el caudal medio estimado para el día.
     *
     * @return caudal medio en m³/s
     */
    public double getCaudalMedio() {
        return caudalMedio;
    }

    /**
     * Establece el caudal medio estimado para el día.
     *
     * @param caudalMedio caudal medio en m³/s
     */
    public void setCaudalMedio(double caudalMedio) {
        this.caudalMedio = caudalMedio;
    }

    /**
     * Obtiene el caudal mínimo estimado para el día.
     *
     * @return caudal mínimo en m³/s
     */
    public double getCaudalMinimo() {
        return caudalMinimo;
    }

    /**
     * Establece el caudal mínimo estimado para el día.
     *
     * @param caudalMinimo caudal mínimo en m³/s
     */
    public void setCaudalMinimo(double caudalMinimo) {
        this.caudalMinimo = caudalMinimo;
    }

    /**
     * Obtiene el caudal máximo estimado para el día.
     *
     * @return caudal máximo en m³/s
     */
    public double getCaudalMaximo() {
        return caudalMaximo;
    }

    /**
     * Establece el caudal máximo estimado para el día.
     *
     * @param caudalMaximo caudal máximo en m³/s
     */
    public void setCaudalMaximo(double caudalMaximo) {
        this.caudalMaximo = caudalMaximo;
    }

    /**
     * Obtiene el estado de riesgo asignado al día.
     *
     * @return etiqueta del estado diario
     */
    public String getEstado() {
        return estado;
    }

    /**
     * Establece el estado de riesgo asignado al día.
     *
     * @param estado etiqueta del estado diario
     */
    public void setEstado(String estado) {
        this.estado = estado;
    }
}
