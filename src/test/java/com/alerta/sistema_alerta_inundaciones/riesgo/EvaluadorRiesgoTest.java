package com.alerta.sistema_alerta_inundaciones.riesgo;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.clima.DatosPrecipitacion;
import com.alerta.sistema_alerta_inundaciones.config.PropiedadesRiesgo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluadorRiesgoTest {

    private EvaluadorRiesgo evaluador;

    @BeforeEach
    void preparar() {
        evaluador = new EvaluadorRiesgo(new PropiedadesRiesgo());
    }

    @Test
    void caudalFrescoYLluviaBajaProduceNormalCompleto() {
        DatosRiesgoTramo datos = datos(10.0, Instant.now().minus(1, ChronoUnit.HOURS),
                lluviaDisponible(5.0, 8.0, Instant.now()));

        EvaluacionRiesgo evaluacion = evaluador.evaluar(datos);

        assertEquals(NivelAlerta.NORMAL, evaluacion.nivel());
        assertEquals(NivelAlerta.NORMAL, evaluacion.nivelCaudal());
        assertEquals(NivelAlerta.NORMAL, evaluacion.nivelLluvia());
        assertEquals(EstadoDatos.COMPLETOS, evaluacion.estadoDatos());
        assertTrue(evaluacion.confiable());
        assertEquals(2, evaluacion.factores().size());
    }

    @Test
    void lluviaElevaElNivelAunqueElCaudalSeaNormal() {
        EvaluacionRiesgo alerta = evaluador.evaluar(datos(10.0, Instant.now(), lluviaDisponible(60.0, 80.0, Instant.now())));
        assertEquals(NivelAlerta.ALERTA, alerta.nivel());
        assertEquals(NivelAlerta.NORMAL, alerta.nivelCaudal());
        assertEquals(NivelAlerta.ALERTA, alerta.nivelLluvia());
        assertTrue(alerta.confiable());

        EvaluacionRiesgo emergencia = evaluador.evaluar(datos(10.0, Instant.now(), lluviaDisponible(100.0, 120.0, Instant.now())));
        assertEquals(NivelAlerta.EMERGENCIA, emergencia.nivel());
        assertEquals(NivelAlerta.EMERGENCIA, emergencia.nivelLluvia());
    }

    @Test
    void umbralesDeCaudalSeAplicanEnLosLimites() {
        assertEquals(NivelAlerta.VIGILANCIA,
                evaluador.evaluar(datos(15.0, Instant.now(), sinLluvia())).nivelCaudal());
        assertEquals(NivelAlerta.ALERTA,
                evaluador.evaluar(datos(30.0, Instant.now(), sinLluvia())).nivelCaudal());
        assertEquals(NivelAlerta.EMERGENCIA,
                evaluador.evaluar(datos(60.0, Instant.now(), sinLluvia())).nivelCaudal());
    }

    @Test
    void pronosticoDesactualizadoNoEsConfiableYNoConfirmaElNivel() {
        DatosRiesgoTramo datos = datos(100.0, Instant.now().minus(48, ChronoUnit.HOURS),
                lluviaDisponible(10.0, 10.0, Instant.now()));

        EvaluacionRiesgo evaluacion = evaluador.evaluar(datos);

        assertFalse(evaluacion.confiable());
        assertEquals(EstadoDatos.DESACTUALIZADOS, evaluacion.estadoDatos());
        assertTrue(evaluacion.mensaje().startsWith("DATOS NO CONFIRMADOS"));
        assertTrue(evaluacion.advertencias().stream().anyMatch(a -> a.contains("desactualizado")));
        assertTrue(evaluacion.advertencias().stream().anyMatch(a -> a.contains("no generará notificaciones")));
    }

    @Test
    void fechaDeCaudalDesconocidaProduceDatosInsuficientes() {
        EvaluacionRiesgo evaluacion = evaluador.evaluar(datos(10.0, null, sinLluvia()));

        assertFalse(evaluacion.confiable());
        assertEquals(EstadoDatos.INSUFICIENTES, evaluacion.estadoDatos());
        assertTrue(evaluacion.advertencias().stream()
                .anyMatch(a -> a.contains("No se pudo determinar la fecha")));
    }

    @Test
    void sinPrecipitacionElEstadoQuedaParcialPeroElCaudalSigueSiendoConfiable() {
        DatosRiesgoTramo datos = datos(35.0, Instant.now(), sinLluvia());

        EvaluacionRiesgo evaluacion = evaluador.evaluar(datos);

        assertEquals(EstadoDatos.PARCIALES, evaluacion.estadoDatos());
        assertTrue(evaluacion.confiable());
        assertEquals(NivelAlerta.ALERTA, evaluacion.nivel());
        assertTrue(evaluacion.advertencias().stream()
                .anyMatch(a -> a.toLowerCase().contains("precipitación")));
    }

    @Test
    void lluviaDesactualizadaNoSeUsaParaElNivel() {
        DatosRiesgoTramo datos = datos(10.0, Instant.now(),
                lluviaDisponible(200.0, 200.0, Instant.now().minus(12, ChronoUnit.HOURS)));

        EvaluacionRiesgo evaluacion = evaluador.evaluar(datos);

        assertEquals(NivelAlerta.NORMAL, evaluacion.nivelLluvia());
        assertEquals(NivelAlerta.NORMAL, evaluacion.nivel());
        assertEquals(EstadoDatos.PARCIALES, evaluacion.estadoDatos());
        assertTrue(evaluacion.advertencias().stream()
                .anyMatch(a -> a.contains("precipitación está desactualizada")));
    }

    @Test
    void caudalNoNumericoProduceInsuficientes() {
        DatosRiesgoTramo datos = datos(Double.NaN, Instant.now(), sinLluvia());

        EvaluacionRiesgo evaluacion = evaluador.evaluar(datos);

        assertEquals(EstadoDatos.INSUFICIENTES, evaluacion.estadoDatos());
        assertFalse(evaluacion.confiable());
        assertTrue(evaluacion.advertencias().stream()
                .anyMatch(a -> a.contains("no contiene valores numéricos")));
    }

    @Test
    void umbralesDeReferenciaSeAdviertenExplicitamente() {
        DatosRiesgoTramo datos = new DatosRiesgoTramo(
                1L, "Río de prueba", 9.0, -79.0,
                5.0, 10.0, 15.0, 30.0, 60.0,
                Instant.now(), "GEOGloWS", "Configuración de referencia (demostración, no oficial)",
                List.of(new DatosRiesgoTramo.PuntoCaudal(Instant.now(), 10.0)),
                sinLluvia(), List.of());

        EvaluacionRiesgo evaluacion = evaluador.evaluar(datos);

        assertNotNull(evaluacion.fuenteUmbrales());
        assertTrue(evaluacion.advertencias().stream()
                .anyMatch(a -> a.contains("umbrales de caudal son de referencia")));
    }

    private DatosRiesgoTramo datos(double caudalMaximo, Instant fechaCaudal, DatosPrecipitacion precipitacion) {
        return new DatosRiesgoTramo(
                1L, "Río de prueba", 9.08, -79.28,
                5.0, caudalMaximo, 15.0, 30.0, 60.0,
                fechaCaudal, "GEOGloWS forecaststats",
                "GEOGloWS - periodos de retorno",
                List.of(new DatosRiesgoTramo.PuntoCaudal(Instant.now(), caudalMaximo)),
                precipitacion, List.of());
    }

    private DatosPrecipitacion lluviaDisponible(double lluvia24, double lluvia72, Instant fechaConsulta) {
        return new DatosPrecipitacion(
                true, 9.08, -79.28, "Open-Meteo (api.open-meteo.com)",
                fechaConsulta, lluvia24, lluvia72, 10.0,
                List.of(), null);
    }

    private DatosPrecipitacion sinLluvia() {
        return DatosPrecipitacion.noDisponible(9.08, -79.28,
                "No hay datos de precipitación disponibles para esta zona.");
    }
}
