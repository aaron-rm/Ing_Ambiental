package com.alerta.sistema_alerta_inundaciones.usuario;

import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioRioServiceTest {

    @Mock
    private UsuarioRioRepository seleccionRepository;

    @Mock
    private RioRepository rioRepository;

    private UsuarioRioService service;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        service = new UsuarioRioService(seleccionRepository, rioRepository);
        usuario = new Usuario("juan", "hash", Rol.USER);
    }

    @Test
    void seguirGuardaLaSeleccionDelUsuario() {
        Rio rio = new Rio("Río Abajo", 710039498L, 9.0, -79.48);
        when(rioRepository.findById(4L)).thenReturn(Optional.of(rio));
        when(seleccionRepository.existsByUsuarioIdAndRioId(null, 4L)).thenReturn(false);
        when(seleccionRepository.save(any(UsuarioRio.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        SeleccionRioResponse respuesta = service.seguir(usuario, 4L);

        assertTrue(respuesta.seguido());
        assertEquals(4L, respuesta.rioId());
        assertTrue(respuesta.mensaje().contains("agregado"));
        verify(seleccionRepository).save(any(UsuarioRio.class));
    }

    @Test
    void seguirEsIdempotenteYNoDuplicaLaSeleccion() {
        Rio rio = new Rio("Río Abajo", 710039498L, 9.0, -79.48);
        when(rioRepository.findById(4L)).thenReturn(Optional.of(rio));
        when(seleccionRepository.existsByUsuarioIdAndRioId(null, 4L)).thenReturn(true);

        SeleccionRioResponse respuesta = service.seguir(usuario, 4L);

        assertTrue(respuesta.seguido());
        assertTrue(respuesta.mensaje().contains("ya estaba"));
        verify(seleccionRepository, never()).save(any(UsuarioRio.class));
    }

    @Test
    void seguirUnRioInexistenteLanzaErrorClaro() {
        when(rioRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.seguir(usuario, 99L));

        assertTrue(error.getMessage().contains("99"));
    }

    @Test
    void dejarDeSeguirBorraLaSeleccionAunqueNoExistiera() {
        service.dejarDeSeguir(usuario, 4L);

        verify(seleccionRepository).deleteByUsuarioIdAndRioId(null, 4L);
    }

    @Test
    void listarDevuelveLosRiosSeguidosConSuFechaDeSeleccion() {
        Rio rio = new Rio("Río Curundú", 710037516L, 8.98, -79.5);
        UsuarioRio seleccion = new UsuarioRio(usuario, rio);
        when(seleccionRepository.findByUsuarioId(null)).thenReturn(List.of(seleccion));

        List<RioSeguidoDTO> seguidos = service.listar(usuario);

        assertEquals(1, seguidos.size());
        assertEquals("Río Curundú", seguidos.get(0).nombre());
        assertEquals(710037516L, seguidos.get(0).reachId());
        assertTrue(seguidos.get(0).fechaSeleccion() == null
                || seguidos.get(0).fechaSeleccion() instanceof Instant);
    }
}
