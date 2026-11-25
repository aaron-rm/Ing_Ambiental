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

    /**
     * Obtiene el identificador del evento de alerta.
     *
     * @return identificador único en base de datos
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el identificador del evento de alerta.
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
     * Obtiene el nombre del río asociado al evento.
     *
     * @return nombre del río
     */
    public String getNombreRio() {
        return nombreRio;
    }

    /**
     * Establece el nombre del río asociado al evento.
     *
     * @param nombreRio nombre del río
     */
    public void setNombreRio(String nombreRio) {
        this.nombreRio = nombreRio;
    }

    /**
     * Obtiene el nivel de alerta alcanzado en este evento.
     *
     * @return nivel de alerta actual
     */
    public NivelAlerta getNivel() {
        return nivel;
    }

    /**
     * Establece el nivel de alerta alcanzado en este evento.
     *
     * @param nivel nivel de alerta actual
     */
    public void setNivel(NivelAlerta nivel) {
        this.nivel = nivel;
    }

    /**
     * Obtiene el nivel de alerta previo del tramo antes del cambio.
     *
     * @return nivel de alerta anterior
     */
    public NivelAlerta getNivelAnterior() {
        return nivelAnterior;
    }

    /**
     * Establece el nivel de alerta previo del tramo antes del cambio.
     *
     * @param nivelAnterior nivel de alerta anterior
     */
    public void setNivelAnterior(NivelAlerta nivelAnterior) {
        this.nivelAnterior = nivelAnterior;
    }

    /**
     * Obtiene el nivel de riesgo calculado solo por caudal.
     *
     * @return nivel de riesgo hidrológico
     */
    public NivelAlerta getNivelCaudal() {
        return nivelCaudal;
    }

    /**
     * Establece el nivel de riesgo calculado solo por caudal.
     *
     * @param nivelCaudal nivel de riesgo hidrológico
     */
    public void setNivelCaudal(NivelAlerta nivelCaudal) {
        this.nivelCaudal = nivelCaudal;
    }

    /**
     * Obtiene el nivel de riesgo calculado solo por precipitación.
     *
     * @return nivel de riesgo por lluvia
     */
    public NivelAlerta getNivelLluvia() {
        return nivelLluvia;
    }

    /**
     * Establece el nivel de riesgo calculado solo por precipitación.
     *
     * @param nivelLluvia nivel de riesgo por lluvia
     */
    public void setNivelLluvia(NivelAlerta nivelLluvia) {
        this.nivelLluvia = nivelLluvia;
    }

    /**
     * Obtiene la clasificación de calidad y vigencia de los datos evaluados.
     *
     * @return estado de los datos
     */
    public EstadoDatos getEstadoDatos() {
        return estadoDatos;
    }

    /**
     * Establece la clasificación de calidad y vigencia de los datos evaluados.
     *
     * @param estadoDatos estado de los datos
     */
    public void setEstadoDatos(EstadoDatos estadoDatos) {
        this.estadoDatos = estadoDatos;
    }

    /**
     * Indica si los datos de la evaluación se consideraron confiables.
     *
     * @return {@code true} si la evaluación se basó en datos vigentes y confiables
     */
    public boolean isDatosConfiables() {
        return datosConfiables;
    }

    /**
     * Establece si los datos de la evaluación se consideraron confiables.
     *
     * @param datosConfiables indicador de confiabilidad
     */
    public void setDatosConfiables(boolean datosConfiables) {
        this.datosConfiables = datosConfiables;
    }

    /**
     * Obtiene el caudal máximo pronosticado que originó el evento.
     *
     * @return caudal máximo en m³/s
     */
    public double getCaudalMaximo() {
        return caudalMaximo;
    }

    /**
     * Establece el caudal máximo pronosticado que originó el evento.
     *
     * @param caudalMaximo caudal máximo en m³/s
     */
    public void setCaudalMaximo(double caudalMaximo) {
        this.caudalMaximo = caudalMaximo;
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
     * Obtiene la precipitación acumulada pronosticada en 24 horas.
     *
     * @return lluvia en milímetros o {@code null} si no estuvo disponible
     */
    public Double getLluvia24hMm() {
        return lluvia24hMm;
    }

    /**
     * Establece la precipitación acumulada pronosticada en 24 horas.
     *
     * @param lluvia24hMm lluvia en milímetros
     */
    public void setLluvia24hMm(Double lluvia24hMm) {
        this.lluvia24hMm = lluvia24hMm;
    }

    /**
     * Obtiene la precipitación acumulada pronosticada en 72 horas.
     *
     * @return lluvia en milímetros o {@code null} si no estuvo disponible
     */
    public Double getLluvia72hMm() {
        return lluvia72hMm;
    }

    /**
     * Establece la precipitación acumulada pronosticada en 72 horas.
     *
     * @param lluvia72hMm lluvia en milímetros
     */
    public void setLluvia72hMm(Double lluvia72hMm) {
        this.lluvia72hMm = lluvia72hMm;
    }

    /**
     * Obtiene el mensaje descriptivo generado para el evento.
     *
     * @return texto explicativo de la alerta
     */
    public String getMensaje() {
        return mensaje;
    }

    /**
     * Establece el mensaje descriptivo generado para el evento.
     *
     * @param mensaje texto explicativo de la alerta
     */
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    /**
     * Obtiene los factores de riesgo serializados en líneas de texto.
     *
     * @return texto con factores de riesgo o {@code null}
     */
    public String getFactores() {
        return factores;
    }

    /**
     * Establece los factores de riesgo serializados en líneas de texto.
     *
     * @param factores texto con factores de riesgo
     */
    public void setFactores(String factores) {
        this.factores = factores;
    }

    /**
     * Obtiene las advertencias sobre calidad o fuentes de datos.
     *
     * @return texto con advertencias o {@code null}
     */
    public String getAdvertencias() {
        return advertencias;
    }

    /**
     * Establece las advertencias sobre calidad o fuentes de datos.
     *
     * @param advertencias texto con advertencias
     */
    public void setAdvertencias(String advertencias) {
        this.advertencias = advertencias;
    }

    /**
     * Obtiene el identificador de la fuente de datos de caudal.
     *
     * @return descripción de la fuente hidrológica
     */
    public String getFuenteCaudal() {
        return fuenteCaudal;
    }

    /**
     * Establece el identificador de la fuente de datos de caudal.
     *
     * @param fuenteCaudal descripción de la fuente hidrológica
     */
    public void setFuenteCaudal(String fuenteCaudal) {
        this.fuenteCaudal = fuenteCaudal;
    }

    /**
     * Obtiene el identificador de la fuente de datos de precipitación.
     *
     * @return descripción de la fuente meteorológica
     */
    public String getFuentePrecipitacion() {
        return fuentePrecipitacion;
    }

    /**
     * Establece el identificador de la fuente de datos de precipitación.
     *
     * @param fuentePrecipitacion descripción de la fuente meteorológica
     */
    public void setFuentePrecipitacion(String fuentePrecipitacion) {
        this.fuentePrecipitacion = fuentePrecipitacion;
    }

    /**
     * Obtiene la fecha de emisión de los datos de caudal evaluados.
     *
     * @return instante de emisión del pronóstico
     */
    public Instant getFechaDatos() {
        return fechaDatos;
    }

    /**
     * Establece la fecha de emisión de los datos de caudal evaluados.
     *
     * @param fechaDatos instante de emisión del pronóstico
     */
    public void setFechaDatos(Instant fechaDatos) {
        this.fechaDatos = fechaDatos;
    }

    /**
     * Obtiene la fecha y hora en que se ejecutó la evaluación.
     *
     * @return instante de la evaluación
     */
    public Instant getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    /**
     * Establece la fecha y hora en que se ejecutó la evaluación.
     *
     * @param fechaEvaluacion instante de la evaluación
     */
    public void setFechaEvaluacion(Instant fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
    }

    /**
     * Obtiene la fecha de creación del registro en base de datos.
     *
     * @return instante de creación
     */
    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    /**
     * Establece la fecha de creación del registro en base de datos.
     *
     * @param fechaCreacion instante de creación
     */
    public void setFechaCreacion(Instant fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /**
     * Obtiene el estado del envío de notificaciones push del evento.
     *
     * @return estado de envío actual
     */
    public EstadoEnvio getEstadoEnvio() {
        return estadoEnvio;
    }

    /**
     * Establece el estado del envío de notificaciones push del evento.
     *
     * @param estadoEnvio estado de envío actual
     */
    public void setEstadoEnvio(EstadoEnvio estadoEnvio) {
        this.estadoEnvio = estadoEnvio;
    }

    /**
     * Obtiene la fecha del último intento de envío de notificaciones push.
     *
     * @return instante del intento de envío
     */
    public Instant getFechaIntentoEnvio() {
        return fechaIntentoEnvio;
    }

    /**
     * Establece la fecha del último intento de envío de notificaciones push.
     *
     * @param fechaIntentoEnvio instante del intento de envío
     */
    public void setFechaIntentoEnvio(Instant fechaIntentoEnvio) {
        this.fechaIntentoEnvio = fechaIntentoEnvio;
    }

    /**
     * Obtiene la fecha en que finalizó el envío de notificaciones push.
     *
     * @return instante de finalización del envío
     */
    public Instant getFechaEnvio() {
        return fechaEnvio;
    }

    /**
     * Establece la fecha en que finalizó el envío de notificaciones push.
     *
     * @param fechaEnvio instante de finalización del envío
     */
    public void setFechaEnvio(Instant fechaEnvio) {
        this.fechaEnvio = fechaEnvio;
    }

    /**
     * Obtiene el total de dispositivos objetivo para el envío push.
     *
     * @return cantidad de dispositivos objetivo
     */
    public int getDispositivosObjetivo() {
        return dispositivosObjetivo;
    }

    /**
     * Establece el total de dispositivos objetivo para el envío push.
     *
     * @param dispositivosObjetivo cantidad de dispositivos objetivo
     */
    public void setDispositivosObjetivo(int dispositivosObjetivo) {
        this.dispositivosObjetivo = dispositivosObjetivo;
    }

    /**
     * Obtiene la cantidad de dispositivos a los que se entregó el push correctamente.
     *
     * @return cantidad de envíos exitosos
     */
    public int getDispositivosEnviados() {
        return dispositivosEnviados;
    }

    /**
     * Establece la cantidad de dispositivos a los que se entregó el push correctamente.
     *
     * @param dispositivosEnviados cantidad de envíos exitosos
     */
    public void setDispositivosEnviados(int dispositivosEnviados) {
        this.dispositivosEnviados = dispositivosEnviados;
    }

    /**
     * Obtiene la cantidad de dispositivos cuyo envío push falló.
     *
     * @return cantidad de envíos fallidos
     */
    public int getDispositivosFallidos() {
        return dispositivosFallidos;
    }

    /**
     * Establece la cantidad de dispositivos cuyo envío push falló.
     *
     * @param dispositivosFallidos cantidad de envíos fallidos
     */
    public void setDispositivosFallidos(int dispositivosFallidos) {
        this.dispositivosFallidos = dispositivosFallidos;
    }

    /**
     * Obtiene el detalle del error ocurrido durante el envío push, si existió.
     *
     * @return mensaje de error del envío o {@code null}
     */
    public String getErrorEnvio() {
        return errorEnvio;
    }

    /**
     * Establece el detalle del error ocurrido durante el envío push.
     *
     * @param errorEnvio mensaje de error del envío
     */
    public void setErrorEnvio(String errorEnvio) {
        this.errorEnvio = errorEnvio;
    }

    /**
     * Obtiene la clave única de idempotencia del evento.
     *
     * @return clave de idempotencia
     */
    public String getClaveIdempotencia() {
        return claveIdempotencia;
    }

    /**
     * Establece la clave única de idempotencia del evento.
     *
     * @param claveIdempotencia clave de idempotencia
     */
    public void setClaveIdempotencia(String claveIdempotencia) {
        this.claveIdempotencia = claveIdempotencia;
    }

    /**
     * Obtiene el origen que disparó la evaluación del evento.
     *
     * @return etiqueta de origen (por ejemplo, PROGRAMADO o MANUAL)
     */
    public String getOrigen() {
        return origen;
    }

    /**
     * Establece el origen que disparó la evaluación del evento.
     *
     * @param origen etiqueta de origen
     */
    public void setOrigen(String origen) {
        this.origen = origen;
    }
}
