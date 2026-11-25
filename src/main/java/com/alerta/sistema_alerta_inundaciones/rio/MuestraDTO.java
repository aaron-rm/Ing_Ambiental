package com.alerta.sistema_alerta_inundaciones.rio;

public record MuestraDTO(
        Long id,
        String fecha,
        String parametro,
        double valor,
        String unidad,
        String nota,
        String responsable
) {

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

    private static String ocultarCorreo(String responsable) {
        if (responsable == null) {
            return null;
        }
        int arroba = responsable.indexOf('@');
        return arroba >= 0 ? responsable.substring(0, arroba) : responsable;
    }
}
