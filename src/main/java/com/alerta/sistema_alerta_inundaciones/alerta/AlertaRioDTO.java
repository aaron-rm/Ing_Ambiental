package com.alerta.sistema_alerta_inundaciones.alerta;

import java.time.Instant;
import java.util.List;

/**
 * Objeto de transferencia de datos con el estado consolidado de riesgo,
 * pronóstico de caudal, precipitación, umbrales y metadatos de un río o tramo.
 */
public class AlertaRioDTO {

    private Long reachId;
    private Long rioId;
    /**
     * true/false si la petición incluye un JWT válido; null si se consulta sin
     * sesión. Solo indica si el río está seleccionado para notificaciones.
     */
    private Boolean siguiendo;
    private String nombreRio;
    private String nivelAlerta; // NORMAL, VIGILANCIA, ALERTA, EMERGENCIA
    private double caudalActualOInicial;
    private double caudalMaximoPronosticado;
    private double umbralVigilancia;
    private double umbralAlerta;
    private double umbralEmergencia;
    private String mensaje;
    private List<PuntoPronosticoDTO> pronostico;
    private List<PronosticoDiaDTO> pronosticoDiario;

    private boolean simulacion;
    private String estadoDatos; // COMPLETOS, PARCIALES, DESACTUALIZADOS, INSUFICIENTES
    private boolean datosConfiables;
    private Instant fechaEvaluacion;
    private Instant fechaEmisionCaudal;
    private Instant fechaActualizacionPrecipitacion;
    private String fuenteCaudal;
    private String fuentePrecipitacion;
    private String fuenteUmbrales;
    private String nivelRiesgoCaudal;
    private String nivelRiesgoLluvia;
    private Double lluvia24hMm;
    private Double lluvia72hMm;
    private Double maxLluviaHorariaMm;
    private List<String> factoresRiesgo;
    private List<String> advertencias;
    private List<String> recomendaciones;
    private Long alertaEventoId;
    private List<PuntoLluviaDTO> pronosticoLluvia;

    /**
     * Punto horario de precipitación pronosticada para la serie de lluvia.
     */
    public static class PuntoLluviaDTO {
        private Instant fecha;
        private double mm;

        /** Crea un punto de lluvia vacío. */
        public PuntoLluviaDTO() {
        }

        /**
         * Crea un punto de precipitación horaria.
         *
         * @param fecha instante de la medición o pronóstico
         * @param mm precipitación en milímetros
         */
        public PuntoLluviaDTO(Instant fecha, double mm) {
            this.fecha = fecha;
            this.mm = mm;
        }

        /**
         * Obtiene la fecha del punto de precipitación.
         *
         * @return instante del pronóstico
         */
        public Instant getFecha() {
            return fecha;
        }

        /**
         * Establece la fecha del punto de precipitación.
         *
         * @param fecha instante del pronóstico
         */
        public void setFecha(Instant fecha) {
            this.fecha = fecha;
        }

        /**
         * Obtiene la cantidad de lluvia pronosticada.
         *
         * @return precipitación en milímetros
         */
        public double getMm() {
            return mm;
        }

        /**
         * Establece la cantidad de lluvia pronosticada.
         *
         * @param mm precipitación en milímetros
         */
        public void setMm(double mm) {
            this.mm = mm;
        }
    }

    /**
     * Punto temporal de caudal medio pronosticado dentro de la serie hidrológica.
     */
    public static class PuntoPronosticoDTO {
        private Instant fecha;
        private double caudalMedio;

        /** Crea un punto de pronóstico vacío. */
        public PuntoPronosticoDTO() {}

        /**
         * Crea un punto de pronóstico de caudal.
         *
         * @param fecha instante del punto pronosticado
         * @param caudalMedio caudal medio estimado en m³/s
         */
        public PuntoPronosticoDTO(Instant fecha, double caudalMedio) {
            this.fecha = fecha;
            this.caudalMedio = caudalMedio;
        }

        /**
         * Obtiene la fecha del punto pronosticado.
         *
         * @return instante del punto
         */
        public Instant getFecha() {
            return fecha;
        }

        /**
         * Establece la fecha del punto pronosticado.
         *
         * @param fecha instante del punto
         */
        public void setFecha(Instant fecha) {
            this.fecha = fecha;
        }

        /**
         * Obtiene el caudal medio pronosticado.
         *
         * @return caudal medio en m³/s
         */
        public double getCaudalMedio() {
            return caudalMedio;
        }

        /**
         * Establece el caudal medio pronosticado.
         *
         * @param caudalMedio caudal medio en m³/s
         */
        public void setCaudalMedio(double caudalMedio) {
            this.caudalMedio = caudalMedio;
        }
    }

    /** Crea una respuesta vacía de alerta para un río o tramo. */
    public AlertaRioDTO() {}

    /**
     * Obtiene el identificador del tramo en GEOGloWS.
     *
     * @return identificador del tramo
     */
    public Long getReachId() {
        return reachId;
    }

    /**
     * Establece el identificador del tramo en GEOGloWS.
     *
     * @param reachId identificador del tramo
     */
    public void setReachId(Long reachId) {
        this.reachId = reachId;
    }

    /**
     * Obtiene el identificador interno del río en base de datos.
     *
     * @return identificador del río o {@code null} si se consultó solo por tramo
     */
    public Long getRioId() {
        return rioId;
    }

    /**
     * Establece el identificador interno del río en base de datos.
     *
     * @param rioId identificador del río
     */
    public void setRioId(Long rioId) {
        this.rioId = rioId;
    }

    /**
     * Indica si el usuario autenticado sigue este río para notificaciones.
     *
     * @return {@code true} o {@code false} con sesión válida, o {@code null} sin sesión
     */
    public Boolean getSiguiendo() {
        return siguiendo;
    }

    /**
     * Establece si el usuario autenticado sigue este río para notificaciones.
     *
     * @param siguiendo indicador de suscripción o {@code null} sin sesión
     */
    public void setSiguiendo(Boolean siguiendo) {
        this.siguiendo = siguiendo;
    }

    /**
     * Obtiene el nombre del río o tramo consultado.
     *
     * @return nombre del río
     */
    public String getNombreRio() {
        return nombreRio;
    }

    /**
     * Establece el nombre del río o tramo consultado.
     *
     * @param nombreRio nombre del río
     */
    public void setNombreRio(String nombreRio) {
        this.nombreRio = nombreRio;
    }

    /**
     * Obtiene el nivel general de alerta calculado.
     *
     * @return nivel de alerta (NORMAL, VIGILANCIA, ALERTA o EMERGENCIA)
     */
    public String getNivelAlerta() {
        return nivelAlerta;
    }

    /**
     * Establece el nivel general de alerta calculado.
     *
     * @param nivelAlerta nivel de alerta
     */
    public void setNivelAlerta(String nivelAlerta) {
        this.nivelAlerta = nivelAlerta;
    }

    /**
     * Obtiene el caudal actual o inicial más cercano a la consulta.
     *
     * @return caudal en m³/s
     */
    public double getCaudalActualOInicial() {
        return caudalActualOInicial;
    }

    /**
     * Establece el caudal actual o inicial más cercano a la consulta.
     *
     * @param caudalActualOInicial caudal en m³/s
     */
    public void setCaudalActualOInicial(double caudalActualOInicial) {
        this.caudalActualOInicial = caudalActualOInicial;
    }

    /**
     * Obtiene el caudal máximo previsto en el horizonte de pronóstico.
     *
     * @return caudal máximo en m³/s
     */
    public double getCaudalMaximoPronosticado() {
        return caudalMaximoPronosticado;
    }

    /**
     * Establece el caudal máximo previsto en el horizonte de pronóstico.
     *
     * @param caudalMaximoPronosticado caudal máximo en m³/s
     */
    public void setCaudalMaximoPronosticado(double caudalMaximoPronosticado) {
        this.caudalMaximoPronosticado = caudalMaximoPronosticado;
    }

    /**
     * Obtiene el umbral de caudal para nivel de vigilancia.
     *
     * @return umbral de vigilancia en m³/s
     */
    public double getUmbralVigilancia() {
        return umbralVigilancia;
    }

    /**
     * Establece el umbral de caudal para nivel de vigilancia.
     *
     * @param umbralVigilancia umbral de vigilancia en m³/s
     */
    public void setUmbralVigilancia(double umbralVigilancia) {
        this.umbralVigilancia = umbralVigilancia;
    }

    /**
     * Obtiene el umbral de caudal para nivel de alerta.
     *
     * @return umbral de alerta en m³/s
     */
    public double getUmbralAlerta() {
        return umbralAlerta;
    }

    /**
     * Establece el umbral de caudal para nivel de alerta.
     *
     * @param umbralAlerta umbral de alerta en m³/s
     */
    public void setUmbralAlerta(double umbralAlerta) {
        this.umbralAlerta = umbralAlerta;
    }

    /**
     * Obtiene el umbral de caudal para nivel de emergencia.
     *
     * @return umbral de emergencia en m³/s
     */
    public double getUmbralEmergencia() {
        return umbralEmergencia;
    }

    /**
     * Establece el umbral de caudal para nivel de emergencia.
     *
     * @param umbralEmergencia umbral de emergencia en m³/s
     */
    public void setUmbralEmergencia(double umbralEmergencia) {
        this.umbralEmergencia = umbralEmergencia;
    }

    /**
     * Obtiene el mensaje explicativo del estado de riesgo actual.
     *
     * @return mensaje descriptivo
     */
    public String getMensaje() {
        return mensaje;
    }

    /**
     * Establece el mensaje explicativo del estado de riesgo actual.
     *
     * @param mensaje mensaje descriptivo
     */
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    /**
     * Obtiene la serie temporal completa de caudal pronosticado.
     *
     * @return lista de puntos de pronóstico de caudal
     */
    public List<PuntoPronosticoDTO> getPronostico() {
        return pronostico;
    }

    /**
     * Establece la serie temporal completa de caudal pronosticado.
     *
     * @param pronostico lista de puntos de pronóstico de caudal
     */
    public void setPronostico(List<PuntoPronosticoDTO> pronostico) {
        this.pronostico = pronostico;
    }

    /**
     * Obtiene el pronóstico de caudal resumido por día.
     *
     * @return lista de resúmenes diarios
     */
    public List<PronosticoDiaDTO> getPronosticoDiario() {
        return pronosticoDiario;
    }

    /**
     * Establece el pronóstico de caudal resumido por día.
     *
     * @param pronosticoDiario lista de resúmenes diarios
     */
    public void setPronosticoDiario(List<PronosticoDiaDTO> pronosticoDiario) {
        this.pronosticoDiario = pronosticoDiario;
    }

    /**
     * Indica si la respuesta corresponde a una simulación de crecida.
     *
     * @return {@code true} si es un escenario simulado
     */
    public boolean isSimulacion() {
        return simulacion;
    }

    /**
     * Establece si la respuesta corresponde a una simulación de crecida.
     *
     * @param simulacion indicador de simulación
     */
    public void setSimulacion(boolean simulacion) {
        this.simulacion = simulacion;
    }

    /**
     * Obtiene el estado de disponibilidad y vigencia de los datos evaluados.
     *
     * @return etiqueta de estado de datos
     */
    public String getEstadoDatos() {
        return estadoDatos;
    }

    /**
     * Establece el estado de disponibilidad y vigencia de los datos evaluados.
     *
     * @param estadoDatos etiqueta de estado de datos
     */
    public void setEstadoDatos(String estadoDatos) {
        this.estadoDatos = estadoDatos;
    }

    /**
     * Indica si los datos de entrada son confiables para confirmar el riesgo.
     *
     * @return {@code true} si los datos cumplen los criterios de vigencia
     */
    public boolean isDatosConfiables() {
        return datosConfiables;
    }

    /**
     * Establece si los datos de entrada son confiables para confirmar el riesgo.
     *
     * @param datosConfiables indicador de confiabilidad
     */
    public void setDatosConfiables(boolean datosConfiables) {
        this.datosConfiables = datosConfiables;
    }

    /**
     * Obtiene la fecha y hora en que se realizó la evaluación.
     *
     * @return instante de evaluación
     */
    public Instant getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    /**
     * Establece la fecha y hora en que se realizó la evaluación.
     *
     * @param fechaEvaluacion instante de evaluación
     */
    public void setFechaEvaluacion(Instant fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
    }

    /**
     * Obtiene la fecha de emisión del pronóstico de caudal.
     *
     * @return instante de emisión del caudal
     */
    public Instant getFechaEmisionCaudal() {
        return fechaEmisionCaudal;
    }

    /**
     * Establece la fecha de emisión del pronóstico de caudal.
     *
     * @param fechaEmisionCaudal instante de emisión del caudal
     */
    public void setFechaEmisionCaudal(Instant fechaEmisionCaudal) {
        this.fechaEmisionCaudal = fechaEmisionCaudal;
    }

    /**
     * Obtiene la fecha de actualización de los datos de precipitación.
     *
     * @return instante de consulta de precipitación
     */
    public Instant getFechaActualizacionPrecipitacion() {
        return fechaActualizacionPrecipitacion;
    }

    /**
     * Establece la fecha de actualización de los datos de precipitación.
     *
     * @param fechaActualizacionPrecipitacion instante de consulta de precipitación
     */
    public void setFechaActualizacionPrecipitacion(Instant fechaActualizacionPrecipitacion) {
        this.fechaActualizacionPrecipitacion = fechaActualizacionPrecipitacion;
    }

    /**
     * Obtiene la descripción de la fuente de datos hidrológicos.
     *
     * @return fuente del caudal
     */
    public String getFuenteCaudal() {
        return fuenteCaudal;
    }

    /**
     * Establece la descripción de la fuente de datos hidrológicos.
     *
     * @param fuenteCaudal fuente del caudal
     */
    public void setFuenteCaudal(String fuenteCaudal) {
        this.fuenteCaudal = fuenteCaudal;
    }

    /**
     * Obtiene la descripción de la fuente de datos de precipitación.
     *
     * @return fuente de la precipitación
     */
    public String getFuentePrecipitacion() {
        return fuentePrecipitacion;
    }

    /**
     * Establece la descripción de la fuente de datos de precipitación.
     *
     * @param fuentePrecipitacion fuente de la precipitación
     */
    public void setFuentePrecipitacion(String fuentePrecipitacion) {
        this.fuentePrecipitacion = fuentePrecipitacion;
    }

    /**
     * Obtiene la descripción del origen de los umbrales aplicados.
     *
     * @return fuente de los umbrales
     */
    public String getFuenteUmbrales() {
        return fuenteUmbrales;
    }

    /**
     * Establece la descripción del origen de los umbrales aplicados.
     *
     * @param fuenteUmbrales fuente de los umbrales
     */
    public void setFuenteUmbrales(String fuenteUmbrales) {
        this.fuenteUmbrales = fuenteUmbrales;
    }

    /**
     * Obtiene el nivel de riesgo evaluado por caudal.
     *
     * @return nivel de riesgo hidrológico
     */
    public String getNivelRiesgoCaudal() {
        return nivelRiesgoCaudal;
    }

    /**
     * Establece el nivel de riesgo evaluado por caudal.
     *
     * @param nivelRiesgoCaudal nivel de riesgo hidrológico
     */
    public void setNivelRiesgoCaudal(String nivelRiesgoCaudal) {
        this.nivelRiesgoCaudal = nivelRiesgoCaudal;
    }

    /**
     * Obtiene el nivel de riesgo evaluado por precipitación.
     *
     * @return nivel de riesgo por lluvia
     */
    public String getNivelRiesgoLluvia() {
        return nivelRiesgoLluvia;
    }

    /**
     * Establece el nivel de riesgo evaluado por precipitación.
     *
     * @param nivelRiesgoLluvia nivel de riesgo por lluvia
     */
    public void setNivelRiesgoLluvia(String nivelRiesgoLluvia) {
        this.nivelRiesgoLluvia = nivelRiesgoLluvia;
    }

    /**
     * Obtiene la lluvia acumulada prevista en las próximas 24 horas.
     *
     * @return precipitación en mm o {@code null}
     */
    public Double getLluvia24hMm() {
        return lluvia24hMm;
    }

    /**
     * Establece la lluvia acumulada prevista en las próximas 24 horas.
     *
     * @param lluvia24hMm precipitación en mm
     */
    public void setLluvia24hMm(Double lluvia24hMm) {
        this.lluvia24hMm = lluvia24hMm;
    }

    /**
     * Obtiene la lluvia acumulada prevista en las próximas 72 horas.
     *
     * @return precipitación en mm o {@code null}
     */
    public Double getLluvia72hMm() {
        return lluvia72hMm;
    }

    /**
     * Establece la lluvia acumulada prevista en las próximas 72 horas.
     *
     * @param lluvia72hMm precipitación en mm
     */
    public void setLluvia72hMm(Double lluvia72hMm) {
        this.lluvia72hMm = lluvia72hMm;
    }

    /**
     * Obtiene la máxima intensidad horaria de lluvia pronosticada.
     *
     * @return precipitación horaria máxima en mm o {@code null}
     */
    public Double getMaxLluviaHorariaMm() {
        return maxLluviaHorariaMm;
    }

    /**
     * Establece la máxima intensidad horaria de lluvia pronosticada.
     *
     * @param maxLluviaHorariaMm precipitación horaria máxima en mm
     */
    public void setMaxLluviaHorariaMm(Double maxLluviaHorariaMm) {
        this.maxLluviaHorariaMm = maxLluviaHorariaMm;
    }

    /**
     * Obtiene la lista de factores que fundamentan el nivel de riesgo.
     *
     * @return lista de factores de riesgo
     */
    public List<String> getFactoresRiesgo() {
        return factoresRiesgo;
    }

    /**
     * Establece la lista de factores que fundamentan el nivel de riesgo.
     *
     * @param factoresRiesgo lista de factores de riesgo
     */
    public void setFactoresRiesgo(List<String> factoresRiesgo) {
        this.factoresRiesgo = factoresRiesgo;
    }

    /**
     * Obtiene las advertencias sobre limitaciones o frescura de los datos.
     *
     * @return lista de advertencias
     */
    public List<String> getAdvertencias() {
        return advertencias;
    }

    /**
     * Establece las advertencias sobre limitaciones o frescura de los datos.
     *
     * @param advertencias lista de advertencias
     */
    public void setAdvertencias(List<String> advertencias) {
        this.advertencias = advertencias;
    }

    /**
     * Obtiene las recomendaciones preventivas sugeridas según el nivel de alerta.
     *
     * @return lista de recomendaciones
     */
    public List<String> getRecomendaciones() {
        return recomendaciones;
    }

    /**
     * Establece las recomendaciones preventivas sugeridas según el nivel de alerta.
     *
     * @param recomendaciones lista de recomendaciones
     */
    public void setRecomendaciones(List<String> recomendaciones) {
        this.recomendaciones = recomendaciones;
    }

    /**
     * Obtiene el identificador del último evento de alerta registrado para el tramo.
     *
     * @return identificador del evento o {@code null} si no existe
     */
    public Long getAlertaEventoId() {
        return alertaEventoId;
    }

    /**
     * Establece el identificador del último evento de alerta registrado para el tramo.
     *
     * @param alertaEventoId identificador del evento
     */
    public void setAlertaEventoId(Long alertaEventoId) {
        this.alertaEventoId = alertaEventoId;
    }

    /**
     * Obtiene la serie horaria de precipitación pronosticada.
     *
     * @return lista de puntos de lluvia
     */
    public List<PuntoLluviaDTO> getPronosticoLluvia() {
        return pronosticoLluvia;
    }

    /**
     * Establece la serie horaria de precipitación pronosticada.
     *
     * @param pronosticoLluvia lista de puntos de lluvia
     */
    public void setPronosticoLluvia(List<PuntoLluviaDTO> pronosticoLluvia) {
        this.pronosticoLluvia = pronosticoLluvia;
    }
}
