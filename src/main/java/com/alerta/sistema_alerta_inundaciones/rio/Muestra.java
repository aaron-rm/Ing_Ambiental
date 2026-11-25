package com.alerta.sistema_alerta_inundaciones.rio;

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

import java.time.Instant;

@Entity
@Table(name = "muestras")
public class Muestra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rio_id", nullable = false)
    private Rio rio;

    @Column(nullable = false)
    private Instant fecha;

    @Column(nullable = false, length = 60)
    private String parametro;

    @Column(nullable = false)
    private double valor;

    @Column(nullable = false, length = 20)
    private String unidad = "";

    @Column(length = 500)
    private String nota;

    @Column(nullable = false, length = 80)
    private String responsable;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    public Muestra() {
    }

    public Muestra(Rio rio,
                   Instant fecha,
                   String parametro,
                   double valor,
                   String unidad,
                   String nota,
                   String responsable) {
        this.rio = rio;
        this.fecha = fecha;
        this.parametro = parametro;
        this.valor = valor;
        this.unidad = unidad == null ? "" : unidad;
        this.nota = nota;
        this.responsable = responsable;
    }

    @PrePersist
    void prePersist() {
        if (creadoEn == null) {
            creadoEn = Instant.now();
        }
        if (unidad == null) {
            unidad = "";
        }
    }

    public Long getId() {
        return id;
    }

    public Rio getRio() {
        return rio;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getParametro() {
        return parametro;
    }

    public double getValor() {
        return valor;
    }

    public String getUnidad() {
        return unidad;
    }

    public String getNota() {
        return nota;
    }

    public String getResponsable() {
        return responsable;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
