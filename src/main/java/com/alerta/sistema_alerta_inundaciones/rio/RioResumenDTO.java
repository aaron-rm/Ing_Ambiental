package com.alerta.sistema_alerta_inundaciones.rio;

public record RioResumenDTO(
        Long rioId,
        String nombre,
        Long reachId,
        Double latitud,
        Double longitud,
        boolean tieneGeometria
) {

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
