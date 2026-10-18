package com.sistemas.distribuidos.puente.controller;

import com.sistemas.distribuidos.puente.dto.ConfiguracionRequest;
import com.sistemas.distribuidos.puente.dto.EstadoPuenteDto;
import com.sistemas.distribuidos.puente.dto.SimulacionRequest;
import com.sistemas.distribuidos.puente.dto.VehiculoRequest;
import com.sistemas.distribuidos.puente.model.Vehiculo;
import com.sistemas.distribuidos.puente.service.PuenteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST que expone los servicios de sincronización del puente.
 */
@RestController
@RequestMapping("/api/puente")
@CrossOrigin(origins = "*")
public class PuenteController {

    private final PuenteService puenteService;

    public PuenteController(PuenteService puenteService) {
        this.puenteService = puenteService;
    }

    /**
     * Consulta el estado en tiempo real del puente.
     */
    @GetMapping("/estado")
    public ResponseEntity<EstadoPuenteDto> obtenerEstado() {
        return ResponseEntity.ok(puenteService.obtenerEstado());
    }

    /**
     * Inicia una simulación masiva concurrente (ej. 5 Norte y 5 Sur).
     */
    @PostMapping("/simular")
    public ResponseEntity<Map<String, Object>> simular(@RequestBody(required = false) SimulacionRequest request) {
        if (request == null) {
            request = new SimulacionRequest();
        }
        puenteService.iniciarSimulacion(request);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Simulación concurrente iniciada con éxito",
                "cantidadNorte", request.getCantidadNorte(),
                "cantidadSur", request.getCantidadSur()
        ));
    }

    /**
     * Encola un vehículo individual en una dirección específica.
     */
    @PostMapping("/vehiculo")
    public ResponseEntity<Vehiculo> encolarVehiculo(@RequestBody VehiculoRequest request) {
        Vehiculo vehiculo = puenteService.encolarVehiculo(request);
        return ResponseEntity.ok(vehiculo);
    }

    /**
     * Reinicia el estado del puente.
     */
    @PostMapping("/reiniciar")
    public ResponseEntity<Map<String, String>> reiniciar() {
        puenteService.reiniciar();
        return ResponseEntity.ok(Map.of("mensaje", "Simulación y puente reiniciados"));
    }

    /**
     * Actualiza la política de equidad o anti-monopolio (inanición).
     */
    @PostMapping("/configurar")
    public ResponseEntity<Map<String, String>> configurar(@RequestBody ConfiguracionRequest request) {
        puenteService.configurar(request);
        return ResponseEntity.ok(Map.of("mensaje", "Configuración actualizada"));
    }

    /**
     * Endpoint de salud del servicio.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "servicio", "puente-backend",
                "practica", "APE 03 - Ejercicio 4"
        ));
    }
}
