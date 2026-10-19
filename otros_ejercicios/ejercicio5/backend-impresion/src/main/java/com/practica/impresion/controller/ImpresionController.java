package com.practica.impresion.controller;

import com.practica.impresion.service.CentroImpresionService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/impresion")
@CrossOrigin(origins = "*")
public class ImpresionController {

    private final CentroImpresionService service;

    public ImpresionController(CentroImpresionService service) {
        this.service = service;
    }

    @GetMapping("/simular")
    public Map<String, Object> simular() {
        return service.ejecutarSimulacion();
    }
}