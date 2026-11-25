package com.alerta.sistema_alerta_inundaciones.rio;

import com.alerta.sistema_alerta_inundaciones.usuario.Rol;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MuestraServiceTest {

    @Mock
    private MuestraRepository muestraRepository;

    @Mock
    private RioRepository rioRepository;

    private MuestraService service;
    private Usuario investigador;
    private Rio rio;

    @BeforeEach
    void preparar() {
        service = new MuestraService(muestraRepository, rioRepository);
        investigador = new Usuario("lab", "hash", Rol.INVESTIGADOR);
        rio = new Rio("Río Abajo", 710039498L, 9.0, -79.48);
    }

    @Test
    void cargarCsvTodoONadaNoInsertaNingunaSiUnaFilaFalla() {
        when(rioRepository.findById(1L)).thenReturn(Optional.of(rio));
        String csv = "fecha,parametro,valor,unidad,nota\n"
                + "2020-01-10,pH,7.1,u,fila valida\n"
                + "fecha_mala,Turbidez,10.0,NTU,fila invalida\n";

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.cargarCsv(1L, csv, investigador));

        assertEquals(400, error.getStatusCode().value());
        assertThat(error.getReason()).contains("Fila 3:");
        verify(muestraRepository, never()).save(any());
        verify(muestraRepository, never()).saveAll(anyList());
    }

    @Test
    void cargarCsvValidoInsertaTodasLasMuestras() {
        when(rioRepository.findById(1L)).thenReturn(Optional.of(rio));
        when(muestraRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        String csv = "fecha,parametro,valor,unidad,nota\n"
                + "2020-01-10,pH,7.1,u,ok\n"
                + "2020-01-11,Turbidez,12.5,NTU,\n";

        int insertadas = service.cargarCsv(1L, csv, investigador);

        assertEquals(2, insertadas);
        verify(muestraRepository).saveAll(anyList());
    }

    @Test
    void desdeOcultaDominioSiResponsableEsCorreo() {
        assertEquals("lab", MuestraDTO.desde(new Muestra(rio, Instant.EPOCH, "pH", 7.0, "u", null, "lab@utp.ac.pa")).responsable());
    }
}
