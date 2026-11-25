package com.alerta.sistema_alerta_inundaciones.alerta;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    public SuscripcionAlerta() {
    }

    public SuscripcionAlerta(Long reachId, String nombreContacto, String contacto) {
        this.reachId = reachId;
        this.nombreContacto = nombreContacto;
        this.contacto = contacto;
        this.fechaRegistro = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getReachId() {
        return reachId;
    }

    public void setReachId(Long reachId) {
        this.reachId = reachId;
    }

    public String getNombreContacto() {
        return nombreContacto;
    }

    public void setNombreContacto(String nombreContacto) {
        this.nombreContacto = nombreContacto;
    }

    public String getContacto() {
        return contacto;
    }

    public void setContacto(String contacto) {
        this.contacto = contacto;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
}
