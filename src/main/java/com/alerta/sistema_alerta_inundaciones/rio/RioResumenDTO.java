package com.alerta.sistema_alerta_inundaciones.rio;

/**
 * DTO con la información resumida de un río del catálogo para listados y paneles.
 *
 * @param rioId          identificador interno del río
 * @param nombre         nombre visible del río
 * @param reachId        identificador del tramo en GEOGloWS
 * @param latitud        latitud de referencia
 * @param longitud       longitud de referencia
 * @param tieneGeometria indica si el río cuenta con geometría GeoJSON almacenada
 */
public record RioResumenDTO(
        Long rioId,
        String nombre,
        Long reachId,
        Double latitud,
        Double longitud,
        boolean tieneGeometria
) {

    /**
     * Construye un {@link RioResumenDTO} a partir de una entidad {@link Rio}.
     *
     * @param rio entidad de río
     * @return DTO de resumen del río
     */
    public static RioResumenDTO desde(Rio rio) {
        return new RioResumenDTO(
                rio.getId(),
                rio.getNombre(),
                rio.getGeoglowsId(),
                rio.getLatitud(),
                rio.getLongitud(),
                rio.getGeometria() != null && !rio.getGeometria().isBlank()
        );
    }
}
