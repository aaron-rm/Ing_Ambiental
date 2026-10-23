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

    public UsuarioRio() {
    }

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

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Rio getRio() {
        return rio;
    }

    public Instant getFechaSeleccion() {
        return fechaSeleccion;
    }
}
