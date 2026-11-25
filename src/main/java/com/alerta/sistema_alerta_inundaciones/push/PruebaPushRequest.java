package com.alerta.sistema_alerta_inundaciones.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de las solicitudes que identifican un dispositivo ya registrado
 * (desactivar suscripción y enviar notificación de prueba).
 */
public class PruebaPushRequest {

    @NotBlank(message = "El token FCM es obligatorio.")
    @Size(min = 20, max = 512, message = "El token FCM tiene una longitud inválida.")
    private String token;

    @NotBlank(message = "El identificador del dispositivo es obligatorio.")
    @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$", message = "El identificador del dispositivo es inválido.")
    private String deviceId;

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
}
