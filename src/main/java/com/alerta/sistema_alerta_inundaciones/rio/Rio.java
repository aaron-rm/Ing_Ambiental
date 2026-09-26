package com.alerta.sistema_alerta_inundaciones.rio;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rios")
public class Rio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private Long geoglowsId;

    private Double latitud;

    private Double longitud;

    public Rio() {
    }

    public Rio(String nombre, Long geoglowsId, Double latitud, Double longitud) {
        this.nombre = nombre;
        this.geoglowsId = geoglowsId;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Long getGeoglowsId() {
        return geoglowsId;
    }

    public void setGeoglowsId(Long geoglowsId) {
        this.geoglowsId = geoglowsId;
    }

    public Double getLatitud() {
        return latitud;
    }

    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }
}