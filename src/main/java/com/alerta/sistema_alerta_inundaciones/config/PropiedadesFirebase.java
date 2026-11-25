package com.alerta.sistema_alerta_inundaciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración bajo el prefijo {@code firebase} para inicializar
 * Firebase Cloud Messaging (FCM) en el backend y exponer la configuración web pública.
 */
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

    /**
     * Indica si la integración con Firebase Cloud Messaging está habilitada.
     *
     * @return {@code true} si FCM está habilitado
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Habilita o deshabilita la integración con Firebase Cloud Messaging.
     *
     * @param enabled {@code true} para activar FCM
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Obtiene la ruta al archivo JSON de credenciales de la cuenta de servicio.
     *
     * @return ruta del archivo de credenciales
     */
    public String getCredentialsPath() {
        return credentialsPath;
    }

    /**
     * Establece la ruta al archivo JSON de credenciales de la cuenta de servicio.
     *
     * @param credentialsPath ruta del archivo de credenciales
     */
    public void setCredentialsPath(String credentialsPath) {
        this.credentialsPath = credentialsPath;
    }

    /**
     * Obtiene el contenido JSON o Base64 de las credenciales de la cuenta de servicio.
     *
     * @return cadena con el JSON o Base64 de credenciales
     */
    public String getCredentialsJson() {
        return credentialsJson;
    }

    /**
     * Establece el contenido JSON o Base64 de las credenciales de la cuenta de servicio.
     *
     * @param credentialsJson cadena con el JSON o Base64 de credenciales
     */
    public void setCredentialsJson(String credentialsJson) {
        this.credentialsJson = credentialsJson;
    }

    /**
     * Obtiene el identificador del proyecto de Firebase.
     *
     * @return identificador del proyecto
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Establece el identificador del proyecto de Firebase.
     *
     * @param projectId identificador del proyecto
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /**
     * Obtiene el bloque de propiedades públicas para clientes web de Firebase.
     *
     * @return configuración pública de Firebase
     */
    public Publica getPublica() {
        return publica;
    }

    /**
     * Establece el bloque de propiedades públicas para clientes web de Firebase.
     *
     * @param publica configuración pública de Firebase
     */
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

        /**
         * Obtiene la clave de API pública de la aplicación web de Firebase.
         *
         * @return clave de API web
         */
        public String getApiKey() {
            return apiKey;
        }

        /**
         * Establece la clave de API pública de la aplicación web de Firebase.
         *
         * @param apiKey clave de API web
         */
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * Obtiene el dominio de autenticación de Firebase.
         *
         * @return dominio de autenticación
         */
        public String getAuthDomain() {
            return authDomain;
        }

        /**
         * Establece el dominio de autenticación de Firebase.
         *
         * @param authDomain dominio de autenticación
         */
        public void setAuthDomain(String authDomain) {
            this.authDomain = authDomain;
        }

        /**
         * Obtiene el identificador público del proyecto de Firebase para el cliente web.
         *
         * @return identificador del proyecto
         */
        public String getProjectId() {
            return projectId;
        }

        /**
         * Establece el identificador público del proyecto de Firebase para el cliente web.
         *
         * @param projectId identificador del proyecto
         */
        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        /**
         * Obtiene el identificador del remitente de mensajería (Sender ID) de FCM.
         *
         * @return identificador del remitente de mensajería
         */
        public String getMessagingSenderId() {
            return messagingSenderId;
        }

        /**
         * Establece el identificador del remitente de mensajería (Sender ID) de FCM.
         *
         * @param messagingSenderId identificador del remitente de mensajería
         */
        public void setMessagingSenderId(String messagingSenderId) {
            this.messagingSenderId = messagingSenderId;
        }

        /**
         * Obtiene el identificador de la aplicación web en Firebase.
         *
         * @return identificador de la aplicación web
         */
        public String getAppId() {
            return appId;
        }

        /**
         * Establece el identificador de la aplicación web en Firebase.
         *
         * @param appId identificador de la aplicación web
         */
        public void setAppId(String appId) {
            this.appId = appId;
        }

        /**
         * Obtiene la clave pública VAPID para suscripciones Web Push.
         *
         * @return clave VAPID pública
         */
        public String getVapidKey() {
            return vapidKey;
        }

        /**
         * Establece la clave pública VAPID para suscripciones Web Push.
         *
         * @param vapidKey clave VAPID pública
         */
        public void setVapidKey(String vapidKey) {
            this.vapidKey = vapidKey;
        }
    }
}
