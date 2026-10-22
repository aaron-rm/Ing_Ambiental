package com.alerta.sistema_alerta_inundaciones.demo;

import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;
import com.alerta.sistema_alerta_inundaciones.alerta.PronosticoDiaDTO;
import com.alerta.sistema_alerta_inundaciones.alerta.ValidadorContacto;
import com.alerta.sistema_alerta_inundaciones.push.ResultadoDemoPush;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Cliente de demostracion. Consume la API REST del backend mediante
 * {@link BackendApiClient}; no accede a repositorios ni ejecuta reglas de
 * negocio propias.
 */
@Component
@ConditionalOnProperty(name = "alerta.demo.consola.habilitada",
        havingValue = "true", matchIfMissing = true)
public class ConsolaDemoRunner implements CommandLineRunner {

    private final BackendApiClient backend;
    private final Environment environment;

    // Estado simulado en memoria para la consola (separado del estado real)
    private AlertaRioDTO ultimaSimulacion = null;

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

        BackendApiClient.RioApi rioDemo;
        try {
            rioDemo = asegurarRioDemo();
        } catch (BackendApiClient.ApiException e) {
            System.out.println();
            System.out.println("[!] No se pudo conectar con la API del backend en " + backend.getBaseUrl());
            System.out.println("    Detalle: " + e.getMessage());
            System.out.println("    Verifique que el servidor siga activo o ajuste ALERTA_API_BASE_URL.");
            return;
        } catch (Exception e) {
            System.out.println();
            System.out.println("[!] No se pudo preparar el rio de demostracion: " + e.getMessage());
            return;
        }

        Scanner scanner = new Scanner(System.in);

        System.out.println();
        System.out.println("============================================================");
        System.out.println("      SISTEMA DE ALERTA TEMPRANA DE INUNDACIONES");
        System.out.println("                       GEOGLOWS");
        System.out.println("============================================================");
        System.out.println("Rio seleccionado: " + rioDemo.nombre());
        System.out.println("ID GEOGLOWS (reach_id): " + rioDemo.geoglowsId());

        if (rioDemo.latitud() != null && rioDemo.longitud() != null
                && (rioDemo.latitud() != 0.0 || rioDemo.longitud() != 0.0)) {
            System.out.printf(Locale.US, "Coordenadas: Lat %.4f, Lon %.4f%n",
                    rioDemo.latitud(), rioDemo.longitud());
        } else {
            System.out.println("Coordenadas: No disponibles");
        }

        System.out.println("\nConfiguracion del sistema (Umbrales de referencia):");
        System.out.println("  Umbral de Vigilancia:  15.00 m3/s");
        System.out.println("  Umbral de Alerta:      30.00 m3/s");
        System.out.println("  Umbral de Emergencia:  60.00 m3/s");
        System.out.println("------------------------------------------------------------");

        boolean continuar = true;
        while (continuar) {
            System.out.println("\nSeleccione una opcion:");
            System.out.println("  1. Consultar estado y pronostico real (GEOGLOWS)");
            System.out.println("  2. Suscribirse a alertas de este rio");
            System.out.println("  3. Simular crecida / inundacion (Deteccion de riesgo)");
            if (ultimaSimulacion != null) {
                System.out.println("  4. Consultar estado simulado");
                System.out.println("  5. Ver suscriptores registrados");
                System.out.println("  0. Enviar notificacion push de demostracion a todos");
                System.out.println("  6. Salir");
            } else {
                System.out.println("  4. Ver suscriptores registrados");
                System.out.println("  0. Enviar notificacion push de demostracion a todos");
                System.out.println("  5. Salir");
            }
            System.out.print("\n> ");

            String input = "";
            if (scanner.hasNextLine()) {
                input = scanner.nextLine().trim();
            } else {
                break;
            }

            if (ultimaSimulacion != null) {
                switch (input) {
                    case "1":
                        mostrarPronosticoReal(rioDemo);
                        break;
                    case "2":
                        registrarSuscripcion(scanner, rioDemo);
                        break;
                    case "3":
                        ejecutarSimulacion(scanner, rioDemo);
                        break;
                    case "4":
                        mostrarEstadoSimulado(rioDemo);
                        break;
                    case "5":
                        mostrarSuscripciones(rioDemo);
                        break;
                    case "0":
                        enviarPushDemo();
                        break;
                    case "6":
                        System.out.println("\nCerrando interfaz de consola. El servidor backend sigue activo.");
                        continuar = false;
                        break;
                    default:
                        System.out.println("Opcion no valida. Ingrese un numero del 1 al 6, o 0 para push.");
                }
            } else {
                switch (input) {
                    case "1":
                        mostrarPronosticoReal(rioDemo);
                        break;
                    case "2":
                        registrarSuscripcion(scanner, rioDemo);
                        break;
                    case "3":
                        ejecutarSimulacion(scanner, rioDemo);
                        break;
                    case "4":
                        mostrarSuscripciones(rioDemo);
                        break;
                    case "0":
                        enviarPushDemo();
                        break;
                    case "5":
                        System.out.println("\nCerrando interfaz de consola. El servidor backend sigue activo.");
                        continuar = false;
                        break;
                    default:
                        System.out.println("Opcion no valida. Ingrese un numero del 1 al 5, o 0 para push.");
                }
            }
        }
    }

    /**
     * Reutiliza el rio inicial del backend o lo crea mediante la API publica.
     * Conserva el comportamiento de la consola anterior sin tocar la BD.
     */
    private BackendApiClient.RioApi asegurarRioDemo() {
        List<BackendApiClient.RioApi> rios = null;
        Exception ultimoError = null;
        for (int intento = 0; intento < 10; intento++) {
            try {
                rios = backend.listarRios();
                ultimoError = null;
                break;
            } catch (BackendApiClient.ApiException e) {
                ultimoError = e;
                if (e.getEstado() > 0) {
                    throw e;
                }
            } catch (Exception e) {
                ultimoError = e;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (ultimoError != null) {
            throw new IllegalStateException(ultimoError.getMessage(), ultimoError);
        }
        if (rios == null || rios.isEmpty()) {
            return backend.crearRio("Rio Pacora (Panama)", 710039498L, 9.08, -79.28);
        }

        BackendApiClient.RioApi rioDemo = rios.get(0);
        boolean modificado = false;
        Long reachId = rioDemo.geoglowsId();
        String nombre = rioDemo.nombre();
        Double latitud = rioDemo.latitud();
        Double longitud = rioDemo.longitud();

        if (reachId == null || reachId == 123456789L) {
            reachId = 710039498L;
            nombre = "Rio Pacora (Panama)";
            modificado = true;
        }
        if (latitud == null || latitud == 0.0) {
            latitud = 9.08;
            longitud = -79.28;
            modificado = true;
        }
        if (modificado) {
            rioDemo = backend.actualizarRio(new BackendApiClient.RioApi(
                    rioDemo.id(), nombre, reachId, latitud, longitud));
        }
        return rioDemo;
    }

    private void mostrarPronosticoReal(BackendApiClient.RioApi rio) {
        System.out.println("\n[1/3] Consultando datos de GEOGLOWS API v2...");
        try {
            AlertaRioDTO alerta = rio.id() != null
                    ? backend.consultarEstadoPorRio(rio.id())
                    : backend.consultarEstado(rio.geoglowsId());

            System.out.println("[2/3] Procesando pronostico extendido...");
            Thread.sleep(300);

            System.out.println("[3/3] Evaluando nivel de riesgo...");
            Thread.sleep(300);

            System.out.println("------------------------------------------------------------");
            System.out.printf(Locale.US, "Caudal actual inicial: %.2f m3/s%n", alerta.getCaudalActualOInicial());
            System.out.printf(Locale.US, "Caudal maximo previsto en el horizonte: %.2f m3/s%n", alerta.getCaudalMaximoPronosticado());
            System.out.println("Estado general: " + formatearEstado(alerta.getNivelAlerta()));
            System.out.println("Mensaje: " + alerta.getMensaje());
            System.out.println("------------------------------------------------------------");

            List<PronosticoDiaDTO> dias = alerta.getPronosticoDiario();
            if (dias != null && !dias.isEmpty()) {
                System.out.println("\n--- PRONOSTICO DIARIO (Horizonte de " + dias.size() + " dias) ---");
                DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM");
                for (PronosticoDiaDTO dia : dias) {
                    System.out.printf(Locale.US,
                            "Dia %2d - %s | Medio: %5.2f | Min: %5.2f | Max: %5.2f m3/s | Estado: %s%n",
                            dia.getDiaNumero(),
                            dia.getFecha().format(df),
                            dia.getCaudalMedio(),
                            dia.getCaudalMinimo(),
                            dia.getCaudalMaximo(),
                            dia.getEstado()
                    );
                }
            } else {
                System.out.println("No se recibieron series temporales detalladas.");
            }

            mostrarPrecipitacion(alerta);
        } catch (Exception e) {
            System.out.println("Error al consultar el backend: " + e.getMessage());
        }
    }

    private void mostrarPrecipitacion(AlertaRioDTO alerta) {
        System.out.println("\n--- PRECIPITACION (Open-Meteo) ---");
        if (alerta.getLluvia24hMm() == null
                && alerta.getLluvia72hMm() == null
                && alerta.getMaxLluviaHorariaMm() == null) {
            System.out.println("Sin datos de precipitacion disponibles.");
            return;
        }
        if (alerta.getLluvia24hMm() != null) {
            System.out.printf(Locale.US, "Lluvia acumulada 24 h:  %.2f mm%n", alerta.getLluvia24hMm());
        }
        if (alerta.getLluvia72hMm() != null) {
            System.out.printf(Locale.US, "Lluvia acumulada 72 h:  %.2f mm%n", alerta.getLluvia72hMm());
        }
        if (alerta.getMaxLluviaHorariaMm() != null) {
            System.out.printf(Locale.US, "Lluvia maxima horaria:  %.2f mm%n", alerta.getMaxLluviaHorariaMm());
        }
        if (alerta.getNivelRiesgoLluvia() != null) {
            System.out.println("Nivel de riesgo por lluvia: " + alerta.getNivelRiesgoLluvia());
        }
        if (alerta.getFuentePrecipitacion() != null) {
            System.out.println("Fuente: " + alerta.getFuentePrecipitacion());
        }
        if (alerta.getFechaActualizacionPrecipitacion() != null) {
            System.out.println("Actualizada: " + formatearFecha(alerta.getFechaActualizacionPrecipitacion()));
        }
        System.out.println("Aviso: los umbrales de lluvia son experimentales y no oficiales.");
    }

    private void registrarSuscripcion(Scanner scanner, BackendApiClient.RioApi rio) {
        System.out.println("\n--- REGISTRO DE SUSCRIPCION PARA ALERTAS ---");

        String nombre;
        while (true) {
            System.out.print("Nombre de contacto (Enter para 'Usuario Demo'): ");
            nombre = scanner.nextLine().trim();
            if (nombre.isEmpty()) {
                nombre = "Usuario Demo";
                break;
            }
            if (ValidadorContacto.esNombreValido(nombre)) {
                break;
            }
            System.out.println("[!] Nombre no valido. Debe contener letras y un formato valido (sin solo numeros).");
        }

        String contacto;
        while (true) {
            System.out.print("Contacto (Email o WhatsApp de Panama, Enter para 'usuario@demo.com'): ");
            contacto = scanner.nextLine().trim();
            if (contacto.isEmpty()) {
                contacto = "usuario@demo.com";
                break;
            }
            if (ValidadorContacto.esContactoValido(contacto)) {
                break;
            }
            System.out.println("[!] Formato invalido. Ingrese un correo valido (ej: persona@correo.com) o celular de Panama de 8 digitos iniciando con 6 (ej: 6123-4567).");
        }

        try {
            BackendApiClient.SuscripcionApi s = backend.suscribir(rio.geoglowsId(), nombre, contacto);
            System.out.println("\n[OK] Suscripcion registrada en base de datos PostgreSQL.");
            System.out.println("  ID Registro: " + s.id());
            System.out.println("  Nombre:      " + s.nombreContacto());
            System.out.println("  Contacto:    " + s.contacto());
            System.out.println("  Tramo:       " + s.reachId());
            System.out.println("  Se notificara a este contacto si se detecta riesgo o peligro de inundacion.");
        } catch (Exception e) {
            System.out.println("[!] Error al registrar suscripcion: " + e.getMessage());
        }
    }

    private void ejecutarSimulacion(Scanner scanner, BackendApiClient.RioApi rio) {
        System.out.println("\n============================================================");
        System.out.println("                 SIMULACION DE INUNDACION");
        System.out.println("============================================================");
        System.out.println("Ingrese un caudal hipotetico para simular crecida (ej. 25.0 Vigilancia, 45.0 Riesgo, 75.0 Peligro):");
        System.out.print("Caudal simulado (m3/s) [Enter para usar 75.0]: ");
        String valStr = scanner.nextLine().trim();
        double caudal = 75.0;
        if (!valStr.isEmpty()) {
            try {
                caudal = Double.parseDouble(valStr);
            } catch (NumberFormatException e) {
                System.out.println("Valor invalido, usando 75.0 m3/s");
                caudal = 75.0;
            }
        }

        try {
            AlertaRioDTO sim = backend.simular(rio.geoglowsId(), caudal);
            this.ultimaSimulacion = sim;

            System.out.println("\n--- RESULTADO DE LA SIMULACION ---");
            System.out.printf(Locale.US, "Caudal simulado: %.2f m3/s%n", caudal);
            String estadoFormateado = formatearEstado(sim.getNivelAlerta());
            System.out.println("Estado resultante: " + estadoFormateado);

            boolean requiereNotificacion = "ALERTA".equalsIgnoreCase(sim.getNivelAlerta())
                    || "EMERGENCIA".equalsIgnoreCase(sim.getNivelAlerta());

            if (requiereNotificacion) {
                System.out.println("\n[ALERTA] Se ha detectado un caudal por encima del nivel de riesgo.");
                List<BackendApiClient.SuscripcionApi> subs = List.of();
                String errorContactos = null;
                try {
                    subs = backend.listarSuscripciones(rio.geoglowsId());
                } catch (Exception e) {
                    errorContactos = e.getMessage();
                }
                System.out.println("\nNOTIFICACIONES A CONTACTOS REGISTRADOS (" + subs.size() + "):");
                if (errorContactos != null) {
                    System.out.println("  (No se pudo obtener la lista: " + errorContactos + ")");
                } else if (subs.isEmpty()) {
                    System.out.println("  (No hay contactos suscritos aun. Use la opcion 2 para registrarse)");
                } else {
                    for (BackendApiClient.SuscripcionApi s : subs) {
                        System.out.printf("  - Notificacion enviada a: %s (%s)%n", s.nombreContacto(), s.contacto());
                    }
                }
            } else if ("VIGILANCIA".equalsIgnoreCase(sim.getNivelAlerta())) {
                System.out.println("\n[AVISO] El caudal simulado se encuentra dentro del nivel de VIGILANCIA (preventivo, sin notificacion).");
            } else {
                System.out.println("\nEl caudal simulado se encuentra dentro de condiciones NORMALES (sin notificacion).");
            }
            System.out.println("------------------------------------------------------------");
        } catch (Exception e) {
            System.out.println("Error en la simulacion: " + e.getMessage());
        }
    }

    private void mostrarEstadoSimulado(BackendApiClient.RioApi rio) {
        if (ultimaSimulacion == null) {
            System.out.println("\nNo hay una simulacion activa en este momento.");
            return;
        }

        System.out.println("\n============================================================");
        System.out.println("                    ESTADO SIMULADO");
        System.out.println("============================================================");
        System.out.println("Rio: " + rio.nombre());
        System.out.printf(Locale.US, "Caudal simulado: %.2f m3/s%n", ultimaSimulacion.getCaudalMaximoPronosticado());
        String estado = formatearEstado(ultimaSimulacion.getNivelAlerta());
        System.out.println("Estado: " + estado);

        if ("PELIGRO DE INUNDACION".equalsIgnoreCase(estado)) {
            System.out.println("El caudal simulado supera el umbral de emergencia. Peligro inminente de desbordamiento.");
        } else if ("RIESGO DE INUNDACION".equalsIgnoreCase(estado)) {
            System.out.println("El caudal simulado supera el umbral de alerta. Posible afectacion ribereña.");
        } else if ("VIGILANCIA".equalsIgnoreCase(estado)) {
            System.out.println("El caudal simulado se encuentra dentro del nivel de vigilancia.");
        } else {
            System.out.println("El caudal simulado se encuentra dentro de los valores normales.");
        }
        System.out.println("------------------------------------------------------------");
    }

    private void mostrarSuscripciones(BackendApiClient.RioApi rio) {
        System.out.println("\n--- SUSCRIPTORES ACTIVOS EN BASE DE DATOS ---");
        try {
            List<BackendApiClient.SuscripcionApi> subs = backend.listarSuscripciones(rio.geoglowsId());
            if (subs.isEmpty()) {
                System.out.println("No hay suscriptores registrados aun.");
            } else {
                for (BackendApiClient.SuscripcionApi s : subs) {
                    System.out.printf("  #%d | %s | %s | Tramo: %d | Registrado: %s%n",
                            s.id(), s.nombreContacto(), s.contacto(), s.reachId(), s.fechaRegistro());
                }
            }
        } catch (BackendApiClient.ApiException e) {
            System.out.println("[!] No se pudo consultar la lista: " + e.getMessage());
            if (e.getEstado() == 403) {
                System.out.println("    Configure ALERTA_ADMIN_TOKEN para acceder a este endpoint administrativo.");
            }
        } catch (Exception e) {
            System.out.println("[!] No se pudo consultar la lista: " + e.getMessage());
        }
    }

    private void enviarPushDemo() {
        System.out.println("\n--- NOTIFICACION PUSH DE DEMOSTRACION ---");
        if (!backend.tieneTokenAdmin()) {
            System.out.println("[!] Configure ALERTA_ADMIN_TOKEN en el entorno del backend.");
            System.out.println("    El endpoint /api/push/demo esta protegido con X-Admin-Token.");
            return;
        }
        try {
            ResultadoDemoPush resultado = backend.enviarDemoPush();
            System.out.println("Dispositivos activos: " + resultado.dispositivos());
            System.out.println("Enviados:             " + resultado.enviados());
            System.out.println("Fallidos:             " + resultado.fallidos());
            if (resultado.dispositivos() == 0) {
                System.out.println("\nNo hay dispositivos suscritos.");
                System.out.println("Abra la PWA de prueba en el PC o celular y pulse 'Activar notificaciones'.");
            } else {
                System.out.println("\nLa notificacion debe aparecer en los dispositivos suscritos.");
            }
        } catch (BackendApiClient.ApiException e) {
            if (e.getEstado() == 403) {
                System.out.println("[!] Token administrativo rechazado o no configurado.");
                System.out.println("    Defina ALERTA_ADMIN_TOKEN con el mismo valor usado por el backend.");
            } else if (e.getEstado() == 409) {
                System.out.println("[!] " + e.getMessage());
            } else {
                System.out.println("[!] No se pudo enviar la demostracion: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("[!] No se pudo enviar la demostracion: " + e.getMessage());
        }
    }

    private String formatearEstado(String nivelAlerta) {
        if ("EMERGENCIA".equalsIgnoreCase(nivelAlerta)) {
            return "PELIGRO DE INUNDACION";
        } else if ("ALERTA".equalsIgnoreCase(nivelAlerta)) {
            return "RIESGO DE INUNDACION";
        } else if ("VIGILANCIA".equalsIgnoreCase(nivelAlerta)) {
            return "VIGILANCIA";
        } else {
            return "NORMAL";
        }
    }

    private String formatearFecha(java.time.Instant fecha) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(fecha);
    }
}
