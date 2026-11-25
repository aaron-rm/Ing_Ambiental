package com.alerta.sistema_alerta_inundaciones.riesgo;

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
