package com.sistemas.distribuidos.puente.model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class EventoLog {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private String timestamp;
    private String tipo;
    private String vehiculoId;
    private Direccion direccion;
    private String mensaje;
    private String hilo;

    public EventoLog() {
    }

    public EventoLog(String tipo, String vehiculoId, Direccion direccion, String mensaje) {
        this.timestamp = LocalTime.now().format(FORMATTER);
        this.tipo = tipo;
        this.vehiculoId = vehiculoId;
        this.direccion = direccion;
        this.mensaje = mensaje;
        this.hilo = Thread.currentThread().getName();
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getVehiculoId() {
        return vehiculoId;
    }

    public void setVehiculoId(String vehiculoId) {
        this.vehiculoId = vehiculoId;
    }

    public Direccion getDireccion() {
        return direccion;
    }

    public void setDireccion(Direccion direccion) {
        this.direccion = direccion;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getHilo() {
        return hilo;
    }

    public void setHilo(String hilo) {
        this.hilo = hilo;
    }
}
