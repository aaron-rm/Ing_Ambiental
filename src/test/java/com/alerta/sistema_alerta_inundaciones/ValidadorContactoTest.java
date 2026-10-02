package com.alerta.sistema_alerta_inundaciones;

import com.alerta.sistema_alerta_inundaciones.alerta.ValidadorContacto;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ValidadorContactoTest {

    @Test
    void testNombres() {
        assertTrue(ValidadorContacto.esNombreValido("Jaziel"));
        assertTrue(ValidadorContacto.esNombreValido("María José Pérez"));
        assertTrue(ValidadorContacto.esNombreValido("Jean-Paul"));
        
        // Sólo números o símbolos
        assertFalse(ValidadorContacto.esNombreValido("12345678"));
        assertFalse(ValidadorContacto.esNombreValido(""));
        assertFalse(ValidadorContacto.esNombreValido("   "));
        assertFalse(ValidadorContacto.esNombreValido("!@#$%"));
    }

    @Test
    void testTelefonosPanama() {
        assertTrue(ValidadorContacto.esCelularPanamaValido("6123-4567"));
        assertTrue(ValidadorContacto.esCelularPanamaValido("61234567"));
        assertTrue(ValidadorContacto.esCelularPanamaValido("+507 6123-4567"));
        assertTrue(ValidadorContacto.esCelularPanamaValido("+50761234567"));

        // Inválidos
        assertFalse(ValidadorContacto.esCelularPanamaValido("12345678"));
        assertFalse(ValidadorContacto.esCelularPanamaValido("81234567"));
        assertFalse(ValidadorContacto.esCelularPanamaValido("612345"));
        assertFalse(ValidadorContacto.esCelularPanamaValido("612345678"));
        assertFalse(ValidadorContacto.esCelularPanamaValido("abc"));
    }

    @Test
    void testEmails() {
        assertTrue(ValidadorContacto.esEmailValido("docente@utp.ac.pa"));
        assertTrue(ValidadorContacto.esEmailValido("usuario@gmail.com"));

        assertFalse(ValidadorContacto.esEmailValido("jaziel@"));
        assertFalse(ValidadorContacto.esEmailValido("abc"));
        assertFalse(ValidadorContacto.esEmailValido("@"));
    }
}
