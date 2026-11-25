package com.alerta.sistema_alerta_inundaciones.usuario;

import com.alerta.sistema_alerta_inundaciones.rio.Rio;

import java.time.Instant;

/**
 * DTO que representa un río seguido por un usuario para recibir alertas,
 * incluyendo la fecha en que fue seleccionado.
 *
 * @param rioId          identificador del río
 * @param nombre         nombre del río
 * @param reachId        identificador del tramo en GEOGloWS
 * @param latitud        latitud del río
 * @param longitud       longitud del río
 * @param fechaSeleccion instante en que el usuario comenzó a seguir el río
 */
public record RioSeguidoDTO(
        Long rioId,
        String nombre,
        Long reachId,
        Double latitud,
        Double longitud,
        Instant fechaSeleccion
) {

    /**
     * Construye un {@link RioSeguidoDTO} a partir de una relación {@link UsuarioRio}.
     *
     * @param seleccion entidad de relación entre usuario y río
     * @return DTO con los datos del río seguido
     */
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
