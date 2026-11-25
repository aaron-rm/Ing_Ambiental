package com.alerta.sistema_alerta_inundaciones.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la solicitud para registrar un dispositivo en el push: token FCM,
 * identificador de la instalación y plataforma opcional.
 */
public class RegistroPushRequest {

    @NotBlank(message = "El token FCM es obligatorio.")
    @Size(min = 20, max = 512, message = "El token FCM tiene una longitud inválida.")
    @Pattern(regexp = "^[A-Za-z0-9_\\-:.=]+$", message = "El token FCM contiene caracteres inválidos.")
    private String token;

    @NotBlank(message = "El identificador del dispositivo es obligatorio.")
    @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$", message = "El identificador del dispositivo es inválido.")
    private String deviceId;

    @Size(max = 30, message = "La plataforma es demasiado larga.")
    private String plataforma;

    /**
     * Obtiene el token FCM.
     *
     * @return token FCM del dispositivo
     */
    public String getToken() {
        return token;
    }

    /**
     * Establece el token FCM.
     *
     * @param token token FCM del dispositivo
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Obtiene el identificador de la instalación.
     *
     * @return identificador del dispositivo
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Establece el identificador de la instalación.
     *
     * @param deviceId identificador del dispositivo
     */
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Obtiene la plataforma del dispositivo.
     *
     * @return plataforma (por ejemplo "web"), o {@code null} si no se indicó
     */
    public String getPlataforma() {
        return plataforma;
    }

    /**
     * Establece la plataforma del dispositivo.
     *
     * @param plataforma plataforma (por ejemplo "web")
     */
    public void setPlataforma(String plataforma) {
        this.plataforma = plataforma;
    }
}
