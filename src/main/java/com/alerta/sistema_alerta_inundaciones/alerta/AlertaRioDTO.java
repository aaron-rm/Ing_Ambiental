package com.alerta.sistema_alerta_inundaciones.alerta;

import java.time.Instant;
import java.util.List;

public class AlertaRioDTO {

    private Long reachId;
    private String nombreRio;
    private String nivelAlerta; // NORMAL, VIGILANCIA, ALERTA, EMERGENCIA
    private double caudalActualOInicial;
    private double caudalMaximoPronosticado;
    private double umbralVigilancia;
    private double umbralAlerta;
    private double umbralEmergencia;
    private String mensaje;
    private List<PuntoPronosticoDTO> pronostico;
    private List<PronosticoDiaDTO> pronosticoDiario;

    private boolean simulacion;
    private String estadoDatos; // COMPLETOS, PARCIALES, DESACTUALIZADOS, INSUFICIENTES
    private boolean datosConfiables;
    private Instant fechaEvaluacion;
    private Instant fechaEmisionCaudal;
    private Instant fechaActualizacionPrecipitacion;
    private String fuenteCaudal;
    private String fuentePrecipitacion;
    private String fuenteUmbrales;
    private String nivelRiesgoCaudal;
    private String nivelRiesgoLluvia;
    private Double lluvia24hMm;
    private Double lluvia72hMm;
    private Double maxLluviaHorariaMm;
    private List<String> factoresRiesgo;
    private List<String> advertencias;
    private List<String> recomendaciones;
    private Long alertaEventoId;
    private List<PuntoLluviaDTO> pronosticoLluvia;

    public static class PuntoLluviaDTO {
        private Instant fecha;
        private double mm;

        public PuntoLluviaDTO() {
        }

        public PuntoLluviaDTO(Instant fecha, double mm) {
            this.fecha = fecha;
            this.mm = mm;
        }

        public Instant getFecha() {
            return fecha;
        }

        public void setFecha(Instant fecha) {
            this.fecha = fecha;
        }

        public double getMm() {
            return mm;
        }

        public void setMm(double mm) {
            this.mm = mm;
        }
    }

    public static class PuntoPronosticoDTO {
        private Instant fecha;
        private double caudalMedio;

        public PuntoPronosticoDTO() {}

        public PuntoPronosticoDTO(Instant fecha, double caudalMedio) {
            this.fecha = fecha;
            this.caudalMedio = caudalMedio;
        }

        public Instant getFecha() {
            return fecha;
        }

        public void setFecha(Instant fecha) {
            this.fecha = fecha;
        }

        public double getCaudalMedio() {
            return caudalMedio;
        }

        public void setCaudalMedio(double caudalMedio) {
            this.caudalMedio = caudalMedio;
        }
    }

    public AlertaRioDTO() {}

    public Long getReachId() {
        return reachId;
    }

    public void setReachId(Long reachId) {
        this.reachId = reachId;
    }

    public String getNombreRio() {
        return nombreRio;
    }

    public void setNombreRio(String nombreRio) {
        this.nombreRio = nombreRio;
    }

    public String getNivelAlerta() {
        return nivelAlerta;
    }

    public void setNivelAlerta(String nivelAlerta) {
        this.nivelAlerta = nivelAlerta;
    }

    public double getCaudalActualOInicial() {
        return caudalActualOInicial;
    }

    public void setCaudalActualOInicial(double caudalActualOInicial) {
        this.caudalActualOInicial = caudalActualOInicial;
    }

    public double getCaudalMaximoPronosticado() {
        return caudalMaximoPronosticado;
    }

    public void setCaudalMaximoPronosticado(double caudalMaximoPronosticado) {
        this.caudalMaximoPronosticado = caudalMaximoPronosticado;
    }

    public double getUmbralVigilancia() {
        return umbralVigilancia;
    }

    public void setUmbralVigilancia(double umbralVigilancia) {
        this.umbralVigilancia = umbralVigilancia;
    }

    public double getUmbralAlerta() {
        return umbralAlerta;
    }

    public void setUmbralAlerta(double umbralAlerta) {
        this.umbralAlerta = umbralAlerta;
    }

    public double getUmbralEmergencia() {
        return umbralEmergencia;
    }

    public void setUmbralEmergencia(double umbralEmergencia) {
        this.umbralEmergencia = umbralEmergencia;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public List<PuntoPronosticoDTO> getPronostico() {
        return pronostico;
    }

    public void setPronostico(List<PuntoPronosticoDTO> pronostico) {
        this.pronostico = pronostico;
    }

    public List<PronosticoDiaDTO> getPronosticoDiario() {
        return pronosticoDiario;
    }

    public void setPronosticoDiario(List<PronosticoDiaDTO> pronosticoDiario) {
        this.pronosticoDiario = pronosticoDiario;
    }

    public boolean isSimulacion() {
        return simulacion;
    }

    public void setSimulacion(boolean simulacion) {
        this.simulacion = simulacion;
    }

    public String getEstadoDatos() {
        return estadoDatos;
    }

    public void setEstadoDatos(String estadoDatos) {
        this.estadoDatos = estadoDatos;
    }

    public boolean isDatosConfiables() {
        return datosConfiables;
    }

    public void setDatosConfiables(boolean datosConfiables) {
        this.datosConfiables = datosConfiables;
    }

    public Instant getFechaEvaluacion() {
        return fechaEvaluacion;
    }

    public void setFechaEvaluacion(Instant fechaEvaluacion) {
        this.fechaEvaluacion = fechaEvaluacion;
    }

    public Instant getFechaEmisionCaudal() {
        return fechaEmisionCaudal;
    }

    public void setFechaEmisionCaudal(Instant fechaEmisionCaudal) {
        this.fechaEmisionCaudal = fechaEmisionCaudal;
    }

    public Instant getFechaActualizacionPrecipitacion() {
        return fechaActualizacionPrecipitacion;
    }

    public void setFechaActualizacionPrecipitacion(Instant fechaActualizacionPrecipitacion) {
        this.fechaActualizacionPrecipitacion = fechaActualizacionPrecipitacion;
    }

    public String getFuenteCaudal() {
        return fuenteCaudal;
    }

    public void setFuenteCaudal(String fuenteCaudal) {
        this.fuenteCaudal = fuenteCaudal;
    }

    public String getFuentePrecipitacion() {
        return fuentePrecipitacion;
    }

    public void setFuentePrecipitacion(String fuentePrecipitacion) {
        this.fuentePrecipitacion = fuentePrecipitacion;
    }

    public String getFuenteUmbrales() {
        return fuenteUmbrales;
    }

    public void setFuenteUmbrales(String fuenteUmbrales) {
        this.fuenteUmbrales = fuenteUmbrales;
    }

    public String getNivelRiesgoCaudal() {
        return nivelRiesgoCaudal;
    }

    public void setNivelRiesgoCaudal(String nivelRiesgoCaudal) {
        this.nivelRiesgoCaudal = nivelRiesgoCaudal;
    }

    public String getNivelRiesgoLluvia() {
        return nivelRiesgoLluvia;
    }

    public void setNivelRiesgoLluvia(String nivelRiesgoLluvia) {
        this.nivelRiesgoLluvia = nivelRiesgoLluvia;
    }

    public Double getLluvia24hMm() {
        return lluvia24hMm;
    }

    public void setLluvia24hMm(Double lluvia24hMm) {
        this.lluvia24hMm = lluvia24hMm;
    }

    public Double getLluvia72hMm() {
        return lluvia72hMm;
    }

    public void setLluvia72hMm(Double lluvia72hMm) {
        this.lluvia72hMm = lluvia72hMm;
    }

    public Double getMaxLluviaHorariaMm() {
        return maxLluviaHorariaMm;
    }

    public void setMaxLluviaHorariaMm(Double maxLluviaHorariaMm) {
        this.maxLluviaHorariaMm = maxLluviaHorariaMm;
    }

    public List<String> getFactoresRiesgo() {
        return factoresRiesgo;
    }

    public void setFactoresRiesgo(List<String> factoresRiesgo) {
        this.factoresRiesgo = factoresRiesgo;
    }

    public List<String> getAdvertencias() {
        return advertencias;
    }

    public void setAdvertencias(List<String> advertencias) {
        this.advertencias = advertencias;
    }

    public List<String> getRecomendaciones() {
        return recomendaciones;
    }

    public void setRecomendaciones(List<String> recomendaciones) {
        this.recomendaciones = recomendaciones;
    }

    public Long getAlertaEventoId() {
        return alertaEventoId;
    }

    public void setAlertaEventoId(Long alertaEventoId) {
        this.alertaEventoId = alertaEventoId;
    }

    public List<PuntoLluviaDTO> getPronosticoLluvia() {
        return pronosticoLluvia;
    }

    public void setPronosticoLluvia(List<PuntoLluviaDTO> pronosticoLluvia) {
        this.pronosticoLluvia = pronosticoLluvia;
    }
}
