package com.alerta.sistema_alerta_inundaciones.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;

/**
 * Entidad JPA que representa una cuenta de usuario registrada en el sistema,
 * con su identificador único normalizado, contraseña codificada y rol de acceso.
 */
@Entity
@Table(name = "usuarios",
        uniqueConstraints = @UniqueConstraint(name = "uk_usuario_identificador", columnNames = "identificador"))
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correo o nombre de usuario, siempre normalizado a minúsculas. */
    @Column(nullable = false, unique = true, length = 80)
    private String identificador;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol = Rol.USER;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    /**
     * Constructor vacío requerido por JPA.
     */
    public Usuario() {
    }

    /**
     * Crea un usuario normalizando su identificador a minúsculas.
     *
     * @param identificador correo o nombre de usuario
     * @param passwordHash  hash BCrypt de la contraseña
     * @param rol           rol asignado al usuario
     */
    public Usuario(String identificador, String passwordHash, Rol rol) {
        this.identificador = normalizar(identificador);
        this.passwordHash = passwordHash;
        this.rol = rol;
    }

    @PrePersist
    void prePersist() {
        if (fechaRegistro == null) {
            fechaRegistro = Instant.now();
        }
    }

    /**
     * Recorta espacios y convierte un identificador a minúsculas de forma uniforme.
     *
     * @param identificador cadena original del identificador
     * @return identificador en minúsculas sin espacios laterales, o {@code null} si la entrada es {@code null}
     */
    public static String normalizar(String identificador) {
        return identificador == null ? null : identificador.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Obtiene el identificador primario del usuario.
     *
     * @return identificador numérico del usuario
     */
    public Long getId() {
        return id;
    }

    /**
     * Obtiene el identificador único (correo o nombre de usuario) normalizado.
     *
     * @return identificador del usuario
     */
    public String getIdentificador() {
        return identificador;
    }

    /**
     * Establece el identificador del usuario aplicándole normalización a minúsculas.
     *
     * @param identificador correo o nombre de usuario
     */
    public void setIdentificador(String identificador) {
        this.identificador = normalizar(identificador);
    }

    /**
     * Obtiene el hash de la contraseña del usuario.
     *
     * @return hash de la contraseña
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Establece el hash de la contraseña del usuario.
     *
     * @param passwordHash hash de la contraseña
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Obtiene el rol de autorización del usuario.
     *
     * @return rol del usuario
     */
    public Rol getRol() {
        return rol;
    }

    /**
     * Establece el rol de autorización del usuario.
     *
     * @param rol nuevo rol del usuario
     */
    public void setRol(Rol rol) {
        this.rol = rol;
    }

    /**
     * Obtiene la fecha y hora en que se registró la cuenta.
     *
     * @return instante de registro
     */
    public Instant getFechaRegistro() {
        return fechaRegistro;
    }
}
