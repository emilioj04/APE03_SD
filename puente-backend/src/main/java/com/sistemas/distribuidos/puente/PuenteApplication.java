package com.sistemas.distribuidos.puente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicación principal Spring Boot para el Ejercicio 4 del APE 03.
 * Cruce de vehículos — Control de acceso en una dirección (Exclusión Mutua).
 */
@SpringBootApplication
public class PuenteApplication {

    public static void main(String[] args) {
        SpringApplication.run(PuenteApplication.class, args);
    }
}
