package com.alerta.sistema_alerta_inundaciones.alerta;

import java.util.regex.Pattern;

/**
 * Utilidad de validación de formato para nombres y canales de contacto
 * (correo electrónico o celular de Panamá) en suscripciones de alerta.
 */
public class ValidadorContacto {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Nombre: al menos una letra, permite espacios, acentos y guiones. No permite sólo números o caracteres raros.
    private static final Pattern NOMBRE_PATTERN = Pattern.compile("^(?=.*[a-zA-ZáéíóúÁÉÍÓÚñÑ])[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s\\-']{2,60}$");

    /**
     * Verifica si el nombre del contacto cumple el formato permitido.
     *
     * @param nombre nombre a validar
     * @return {@code true} si tiene entre 2 y 60 caracteres válidos y al menos una letra
     */
    public static boolean esNombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) return false;
        return NOMBRE_PATTERN.matcher(nombre.trim()).matches();
    }

    /**
     * Verifica si una cadena tiene formato válido de correo electrónico.
     *
     * @param email dirección de correo a validar
     * @return {@code true} si coincide con el patrón de correo electrónico
     */
    public static boolean esEmailValido(String email) {
        if (email == null || email.isBlank()) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Valida celular panameño: 8 dígitos empezando en 6.
     * Acepta formatos como: "6123-4567", "61234567", "+507 6123-4567", "+50761234567".
     *
     * @param tel número telefónico a validar
     * @return {@code true} si corresponde a un número móvil válido de Panamá
     */
    public static boolean esCelularPanamaValido(String tel) {
        if (tel == null || tel.isBlank()) return false;
        String limpio = tel.trim().replaceAll("[\\s\\-]", "");
        if (limpio.startsWith("+507")) {
            limpio = limpio.substring(4);
        } else if (limpio.startsWith("507") && limpio.length() == 11) {
            limpio = limpio.substring(3);
        }
        return limpio.matches("^6[0-9]{7}$");
    }

    /**
     * Verifica si el contacto es un correo electrónico o un celular panameño válido.
     *
     * @param contacto dato de contacto a validar
     * @return {@code true} si cumple alguno de los dos formatos aceptados
     */
    public static boolean esContactoValido(String contacto) {
        return esEmailValido(contacto) || esCelularPanamaValido(contacto);
    }
}
