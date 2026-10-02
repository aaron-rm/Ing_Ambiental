package com.alerta.sistema_alerta_inundaciones.alerta;

import java.time.LocalDate;

public class PronosticoDiaDTO {

    private int diaNumero;
    private LocalDate fecha;
    private double caudalMedio;
    private double caudalMaximo;
    private String estado;

    public PronosticoDiaDTO() {}

    public PronosticoDiaDTO(int diaNumero, LocalDate fecha, double caudalMedio, double caudalMaximo, String estado) {
        this.diaNumero = diaNumero;
        this.fecha = fecha;
        this.caudalMedio = caudalMedio;
        this.caudalMaximo = caudalMaximo;
        this.estado = estado;
    }

    public int getDiaNumero() {
        return diaNumero;
    }

    public void setDiaNumero(int diaNumero) {
        this.diaNumero = diaNumero;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public double getCaudalMedio() {
        return caudalMedio;
    }

    public void setCaudalMedio(double caudalMedio) {
        this.caudalMedio = caudalMedio;
    }

    public double getCaudalMaximo() {
        return caudalMaximo;
    }

    public void setCaudalMaximo(double caudalMaximo) {
        this.caudalMaximo = caudalMaximo;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
