package com.sistemas.distribuidos.puente.model;

/**
 * Estados por los que transita un hilo de vehículo durante la simulación.
 */
public enum EstadoVehiculo {
    ESPERANDO,   // En cola de espera por exclusión de sentido contrario
    CRUZANDO,    // En el puente
    COMPLETADO   // Ya cruzó el puente exitosamente
}
