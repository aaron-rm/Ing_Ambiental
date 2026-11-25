package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Estado actual del riesgo por tramo. Se persiste para que la detección de
 * cambios sea idempotente incluso después de reiniciar el backend.
 */
@Entity
@Table(name = "estado_riesgo_tramo",
        uniqueConstraints = @UniqueConstraint(name = "uk_estado_riesgo_reach", columnNames = "reach_id"))
public class EstadoRiesgoTramo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reach_id", nullable = false, unique = true)
    private Long reachId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_actual", nullable = false, length = 20)
    private NivelAlerta nivelActual = NivelAlerta.NORMAL;

    @Column(name = "evaluacion_confiable", nullable = false)
    private boolean evaluacionConfiable;

    @Column(name = "caudal_maximo")
    private double caudalMaximo;

    @Column(name = "lluvia_24h_mm")
    private Double lluvia24hMm;

    @Column(name = "fecha_evaluacion")
    private Instant fechaEvaluacion;

    @Column(name = "fecha_datos")
    private Instant fechaDatos;

    @Column(name = "ultima_alerta_id")
    private Long ultimaAlertaId;

    @Version
    private Long version;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    @PrePersist
    @PreUpdate
    void actualizarFecha() {
        fechaActualizacion = Instant.now();
    }

    /**
     * Obtiene el identificador del registro de estado.
     *
     * @return identificador único en base de datos
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el identificador del registro de estado.
     *
     * @param id identificador único en base de datos
     */
    public void setId(Long id) {
        this.id = id;
    }

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
     * Obtiene el nivel de alerta vigente del tramo.
     *
     * @return nivel de alerta actual
     */
    public NivelAlerta getNivelActual() {
        return nivelActual;
    }

    /**
     * Establece el nivel de alerta vigente del tramo.
     *
     * @param nivelActual nivel de alerta actual
     */
    public void setNivelActual(NivelAlerta nivelActual) {
        this.nivelActual = nivelActual;
    }

    /**
     * Indica si la última evaluación del tramo se consideró confiable.
     *
     * @return {@code true} si la evaluación fue confiable
     */
    public boolean isEvaluacionConfiable() {
        return evaluacionConfiable;
    }

    /**
     * Establece si la última evaluación del tramo se consideró confiable.
     *
     * @param evaluacionConfiable indicador de confiabilidad
     */
    public void setEvaluacionConfiable(boolean evaluacionConfiable) {
        this.evaluacionConfiable = evaluacionConfiable;
    }

    /**
     * Obtiene el caudal máximo registrado en la última evaluación.
     *
     * @return caudal máximo en m³/s
     */
    public double getCaudalMaximo() {
        return caudalMaximo;
    }

    /**
     * Establece el caudal máximo registrado en la última evaluación.
     *
     * @param caudalMaximo caudal máximo en m³/s
     */
    public void setCaudalMaximo(double caudalMaximo) {
        this.caudalMaximo = caudalMaximo;
    }

    /**
     * Obtiene la lluvia acumulada en 24 horas de la última evaluación.
     *
     * @return precipitación en mm o {@code null}
     */
    public Double getLluvia24hMm() {
        return lluvia24hMm;
    }

    /**
     * Establece la lluvia acumulada en 24 horas de la última evaluación.
     *
     * @param lluvia24hMm precipitación en mm
     */
    public void setLluvia24hMm(Double lluvia24hMm) {
        this.lluvia24hMm = lluvia24hMm;
    }

    /**
     * Obtiene la fecha en que se realizó la última evaluación.
     *
     * @return instante de evaluación
     */
    public Instant getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    /**
     * Establece la fecha en que se realizó la última evaluación.
     *
     * @param fechaEvaluacion instante de evaluación
     */
    public void setFechaEvaluacion(Instant fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
    }

    /**
     * Obtiene la fecha de emisión de los datos hidrológicos evaluados.
     *
     * @return instante de emisión de datos
     */
    public Instant getFechaDatos() {
        return fechaDatos;
    }

    /**
     * Establece la fecha de emisión de los datos hidrológicos evaluados.
     *
     * @param fechaDatos instante de emisión de datos
     */
    public void setFechaDatos(Instant fechaDatos) {
        this.fechaDatos = fechaDatos;
    }

    /**
     * Obtiene el identificador del último evento de alerta generado para el tramo.
     *
     * @return identificador del evento o {@code null}
     */
    public Long getUltimaAlertaId() {
        return ultimaAlertaId;
    }

    /**
     * Establece el identificador del último evento de alerta generado para el tramo.
     *
     * @param ultimaAlertaId identificador del evento
     */
    public void setUltimaAlertaId(Long ultimaAlertaId) {
        this.ultimaAlertaId = ultimaAlertaId;
    }

    /**
     * Obtiene el número de versión para control de concurrencia optimista.
     *
     * @return versión del registro
     */
    public Long getVersion() {
        return version;
    }

    /**
     * Establece el número de versión para control de concurrencia optimista.
     *
     * @param version versión del registro
     */
    public void setVersion(Long version) {
        this.version = version;
    }

    /**
     * Obtiene la fecha de la última actualización del registro en base de datos.
     *
     * @return instante de actualización
     */
    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    /**
     * Establece la fecha de la última actualización del registro en base de datos.
     *
     * @param fechaActualizacion instante de actualización
     */
    public void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
