package com.sistemas.distribuidos.puente.dto;

import com.sistemas.distribuidos.puente.model.Direccion;

public class VehiculoRequest {
    private String id;
    private Direccion direccion;
    private long tiempoCruceMs = 2000;

    public VehiculoRequest() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Direccion getDireccion() {
        return direccion;
    }

    public void setDireccion(Direccion direccion) {
        this.direccion = direccion;
    }

    public long getTiempoCruceMs() {
        return tiempoCruceMs;
    }

    public void setTiempoCruceMs(long tiempoCruceMs) {
        this.tiempoCruceMs = tiempoCruceMs;
    }
}
