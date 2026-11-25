package com.alerta.sistema_alerta_inundaciones.usuario;

import com.alerta.sistema_alerta_inundaciones.rio.Rio;

import java.time.Instant;

public record RioSeguidoDTO(
        Long rioId,
        String nombre,
        Long reachId,
        Double latitud,
        Double longitud,
        Instant fechaSeleccion
) {

    public static RioSeguidoDTO desde(UsuarioRio seleccion) {
        Rio rio = seleccion.getRio();
        return new RioSeguidoDTO(
                rio.getId(),
                rio.getNombre(),
                rio.getGeoglowsId(),
                rio.getLatitud(),
                rio.getLongitud(),
                seleccion.getFechaSeleccion()
        );
    }
}
