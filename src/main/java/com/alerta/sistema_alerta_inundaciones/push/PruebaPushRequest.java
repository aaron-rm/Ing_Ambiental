package com.alerta.sistema_alerta_inundaciones.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PruebaPushRequest {

    @NotBlank(message = "El token FCM es obligatorio.")
    @Size(min = 20, max = 512, message = "El token FCM tiene una longitud inválida.")
    private String token;

    @NotBlank(message = "El identificador del dispositivo es obligatorio.")
    @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$", message = "El identificador del dispositivo es inválido.")
    private String deviceId;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
