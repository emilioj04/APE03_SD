package com.sistemas.distribuidos.puente.dto;

import com.sistemas.distribuidos.puente.model.Direccion;
import com.sistemas.distribuidos.puente.model.EventoLog;
import com.sistemas.distribuidos.puente.model.Vehiculo;

import java.util.List;

public class EstadoPuenteDto {
    private Direccion direccionActual;
    private int vehiculosNorteEnPuente;
    private int vehiculosSurEnPuente;
    private int totalEnPuente;
    private int vehiculosEsperandoNorte;
    private int vehiculosEsperandoSur;
    private int totalEsperando;
    private int totalCruzados;
    private boolean colisionDetectada;
    private boolean modoEquitativo;
    private int limiteConsecutivos;
    private int consecutivosCruzados;
    private boolean puenteLibre;
    private List<Vehiculo> enPuente;
    private List<Vehiculo> colaNorte;
    private List<Vehiculo> colaSur;
    private List<EventoLog> ultimosEventos;

    public EstadoPuenteDto() {
    }

    public Direccion getDireccionActual() {
        return direccionActual;
    }

    public void setDireccionActual(Direccion direccionActual) {
        this.direccionActual = direccionActual;
    }

    public int getVehiculosNorteEnPuente() {
        return vehiculosNorteEnPuente;
    }

    public void setVehiculosNorteEnPuente(int vehiculosNorteEnPuente) {
        this.vehiculosNorteEnPuente = vehiculosNorteEnPuente;
    }

    public int getVehiculosSurEnPuente() {
        return vehiculosSurEnPuente;
    }

    public void setVehiculosSurEnPuente(int vehiculosSurEnPuente) {
        this.vehiculosSurEnPuente = vehiculosSurEnPuente;
    }

    public int getTotalEnPuente() {
        return totalEnPuente;
    }

    public void setTotalEnPuente(int totalEnPuente) {
        this.totalEnPuente = totalEnPuente;
    }

    public int getVehiculosEsperandoNorte() {
        return vehiculosEsperandoNorte;
    }

    public void setVehiculosEsperandoNorte(int vehiculosEsperandoNorte) {
        this.vehiculosEsperandoNorte = vehiculosEsperandoNorte;
    }

    public int getVehiculosEsperandoSur() {
        return vehiculosEsperandoSur;
    }

    public void setVehiculosEsperandoSur(int vehiculosEsperandoSur) {
        this.vehiculosEsperandoSur = vehiculosEsperandoSur;
    }

    public int getTotalEsperando() {
        return totalEsperando;
    }

    public void setTotalEsperando(int totalEsperando) {
        this.totalEsperando = totalEsperando;
    }

    public int getTotalCruzados() {
        return totalCruzados;
    }

    public void setTotalCruzados(int totalCruzados) {
        this.totalCruzados = totalCruzados;
    }

    public boolean isColisionDetectada() {
        return colisionDetectada;
    }

    public void setColisionDetectada(boolean colisionDetectada) {
        this.colisionDetectada = colisionDetectada;
    }

    public boolean isModoEquitativo() {
        return modoEquitativo;
    }

    public void setModoEquitativo(boolean modoEquitativo) {
        this.modoEquitativo = modoEquitativo;
    }

    public int getLimiteConsecutivos() {
        return limiteConsecutivos;
    }

    public void setLimiteConsecutivos(int limiteConsecutivos) {
        this.limiteConsecutivos = limiteConsecutivos;
    }

    public int getConsecutivosCruzados() {
        return consecutivosCruzados;
    }

    public void setConsecutivosCruzados(int consecutivosCruzados) {
        this.consecutivosCruzados = consecutivosCruzados;
    }

    public boolean isPuenteLibre() {
        return puenteLibre;
    }

    public void setPuenteLibre(boolean puenteLibre) {
        this.puenteLibre = puenteLibre;
    }

    public List<Vehiculo> getEnPuente() {
        return enPuente;
    }

    public void setEnPuente(List<Vehiculo> enPuente) {
        this.enPuente = enPuente;
    }

    public List<Vehiculo> getColaNorte() {
        return colaNorte;
    }

    public void setColaNorte(List<Vehiculo> colaNorte) {
        this.colaNorte = colaNorte;
    }

    public List<Vehiculo> getColaSur() {
        return colaSur;
    }

    public void setColaSur(List<Vehiculo> colaSur) {
        this.colaSur = colaSur;
    }

    public List<EventoLog> getUltimosEventos() {
        return ultimosEventos;
    }

    public void setUltimosEventos(List<EventoLog> ultimosEventos) {
        this.ultimosEventos = ultimosEventos;
    }
}
