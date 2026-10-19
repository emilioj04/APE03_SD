package com.sistemas.distribuidos.estacionamiento.service;

import com.sistemas.distribuidos.estacionamiento.dto.PaqueteVehiculoDto;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

@Service
public class EstacionamientoService {

    // Capacidad máxima 4 carros
    private static final int CAPACIDAD_MAXIMA = 4;
    // Semáforo contador cFIFO
    private final Semaphore espacios = new Semaphore(CAPACIDAD_MAXIMA, true);

    public PaqueteVehiculoDto procesarVehiculo(int vehiculoId, long tiempoEstacionadoMs) {
        try {
            System.out.println("Vehículo " + vehiculoId + " llega al estacionamiento.");
            
            //  contador (wait)
            espacios.acquire(); 

            System.out.println("Vehículo " + vehiculoId + " ingresa al estacionamiento.");
            
            //  permanencia del vehículo
            Thread.sleep(tiempoEstacionadoMs);

            System.out.println("Vehículo " + vehiculoId + " sale del estacionamiento.");
            
            return new PaqueteVehiculoDto(
                vehiculoId, 
                "PROCESADO_EXITO", 
                espacios.availablePermits(), 
                "El vehículo " + vehiculoId + " utilizó el espacio respetando la concurrencia solidaria."
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PaqueteVehiculoDto(vehiculoId, "ERROR", espacios.availablePermits(), "Hilo interrumpido.");
        } finally {
            // Libera el espacio del vehiculo
            espacios.release();
        }
    }

    public int getEspaciosDisponibles() {
        return espacios.availablePermits();
    }
}