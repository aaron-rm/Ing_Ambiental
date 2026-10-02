package com.alerta.sistema_alerta_inundaciones.alerta;

import java.util.regex.Pattern;

public class ValidadorContacto {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // Nombre: al menos una letra, permite espacios, acentos y guiones. No permite sólo números o caracteres raros.
    private static final Pattern NOMBRE_PATTERN = Pattern.compile("^(?=.*[a-zA-ZáéíóúÁÉÍÓÚñÑ])[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s\\-']{2,60}$");

    public static boolean esNombreValido(String nombre) {
        if (nombre == null || nombre.isBlank()) return false;
        return NOMBRE_PATTERN.matcher(nombre.trim()).matches();
    }

    public static boolean esEmailValido(String email) {
        if (email == null || email.isBlank()) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Valida celular panameño: 8 dígitos empezando en 6.
     * Acepta formatos como: "6123-4567", "61234567", "+507 6123-4567", "+50761234567".
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

    public static boolean esContactoValido(String contacto) {
        return esEmailValido(contacto) || esCelularPanamaValido(contacto);
    }
}
