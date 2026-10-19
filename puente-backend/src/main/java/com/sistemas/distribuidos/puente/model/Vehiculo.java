package com.sistemas.distribuidos.puente.model;

import java.time.LocalDateTime;

public class Vehiculo {
    private String id;
    private Direccion direccion;
    private EstadoVehiculo estado;
    private long tiempoEsperaMs;
    private long tiempoCruceMs;
    private LocalDateTime horaLlegada;
    private LocalDateTime horaEntrada;
    private LocalDateTime horaSalida;

    public Vehiculo() {
    }

    public Vehiculo(String id, Direccion direccion) {
        this.id = id;
        this.direccion = direccion;
        this.estado = EstadoVehiculo.ESPERANDO;
        this.horaLlegada = LocalDateTime.now();
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

    public EstadoVehiculo getEstado() {
        return estado;
    }

    public void setEstado(EstadoVehiculo estado) {
        this.estado = estado;
    }

    public long getTiempoEsperaMs() {
        return tiempoEsperaMs;
    }

    public void setTiempoEsperaMs(long tiempoEsperaMs) {
        this.tiempoEsperaMs = tiempoEsperaMs;
    }

    public long getTiempoCruceMs() {
        return tiempoCruceMs;
    }

    public void setTiempoCruceMs(long tiempoCruceMs) {
        this.tiempoCruceMs = tiempoCruceMs;
    }

    public LocalDateTime getHoraLlegada() {
        return horaLlegada;
    }

    public void setHoraLlegada(LocalDateTime horaLlegada) {
        this.horaLlegada = horaLlegada;
    }

    public LocalDateTime getHoraEntrada() {
        return horaEntrada;
    }

    public void setHoraEntrada(LocalDateTime horaEntrada) {
        this.horaEntrada = horaEntrada;
    }

    public LocalDateTime getHoraSalida() {
        return horaSalida;
    }

    public void setHoraSalida(LocalDateTime horaSalida) {
        this.horaSalida = horaSalida;
    }
}
