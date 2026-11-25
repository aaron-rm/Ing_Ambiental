package com.alerta.sistema_alerta_inundaciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firebase")
public class PropiedadesFirebase {

    /** Permite desactivar FCM sin borrar credenciales (útil en desarrollo/pruebas). */
    private boolean enabled = true;

    /** Ruta absoluta o relativa al archivo JSON de la cuenta de servicio. */
    private String credentialsPath = "";

    /**
     * Contenido JSON de la cuenta de servicio, o su representación Base64.
     * Pensado para variables de entorno; nunca debe incluirse en el repositorio.
     */
    private String credentialsJson = "";

    /** Proyecto de Firebase. Si está vacío se intenta leer de la cuenta de servicio. */
    private String projectId = "";

    private Publica publica = new Publica();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCredentialsPath() {
        return credentialsPath;
    }

    public void setCredentialsPath(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    public String getCredentialsJson() {
        return credentialsJson;
    }

    public void setCredentialsJson(String credentialsJson) {
        this.credentialsJson = credentialsJson;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public Publica getPublica() {
        return publica;
    }

    public void setPublica(Publica publica) {
        this.publica = publica;
    }

    /** Datos públicos de la app web de Firebase (no son secretos). */
    public static class Publica {
        private String apiKey = "";
        private String authDomain = "";
        private String projectId = "";
        private String messagingSenderId = "";
        private String appId = "";
        private String vapidKey = "";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAuthDomain() {
            return authDomain;
        }

        public void setAuthDomain(String authDomain) {
            this.authDomain = authDomain;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getMessagingSenderId() {
            return messagingSenderId;
        }

        public void setMessagingSenderId(String messagingSenderId) {
            this.messagingSenderId = messagingSenderId;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getVapidKey() {
            return vapidKey;
        }

        public void setVapidKey(String vapidKey) {
            this.vapidKey = vapidKey;
        }
    }
}
