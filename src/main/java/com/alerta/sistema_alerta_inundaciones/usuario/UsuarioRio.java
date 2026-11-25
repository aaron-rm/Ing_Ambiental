package com.alerta.sistema_alerta_inundaciones.usuario;

import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Entidad JPA que representa la suscripción o selección de seguimiento
 * de un {@link Usuario} sobre un {@link Rio} para recibir notificaciones de alerta.
 */
@Entity
@Table(name = "usuario_rios",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuario_rio",
                columnNames = {"usuario_id", "rio_id"}))
public class UsuarioRio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rio_id", nullable = false)
    private Rio rio;

    @Column(name = "fecha_seleccion", nullable = false, updatable = false)
    private Instant fechaSeleccion;

    /**
     * Constructor vacío requerido por JPA.
     */
    public UsuarioRio() {
    }

    /**
     * Crea una relación de seguimiento entre un usuario y un río.
     *
     * @param usuario usuario que sigue al río
     * @param rio     río seguido
     */
    public UsuarioRio(Usuario usuario, Rio rio) {
        this.usuario = usuario;
        this.rio = rio;
    }

    @PrePersist
    void prePersist() {
        if (fechaSeleccion == null) {
            fechaSeleccion = Instant.now();
        }
    }

    /**
     * Obtiene el identificador único de la relación usuario-río.
     *
     * @return identificador del registro
     */
    public Long getId() {
        return id;
    }

    /**
     * Obtiene el usuario que sigue al río.
     *
     * @return entidad {@link Usuario}
     */
    public Usuario getUsuario() {
        return usuario;
    }

    /**
     * Obtiene el río seleccionado por el usuario.
     *
     * @return entidad {@link Rio}
     */
    public Rio getRio() {
        return rio;
    }

    /**
     * Obtiene la fecha y hora en que el usuario comenzó a seguir el río.
     *
     * @return instante de selección
     */
    public Instant getFechaSeleccion() {
        return fechaSeleccion;
    }
}
