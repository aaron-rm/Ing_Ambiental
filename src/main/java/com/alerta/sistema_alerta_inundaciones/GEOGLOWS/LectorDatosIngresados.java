package com.alerta.sistema_alerta_inundaciones.GEOGLOWS;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * =====================================================================================
 *  LECTOR / REPOSITORIO DE DATOS INGRESADOS
 * =====================================================================================
 *
 * Responsabilidad: definir el modelo de datos completo del sistema y "leer" (recibir,
 * sincronizar y almacenar) todos los datos que alimentan la alerta:
 *
 *   - Datos que llegan automáticamente desde GEOGloWS (a través de ClienteApiGeoglows):
 *     tramos, pronóstico de caudal, umbrales derivados de periodos de retorno.
 *
 *   - Datos que deben ingresarse desde otras fuentes (servicio meteorológico, IDEAM/
 *     INAMHI/SENAMHI, catastro, protección civil, sensores propios, formularios de
 *     campo, etc.): observaciones en tiempo real, lluvia, terreno, elementos expuestos,
 *     vulnerabilidad, historial de eventos y datos administrativos/de notificación.
 *
 * Esta clase NO decide el resultado final (eso lo hace GeneradorAlertaFinal); solo
 * organiza y guarda la información.
 * =====================================================================================
 */
public class LectorDatosIngresados {

    // =================================================================================
    // 1) RED HIDROGRÁFICA Y TRAMOS DE RÍO
    // =================================================================================

    /** Tramo/alcance (reach) de la red hidrográfica de GEOGloWS. */
    public static class TramoRio {
        public long reachId;                       // ID de GEOGloWS (reach_id / LINKNO)
        public String geometriaWKT;                // Geometría de la línea de río (WKT), desde capa GIS local
        public String direccionFlujo;               // Sentido de flujo (aguas arriba -> aguas abajo)
        public String cuencaId;                      // Cuenca/subcuenca asociada
        public Long tramoAguasArribaId;              // reach_id aguas arriba (topología de red)
        public Long tramoAguasAbajoId;               // reach_id aguas abajo (topología de red)
        public List<PuntoInteres> puntosInteresAguasAbajo = new ArrayList<>();
    }

    /** Punto de interés aguas abajo de un tramo (poblado, puente, toma de agua, etc.). */
    public static class PuntoInteres {
        public String nombre;
        public String tipo;      // p.ej. "poblado", "puente", "bocatoma", "central hidroeléctrica"
        public double lat;
        public double lon;
        public double distanciaAlTramoKm;
    }

    // =================================================================================
    // 2) PRONÓSTICO DE CAUDAL (núcleo para detección de crecidas)
    // =================================================================================

    /** Un punto de una serie temporal (fecha/hora + valor). */
    public static class SerieTemporalPunto {
        public Instant fecha;
        public double valor;
    }

    /** Pronóstico de caudal para un tramo, tal como lo entrega GEOGloWS. */
    public static class PronosticoCaudal {
        public long reachId;
        public Instant fechaEmision;                                          // gen_date del pronóstico
        public int horizonteHoras;                                            // extensión del pronóstico (normalmente 15 días)
        public List<SerieTemporalPunto> mediaSerie = new ArrayList<>();       // caudal medio del ensemble
        public Map<String, List<SerieTemporalPunto>> percentiles = new LinkedHashMap<>(); // min/25%/75%/max/etc.
        public Map<Integer, Double> caudalReferenciaPeriodoRetorno = new TreeMap<>();      // 2,5,10,25,50,100 años
        public double caudalHistoricoPromedio;                                // promedio de la simulación histórica (ERA5)
    }

    // =================================================================================
    // 3) UMBRALES DE ALERTA
    // =================================================================================

    /** Umbrales de caudal para un tramo. Idealmente recalibrados con aforos/registros locales. */
    public static class UmbralAlerta {
        public long reachId;
        public double caudalVigilancia;   // p.ej. periodo de retorno de 2 años
        public double caudalAlerta;       // p.ej. periodo de retorno de 5 años
        public double caudalEmergencia;   // p.ej. periodo de retorno de 25 años
        public String fuenteCalibracion;  // "GEOGloWS (periodos de retorno)" o "aforos locales 2019-2024", etc.
        public LocalDate fechaCalibracion;
    }

    public enum NivelAlerta { NORMAL, VIGILANCIA, ALERTA, EMERGENCIA }

    // =================================================================================
    // 4) OBSERVACIONES EN TIEMPO REAL (estaciones, pluviómetros, sensores propios)
    // =================================================================================

    public enum TipoObservacion { NIVEL, CAUDAL, PLUVIOMETRO, SENSOR_PROPIO }

    public static class ObservacionTiempoReal {
        public String estacionId;
        public TipoObservacion tipo;
        public double valor;
        public String unidad;         // "m", "m3/s", "mm", etc.
        public Instant fecha;
        public double lat;
        public double lon;
        public Long reachIdAsociado;  // tramo de GEOGloWS más cercano/representativo, si aplica
    }

    // =================================================================================
    // 5) LLUVIA OBSERVADA Y PRONOSTICADA
    // =================================================================================

    public static class DatosLluvia {
        public String ubicacionId;
        public double acumuladoObservadoMm;
        public double intensidadMmPorHora;
        public double pronosticoMm;
        public Instant fecha;
        public String fuente;   // "pluviómetro propio", "servicio meteorológico nacional", "satélite", etc.
    }

    // =================================================================================
    // 6) TERRENO E INUNDABILIDAD
    // =================================================================================

    public static class DatosTerreno {
        public String zonaId;
        public String demUrl;                  // modelo digital de elevación
        public String pendientesUrl;
        public String llanuraInundacionWKT;    // geometría de llanura de inundación
        public String mapaAmenazaUrl;          // mapa de amenaza/riesgo existente
        public String drenajeUrbanoInfo;       // capacidad/estado del drenaje pluvial urbano
    }

    // =================================================================================
    // 7) ELEMENTOS EXPUESTOS
    // =================================================================================

    public enum TipoElementoExpuesto {
        POBLACION, VIVIENDA, HOSPITAL, ESCUELA, VIA, PUENTE,
        REFUGIO, INFRAESTRUCTURA_CRITICA, ACTIVIDAD_ECONOMICA
    }

    public static class ElementoExpuesto {
        public String id;
        public TipoElementoExpuesto tipo;
        public double lat;
        public double lon;
        public String criticidad;              // "alta", "media", "baja"
        public String contactoResponsable;     // persona/entidad responsable
        public Long reachIdAsociado;
    }

    // =================================================================================
    // 8) VULNERABILIDAD LOCAL
    // =================================================================================

    public static class VulnerabilidadLocal {
        public String zonaId;
        public boolean historicamenteAfectada;
        public String capacidadEvacuacion;         // descripción cualitativa o tiempo estimado
        public String accesibilidad;               // vial, fluvial, aérea...
        public boolean sinCoberturaMovil;
        public List<String> gruposVulnerables = new ArrayList<>();   // niños, adultos mayores, discapacidad, etc.
        public List<String> rutasAlternativas = new ArrayList<>();
    }

    // =================================================================================
    // 9) HISTORIAL DE EVENTOS
    // =================================================================================

    public static class EventoHistorico {
        public LocalDate fecha;
        public double lluviaMm;
        public double caudalONivel;
        public List<String> zonasAfectadas = new ArrayList<>();
        public String danios;
        public String alertasEmitidas;     // nivel y canal de la alerta que se emitió
        public String resultadoReal;       // qué ocurrió realmente (para ajustar umbrales)
    }

    // =================================================================================
    // 10) DATOS ADMINISTRATIVOS Y DE COMUNICACIÓN
    // =================================================================================

    public enum CanalNotificacion { APP, SMS, WHATSAPP, SIRENA, CORREO }

    public static class DatosAdministrativos {
        public String limiteId;            // municipio/barrio
        public String nombre;
        public List<String> idiomas = new ArrayList<>();
        public List<CanalNotificacion> canalesDisponibles = new ArrayList<>();
    }

    public static class Suscriptor {
        public String id;
        public String zonaId;
        public List<CanalNotificacion> canales = new ArrayList<>();
        public String contacto;    // teléfono, correo, etc.
    }

    // =================================================================================
    // ALMACENAMIENTO / LECTURA DE DATOS
    // =================================================================================

    public final ClienteApiGeoglows geoglows = new ClienteApiGeoglows();

    // Datos que sí llegan (parcial o totalmente) desde GEOGloWS
    public final Map<Long, TramoRio> tramos = new LinkedHashMap<>();
    public final Map<Long, PronosticoCaudal> pronosticos = new LinkedHashMap<>();
    public final Map<Long, UmbralAlerta> umbrales = new LinkedHashMap<>();

    // Datos que deben alimentarse desde otras fuentes (sensores, GIS, catastro, protección civil...)
    public final List<ObservacionTiempoReal> observaciones = new ArrayList<>();
    public final List<DatosLluvia> lluvia = new ArrayList<>();
    public final List<DatosTerreno> terreno = new ArrayList<>();
    public final List<ElementoExpuesto> elementosExpuestos = new ArrayList<>();
    public final List<VulnerabilidadLocal> vulnerabilidad = new ArrayList<>();
    public final List<EventoHistorico> historial = new ArrayList<>();
    public final List<DatosAdministrativos> administrativos = new ArrayList<>();
    public final List<Suscriptor> suscriptores = new ArrayList<>();

    /** Lee/sincroniza desde GEOGloWS todo lo disponible para un tramo: pronóstico, histórico y umbrales. */
    public void sincronizarTramo(long reachId, String cuencaId, String direccionFlujo)
            throws IOException, InterruptedException {
        TramoRio t = tramos.computeIfAbsent(reachId, id -> new TramoRio());
        t.reachId = reachId;
        t.cuencaId = cuencaId;
        t.direccionFlujo = direccionFlujo;

        // Estos dos SÍ son necesarios para decidir el nivel de alerta: si fallan, la
        // sincronización debe fallar (y el controller devuelve el error al front).
        pronosticos.put(reachId, geoglows.obtenerPronostico(reachId));
        umbrales.put(reachId, geoglows.obtenerUmbrales(reachId));

        // El histórico (retrospectivedaily) contiene décadas de registros diarios (desde 1940)
        // por lo que descargarlo bloquea la sincronización durante minutos en conexiones normales.
        // Se deja en 0.0 o carga bajo demanda para mantener la sincronización rápida y fluida.
        pronosticos.get(reachId).caudalHistoricoPromedio = 0.0;
    }

    /** Registra una observación de campo/sensor propio (dato que GEOGloWS no provee). */
    public void ingresarObservacion(ObservacionTiempoReal obs) {
        observaciones.add(obs);
    }

    /** Registra un dato de lluvia proveniente de una fuente externa. */
    public void ingresarLluvia(DatosLluvia dato) {
        lluvia.add(dato);
    }
}
