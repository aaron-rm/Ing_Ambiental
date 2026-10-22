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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getReachId() {
        return reachId;
    }

    public void setReachId(Long reachId) {
        this.reachId = reachId;
    }

    public NivelAlerta getNivelActual() {
        return nivelActual;
    }

    public void setNivelActual(NivelAlerta nivelActual) {
        this.nivelActual = nivelActual;
    }

    public boolean isEvaluacionConfiable() {
        return evaluacionConfiable;
    }

    public void setEvaluacionConfiable(boolean evaluacionConfiable) {
        this.evaluacionConfiable = evaluacionConfiable;
    }

    public double getCaudalMaximo() {
        return caudalMaximo;
    }

    public void setCaudalMaximo(double caudalMaximo) {
        this.caudalMaximo = caudalMaximo;
    }

    public Double getLluvia24hMm() {
        return lluvia24hMm;
    }

    public void setLluvia24hMm(Double lluvia24hMm) {
        this.lluvia24hMm = lluvia24hMm;
    }

    public Instant getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    public void setFechaEvaluacion(Instant fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
    }

    public Instant getFechaDatos() {
        return fechaDatos;
    }

    public void setFechaDatos(Instant fechaDatos) {
        this.fechaDatos = fechaDatos;
    }

    public Long getUltimaAlertaId() {
        return ultimaAlertaId;
    }

    public void setUltimaAlertaId(Long ultimaAlertaId) {
        this.ultimaAlertaId = ultimaAlertaId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
