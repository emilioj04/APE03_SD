package com.sistemas.distribuidos.puente.dto;

/**
 * Petición para actualizar la configuración de sincronización (política anti-inanición).
 */
public class ConfiguracionRequest {
    private Boolean modoEquitativo;
    private Integer limiteConsecutivos;

    public ConfiguracionRequest() {
    }

    public Boolean getModoEquitativo() {
        return modoEquitativo;
    }

    public void setModoEquitativo(Boolean modoEquitativo) {
        this.modoEquitativo = modoEquitativo;
    }

    public Integer getLimiteConsecutivos() {
        return limiteConsecutivos;
    }

    public void setLimiteConsecutivos(Integer limiteConsecutivos) {
        this.limiteConsecutivos = limiteConsecutivos;
    }
}
