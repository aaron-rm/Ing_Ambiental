package com.alerta.sistema_alerta_inundaciones.demo;

import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaService;
import com.alerta.sistema_alerta_inundaciones.alerta.PronosticoDiaDTO;
import com.alerta.sistema_alerta_inundaciones.alerta.SuscripcionAlerta;
import com.alerta.sistema_alerta_inundaciones.alerta.ValidadorContacto;
import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

@Component
public class ConsolaDemoRunner implements CommandLineRunner {

    private final AlertaService alertaService;
    private final RioRepository rioRepository;
    private final org.springframework.core.env.Environment environment;

    // Estado simulado en memoria para la consola (separado del estado real)
    private AlertaRioDTO ultimaSimulacion = null;

    public ConsolaDemoRunner(AlertaService alertaService, RioRepository rioRepository, org.springframework.core.env.Environment environment) {
        this.alertaService = alertaService;
        this.rioRepository = rioRepository;
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
        } catch (InterruptedException ignored) {}

        List<Rio> rios = rioRepository.findAll();
        Rio rioDemo;
        if (rios.isEmpty()) {
            rioDemo = new Rio("Rio Pacora (Panama)", 710039498L, 9.08, -79.28);
            rioDemo = rioRepository.save(rioDemo);
        } else {
            rioDemo = rios.get(0);
            boolean modificado = false;
            if (rioDemo.getGeoglowsId() == null || rioDemo.getGeoglowsId() == 123456789L) {
                rioDemo.setGeoglowsId(710039498L);
                rioDemo.setNombre("Rio Pacora (Panama)");
                modificado = true;
            }
            if (rioDemo.getLatitud() == null || rioDemo.getLatitud() == 0.0) {
                rioDemo.setLatitud(9.08);
                rioDemo.setLongitud(-79.28);
                modificado = true;
            }
            if (modificado) {
                rioDemo = rioRepository.save(rioDemo);
            }
        }

        Scanner scanner = new Scanner(System.in);

        System.out.println();
        System.out.println("============================================================");
        System.out.println("      SISTEMA DE ALERTA TEMPRANA DE INUNDACIONES");
        System.out.println("                       GEOGLOWS");
        System.out.println("============================================================");
        System.out.println("Rio seleccionado: " + rioDemo.getNombre());
        System.out.println("ID GEOGLOWS (reach_id): " + rioDemo.getGeoglowsId());

        if (rioDemo.getLatitud() != null && rioDemo.getLongitud() != null
                && (rioDemo.getLatitud() != 0.0 || rioDemo.getLongitud() != 0.0)) {
            System.out.printf(Locale.US, "Coordenadas: Lat %.4f, Lon %.4f%n", rioDemo.getLatitud(), rioDemo.getLongitud());
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
                System.out.println("  6. Salir");
            } else {
                System.out.println("  4. Ver suscriptores registrados");
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
                    case "6":
                        System.out.println("\nCerrando interfaz de consola. El servidor backend sigue activo.");
                        continuar = false;
                        break;
                    default:
                        System.out.println("Opcion no valida. Ingrese un numero del 1 al 6.");
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
                    case "5":
                        System.out.println("\nCerrando interfaz de consola. El servidor backend sigue activo.");
                        continuar = false;
                        break;
                    default:
                        System.out.println("Opcion no valida. Ingrese un numero del 1 al 5.");
                }
            }
        }
    }

    private void mostrarPronosticoReal(Rio rio) {
        System.out.println("\n[1/3] Consultando datos de GEOGLOWS API v2...");
        try {
            AlertaRioDTO alerta = alertaService.consultarAlertaTramo(rio.getGeoglowsId(), rio.getNombre());

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
        } catch (Exception e) {
            System.out.println("Error al comunicarse con GEOGLOWS: " + e.getMessage());
        }
    }

    private void registrarSuscripcion(Scanner scanner, Rio rio) {
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
            SuscripcionAlerta s = alertaService.suscribirUsuario(rio.getGeoglowsId(), nombre, contacto);
            System.out.println("\n[OK] Suscripcion registrada en base de datos PostgreSQL.");
            System.out.println("  ID Registro: " + s.getId());
            System.out.println("  Nombre:      " + s.getNombreContacto());
            System.out.println("  Contacto:    " + s.getContacto());
            System.out.println("  Tramo:       " + s.getReachId());
            System.out.println("  Se notificara a este contacto si se detecta riesgo o peligro de inundacion.");
        } catch (Exception e) {
            System.out.println("[!] Error al registrar suscripcion: " + e.getMessage());
        }
    }

    private void ejecutarSimulacion(Scanner scanner, Rio rio) {
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
            AlertaRioDTO sim = alertaService.simularInundacion(rio.getGeoglowsId(), caudal);
            this.ultimaSimulacion = sim;

            System.out.println("\n--- RESULTADO DE LA SIMULACION ---");
            System.out.printf(Locale.US, "Caudal simulado: %.2f m3/s%n", caudal);
            String estadoFormateado = formatearEstado(sim.getNivelAlerta());
            System.out.println("Estado resultante: " + estadoFormateado);

            boolean requiereNotificacion = "ALERTA".equalsIgnoreCase(sim.getNivelAlerta()) ||
                                           "EMERGENCIA".equalsIgnoreCase(sim.getNivelAlerta());

            if (requiereNotificacion) {
                System.out.println("\n[ALERTA] Se ha detectado un caudal por encima del nivel de riesgo.");
                List<SuscripcionAlerta> subs = alertaService.obtenerSuscripcionesPorTramo(rio.getGeoglowsId());
                System.out.println("\nNOTIFICACIONES A CONTACTOS REGISTRADOS (" + subs.size() + "):");
                if (subs.isEmpty()) {
                    System.out.println("  (No hay contactos suscritos aun. Use la opcion 2 para registrarse)");
                } else {
                    for (SuscripcionAlerta s : subs) {
                        System.out.printf("  - Notificacion enviada a: %s (%s)%n", s.getNombreContacto(), s.getContacto());
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

    private void mostrarEstadoSimulado(Rio rio) {
        if (ultimaSimulacion == null) {
            System.out.println("\nNo hay una simulacion activa en este momento.");
            return;
        }

        System.out.println("\n============================================================");
        System.out.println("                    ESTADO SIMULADO");
        System.out.println("============================================================");
        System.out.println("Rio: " + rio.getNombre());
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

    private void mostrarSuscripciones(Rio rio) {
        System.out.println("\n--- SUSCRIPTORES ACTIVOS EN BASE DE DATOS ---");
        List<SuscripcionAlerta> subs = alertaService.obtenerSuscripcionesPorTramo(rio.getGeoglowsId());
        if (subs.isEmpty()) {
            System.out.println("No hay suscriptores registrados aun.");
        } else {
            for (SuscripcionAlerta s : subs) {
                System.out.printf("  #%d | %s | %s | Tramo: %d | Registrado: %s%n",
                        s.getId(), s.getNombreContacto(), s.getContacto(), s.getReachId(), s.getFechaRegistro());
            }
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
}
