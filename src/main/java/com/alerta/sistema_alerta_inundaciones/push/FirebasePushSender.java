package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.config.PropiedadesFirebase;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.WebpushConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Implementación con el SDK administrativo de Firebase. La inicialización es
 * tolerante a fallos: si faltan credenciales o son inválidas, el backend sigue
 * funcionando y las notificaciones quedan deshabilitadas con un motivo visible.
 */
@Component
public class FirebasePushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FirebasePushSender.class);
    private static final String NOMBRE_APP = "alerta-inundaciones";

    private final FirebaseMessaging messaging;
    private final String motivoDeshabilitado;

    public FirebasePushSender(PropiedadesFirebase propiedades) {
        this.messaging = inicializar(propiedades);
        this.motivoDeshabilitado = messaging == null ? motivoInicializacion(propiedades) : null;
        if (messaging != null) {
            log.info("Firebase Cloud Messaging inicializado correctamente.");
        } else {
            log.warn("FCM no está disponible: {}", motivoDeshabilitado);
        }
    }

    @Override
    public boolean estaHabilitado() {
        return messaging != null;
    }

    @Override
    public String motivoDeshabilitado() {
        return motivoDeshabilitado;
    }

    @Override
    public ResultadoEnvioPush enviar(NotificacionPush notificacion, String token) {
        if (messaging == null) {
            return ResultadoEnvioPush.fallo("FCM no está configurado en el servidor.");
        }
        try {
            Message mensaje = Message.builder()
                    .setToken(token)
                    .putAllData(notificacion.datos())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(notificacion.critico()
                                    ? AndroidConfig.Priority.HIGH : AndroidConfig.Priority.NORMAL)
                            .setTtl(21_600L)
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .putHeader("Urgency", notificacion.critico() ? "high" : "normal")
                            .putHeader("TTL", "21600")
                            .build())
                    .build();
            messaging.send(mensaje);
            return ResultadoEnvioPush.ok();
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode codigo = e.getMessagingErrorCode();
            boolean invalido = codigo == MessagingErrorCode.UNREGISTERED
                    || codigo == MessagingErrorCode.SENDER_ID_MISMATCH
                    || (codigo == MessagingErrorCode.INVALID_ARGUMENT
                    && e.getMessage() != null
                    && e.getMessage().toLowerCase(java.util.Locale.ROOT).contains("token"));
            log.warn("FCM rechazó el envío para token {}: {} ({})",
                    enmascarar(token), e.getMessage(), codigo);
            return invalido
                    ? ResultadoEnvioPush.tokenInvalido("Token FCM inválido o no registrado.")
                    : ResultadoEnvioPush.fallo("FCM: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Error inesperado al enviar push para token {}: {}", enmascarar(token), e.getMessage());
            return ResultadoEnvioPush.fallo("Error al enviar la notificación: " + e.getMessage());
        }
    }

    private static FirebaseMessaging inicializar(PropiedadesFirebase propiedades) {
        if (!propiedades.isEnabled()) {
            return null;
        }
        if (esVacio(propiedades.getCredentialsPath()) && esVacio(propiedades.getCredentialsJson())) {
            return null;
        }
        try (InputStream credenciales = abrirCredenciales(propiedades)) {
            ServiceAccountCredentials serviceAccount = ServiceAccountCredentials.fromStream(credenciales);
            String projectId = !esVacio(propiedades.getProjectId())
                    ? propiedades.getProjectId() : serviceAccount.getProjectId();
            if (esVacio(projectId)) {
                log.warn("No se pudo determinar el projectId de Firebase.");
                return null;
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(serviceAccount)
                    .setProjectId(projectId)
                    .build();
            FirebaseApp app;
            try {
                app = FirebaseApp.initializeApp(options, NOMBRE_APP);
            } catch (IllegalStateException yaExiste) {
                app = FirebaseApp.getInstance(NOMBRE_APP);
            }
            return FirebaseMessaging.getInstance(app);
        } catch (Exception e) {
            log.error("No se pudieron cargar las credenciales de Firebase: {}", e.getMessage());
            return null;
        }
    }

    private static InputStream abrirCredenciales(PropiedadesFirebase propiedades) throws Exception {
        if (!esVacio(propiedades.getCredentialsPath())) {
            return Files.newInputStream(Path.of(propiedades.getCredentialsPath()));
        }
        String json = propiedades.getCredentialsJson().trim();
        if (json.startsWith("{")) {
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }
        // Permite variables de entorno con el JSON codificado en Base64.
        byte[] decodificado = Base64.getDecoder().decode(json);
        return new ByteArrayInputStream(decodificado);
    }

    private static String motivoInicializacion(PropiedadesFirebase propiedades) {
        if (!propiedades.isEnabled()) {
            return "FCM está deshabilitado por configuración (firebase.enabled=false).";
        }
        if (esVacio(propiedades.getCredentialsPath()) && esVacio(propiedades.getCredentialsJson())) {
            return "Faltan credenciales de Firebase Admin (FIREBASE_CREDENTIALS_PATH o FIREBASE_CREDENTIALS_JSON).";
        }
        return "No se pudo inicializar Firebase; revise los logs del servidor.";
    }

    private static boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String enmascarar(String token) {
        if (token == null || token.length() < 12) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }
}
