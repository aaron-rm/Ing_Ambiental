package com.alerta.sistema_alerta_inundaciones.rio;

/**
 * DTO de salida con los datos públicos de una muestra registrada en un río.
 *
 * @param id          identificador único de la muestra
 * @param fecha       fecha y hora de la muestra en formato ISO-8601
 * @param parametro   nombre del parámetro medido
 * @param valor       valor numérico de la medición
 * @param unidad      unidad de medida
 * @param nota        observación opcional
 * @param responsable nombre público del responsable (sin dominio de correo)
 */
public record MuestraDTO(
        Long id,
        String fecha,
        String parametro,
        double valor,
        String unidad,
        String nota,
        String responsable
) {

    /**
     * Convierte una entidad {@link Muestra} en su DTO público ocultando el dominio del correo del responsable.
     *
     * @param muestra entidad de muestra
     * @return representación {@link MuestraDTO}
     */
    public static MuestraDTO desde(Muestra muestra) {
        return new MuestraDTO(
                muestra.getId(),
                muestra.getFecha().toString(),
                muestra.getParametro(),
                muestra.getValor(),
                muestra.getUnidad(),
                muestra.getNota(),
                ocultarCorreo(muestra.getResponsable())
        );
    }

    // Recorta la parte del dominio si el identificador del responsable es un correo electrónico.
    private static String ocultarCorreo(String responsable) {
        if (responsable == null) {
            return null;
        }
        int arroba = responsable.indexOf('@');
        return arroba >= 0 ? responsable.substring(0, arroba) : responsable;
    }
}
