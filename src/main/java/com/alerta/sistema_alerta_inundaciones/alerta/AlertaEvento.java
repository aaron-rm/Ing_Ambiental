package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.riesgo.EstadoDatos;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Evento persistido cuando el nivel de riesgo de un tramo cambia de forma
 * relevante. Es la fuente de verdad del historial y del estado de envío, por
 * lo que un reinicio del backend no vuelve a notificar el mismo evento.
 */
@Entity
@Table(name = "alertas_evento",
        uniqueConstraints = @UniqueConstraint(name = "uk_alerta_evento_clave", columnNames = "clave_idempotencia"),
        indexes = {
                @Index(name = "idx_alerta_evento_reach", columnList = "reach_id"),
                @Index(name = "idx_alerta_evento_creacion", columnList = "fecha_creacion")
        })
public class AlertaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reach_id", nullable = false)
    private Long reachId;

    @Column(name = "nombre_rio", length = 200)
    private String nombreRio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelAlerta nivel;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_anterior", nullable = false, length = 20)
    private NivelAlerta nivelAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_caudal", length = 20)
    private NivelAlerta nivelCaudal;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_lluvia", length = 20)
    private NivelAlerta nivelLluvia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_datos", length = 20)
    private EstadoDatos estadoDatos;

    @Column(name = "datos_confiables", nullable = false)
    private boolean datosConfiables;

    @Column(name = "caudal_maximo")
    private double caudalMaximo;

    @Column(name = "umbral_vigilancia")
    private double umbralVigilancia;

    @Column(name = "umbral_alerta")
    private double umbralAlerta;

    @Column(name = "umbral_emergencia")
    private double umbralEmergencia;

    @Column(name = "lluvia_24h_mm")
    private Double lluvia24hMm;

    @Column(name = "lluvia_72h_mm")
    private Double lluvia72hMm;

    @Column(columnDefinition = "TEXT")
    private String mensaje;

    @Column(columnDefinition = "TEXT")
    private String factores;

    @Column(columnDefinition = "TEXT")
    private String advertencias;

    @Column(name = "fuente_caudal", length = 250)
    private String fuenteCaudal;

    @Column(name = "fuente_precipitacion", length = 250)
    private String fuentePrecipitacion;

    @Column(name = "fecha_datos")
    private Instant fechaDatos;

    @Column(name = "fecha_evaluacion", nullable = false)
    private Instant fechaEvaluacion;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_envio", nullable = false, length = 20)
    private EstadoEnvio estadoEnvio = EstadoEnvio.PENDIENTE;

    @Column(name = "fecha_intento_envio")
    private Instant fechaIntentoEnvio;

    @Column(name = "fecha_envio")
    private Instant fechaEnvio;

    @Column(name = "dispositivos_objetivo")
    private int dispositivosObjetivo;

    @Column(name = "dispositivos_enviados")
    private int dispositivosEnviados;

    @Column(name = "dispositivos_fallidos")
    private int dispositivosFallidos;

    @Column(name = "error_envio", length = 1000)
    private String errorEnvio;

    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 200)
    private String claveIdempotencia;

    @Column(length = 30)
    private String origen;

    @PrePersist
    void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = Instant.now();
        }
        if (fechaEvaluacion == null) {
            fechaEvaluacion = fechaCreacion;
        }
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

    public String getNombreRio() {
        return nombreRio;
    }

    public void setNombreRio(String nombreRio) {
        this.nombreRio = nombreRio;
    }

    public NivelAlerta getNivel() {
        return nivel;
    }

    public void setNivel(NivelAlerta nivel) {
        this.nivel = nivel;
    }

    public NivelAlerta getNivelAnterior() {
        return nivelAnterior;
    }

    public void setNivelAnterior(NivelAlerta nivelAnterior) {
        this.nivelAnterior = nivelAnterior;
    }

    public NivelAlerta getNivelCaudal() {
        return nivelCaudal;
    }

    public void setNivelCaudal(NivelAlerta nivelCaudal) {
        this.nivelCaudal = nivelCaudal;
    }

    public NivelAlerta getNivelLluvia() {
        return nivelLluvia;
    }

    public void setNivelLluvia(NivelAlerta nivelLluvia) {
        this.nivelLluvia = nivelLluvia;
    }

    public EstadoDatos getEstadoDatos() {
        return estadoDatos;
    }

    public void setEstadoDatos(EstadoDatos estadoDatos) {
        this.estadoDatos = estadoDatos;
    }

    public boolean isDatosConfiables() {
        return datosConfiables;
    }

    public void setDatosConfiables(boolean datosConfiables) {
        this.datosConfiables = datosConfiables;
    }

    public double getCaudalMaximo() {
        return caudalMaximo;
    }

    public void setCaudalMaximo(double caudalMaximo) {
        this.caudalMaximo = caudalMaximo;
    }

    public double getUmbralVigilancia() {
        return umbralVigilancia;
    }

    public void setUmbralVigilancia(double umbralVigilancia) {
        this.umbralVigilancia = umbralVigilancia;
    }

    public double getUmbralAlerta() {
        return umbralAlerta;
    }

    public void setUmbralAlerta(double umbralAlerta) {
        this.umbralAlerta = umbralAlerta;
    }

    public double getUmbralEmergencia() {
        return umbralEmergencia;
    }

    public void setUmbralEmergencia(double umbralEmergencia) {
        this.umbralEmergencia = umbralEmergencia;
    }

    public Double getLluvia24hMm() {
        return lluvia24hMm;
    }

    public void setLluvia24hMm(Double lluvia24hMm) {
        this.lluvia24hMm = lluvia24hMm;
    }

    public Double getLluvia72hMm() {
        return lluvia72hMm;
    }

    public void setLluvia72hMm(Double lluvia72hMm) {
        this.lluvia72hMm = lluvia72hMm;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getFactores() {
        return factores;
    }

    public void setFactores(String factores) {
        this.factores = factores;
    }

    public String getAdvertencias() {
        return advertencias;
    }

    public void setAdvertencias(String advertencias) {
        this.advertencias = advertencias;
    }

    public String getFuenteCaudal() {
        return fuenteCaudal;
    }

    public void setFuenteCaudal(String fuenteCaudal) {
        this.fuenteCaudal = fuenteCaudal;
    }

    public String getFuentePrecipitacion() {
        return fuentePrecipitacion;
    }

    public void setFuentePrecipitacion(String fuentePrecipitacion) {
        this.fuentePrecipitacion = fuentePrecipitacion;
    }

    public Instant getFechaDatos() {
        return fechaDatos;
    }

    public void setFechaDatos(Instant fechaDatos) {
        this.fechaDatos = fechaDatos;
    }

    public Instant getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    public void setFechaEvaluacion(Instant fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public EstadoEnvio getEstadoEnvio() {
        return estadoEnvio;
    }

    public void setEstadoEnvio(EstadoEnvio estadoEnvio) {
        this.estadoEnvio = estadoEnvio;
    }

    public Instant getFechaIntentoEnvio() {
        return fechaIntentoEnvio;
    }

    public void setFechaIntentoEnvio(Instant fechaIntentoEnvio) {
        this.fechaIntentoEnvio = fechaIntentoEnvio;
    }

    public Instant getFechaEnvio() {
        return fechaEnvio;
    }

    public void setFechaEnvio(Instant fechaEnvio) {
        this.fechaEnvio = fechaEnvio;
    }

    public int getDispositivosObjetivo() {
        return dispositivosObjetivo;
    }

    public void setDispositivosObjetivo(int dispositivosObjetivo) {
        this.dispositivosObjetivo = dispositivosObjetivo;
    }

    public int getDispositivosEnviados() {
        return dispositivosEnviados;
    }

    public void setDispositivosEnviados(int dispositivosEnviados) {
        this.dispositivosEnviados = dispositivosEnviados;
    }

    public int getDispositivosFallidos() {
        return dispositivosFallidos;
    }

    public void setDispositivosFallidos(int dispositivosFallidos) {
        this.dispositivosFallidos = dispositivosFallidos;
    }

    public String getErrorEnvio() {
        return errorEnvio;
    }

    public void setErrorEnvio(String errorEnvio) {
        this.errorEnvio = errorEnvio;
    }

    public String getClaveIdempotencia() {
        return claveIdempotencia;
    }

    public void setClaveIdempotencia(String claveIdempotencia) {
        this.claveIdempotencia = claveIdempotencia;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }
}
