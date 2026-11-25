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

/**
 * Entidad JPA que representa una muestra o medición de calidad/parámetro ambiental
 * tomada en un río por un usuario autorizado.
 */
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

    /**
     * Constructor vacío requerido por JPA.
     */
    public Muestra() {
    }

    /**
     * Crea una nueva muestra asociada a un río.
     *
     * @param rio         río al que pertenece la muestra
     * @param fecha       instante en que se tomó la muestra
     * @param parametro   nombre del parámetro medido
     * @param valor       valor numérico de la medición
     * @param unidad      unidad de medida (si es {@code null} se guarda cadena vacía)
     * @param nota        observaciones adicionales opcionales
     * @param responsable identificador del usuario que registra la muestra
     */
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

    /**
     * Obtiene el identificador único de la muestra.
     *
     * @return identificador de la muestra
     */
    public Long getId() {
        return id;
    }

    /**
     * Obtiene el río al que pertenece la muestra.
     *
     * @return entidad {@link Rio} asociada
     */
    public Rio getRio() {
        return rio;
    }

    /**
     * Obtiene el instante en que fue tomada la muestra.
     *
     * @return fecha y hora de la muestra
     */
    public Instant getFecha() {
        return fecha;
    }

    /**
     * Obtiene el nombre del parámetro medido.
     *
     * @return nombre del parámetro
     */
    public String getParametro() {
        return parametro;
    }

    /**
     * Obtiene el valor numérico de la medición.
     *
     * @return valor medido
     */
    public double getValor() {
        return valor;
    }

    /**
     * Obtiene la unidad de medida del parámetro.
     *
     * @return unidad de medida
     */
    public String getUnidad() {
        return unidad;
    }

    /**
     * Obtiene la nota u observación asociada a la muestra.
     *
     * @return nota de la muestra, o {@code null} si no tiene
     */
    public String getNota() {
        return nota;
    }

    /**
     * Obtiene el identificador del usuario responsable del registro.
     *
     * @return identificador del responsable
     */
    public String getResponsable() {
        return responsable;
    }

    /**
     * Obtiene el instante en que se persistió el registro en la base de datos.
     *
     * @return fecha y hora de creación del registro
     */
    public Instant getCreadoEn() {
        return creadoEn;
    }
}
