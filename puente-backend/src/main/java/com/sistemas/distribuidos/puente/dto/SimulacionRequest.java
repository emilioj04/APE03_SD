package com.sistemas.distribuidos.puente.dto;

public class SimulacionRequest {
    private int cantidadNorte = 5;
    private int cantidadSur = 5;
    private long tiempoCruceMinMs = 1500;
    private long tiempoCruceMaxMs = 2500;
    private long intervaloGeneracionMs = 300;

    public SimulacionRequest() {
    }

    public int getCantidadNorte() {
        return cantidadNorte;
    }

    public void setCantidadNorte(int cantidadNorte) {
        this.cantidadNorte = cantidadNorte;
    }

    public int getCantidadSur() {
        return cantidadSur;
    }

    public void setCantidadSur(int cantidadSur) {
        this.cantidadSur = cantidadSur;
    }

    public long getTiempoCruceMinMs() {
        return tiempoCruceMinMs;
    }

    public void setTiempoCruceMinMs(long tiempoCruceMinMs) {
        this.tiempoCruceMinMs = tiempoCruceMinMs;
    }

    public long getTiempoCruceMaxMs() {
        return tiempoCruceMaxMs;
    }

    public void setTiempoCruceMaxMs(long tiempoCruceMaxMs) {
        this.tiempoCruceMaxMs = tiempoCruceMaxMs;
    }

    public long getIntervaloGeneracionMs() {
        return intervaloGeneracionMs;
    }

    public void setIntervaloGeneracionMs(long intervaloGeneracionMs) {
        this.intervaloGeneracionMs = intervaloGeneracionMs;
    }
}
