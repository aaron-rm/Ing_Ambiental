package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Token de registro FCM asociado a una instalación (deviceId) y a un usuario.
 * No se expone en las respuestas de la API.
 */
@Entity
@Table(name = "dispositivos_push",
        uniqueConstraints = @UniqueConstraint(name = "uk_dispositivo_push_token", columnNames = "token"),
        indexes = {
                @Index(name = "idx_dispositivo_push_usuario", columnList = "usuario_id,activo"),
                @Index(name = "idx_dispositivo_push_device", columnList = "device_id")
        })
public class DispositivoPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /**
     * Columna heredada de la versión anterior. Ya no define el enrutamiento del
     * push; se conserva con valor 0 para no romper bases de datos existentes.
     */
    @Column(name = "reach_id", nullable = false)
    private Long reachId = 0L;

    @Column(length = 30)
    private String plataforma;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    // Antes de insertar: fija la fecha de registro (si falta) y la de actualización.
    @PrePersist
    void prePersist() {
        Instant ahora = Instant.now();
        if (fechaRegistro == null) {
            fechaRegistro = ahora;
        }
        fechaActualizacion = ahora;
    }

    // Antes de actualizar: refresca la fecha de actualización.
    @PreUpdate
    void preUpdate() {
        fechaActualizacion = Instant.now();
    }

    /**
     * Obtiene el identificador del registro.
     *
     * @return identificador generado por la base de datos
     */
    public Long getId() {
        return id;
    }

    /**
     * Establece el identificador del registro.
     *
     * @param id identificador del registro
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Obtiene el token de registro FCM.
     *
     * @return token FCM del dispositivo
     */
    public String getToken() {
        return token;
    }

    /**
     * Establece el token de registro FCM.
     *
     * @param token token FCM del dispositivo
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Obtiene el identificador de la instalación.
     *
     * @return identificador del dispositivo
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Establece el identificador de la instalación.
     *
     * @param deviceId identificador del dispositivo
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Obtiene el usuario dueño del dispositivo.
     *
     * @return usuario asociado
     */
    public Usuario getUsuario() {
        return usuario;
    }

    /**
     * Establece el usuario dueño del dispositivo.
     *
     * @param usuario usuario asociado
     */
    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    /**
     * Obtiene el reachId heredado (siempre 0 en el modelo actual).
     *
     * @return valor de la columna heredada
     */
    public Long getReachId() {
        return reachId;
    }

    /**
     * Establece el reachId heredado.
     *
     * @param reachId valor de la columna heredada
     */
    public void setReachId(Long reachId) {
        this.reachId = reachId;
    }

    /**
     * Obtiene la plataforma del dispositivo.
     *
     * @return plataforma (por ejemplo "web")
     */
    public String getPlataforma() {
        return plataforma;
    }

    /**
     * Establece la plataforma del dispositivo.
     *
     * @param plataforma plataforma (por ejemplo "web")
     */
    public void setPlataforma(String plataforma) {
        this.plataforma = plataforma;
    }

    /**
     * Indica si la suscripción está activa.
     *
     * @return {@code true} si el dispositivo recibe notificaciones
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * Activa o desactiva la suscripción.
     *
     * @param activo {@code true} para recibir notificaciones
     */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /**
     * Obtiene la fecha de registro.
     *
     * @return instante en que se creó el registro
     */
    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    /**
     * Establece la fecha de registro.
     *
     * @param fechaRegistro instante de creación del registro
     */
    public void setFechaRegistro(Instant fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    /**
     * Obtiene la fecha de la última actualización.
     *
     * @return instante de la última modificación
     */
    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    /**
     * Establece la fecha de la última actualización.
     *
     * @param fechaActualizacion instante de la última modificación
     */
    public void setFechaActualizacion(Instant fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
