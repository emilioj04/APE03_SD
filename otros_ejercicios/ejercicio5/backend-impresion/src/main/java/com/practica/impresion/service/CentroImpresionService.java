package com.practica.impresion.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
public class CentroImpresionService {

    private Semaphore turnoDiseno;
    private Semaphore turnoRevision;
    private Semaphore turnoImpresion;
    private List<String> logs;

    private void diseno() {
        try {
            for (int i = 1; i <= 3; i++) {
                turnoDiseno.acquire();
                logs.add("Documento " + i + " -> [DISEÑO] prepara documento.");
                turnoRevision.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void revision() {
        try {
            for (int i = 1; i <= 3; i++) {
                turnoRevision.acquire();
                logs.add("Documento " + i + " -> [REVISIÓN] verifica documento.");
                turnoImpresion.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void impresion() {
        try {
            for (int i = 1; i <= 3; i++) {
                turnoImpresion.acquire();
                logs.add("Documento " + i + " -> [IMPRESIÓN] imprime documento.");
                logs.add("---------------------------------------------------");
                turnoDiseno.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Map<String, Object> ejecutarSimulacion() {
        logs = new CopyOnWriteArrayList<>();
        
        // 1 permiso para Diseño, 0 para los demás
        turnoDiseno = new Semaphore(1);
        turnoRevision = new Semaphore(0);
        turnoImpresion = new Semaphore(0);

        ExecutorService executor = Executors.newFixedThreadPool(3);

        executor.submit(this::impresion);
        executor.submit(this::revision);
        executor.submit(this::diseno);

        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logs.add("Error en la ejecución concurrente.");
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("logs", logs);
        return resultado;
    }
}