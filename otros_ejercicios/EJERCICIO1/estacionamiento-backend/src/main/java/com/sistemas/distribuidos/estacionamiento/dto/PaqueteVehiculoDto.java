package com.sistemas.distribuidos.estacionamiento.dto;

import java.time.LocalDateTime;

public class PaqueteVehiculoDto {
    private int vehiculoId;
    private String estado;
    private int espaciosDisponibles;
    private String mensajeSolidario;
    private String timestamp;

    public PaqueteVehiculoDto(int vehiculoId, String estado, int espaciosDisponibles, String mensajeSolidario) {
        this.vehiculoId = vehiculoId;
        this.estado = estado;
        this.espaciosDisponibles = espaciosDisponibles;
        this.mensajeSolidario = mensajeSolidario;
        this.timestamp = LocalDateTime.now().toString();
    }

    public int getVehiculoId() { return vehiculoId; }
    public String getEstado() { return estado; }
    public int getEspaciosDisponibles() { return espaciosDisponibles; }
    public String getMensajeSolidario() { return mensajeSolidario; }
    public String getTimestamp() { return timestamp; }
}