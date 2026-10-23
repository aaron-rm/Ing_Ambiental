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
    @Column(nullable = false, length = 10)
    private Rol rol = Rol.USER;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    public Usuario() {
    }

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

    public static String normalizar(String identificador) {
        return identificador == null ? null : identificador.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getIdentificador() {
        return identificador;
    }

    public void setIdentificador(String identificador) {
        this.identificador = normalizar(identificador);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }
}
