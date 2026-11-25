package com.alerta.sistema_alerta_inundaciones.rio;

import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EliminacionRioServiceTest {

    @Mock
    private RioRepository rioRepository;

    @Mock
    private MuestraRepository muestraRepository;

    @Mock
    private UsuarioRioRepository usuarioRioRepository;

    private EliminacionRioService service;

    @BeforeEach
    void preparar() {
        service = new EliminacionRioService(rioRepository, muestraRepository, usuarioRioRepository);
    }

    @Test
    void eliminarIdExistenteBorraMuestrasSeguidoresYRio() {
        when(rioRepository.existsById(1L)).thenReturn(true);

        service.eliminar(1L);

        verify(muestraRepository).deleteByRioId(1L);
        verify(usuarioRioRepository).deleteByRioId(1L);
        verify(rioRepository).deleteById(1L);
    }

    @Test
    void eliminarIdInexistenteLanzaExcepcionYNoBorraNada() {
        when(rioRepository.existsById(99L)).thenReturn(false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.eliminar(99L));

        assertEquals("Río no encontrado", error.getMessage());
        verify(muestraRepository, never()).deleteByRioId(anyLong());
        verify(usuarioRioRepository, never()).deleteByRioId(anyLong());
        verify(rioRepository, never()).deleteById(anyLong());
    }
}
