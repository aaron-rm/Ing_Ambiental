package com.alerta.sistema_alerta_inundaciones.rio;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parser CSV y de fechas para muestras de laboratorio, sin dependencias de Spring.
 * En los mensajes de error "Fila N: motivo", N corresponde al número de línea en el
 * archivo CSV (1 para la cabecera, 2 en adelante para las filas de datos).
 */
public final class MuestraCsvParser {

    public static final ZoneId ZONA_PANAMA = ZoneId.of("America/Panama");
    public static final int MAX_FILAS = 5000;
    public static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_ERRORES_EN_MENSAJE = 10;
    private static final List<String> CABECERA_ESPERADA =
            List.of("fecha", "parametro", "valor", "unidad", "nota");

    private MuestraCsvParser() {
    }

    public record FilaMuestra(
            Instant fecha,
            String parametro,
            double valor,
            String unidad,
            String nota
    ) {
    }

    private record LineaCruda(int numeroLinea, String texto) {
    }

    public static List<FilaMuestra> parsear(String contenido) {
        return parsear(contenido, Instant.now());
    }

    static List<FilaMuestra> parsear(String contenido, Instant ahora) {
        if (contenido == null || contenido.isBlank()) {
            throw new IllegalArgumentException("El contenido CSV está vacío.");
        }
        if (contenido.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("El archivo CSV supera el tamaño máximo permitido de 1 MB.");
        }

        String sinBom = contenido.startsWith("\uFEFF") ? contenido.substring(1) : contenido;
        List<LineaCruda> lineas = dividirLineasNoVacias(sinBom);
        if (lineas.isEmpty()) {
            throw new IllegalArgumentException("El contenido CSV está vacío.");
        }

        LineaCruda primeraLinea = lineas.get(0);
        char separador = detectarSeparador(primeraLinea.texto());
        List<String> cabecera = parsearLineaCsv(primeraLinea.texto(), separador).stream()
                .map(c -> c.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (!CABECERA_ESPERADA.equals(cabecera)) {
            throw new IllegalArgumentException(
                    "Cabecera CSV inválida. Se esperaba: fecha,parametro,valor,unidad,nota.");
        }

        int filasDatos = lineas.size() - 1;
        if (filasDatos > MAX_FILAS) {
            throw new IllegalArgumentException(
                    "El CSV supera el máximo permitido de " + MAX_FILAS + " filas de datos.");
        }

        List<FilaMuestra> resultado = new ArrayList<>(filasDatos);
        List<String> errores = new ArrayList<>();

        for (int i = 1; i < lineas.size(); i++) {
            LineaCruda linea = lineas.get(i);
            try {
                resultado.add(parsearFila(linea.texto(), separador, ahora));
            } catch (IllegalArgumentException e) {
                errores.add("Fila " + linea.numeroLinea() + ": " + e.getMessage());
            }
        }

        if (!errores.isEmpty()) {
            throw new IllegalArgumentException(formatearErrores(errores));
        }
        return resultado;
    }

    /**
     * Parsea una fecha en formato yyyy-MM-dd (medianoche en America/Panama),
     * yyyy-MM-ddTHH:mm / yyyy-MM-dd HH:mm (hora en America/Panama) o ISO con zona/offset.
     */
    public static Instant parsearFecha(String texto) {
        return parsearFecha(texto, Instant.now());
    }

    static Instant parsearFecha(String texto, Instant ahora) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("La fecha es obligatoria.");
        }
        String limpio = texto.trim();
        Instant instante = intentarParsearInstant(limpio);
        if (instante == null) {
            throw new IllegalArgumentException("Fecha inválida: '" + limpio + "'.");
        }
        if (instante.isAfter(ahora.plus(Duration.ofDays(1)))) {
            throw new IllegalArgumentException("La fecha no puede estar más de 1 día en el futuro.");
        }
        return instante;
    }

    public static String validarParametro(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El parámetro es obligatorio.");
        }
        if (limpio.length() > 60) {
            throw new IllegalArgumentException("El parámetro no puede superar 60 caracteres.");
        }
        return limpio;
    }

    public static String validarUnidad(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.length() > 20) {
            throw new IllegalArgumentException("La unidad no puede superar 20 caracteres.");
        }
        return limpio;
    }

    public static String validarNota(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.isEmpty()) {
            return null;
        }
        if (limpio.length() > 500) {
            throw new IllegalArgumentException("La nota no puede superar 500 caracteres.");
        }
        return limpio;
    }

    private static Instant intentarParsearInstant(String limpio) {
        try {
            return Instant.parse(limpio);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(limpio).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return ZonedDateTime.parse(limpio).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(limpio.replace(' ', 'T'))
                    .atZone(ZONA_PANAMA)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(limpio)
                    .atStartOfDay(ZONA_PANAMA)
                    .toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private static List<LineaCruda> dividirLineasNoVacias(String texto) {
        String[] crudas = texto.split("\r\n|\n|\r", -1);
        List<LineaCruda> resultado = new ArrayList<>();
        for (int i = 0; i < crudas.length; i++) {
            if (!crudas[i].isBlank()) {
                resultado.add(new LineaCruda(i + 1, crudas[i]));
            }
        }
        return resultado;
    }

    private static char detectarSeparador(String lineaCabecera) {
        if (lineaCabecera.indexOf(';') >= 0 && lineaCabecera.indexOf(',') < 0) {
            return ';';
        }
        return ',';
    }

    private static List<String> parsearLineaCsv(String linea, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean enComillas = false;
        boolean tuvoComillas = false;

        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (enComillas) {
                if (c == '"') {
                    if (i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                        actual.append('"');
                        i++;
                    } else {
                        enComillas = false;
                    }
                } else {
                    actual.append(c);
                }
            } else if (!tuvoComillas && c == '"' && actual.toString().isBlank()) {
                actual.setLength(0);
                enComillas = true;
                tuvoComillas = true;
            } else if (c == separador) {
                campos.add(actual.toString());
                actual.setLength(0);
                tuvoComillas = false;
            } else {
                actual.append(c);
            }
        }
        if (enComillas) {
            throw new IllegalArgumentException("Comillas dobles sin cerrar.");
        }
        campos.add(actual.toString());
        return campos;
    }

    private static FilaMuestra parsearFila(String linea, char separador, Instant ahora) {
        List<String> campos = parsearLineaCsv(linea, separador);
        if (campos.size() != 5) {
            throw new IllegalArgumentException(
                    "Se esperaban 5 columnas (fecha, parametro, valor, unidad, nota) y se encontraron "
                            + campos.size() + ".");
        }
        Instant fecha = parsearFecha(campos.get(0), ahora);
        String parametro = validarParametro(campos.get(1));
        double valor = parsearValor(campos.get(2), separador);
        String unidad = validarUnidad(campos.get(3));
        String nota = validarNota(campos.get(4));
        return new FilaMuestra(fecha, parametro, valor, unidad, nota);
    }

    private static double parsearValor(String texto, char separador) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El valor es obligatorio.");
        }
        if (separador == ';') {
            limpio = limpio.replace(',', '.');
        }
        try {
            double valor = Double.parseDouble(limpio);
            if (!Double.isFinite(valor)) {
                throw new IllegalArgumentException("El valor debe ser un número finito.");
            }
            return valor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido: '" + texto.trim() + "'.");
        }
    }

    private static String formatearErrores(List<String> errores) {
        StringBuilder sb = new StringBuilder();
        int limite = Math.min(MAX_ERRORES_EN_MENSAJE, errores.size());
        for (int i = 0; i < limite; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(errores.get(i));
        }
        if (errores.size() > MAX_ERRORES_EN_MENSAJE) {
            sb.append("\n... y ")
                    .append(errores.size() - MAX_ERRORES_EN_MENSAJE)
                    .append(" errores más.");
        }
        return sb.toString();
    }
}
