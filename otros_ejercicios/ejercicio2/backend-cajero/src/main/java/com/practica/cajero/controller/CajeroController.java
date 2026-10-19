package com.practica.cajero.controller;

import com.practica.cajero.service.CuentaBancariaService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/cajero")
@CrossOrigin(origins = "*") // Importante para permitir que el frontend se comunique con el backend
public class CajeroController {

    private final CuentaBancariaService cajeroService;

    public CajeroController(CuentaBancariaService cajeroService) {
        this.cajeroService = cajeroService;
    }

    @GetMapping("/simular")
    public Map<String, Object> ejecutarSimulacion() {
        return cajeroService.ejecutarSimulacion();
    }
}