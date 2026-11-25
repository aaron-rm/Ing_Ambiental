package com.alerta.sistema_alerta_inundaciones.demo;

import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;
import com.alerta.sistema_alerta_inundaciones.alerta.PronosticoDiaDTO;
import com.alerta.sistema_alerta_inundaciones.push.ResultadoDemoPush;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.Console;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Cliente de demostracion. Consume la API REST del backend mediante
 * {@link BackendApiClient}; no accede a repositorios ni ejecuta reglas de
 * negocio propias. Requiere login y adapta el menu al rol del usuario.
 */
@Component
@ConditionalOnProperty(name = "alerta.demo.consola.habilitada",
        havingValue = "true", matchIfMissing = true)
public class ConsolaDemoRunner implements CommandLineRunner {

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final BackendApiClient backend;
    private final Environment environment;

    public ConsolaDemoRunner(BackendApiClient backend, Environment environment) {
        this.backend = backend;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        if ("true".equalsIgnoreCase(System.getProperty("skip.demo.console"))
                || (environment != null && java.util.Arrays.asList(environment.getActiveProfiles()).contains("test"))) {
            return;
        }
        if (System.console() == null) {
            System.out.println("[i] Sin terminal interactiva: la consola de demostracion se omite.");
            System.out.println("    Ejecute 'bash scripts/iniciar-demo.sh' en una terminal para ver el menu.");
            return;
        }
        Thread demoThread = new Thread(this::iniciarConsolaInteractiva);
        demoThread.setDaemon(true);
        demoThread.start();
    }

    private void iniciarConsolaInteractiva() {
        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        Scanner scanner = new Scanner(System.in);
        try {
            boolean salir = false;
            while (!salir) {
                if (!backend.tieneSesion()) {
                    salir = menuSinSesion(scanner);
                    continue;
                }
                salir = menuConSesion(scanner);
            }
        } finally {
            scanner.close();
        }
    }

    /** @return true si la consola debe terminar. */
    private boolean menuSinSesion(Scanner scanner) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("      SISTEMA DE ALERTA TEMPRANA DE INUNDACIONES");
        System.out.println("                       GEOGLOWS");
        System.out.println("============================================================");
        System.out.println("  1. Iniciar sesion");
        System.out.println("  2. Registrarme");
        System.out.println("  0. Salir de la consola");
        System.out.print("\n> ");

        String opcion = leerLinea(scanner);
        switch (opcion) {
            case "1":
                iniciarSesion(scanner);
                return false;
            case "2":
                registrarse(scanner);
                return false;
            case "0":
                System.out.println("\nCerrando la consola. El servidor backend sigue activo.");
                return true;
            default:
                System.out.println("Opcion no valida.");
                return false;
        }
    }

    private void iniciarSesion(Scanner scanner) {
        System.out.println("\n--- INICIAR SESION ---");
        System.out.println("Puede usar un correo o un nombre de usuario.");
        System.out.print("Identificador: ");
        String identificador = leerLinea(scanner);
        if (identificador.isEmpty()) {
            System.out.println("Operacion cancelada.");
            return;
        }
        String password = leerPassword(scanner, "Contrasena: ");
        if (password.isEmpty()) {
            System.out.println("Operacion cancelada.");
            return;
        }
        try {
            BackendApiClient.SesionApi sesion = backend.iniciarSesion(identificador, password);
            mostrarBienvenida(sesion);
        } catch (BackendApiClient.ApiException e) {
            if (e.getEstado() == 401) {
                System.out.println("[!] Identificador o contrasena incorrectos.");
            } else if (e.getEstado() == 0) {
                System.out.println("[!] " + e.getMessage());
            } else {
                System.out.println("[!] No se pudo iniciar sesion: " + e.getMessage());
            }
        }
    }

    private void registrarse(Scanner scanner) {
        System.out.println("\n--- CREAR CUENTA ---");
        System.out.println("El registro publico siempre crea un usuario normal (USER).");
        System.out.print("Correo o nombre de usuario: ");
        String identificador = leerLinea(scanner);
        if (identificador.isEmpty()) {
            System.out.println("Operacion cancelada.");
            return;
        }
        String password = leerPassword(scanner, "Contrasena (minimo 8 caracteres): ");
        if (password.isEmpty()) {
            System.out.println("Operacion cancelada.");
            return;
        }
        String confirmacion = leerPassword(scanner, "Repita la contrasena: ");
        if (!password.equals(confirmacion)) {
            System.out.println("[!] Las contrasenas no coinciden. Intente de nuevo.");
            return;
        }
        try {
            BackendApiClient.SesionApi sesion = backend.registrarse(identificador, password);
            System.out.println("\n[OK] Cuenta creada.");
            mostrarBienvenida(sesion);
        } catch (BackendApiClient.ApiException e) {
            if (e.getEstado() == 409) {
                System.out.println("[!] " + e.getMessage());
            } else {
                System.out.println("[!] No se pudo crear la cuenta: " + e.getMessage());
            }
        }
    }

    private void mostrarBienvenida(BackendApiClient.SesionApi sesion) {
        System.out.println();
        System.out.println("[OK] Sesion iniciada como " + sesion.usuario().identificador()
                + " (" + sesion.usuario().rol() + ").");
    }

    /** @return true si la consola debe terminar. */
    private boolean menuConSesion(Scanner scanner) {
        BackendApiClient.UsuarioApi usuario = backend.sesionActual();
        boolean admin = "ADMIN".equalsIgnoreCase(usuario.rol());

        System.out.println();
        System.out.println("============================================================");
        System.out.println(" Sesion: " + usuario.identificador() + " (" + usuario.rol() + ")");
        System.out.println("============================================================");
        System.out.println("  1. Ver catalogo de rios y mis rios seguidos");
        System.out.println("  2. Consultar panel de un rio (caudal y lluvia)");
        System.out.println("  3. Seguir un rio");
        System.out.println("  4. Dejar de seguir un rio");
        System.out.println("  5. Ver mis rios seguidos");
        if (admin) {
            System.out.println("  6. Simular una crecida y enviar push a sus seguidores");
            System.out.println("  7. Enviar notificacion push de demostracion");
        }
        System.out.println("  0. Cerrar sesion");
        System.out.println("  9. Salir de la consola");
        System.out.print("\n> ");

        String opcion = leerLinea(scanner);
        try {
            switch (opcion) {
                case "1":
                    mostrarCatalogo();
                    return false;
                case "2":
                    abrirPanel(scanner);
                    return false;
                case "3":
                    seguirRio(scanner);
                    return false;
                case "4":
                    dejarDeSeguirRio(scanner);
                    return false;
                case "5":
                    mostrarRiosSeguidos();
                    return false;
                case "6":
                    if (admin) {
                        simularCrecida(scanner);
                    } else {
                        System.out.println("Opcion no valida.");
                    }
                    return false;
                case "7":
                    if (admin) {
                        enviarPushDemo();
                    } else {
                        System.out.println("Opcion no valida.");
                    }
                    return false;
                case "0":
                    backend.cerrarSesion();
                    System.out.println("\nSesion cerrada. El dispositivo de la PWA se desactiva "
                            + "desde la propia PWA al cerrar sesion.");
                    return false;
                case "9":
                    System.out.println("\nCerrando la consola. El servidor backend sigue activo.");
                    return true;
                default:
                    System.out.println("Opcion no valida.");
                    return false;
            }
        } catch (BackendApiClient.ApiException e) {
            if (e.getEstado() == 401) {
                backend.cerrarSesion();
                System.out.println("\n[!] La sesion vencio o el token ya no es valido. Inicie sesion de nuevo.");
            } else if (e.getEstado() == 403) {
                System.out.println("\n[!] No tienes permiso para esa operacion (rol insuficiente).");
            } else {
                System.out.println("\n[!] " + e.getMessage());
            }
            return false;
        }
    }

    private void mostrarCatalogo() {
        List<BackendApiClient.RioApi> rios = backend.listarRios();
        List<Long> seguidos = idsSeguidos();
        System.out.println("\n--- CATALOGO DE RIOS (" + rios.size() + ") ---");
        if (rios.isEmpty()) {
            System.out.println("El backend no tiene rios cargados.");
            return;
        }
        for (BackendApiClient.RioApi rio : rios) {
            System.out.printf(Locale.US, "  #%d | %s | rioId=%d | reachId=%d | %.4f, %.4f | geometria: %s | %s%n",
                    rios.indexOf(rio) + 1,
                    rio.nombre(),
                    rio.rioId(),
                    rio.reachId(),
                    rio.latitud() == null ? 0.0 : rio.latitud(),
                    rio.longitud() == null ? 0.0 : rio.longitud(),
                    rio.tieneGeometria() ? "si" : "no",
                    seguidos.contains(rio.rioId()) ? "SEGUIDO" : "no seguido");
        }
    }

    private void abrirPanel(Scanner scanner) {
        BackendApiClient.RioApi rio = elegirRio(scanner, "Consultar panel de que rio?");
        if (rio == null) {
            return;
        }

        System.out.println("\nConsultando caudal y lluvia de " + rio.nombre() + "...");
        AlertaRioDTO alerta = backend.consultarEstadoPorRio(rio.rioId());

        boolean seguido = idsSeguidos().contains(rio.rioId());
        System.out.println("\n============================================================");
        System.out.println(" " + rio.nombre() + " (rioId=" + rio.rioId() + ", reachId=" + rio.reachId() + ")");
        System.out.println("============================================================");
        System.out.println("Estado: " + formatearNivel(alerta.getNivelAlerta())
                + " | Datos: " + texto(alerta.getEstadoDatos(), "sin clasificar")
                + " | Confiables: " + (alerta.isDatosConfiables() ? "si" : "no"));
        System.out.printf(Locale.US, "Caudal actual: %.2f m3/s | Maximo pronosticado: %.2f m3/s%n",
                alerta.getCaudalActualOInicial(), alerta.getCaudalMaximoPronosticado());
        System.out.printf(Locale.US, "Umbrales de caudal: vigilancia %.2f | alerta %.2f | emergencia %.2f m3/s%n",
                alerta.getUmbralVigilancia(), alerta.getUmbralAlerta(), alerta.getUmbralEmergencia());
        System.out.println("Mensaje: " + texto(alerta.getMensaje(), "sin mensaje"));

        System.out.println("\n--- LLUVIA (Open-Meteo) ---");
        if (alerta.getLluvia24hMm() != null || alerta.getLluvia72hMm() != null) {
            System.out.printf(Locale.US, "Acumulada 24 h: %.1f mm | 72 h: %.1f mm | max. horaria: %.1f mm%n",
                    valor(alerta.getLluvia24hMm()), valor(alerta.getLluvia72hMm()),
                    valor(alerta.getMaxLluviaHorariaMm()));
            System.out.println("Nivel por lluvia: " + formatearNivel(alerta.getNivelRiesgoLluvia()));
        } else {
            System.out.println("Sin datos de lluvia disponibles.");
        }
        if (alerta.getFechaActualizacionPrecipitacion() != null) {
            System.out.println("Actualizada: " + formatearFecha(alerta.getFechaActualizacionPrecipitacion()));
        }
        System.out.println("Aviso: los umbrales de lluvia son experimentales y no oficiales.");

        System.out.println("\n--- RECOMENDACIONES ---");
        imprimirLista(alerta.getRecomendaciones(), "Sin recomendaciones.");
        if (alerta.getAdvertencias() != null && !alerta.getAdvertencias().isEmpty()) {
            System.out.println("\n--- ADVERTENCIAS ---");
            imprimirLista(alerta.getAdvertencias(), "Sin advertencias.");
        }

        List<PronosticoDiaDTO> dias = alerta.getPronosticoDiario();
        if (dias != null && !dias.isEmpty()) {
            System.out.println("\n--- PRONOSTICO DIARIO (" + dias.size() + " dias) ---");
            for (PronosticoDiaDTO dia : dias) {
                System.out.printf(Locale.US,
                        "Dia %2d - %s | medio %5.2f | min %5.2f | max %5.2f m3/s | %s%n",
                        dia.getDiaNumero(),
                        dia.getFecha() == null ? "--/--" : dia.getFecha().format(DIA_MES),
                        dia.getCaudalMedio(), dia.getCaudalMinimo(), dia.getCaudalMaximo(),
                        formatearNivel(dia.getEstado()));
            }
        }

        System.out.println("\nFuente caudal: " + texto(alerta.getFuenteCaudal(), "no indicada"));
        System.out.println("Fuente lluvia: " + texto(alerta.getFuentePrecipitacion(), "no indicada"));
        if (alerta.getFechaEmisionCaudal() != null) {
            System.out.println("Emision del pronostico: " + formatearFecha(alerta.getFechaEmisionCaudal()));
        }
        System.out.println("Suscripcion push de este rio: " + (seguido ? "SI" : "NO")
                + " (la consulta funciona igual).");
    }

    private void seguirRio(Scanner scanner) {
        BackendApiClient.RioApi rio = elegirRio(scanner, "Seguir que rio?");
        if (rio == null) {
            return;
        }
        BackendApiClient.SeleccionApi respuesta = backend.seguirRio(rio.rioId());
        System.out.println("\n[OK] " + respuesta.mensaje());
    }

    private void dejarDeSeguirRio(Scanner scanner) {
        List<BackendApiClient.RioSeguidoApi> seguidos = backend.listarRiosSeguidos();
        if (seguidos.isEmpty()) {
            System.out.println("\nNo sigues ningun rio.");
            return;
        }
        BackendApiClient.RioSeguidoApi seleccionado = elegirRioSeguido(scanner, seguidos,
                "Dejar de seguir que rio?");
        if (seleccionado == null) {
            return;
        }
        backend.dejarDeSeguirRio(seleccionado.rioId());
        System.out.println("\n[OK] Dejaste de seguir " + seleccionado.nombre()
                + ". Ya no recibiras notificaciones de ese rio.");
    }

    private void mostrarRiosSeguidos() {
        List<BackendApiClient.RioSeguidoApi> seguidos = backend.listarRiosSeguidos();
        System.out.println("\n--- MIS RIOS SEGUIDOS (" + seguidos.size() + ") ---");
        if (seguidos.isEmpty()) {
            System.out.println("No sigues ningun rio. Usa la opcion 3 para agregar uno.");
            return;
        }
        for (BackendApiClient.RioSeguidoApi rio : seguidos) {
            System.out.printf("  rioId=%d | %s | reachId=%d | desde %s%n",
                    rio.rioId(), rio.nombre(), rio.reachId(), formatearFecha(rio.fechaSeleccion()));
        }
    }

    private void simularCrecida(Scanner scanner) {
        BackendApiClient.RioApi rio = elegirRio(scanner, "Simular crecida en que rio?");
        if (rio == null) {
            return;
        }
        System.out.print("Caudal simulado en m3/s [Enter = 75.0]: ");
        String entrada = leerLinea(scanner);
        double caudal = 75.0;
        if (!entrada.isEmpty()) {
            try {
                caudal = Double.parseDouble(entrada.replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Valor invalido; se usara 75.0 m3/s.");
            }
        }
        System.out.println("\nSimulando " + caudal + " m3/s en " + rio.nombre()
                + " y enviando push a sus seguidores...");
        BackendApiClient.ResultadoSimulacionApi resultado = backend.simular(rio.reachId(), caudal);
        AlertaRioDTO simulacion = resultado.alerta();

        System.out.println("\n--- RESULTADO DE LA SIMULACION ---");
        System.out.println("Rio: " + rio.nombre());
        System.out.printf(Locale.US, "Caudal simulado: %.2f m3/s%n", caudal);
        System.out.println("Estado resultante: " + formatearNivel(simulacion.getNivelAlerta()));
        System.out.println("Mensaje: " + texto(simulacion.getMensaje(), "sin mensaje"));
        System.out.println("\nPush a seguidores:");
        System.out.println("  Dispositivos: " + resultado.dispositivos());
        System.out.println("  Enviados:     " + resultado.enviados());
        System.out.println("  Fallidos:     " + resultado.fallidos());
        if (resultado.dispositivos() == 0) {
            System.out.println("\nNingun dispositivo sigue este rio. La simulacion se ejecuto, "
                    + "pero no habia a quien notificar.");
        } else if (resultado.enviados() == 0) {
            System.out.println("\nNo se pudo entregar ninguna notificacion. Revise la configuracion de FCM.");
        } else {
            System.out.println("\nLa notificacion de simulacro debe aparecer en los dispositivos suscritos.");
        }
        System.out.println("Esta simulacion no representa una condicion real.");
        System.out.println("El texto visible de la notificacion usa el formato de una alerta real.");
    }

    private void enviarPushDemo() {
        System.out.println("\n--- NOTIFICACION PUSH DE DEMOSTRACION ---");
        System.out.println("Se enviara a todos los dispositivos activos, sin crear un evento real.");
        System.out.println("El texto visible usa el formato de una alerta real de emergencia.");
        ResultadoDemoPush resultado = backend.enviarDemoPush();
        System.out.println("Dispositivos activos: " + resultado.dispositivos());
        System.out.println("Enviados:             " + resultado.enviados());
        System.out.println("Fallidos:             " + resultado.fallidos());
        if (resultado.dispositivos() == 0) {
            System.out.println("\nNo hay dispositivos suscritos.");
            System.out.println("Abra la PWA, inicie sesion y pulse 'Activar notificaciones'.");
        } else {
            System.out.println("\nLa notificacion debe aparecer en los dispositivos suscritos.");
        }
    }

    private BackendApiClient.RioApi elegirRio(Scanner scanner, String pregunta) {
        List<BackendApiClient.RioApi> rios = backend.listarRios();
        if (rios.isEmpty()) {
            System.out.println("\nEl backend no tiene rios cargados.");
            return null;
        }
        System.out.println("\n" + pregunta);
        for (int i = 0; i < rios.size(); i++) {
            BackendApiClient.RioApi rio = rios.get(i);
            System.out.printf("  %d. %s (rioId=%d, reachId=%d)%n",
                    i + 1, rio.nombre(), rio.rioId(), rio.reachId());
        }
        while (true) {
            System.out.print("Numero [Enter para cancelar]: ");
            String entrada = leerLinea(scanner);
            if (entrada.isEmpty() || "0".equals(entrada)) {
                System.out.println("Operacion cancelada.");
                return null;
            }
            try {
                int indice = Integer.parseInt(entrada);
                if (indice >= 1 && indice <= rios.size()) {
                    return rios.get(indice - 1);
                }
                System.out.println("Numero fuera de rango. Intente de nuevo.");
            } catch (NumberFormatException e) {
                System.out.println("Numero invalido. Intente de nuevo.");
            }
        }
    }

    private BackendApiClient.RioSeguidoApi elegirRioSeguido(
            Scanner scanner, List<BackendApiClient.RioSeguidoApi> rios, String pregunta) {
        System.out.println("\n" + pregunta);
        for (int i = 0; i < rios.size(); i++) {
            BackendApiClient.RioSeguidoApi rio = rios.get(i);
            System.out.printf("  %d. %s (rioId=%d, reachId=%d)%n",
                    i + 1, rio.nombre(), rio.rioId(), rio.reachId());
        }
        while (true) {
            System.out.print("Numero [Enter para cancelar]: ");
            String entrada = leerLinea(scanner);
            if (entrada.isEmpty() || "0".equals(entrada)) {
                System.out.println("Operacion cancelada.");
                return null;
            }
            try {
                int indice = Integer.parseInt(entrada);
                if (indice >= 1 && indice <= rios.size()) {
                    return rios.get(indice - 1);
                }
                System.out.println("Numero fuera de rango. Intente de nuevo.");
            } catch (NumberFormatException e) {
                System.out.println("Numero invalido. Intente de nuevo.");
            }
        }
    }

    private List<Long> idsSeguidos() {
        return backend.listarRiosSeguidos().stream()
                .map(BackendApiClient.RioSeguidoApi::rioId)
                .toList();
    }

    private static void imprimirLista(List<String> textos, String vacio) {
        if (textos == null || textos.isEmpty()) {
            System.out.println("  " + vacio);
            return;
        }
        for (String texto : textos) {
            System.out.println("  - " + texto);
        }
    }

    private static String leerLinea(Scanner scanner) {
        // Sin entrada interactiva (stdin cerrado), se devuelve "0" para que los
        // menus cierren la consola en lugar de repetirse sin fin.
        if (!scanner.hasNextLine()) {
            return "0";
        }
        return scanner.nextLine().trim();
    }

    private static String leerPassword(Scanner scanner, String etiqueta) {
        Console consola = System.console();
        if (consola != null) {
            char[] caracteres = consola.readPassword(etiqueta);
            return caracteres == null ? "" : new String(caracteres).trim();
        }
        System.out.print(etiqueta);
        return leerLinea(scanner);
    }

    private static String formatearNivel(String nivel) {
        if (nivel == null) {
            return "DESCONOCIDO";
        }
        return switch (nivel.toUpperCase(Locale.ROOT)) {
            case "EMERGENCIA" -> "EMERGENCIA";
            case "ALERTA" -> "ALERTA";
            case "VIGILANCIA" -> "VIGILANCIA";
            case "NORMAL" -> "NORMAL";
            default -> nivel;
        };
    }

    private static String formatearFecha(java.time.Instant fecha) {
        return fecha == null ? "sin fecha" : FECHA_HORA.format(fecha);
    }

    private static String texto(String valor, String respaldo) {
        return valor == null || valor.isBlank() ? respaldo : valor;
    }

    private static double valor(Double numero) {
        return numero == null ? 0.0 : numero;
    }
}
