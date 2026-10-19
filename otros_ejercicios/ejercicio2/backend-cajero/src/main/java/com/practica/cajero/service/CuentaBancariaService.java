package com.practica.cajero.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

@Service
public class CuentaBancariaService {
    
    private double saldo;
    // Nuestro Mutex para proteger la variable compartida 'saldo'
    private final ReentrantLock mutexSaldo = new ReentrantLock();
    // CopyOnWriteArrayList es thread-safe para guardar los logs de diferentes hilos
    private List<String> logs;

    public void retirar(int id, double cantidad) {
        // Bloqueamos el acceso (Punto 4 del procedimiento)
        mutexSaldo.lock();
        try {
            logs.add("Proceso " + id + " intenta retirar $" + cantidad);
            if (saldo >= cantidad) {
                saldo -= cantidad;
                logs.add("Proceso " + id + ": Retiro realizado. Saldo actual: $" + saldo);
            } else {
                logs.add("Proceso " + id + ": Saldo insuficiente");
            }
        } finally {
            // Liberamos el mutex (Punto 6 del procedimiento)
            mutexSaldo.unlock();
        }
    }

    public void depositar(int id, double cantidad) {
        // Bloqueamos el acceso
        mutexSaldo.lock();
        try {
            logs.add("Proceso " + id + " deposita $" + cantidad);
            saldo += cantidad;
            logs.add("Proceso " + id + ": Depósito realizado. Saldo actual: $" + saldo);
        } finally {
            // Liberamos el mutex
            mutexSaldo.unlock();
        }
    }

    public Map<String, Object> ejecutarSimulacion() {
        saldo = 1000.0; // Saldo inicial (Punto 1)
        logs = new CopyOnWriteArrayList<>();
        logs.add("--- Iniciando Simulación. Saldo Inicial: $" + saldo + " ---");

        // Creamos un pool de 3 hilos para los 3 procesos
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // Creamos y ejecutamos los procesos (Punto 7)
        executor.submit(() -> retirar(1, 300));
        executor.submit(() -> retirar(2, 250));
        executor.submit(() -> depositar(3, 200));

        // Esperamos a que los hilos terminen
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logs.add("Error en la ejecución concurrente.");
        }

        logs.add("--- Simulación Finalizada. Saldo Final: $" + saldo + " ---");

        // Preparamos la respuesta para el frontend
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("logs", logs);
        resultado.put("saldoFinal", saldo);
        return resultado;
    }
}