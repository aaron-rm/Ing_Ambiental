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
}
