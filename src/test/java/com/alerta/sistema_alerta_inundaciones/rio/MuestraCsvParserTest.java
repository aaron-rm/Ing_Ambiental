package com.alerta.sistema_alerta_inundaciones.rio;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MuestraCsvParserTest {

    private static final Instant AHORA_FIJO = Instant.parse("2026-11-24T12:00:00Z");

    @Test
    void parseaCsvConComillasComasInternasYComillasEscapadas() {
        String csv = "\uFEFF  FECHA , Parametro , Valor , Unidad , Nota \n"
                + "2026-11-20,Oxígeno disuelto,7.4,mg/L,\"Muestra en \"\"puente\"\", sin lluvia\"\n"
                + "2026-11-20T14:30,Turbidez,12.5,,\n";

        List<MuestraCsvParser.FilaMuestra> filas = MuestraCsvParser.parsear(csv, AHORA_FIJO);

        assertEquals(2, filas.size());
        assertEquals(Instant.parse("2026-11-20T05:00:00Z"), filas.get(0).fecha());
        assertEquals("Oxígeno disuelto", filas.get(0).parametro());
        assertEquals(7.4, filas.get(0).valor());
        assertEquals("mg/L", filas.get(0).unidad());
        assertEquals("Muestra en \"puente\", sin lluvia", filas.get(0).nota());

        assertEquals(Instant.parse("2026-11-20T19:30:00Z"), filas.get(1).fecha());
        assertEquals("", filas.get(1).unidad());
        assertNull(filas.get(1).nota());
    }

    @Test
    void rechazaCabeceraInvalida() {
        String csv = "fecha,parametro,valor,unidad\n2026-11-20,pH,7.1,u\n";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MuestraCsvParser.parsear(csv, AHORA_FIJO));

        assertThat(error.getMessage()).contains("Cabecera");
    }

    @Test
    void reportaNumeroDeLineaEnFilaInvalida() {
        String csv = "fecha,parametro,valor,unidad,nota\n"
                + "2026-11-20,pH,7.1,u,ok\n"
                + "2026-11-20,pH,no_numero,u,mal\n";

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MuestraCsvParser.parsear(csv, AHORA_FIJO));

        assertThat(error.getMessage()).contains("Fila 3:");
    }

    @Test
    void aceptaSeparadorPuntoYComaConComaDecimal() {
        String csv = "fecha;parametro;valor;unidad;nota\n"
                + "2026-11-20 08:15;pH;7,25;u;Lectura manual\n";

        List<MuestraCsvParser.FilaMuestra> filas = MuestraCsvParser.parsear(csv, AHORA_FIJO);

        assertEquals(1, filas.size());
        assertEquals(7.25, filas.get(0).valor());
        assertEquals(Instant.parse("2026-11-20T13:15:00Z"), filas.get(0).fecha());
    }

    @Test
    void parsearFechaAceptaLosTresFormatosYRechazaFechaFutura() {
        assertEquals(
                Instant.parse("2026-11-20T05:00:00Z"),
                MuestraCsvParser.parsearFecha("2026-11-20", AHORA_FIJO));
        assertEquals(
                Instant.parse("2026-11-20T19:30:00Z"),
                MuestraCsvParser.parsearFecha("2026-11-20T14:30", AHORA_FIJO));
        assertEquals(
                Instant.parse("2026-11-20T19:30:00Z"),
                MuestraCsvParser.parsearFecha("2026-11-20 14:30", AHORA_FIJO));

        assertThrows(IllegalArgumentException.class,
                () -> MuestraCsvParser.parsearFecha("2026-11-26T12:00", AHORA_FIJO));
    }
}
