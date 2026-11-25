package com.alerta.sistema_alerta_inundaciones.alerta;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad JPA que representa una suscripción heredada por correo o teléfono
 * asociada a un tramo específico para avisos de alerta.
 */
@Entity
@Table(name = "suscripciones_alerta")
public class SuscripcionAlerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reachId;

    private String nombreContacto;

    private String contacto; // Email o teléfono

    private LocalDateTime fechaRegistro;

    /** Crea una suscripción vacía requerida por JPA. */
    public SuscripcionAlerta() {
    }

    /**
     * Crea una suscripción con la fecha de registro actual.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @param nombreContacto nombre de la persona suscrita
     * @param contacto correo electrónico o teléfono de contacto
     */
    public SuscripcionAlerta(Long reachId, String nombreContacto, String contacto) {
        this.reachId = reachId;
        this.nombreContacto = nombreContacto;
        this.contacto = contacto;
        this.fechaRegistro = LocalDateTime.now();
    }

    /**
     * Obtiene el identificador de la suscripción.
     *
     * @return identificador único en base de datos
     */
    public Long getId() {
        return id;
    }

    /**
     * Obtiene el identificador del tramo suscrito.
     *
     * @return identificador del tramo en GEOGloWS
     */
    public Long getReachId() {
        return reachId;
    }

    /**
     * Establece el identificador del tramo suscrito.
     *
     * @param reachId identificador del tramo en GEOGloWS
     */
    public void setReachId(Long reachId) {
        this.reachId = reachId;
    }

    /**
     * Obtiene el nombre del contacto suscrito.
     *
     * @return nombre del contacto
     */
    public String getNombreContacto() {
        return nombreContacto;
    }

    /**
     * Establece el nombre del contacto suscrito.
     *
     * @param nombreContacto nombre del contacto
     */
    public void setNombreContacto(String nombreContacto) {
        this.nombreContacto = nombreContacto;
    }

    /**
     * Obtiene el medio de contacto registrado.
     *
     * @return correo electrónico o teléfono del contacto
     */
    public String getContacto() {
        return contacto;
    }

    /**
     * Establece el medio de contacto registrado.
     *
     * @param contacto correo electrónico o teléfono del contacto
     */
    public void setContacto(String contacto) {
        this.contacto = contacto;
    }

    /**
     * Obtiene la fecha y hora de registro de la suscripción.
     *
     * @return fecha y hora de creación
     */
    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    /**
     * Establece la fecha y hora de registro de la suscripción.
     *
     * @param fechaRegistro fecha y hora de creación
     */
    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}
