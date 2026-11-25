package com.alerta.sistema_alerta_inundaciones.rio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA que representa un río monitoreado en el catálogo del sistema,
 * incluyendo su identificador de tramo en GEOGloWS, coordenadas y geometría GeoJSON.
 */
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

    /**
     * Geometría GeoJSON simplificada en WGS84 (LineString o MultiLineString).
     * Se mapea como TEXT, no como LOB: en PostgreSQL un LOB puede quedar ligado
     * a la conexión y fallar con «Unable to access lob stream» al leerlo desde
     * una transacción ya cerrada.
     */
    @Column(columnDefinition = "TEXT")
    private String geometria;

    @Column(name = "creado_por", length = 80)
    private String creadoPor;

    /**
     * Indica que un administrador o investigador cambió el nombre a mano. El
     * inicializador del catálogo no lo sobrescribe en los siguientes arranques.
     * Es {@link Boolean} para que las filas anteriores (columna en NULL) cuenten como no editadas.
     */
    @Column(name = "nombre_editado")
    private Boolean nombreEditado;

    /**
     * Constructor vacío requerido por JPA.
     */
    public Rio() {
    }

    /**
     * Crea un nuevo río con sus datos básicos de identificación y ubicación.
     *
     * @param nombre     nombre del río
     * @param geoglowsId identificador del tramo ({@code reach_id}) en GEOGloWS
     * @param latitud    latitud de referencia en grados decimales
     * @param longitud   longitud de referencia en grados decimales
     */
    public Rio(String nombre, Long geoglowsId, Double latitud, Double longitud) {
        this.nombre = nombre;
        this.geoglowsId = geoglowsId;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    /**
     * Obtiene el identificador interno del río en la base de datos.
     *
     * @return identificador del río
     */
    public Long getId() {
        return id;
    }

    /**
     * Obtiene el nombre del río.
     *
     * @return nombre del río
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * Establece el nombre del río.
     *
     * @param nombre nombre del río
     */
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    /**
     * Obtiene el identificador del tramo en GEOGloWS ({@code reach_id}).
     *
     * @return identificador de GEOGloWS
     */
    public Long getGeoglowsId() {
        return geoglowsId;
    }

    /**
     * Establece el identificador del tramo en GEOGloWS ({@code reach_id}).
     *
     * @param geoglowsId identificador de GEOGloWS
     */
    public void setGeoglowsId(Long geoglowsId) {
        this.geoglowsId = geoglowsId;
    }

    /**
     * Obtiene la latitud del punto de referencia del río.
     *
     * @return latitud en grados decimales
     */
    public Double getLatitud() {
        return latitud;
    }

    /**
     * Establece la latitud del punto de referencia del río.
     *
     * @param latitud latitud en grados decimales
     */
    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    /**
     * Obtiene la longitud del punto de referencia del río.
     *
     * @return longitud en grados decimales
     */
    public Double getLongitud() {
        return longitud;
    }

    /**
     * Establece la longitud del punto de referencia del río.
     *
     * @param longitud longitud en grados decimales
     */
    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    /**
     * Obtiene la geometría GeoJSON almacenada para el tramo del río.
     *
     * @return cadena JSON con la geometría GeoJSON, o {@code null} si no tiene
     */
    public String getGeometria() {
        return geometria;
    }

    /**
     * Establece la geometría GeoJSON del tramo del río.
     *
     * @param geometria cadena JSON con la geometría GeoJSON
     */
    public void setGeometria(String geometria) {
        this.geometria = geometria;
    }

    /**
     * Obtiene el identificador del usuario que creó el río, si fue dado de alta manualmente.
     *
     * @return identificador del creador o {@code null}
     */
    public String getCreadoPor() {
        return creadoPor;
    }

    /**
     * Establece el identificador del usuario que creó el río.
     *
     * @param creadoPor identificador del creador
     */
    public void setCreadoPor(String creadoPor) {
        this.creadoPor = creadoPor;
    }

    /**
     * Indica si el nombre fue cambiado manualmente por un administrador o investigador.
     *
     * @return {@code true} si el nombre fue editado a mano
     */
    public boolean isNombreEditado() {
        return Boolean.TRUE.equals(nombreEditado);
    }

    /**
     * Marca si el nombre fue cambiado manualmente.
     *
     * @param nombreEditado {@code true} si el nombre fue editado a mano
     */
    public void setNombreEditado(boolean nombreEditado) {
        this.nombreEditado = nombreEditado;
    }
}