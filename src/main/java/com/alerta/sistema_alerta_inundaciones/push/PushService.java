package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEvento;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEventoRepository;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;
import com.alerta.sistema_alerta_inundaciones.alerta.EstadoEnvio;
import com.alerta.sistema_alerta_inundaciones.config.PropiedadesFirebase;
import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final Duration ESPERA_PRUEBA = Duration.ofSeconds(60);
    private static final long REACH_ID_HEREDADO = 0L;

    private final DispositivoPushRepository dispositivoRepository;
    private final RioRepository rioRepository;
    private final UsuarioRioRepository seleccionRepository;
    private final AlertaEventoRepository eventoRepository;
    private final PushSender pushSender;
    private final PropiedadesFirebase propiedadesFirebase;
    private final Map<String, Instant> ultimaPruebaPorToken = new ConcurrentHashMap<>();

    public PushService(DispositivoPushRepository dispositivoRepository,
                       RioRepository rioRepository,
                       UsuarioRioRepository seleccionRepository,
                       AlertaEventoRepository eventoRepository,
                       PushSender pushSender,
                       PropiedadesFirebase propiedadesFirebase) {
        this.dispositivoRepository = dispositivoRepository;
        this.rioRepository = rioRepository;
        this.seleccionRepository = seleccionRepository;
        this.eventoRepository = eventoRepository;
        this.pushSender = pushSender;
        this.propiedadesFirebase = propiedadesFirebase;
    }

    public ConfigPushDTO configuracion() {
        PropiedadesFirebase.Publica publica = propiedadesFirebase.getPublica();
        boolean configuracionWebCompleta = !vacio(publica.getApiKey())
                && !vacio(publica.getAuthDomain())
                && !vacio(publica.getProjectId())
                && !vacio(publica.getMessagingSenderId())
                && !vacio(publica.getAppId())
                && !vacio(publica.getVapidKey());

        boolean habilitado = pushSender.estaHabilitado() && configuracionWebCompleta;
        String motivo = null;
        if (!pushSender.estaHabilitado()) {
            motivo = pushSender.motivoDeshabilitado();
        } else if (!configuracionWebCompleta) {
            motivo = "Faltan datos públicos de la app web de Firebase (API key, sender ID, app ID o VAPID key).";
        }

        return new ConfigPushDTO(
                habilitado,
                motivo,
                publica.getApiKey(),
                publica.getAuthDomain(),
                publica.getProjectId(),
                publica.getMessagingSenderId(),
                publica.getAppId(),
                publica.getVapidKey()
        );
    }

    @Transactional
    public RegistroPushResponse registrar(Usuario usuario, RegistroPushRequest solicitud) {
        String token = solicitud.getToken().trim();
        String deviceId = solicitud.getDeviceId().trim();

        var existente = dispositivoRepository.findByToken(token);
        if (existente.isPresent()) {
            DispositivoPush dispositivo = existente.get();
            if (!deviceId.equals(dispositivo.getDeviceId())) {
                throw new IllegalStateException("El token FCM ya está asociado a otro dispositivo.");
            }
            dispositivo.setUsuario(usuario);
            dispositivo.setReachId(REACH_ID_HEREDADO);
            dispositivo.setPlataforma(limpiarPlataforma(solicitud.getPlataforma()));
            dispositivo.setActivo(true);
            dispositivoRepository.save(dispositivo);
            return respuesta(dispositivo, "Dispositivo actualizado y asociado a tu cuenta.");
        }

        // Si la misma instalación obtiene un token nuevo (rotación), el anterior
        // deja de ser válido para este backend.
        for (DispositivoPush anterior : dispositivoRepository.findByDeviceIdAndActivoTrue(deviceId)) {
            anterior.setActivo(false);
            dispositivoRepository.save(anterior);
        }

        DispositivoPush dispositivo = new DispositivoPush();
        dispositivo.setToken(token);
        dispositivo.setDeviceId(deviceId);
        dispositivo.setUsuario(usuario);
        dispositivo.setReachId(REACH_ID_HEREDADO);
        dispositivo.setPlataforma(limpiarPlataforma(solicitud.getPlataforma()));
        dispositivo.setActivo(true);
        dispositivoRepository.save(dispositivo);
        return respuesta(dispositivo, "Dispositivo registrado para recibir alertas de tus ríos.");
    }

    @Transactional
    public void desactivar(Usuario usuario, String token, String deviceId) {
        DispositivoPush dispositivo = dispositivoRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("No existe una suscripción con ese token."));
        if (!dispositivo.getDeviceId().equals(deviceId)) {
            throw new IllegalStateException("El token no pertenece al dispositivo indicado.");
        }
        if (dispositivo.getUsuario() != null
                && !dispositivo.getUsuario().getId().equals(usuario.getId())) {
            throw new IllegalStateException("El token no pertenece al usuario autenticado.");
        }
        dispositivo.setActivo(false);
        dispositivoRepository.save(dispositivo);
        log.info("Suscripción push desactivada para el usuario {}.", usuario.getIdentificador());
    }

    @Transactional
    public ResultadoEnvioPush enviarPrueba(Usuario usuario, PruebaPushRequest solicitud) {
        DispositivoPush dispositivo = dispositivoRepository.findByToken(solicitud.getToken().trim())
                .orElseThrow(() -> new IllegalArgumentException("No hay un dispositivo registrado con ese token."));
        if (!dispositivo.getDeviceId().equals(solicitud.getDeviceId().trim())) {
            throw new IllegalStateException("El token no pertenece al dispositivo indicado.");
        }
        if (dispositivo.getUsuario() != null
                && !dispositivo.getUsuario().getId().equals(usuario.getId())) {
            throw new IllegalStateException("El token no pertenece al usuario autenticado.");
        }
        if (!dispositivo.isActivo()) {
            throw new IllegalStateException("La suscripción está inactiva. Vuelva a activar las notificaciones.");
        }
        if (!pushSender.estaHabilitado()) {
            throw new IllegalStateException(pushSender.motivoDeshabilitado());
        }

        Instant ahora = Instant.now();
        Instant anterior = ultimaPruebaPorToken.get(dispositivo.getToken());
        if (anterior != null && anterior.plus(ESPERA_PRUEBA).isAfter(ahora)) {
            throw new LimitePruebaExcedidoException(
                    "Espere al menos 60 segundos antes de enviar otra notificación de prueba.");
        }
        ultimaPruebaPorToken.put(dispositivo.getToken(), ahora);

        NotificacionPush notificacion = new NotificacionPush(
                "Notificación de prueba",
                "Las notificaciones push están activas en este dispositivo. Esta es una prueba del sistema de alertas.",
                "PRUEBA",
                dispositivo.getReachId(),
                0L,
                "/?prueba=1",
                "prueba-push-" + dispositivo.getDeviceId(),
                "/icons/icon-192.png",
                "/icons/badge-72.png",
                false,
                false,
                false,
                ahora.toEpochMilli()
        );

        ResultadoEnvioPush resultado = pushSender.enviar(notificacion, dispositivo.getToken());
        if (resultado.tokenInvalido()) {
            dispositivo.setActivo(false);
            dispositivoRepository.save(dispositivo);
        }
        return resultado;
    }

    /**
     * Envía una alerta de simulacro a todos los dispositivos activos, sin
     * persistir un evento. Sirve para demostrar el canal push en clase.
     */
    public ResultadoDemoPush enviarDemoATodos() {
        if (!pushSender.estaHabilitado()) {
            throw new IllegalStateException(pushSender.motivoDeshabilitado());
        }

        List<DispositivoPush> dispositivos = dispositivoRepository.findByActivoTrue();
        if (dispositivos.isEmpty()) {
            return new ResultadoDemoPush(0, 0, 0);
        }

        NotificacionPush notificacion = new NotificacionPush(
                "ALERTA DE INUNDACIÓN (DEMOSTRACIÓN)",
                "Simulacro del sistema de alertas. No hay una emergencia real. "
                        + "Esta notificación se envió a todos los dispositivos suscritos.",
                "EMERGENCIA",
                0L,
                0L,
                "/?demo=1",
                "demo-push",
                "/icons/icon-192.png",
                "/icons/badge-72.png",
                true,
                false,
                true,
                Instant.now().toEpochMilli()
        );

        ResultadoDemoPush resultado = enviarADispositivos(dispositivos, notificacion);
        log.info("Demostración push: {} dispositivos, {} enviados, {} fallidos.",
                resultado.dispositivos(), resultado.enviados(), resultado.fallidos());
        return resultado;
    }

    /**
     * Envía el simulacro de crecida solo a los dispositivos activos de los
     * usuarios que siguen el río simulado. No persiste un evento.
     *
     * @throws IllegalStateException si FCM no está configurado.
     */
    public ResultadoDemoPush enviarSimulacion(AlertaRioDTO alerta) {
        if (!pushSender.estaHabilitado()) {
            throw new IllegalStateException(pushSender.motivoDeshabilitado());
        }
        if (alerta == null || alerta.getReachId() == null) {
            throw new IllegalArgumentException("La simulación no tiene un reachId válido.");
        }

        List<DispositivoPush> dispositivos = dispositivosQueSiguen(alerta.getReachId());
        if (dispositivos.isEmpty()) {
            log.info("Simulación del tramo {} sin seguidores con push activo.",
                    alerta.getReachId());
            return new ResultadoDemoPush(0, 0, 0);
        }

        String nivel = alerta.getNivelAlerta() == null ? "SIMULACION" : alerta.getNivelAlerta();
        boolean critico = "EMERGENCIA".equalsIgnoreCase(nivel);
        String nombreRio = alerta.getNombreRio() == null || alerta.getNombreRio().isBlank()
                ? "el río seleccionado" : alerta.getNombreRio();
        String titulo = critico
                ? "SIMULACRO: alerta de inundación"
                : "SIMULACRO: prueba de alerta";
        String cuerpo = "Simulación para probar las notificaciones de " + nombreRio
                + ". Nivel simulado: " + nivel + ". No es una emergencia real.";

        NotificacionPush notificacion = new NotificacionPush(
                titulo,
                cuerpo,
                nivel,
                alerta.getReachId(),
                0L,
                "/?simulacion=" + alerta.getReachId(),
                "simulacion-" + alerta.getReachId(),
                "/icons/icon-192.png",
                "/icons/badge-72.png",
                critico,
                critico,
                true,
                Instant.now().toEpochMilli()
        );

        ResultadoDemoPush resultado = enviarADispositivos(dispositivos, notificacion);
        log.info("Simulación del tramo {}: {} dispositivos, {} enviados, {} fallidos.",
                alerta.getReachId(), resultado.dispositivos(), resultado.enviados(),
                resultado.fallidos());
        return resultado;
    }

    private ResultadoDemoPush enviarADispositivos(List<DispositivoPush> dispositivos,
                                                  NotificacionPush notificacion) {
        int enviados = 0;
        int fallidos = 0;
        for (DispositivoPush dispositivo : dispositivos) {
            ResultadoEnvioPush resultado = pushSender.enviar(notificacion, dispositivo.getToken());
            if (resultado.exito()) {
                enviados++;
                continue;
            }
            fallidos++;
            if (resultado.tokenInvalido()) {
                dispositivo.setActivo(false);
                dispositivoRepository.save(dispositivo);
            }
        }
        return new ResultadoDemoPush(dispositivos.size(), enviados, fallidos);
    }

    /**
     * Envía la notificación de un evento solo a los dispositivos activos de los
     * usuarios que siguen el río afectado. Los errores se registran en el evento
     * y nunca se propagan al ciclo de monitoreo.
     */
    public void enviarParaEvento(AlertaEvento evento) {
        List<DispositivoPush> dispositivos = dispositivosQueSiguen(evento.getReachId());

        if (dispositivos.isEmpty()) {
            evento.setEstadoEnvio(EstadoEnvio.SIN_DISPOSITIVOS);
            evento.setDispositivosObjetivo(0);
            evento.setFechaIntentoEnvio(Instant.now());
            eventoRepository.save(evento);
            log.info("Alerta {} del tramo {} sin usuarios seguidores con push activo.",
                    evento.getId(), evento.getReachId());
            return;
        }

        if (!pushSender.estaHabilitado()) {
            evento.setEstadoEnvio(EstadoEnvio.DESHABILITADO);
            evento.setDispositivosObjetivo(dispositivos.size());
            evento.setFechaIntentoEnvio(Instant.now());
            evento.setErrorEnvio(limitar(pushSender.motivoDeshabilitado(), 1000));
            eventoRepository.save(evento);
            log.warn("Alerta {} no enviada: FCM no está configurado.", evento.getId());
            return;
        }

        // Se marca ENVIANDO antes de llamar a FCM. Si el proceso se reinicia a
        // mitad de un envío, el evento no se reenvía automáticamente y se evita
        // una ráfaga duplicada de notificaciones.
        evento.setEstadoEnvio(EstadoEnvio.ENVIANDO);
        evento.setFechaIntentoEnvio(Instant.now());
        evento.setDispositivosObjetivo(dispositivos.size());
        eventoRepository.save(evento);

        int enviados = 0;
        int fallidos = 0;
        String primerError = null;

        try {
            NotificacionPush notificacion = NotificacionFactory.paraEvento(evento);
            for (DispositivoPush dispositivo : dispositivos) {
                ResultadoEnvioPush resultado = pushSender.enviar(notificacion, dispositivo.getToken());
                if (resultado.exito()) {
                    enviados++;
                } else {
                    fallidos++;
                    if (primerError == null) {
                        primerError = resultado.error();
                    }
                    if (resultado.tokenInvalido()) {
                        dispositivo.setActivo(false);
                        dispositivoRepository.save(dispositivo);
                    }
                }
            }

            evento.setDispositivosEnviados(enviados);
            evento.setDispositivosFallidos(fallidos);
            evento.setErrorEnvio(limitar(primerError, 1000));
            if (enviados == 0) {
                evento.setEstadoEnvio(EstadoEnvio.FALLIDA);
            } else if (fallidos == 0) {
                evento.setEstadoEnvio(EstadoEnvio.ENVIADA);
                evento.setFechaEnvio(Instant.now());
            } else {
                evento.setEstadoEnvio(EstadoEnvio.PARCIAL);
                evento.setFechaEnvio(Instant.now());
            }
            eventoRepository.save(evento);

            log.info("Alerta {} (tramo {}): {} enviadas, {} fallidas, estado {}.",
                    evento.getId(), evento.getReachId(), enviados, fallidos, evento.getEstadoEnvio());
        } catch (Exception e) {
            evento.setEstadoEnvio(EstadoEnvio.FALLIDA);
            evento.setDispositivosEnviados(enviados);
            evento.setDispositivosFallidos(fallidos);
            evento.setErrorEnvio(limitar("Error inesperado al enviar notificaciones: " + e.getMessage(), 1000));
            eventoRepository.save(evento);
            log.error("Error inesperado al enviar la alerta {}: {}", evento.getId(), e.getMessage());
        }
    }

    private List<DispositivoPush> dispositivosQueSiguen(Long reachId) {
        Optional<Rio> rio = rioRepository.findFirstByGeoglowsId(reachId);
        if (rio.isEmpty()) {
            return List.of();
        }
        List<Long> usuarioIds = seleccionRepository.findUsuarioIdsByRioId(rio.get().getId()).stream()
                .distinct()
                .toList();
        if (usuarioIds.isEmpty()) {
            return List.of();
        }
        return dispositivoRepository.findByUsuarioIdInAndActivoTrue(usuarioIds);
    }

    private static RegistroPushResponse respuesta(DispositivoPush dispositivo, String mensaje) {
        return new RegistroPushResponse(
                dispositivo.getId(),
                dispositivo.isActivo(),
                mensaje,
                dispositivo.getFechaActualizacion()
        );
    }

    private static String limpiarPlataforma(String plataforma) {
        if (plataforma == null || plataforma.isBlank()) {
            return "web";
        }
        String limpia = plataforma.trim().toLowerCase(java.util.Locale.ROOT);
        return limpia.length() > 30 ? limpia.substring(0, 30) : limpia;
    }

    private static String limitar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
