package com.alerta.sistema_alerta_inundaciones.riesgo;

/**
 * Calidad de los datos usados en una evaluación de riesgo. Se usa para indicar
 * si el nivel calculado es confiable y puede generar notificaciones.
 */
public enum EstadoDatos {
    /** Caudal y precipitación recientes. */
    COMPLETOS,
    /** Falta precipitación o no es utilizable, pero el caudal es reciente. */
    PARCIALES,
    /** El pronóstico de caudal está desactualizado o su fecha es desconocida. */
    DESACTUALIZADOS,
    /** No hay datos suficientes para evaluar. */
    INSUFICIENTES
}
