package ec.unl.distribuidos.almacen;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/** Patrón Monitor / Bounded Buffer: el recurso compartido y su sincronización viven aquí. */
@Component
public class BufferAcotado {

    private final int capacidad;
    private final Semaphore espacios;                          // espacios libres
    private final Semaphore elementos = new Semaphore(0);      // piezas disponibles
    private final ReentrantLock mutex = new ReentrantLock();   // protege la cola

    private final Deque<String> cola = new ArrayDeque<>();
    private final List<String> eventos = new ArrayList<>();
    private int producidas, consumidas, maxOcupados;

    public BufferAcotado(@Value("${almacen.capacidad:5}") int capacidad) {
        this.capacidad = capacidad;
        this.espacios = new Semaphore(capacidad);
    }

    public void insertar(String productor, String pieza) throws InterruptedException {
        espacios.acquire();                 // wait(espacios)
        mutex.lock();
        try {
            cola.addLast(pieza);
            producidas++;
            maxOcupados = Math.max(maxOcupados, cola.size());
            registrar("Productor " + productor + " agregó " + pieza);
        } finally {
            mutex.unlock();
        }
        elementos.release();                // signal(elementos)
    }

    public String extraer(String consumidor) throws InterruptedException {
        elementos.acquire();                // wait(elementos)
        String pieza;
        mutex.lock();
        try {
            pieza = cola.removeFirst();
            consumidas++;
            registrar("Consumidor " + consumidor + " retiró " + pieza);
        } finally {
            mutex.unlock();
        }
        espacios.release();                 // signal(espacios)
        return pieza;
    }

    public Map<String, Object> estado() {
        mutex.lock();
        try {
            return Map.of(
                "capacidad", capacidad,
                "ocupados", cola.size(),
                "piezas", new ArrayList<>(cola),
                "producidas", producidas,
                "consumidas", consumidas,
                "maxOcupados", maxOcupados,
                "eventos", new ArrayList<>(eventos));
        } finally {
            mutex.unlock();
        }
    }

    private void registrar(String msg) {    // siempre con el mutex tomado
        eventos.add(LocalTime.now().withNano(0) + " " + msg);
        if (eventos.size() > 50) eventos.remove(0);
    }
}
