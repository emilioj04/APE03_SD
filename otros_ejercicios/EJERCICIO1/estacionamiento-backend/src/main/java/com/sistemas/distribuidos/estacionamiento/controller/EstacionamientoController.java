package com.sistemas.distribuidos.estacionamiento.controller;

import com.sistemas.distribuidos.estacionamiento.dto.PaqueteVehiculoDto;
import com.sistemas.distribuidos.estacionamiento.service.EstacionamientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

@RestController
@RequestMapping("/api/estacionamiento")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST})
public class EstacionamientoController {

    @Autowired
    private EstacionamientoService estacionamientoService;

    @PostMapping("/ingresar/{id}")
    public PaqueteVehiculoDto ingresarVehiculo(@PathVariable int id, @RequestParam(defaultValue = "3000") long tiempoMs) {
        return estacionamientoService.procesarVehiculo(id, tiempoMs);
    }

    @GetMapping("/estado")
    public Map<String, Object> obtenerEstado() {
        return Map.of(
            "espaciosDisponibles", estacionamientoService.getEspaciosDisponibles(),
            "capacidadTotal", 4
        );
    }

    
}