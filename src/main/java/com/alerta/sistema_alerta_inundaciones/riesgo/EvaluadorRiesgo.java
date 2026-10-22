package com.alerta.sistema_alerta_inundaciones.riesgo;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.clima.DatosPrecipitacion;
import com.alerta.sistema_alerta_inundaciones.config.PropiedadesRiesgo;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lógica de evaluación de riesgo, separada de las llamadas externas para poder
 * probarla y para dejar claro qué es dato y qué es regla.
 *
 * Reglas:
 *  - Nivel por caudal: máximo pronosticado contra los umbrales de GEOGloWS
 *    (o los de referencia si la API de periodos de retorno no responde).
 *  - Nivel por lluvia: máximo acumulado en 24 h contra umbrales configurables
 *    PROVISIONALES, documentados como experimentales.
 *  - Nivel final: el mayor de ambos.
 *  - Si el pronóstico de caudal no es reciente, el resultado se marca como no
 *    confiable y no debe generar notificaciones push.
 */
@Component
public class EvaluadorRiesgo {

    private final PropiedadesRiesgo propiedades;

    public EvaluadorRiesgo(PropiedadesRiesgo propiedades) {
        this.propiedades = propiedades;
    }

    public EvaluacionRiesgo evaluar(DatosRiesgoTramo datos) {
        Instant ahora = Instant.now();
        List<String> factores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>(datos.advertencias() == null ? List.of() : datos.advertencias());

        if (Double.isNaN(datos.caudalMaximo()) || Double.isInfinite(datos.caudalMaximo())) {
            advertencias.add("El pronóstico de caudal no contiene valores numéricos válidos.");
            return new EvaluacionRiesgo(
                    datos.reachId(), datos.nombreRio(), NivelAlerta.NORMAL, NivelAlerta.NORMAL, NivelAlerta.NORMAL,
                    false, EstadoDatos.INSUFICIENTES,
                    datos.caudalActual(), datos.caudalMaximo(),
                    datos.umbralVigilancia(), datos.umbralAlerta(), datos.umbralEmergencia(),
                    null, null, ahora, datos.fechaEmisionCaudal(), null,
                    datos.fuenteCaudal(), datos.precipitacion() != null ? datos.precipitacion().fuente() : "No disponible",
                    datos.fuenteUmbrales(), List.copyOf(factores), List.copyOf(advertencias),
                    "Sin datos de caudal válidos. No se puede confirmar el nivel de riesgo."
            );
        }

        boolean fechaCaudalConocida = datos.fechaEmisionCaudal() != null;
        boolean caudalFresco = fechaCaudalConocida
                && !datos.fechaEmisionCaudal().isBefore(ahora.minus(Duration.ofHours(
                        Math.max(1, propiedades.getMaxEdadPronosticoHoras()))));

        if (!fechaCaudalConocida) {
            advertencias.add("No se pudo determinar la fecha de emisión del pronóstico de caudal.");
        } else if (!caudalFresco) {
            advertencias.add("El pronóstico de caudal está desactualizado (emitido el "
                    + datos.fechaEmisionCaudal() + ").");
        }

        if (datos.fuenteUmbrales() != null
                && datos.fuenteUmbrales().toLowerCase(Locale.ROOT).contains("referencia")) {
            advertencias.add("Los umbrales de caudal son de referencia y no están calibrados con datos locales.");
        }

        NivelAlerta nivelCaudal = nivelPorCaudal(datos.caudalMaximo(), datos);
        factores.add(String.format(Locale.US,
                "Caudal máximo previsto: %.2f m³/s (umbrales: vigilancia %.2f, alerta %.2f, emergencia %.2f m³/s).",
                datos.caudalMaximo(), datos.umbralVigilancia(), datos.umbralAlerta(), datos.umbralEmergencia()));

        DatosPrecipitacion precipitacion = datos.precipitacion();
        boolean lluviaDisponible = precipitacion != null && precipitacion.disponible()
                && precipitacion.fechaConsulta() != null;
        boolean lluviaFresca = false;
        NivelAlerta nivelLluvia = NivelAlerta.NORMAL;

        if (lluviaDisponible) {
            lluviaFresca = !precipitacion.fechaConsulta().isBefore(ahora.minus(Duration.ofHours(
                    Math.max(1, propiedades.getMaxEdadPrecipitacionHoras()))));
            if (lluviaFresca) {
                nivelLluvia = nivelPorLluvia(precipitacion.lluvia24hMm());
                factores.add(String.format(Locale.US,
                        "Lluvia prevista en 24 h: %.2f mm (umbrales experimentales: vigilancia %.1f, alerta %.1f, emergencia %.1f mm).",
                        precipitacion.lluvia24hMm(),
                        propiedades.getLluviaUmbralVigilanciaMm(),
                        propiedades.getLluviaUmbralAlertaMm(),
                        propiedades.getLluviaUmbralEmergenciaMm()));
            } else {
                advertencias.add("La consulta de precipitación está desactualizada ("
                        + precipitacion.fechaConsulta() + "); no se usó para el nivel final.");
            }
        } else {
            advertencias.add(precipitacion != null && precipitacion.advertencia() != null
                    ? precipitacion.advertencia()
                    : "No hay datos de precipitación disponibles para esta zona.");
        }

        NivelAlerta nivelFinal = mayor(nivelCaudal, nivelLluvia);
        boolean confiable = caudalFresco;
        EstadoDatos estadoDatos;
        if (!caudalFresco) {
            estadoDatos = fechaCaudalConocida ? EstadoDatos.DESACTUALIZADOS : EstadoDatos.INSUFICIENTES;
        } else if (lluviaDisponible && lluviaFresca) {
            estadoDatos = EstadoDatos.COMPLETOS;
        } else {
            estadoDatos = EstadoDatos.PARCIALES;
        }

        if (!confiable) {
            advertencias.add("El nivel calculado no se considera confirmado y no generará notificaciones push.");
        }

        String mensaje = construirMensaje(nivelFinal, datos, nivelCaudal, nivelLluvia, confiable, estadoDatos);

        return new EvaluacionRiesgo(
                datos.reachId(),
                datos.nombreRio(),
                nivelFinal,
                nivelCaudal,
                nivelLluvia,
                confiable,
                estadoDatos,
                datos.caudalActual(),
                datos.caudalMaximo(),
                datos.umbralVigilancia(),
                datos.umbralAlerta(),
                datos.umbralEmergencia(),
                lluviaDisponible ? redondear(precipitacion.lluvia24hMm()) : null,
                lluviaDisponible ? redondear(precipitacion.lluvia72hMm()) : null,
                ahora,
                datos.fechaEmisionCaudal(),
                lluviaDisponible ? precipitacion.fechaConsulta() : null,
                datos.fuenteCaudal(),
                lluviaDisponible ? precipitacion.fuente() : "No disponible",
                datos.fuenteUmbrales(),
                List.copyOf(factores),
                List.copyOf(advertencias),
                mensaje
        );
    }

    public NivelAlerta nivelPorCaudal(double caudalMaximo, DatosRiesgoTramo datos) {
        if (caudalMaximo >= datos.umbralEmergencia()) {
            return NivelAlerta.EMERGENCIA;
        }
        if (caudalMaximo >= datos.umbralAlerta()) {
            return NivelAlerta.ALERTA;
        }
        if (caudalMaximo >= datos.umbralVigilancia()) {
            return NivelAlerta.VIGILANCIA;
        }
        return NivelAlerta.NORMAL;
    }

    public NivelAlerta nivelPorLluvia(double lluvia24hMm) {
        if (lluvia24hMm >= propiedades.getLluviaUmbralEmergenciaMm()) {
            return NivelAlerta.EMERGENCIA;
        }
        if (lluvia24hMm >= propiedades.getLluviaUmbralAlertaMm()) {
            return NivelAlerta.ALERTA;
        }
        if (lluvia24hMm >= propiedades.getLluviaUmbralVigilanciaMm()) {
            return NivelAlerta.VIGILANCIA;
        }
        return NivelAlerta.NORMAL;
    }

    public static NivelAlerta mayor(NivelAlerta a, NivelAlerta b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private String construirMensaje(NivelAlerta nivel, DatosRiesgoTramo datos,
                                    NivelAlerta nivelCaudal, NivelAlerta nivelLluvia,
                                    boolean confiable, EstadoDatos estadoDatos) {
        if (!confiable) {
            return "DATOS NO CONFIRMADOS: no hay un pronostico de caudal reciente. "
                    + "Nivel calculado sin confirmar: " + textoNivel(nivel)
                    + ". No se enviaran notificaciones push hasta actualizar los datos.";
        }

        String detalle = String.format(Locale.US,
                "Caudal maximo previsto %.2f m3/s (nivel %s); lluvia 24 h %s (nivel %s).",
                datos.caudalMaximo(), textoNivel(nivelCaudal),
                datos.precipitacion() != null && datos.precipitacion().disponible()
                        ? String.format(Locale.US, "%.2f mm", datos.precipitacion().lluvia24hMm())
                        : "no disponible",
                textoNivel(nivelLluvia));

        return switch (nivel) {
            case EMERGENCIA -> "PELIGRO DE INUNDACION: " + detalle
                    + " Nivel critico segun los umbrales configurados. Este sistema no es una declaracion oficial de emergencia.";
            case ALERTA -> "RIESGO DE INUNDACION: " + detalle
                    + " Puede haber afectacion en zonas riberenas. Siga las indicaciones de las autoridades competentes.";
            case VIGILANCIA -> "VIGILANCIA: " + detalle
                    + " Condiciones que ameritan monitoreo preventivo; no es una alerta de emergencia.";
            case NORMAL -> "NORMAL: " + detalle
                    + " Sin cambios relevantes respecto a los umbrales configurados.";
        };
    }

    private static String textoNivel(NivelAlerta nivel) {
        return switch (nivel) {
            case EMERGENCIA -> "PELIGRO DE INUNDACION";
            case ALERTA -> "RIESGO DE INUNDACION";
            case VIGILANCIA -> "VIGILANCIA";
            case NORMAL -> "NORMAL";
        };
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
