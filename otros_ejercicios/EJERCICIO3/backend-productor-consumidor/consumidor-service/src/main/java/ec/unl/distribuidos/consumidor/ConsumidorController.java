package ec.unl.distribuidos.consumidor;

import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/consumidor")
@CrossOrigin
public class ConsumidorController {

    private final AlmacenClient almacen;
    private final ExecutorService pool = Executors.newCachedThreadPool();   // Thread Pool
    private final List<String> ensambladas = new CopyOnWriteArrayList<>();
    private final AtomicInteger esperandoPieza = new AtomicInteger();

    public ConsumidorController(AlmacenClient almacen) {
        this.almacen = almacen;
    }

    @PostMapping("/iniciar")
    public Map<String, Object> iniciar(@RequestParam(defaultValue = "2") int consumidores,
                                       @RequestParam(defaultValue = "5") int piezas) {
        for (int c = 1; c <= consumidores; c++) {
            final int id = c;
            pool.submit(() -> consumir(id, piezas));
        }
        return Map.of("estado", "iniciado", "consumidores", consumidores, "piezasCadaUno", piezas);
    }

    private void consumir(int id, int piezas) {
        for (int i = 1; i <= piezas; i++) {
            try {
                String pieza;
                esperandoPieza.incrementAndGet();
                try {
                    pieza = almacen.consumir(id);
                } finally {
                    esperandoPieza.decrementAndGet();
                }
                Thread.sleep(ThreadLocalRandom.current().nextInt(300, 800));   // ensamblar(pieza)
                ensambladas.add("Consumidor " + id + " ensambló " + pieza);
            } catch (Exception e) {
                System.err.println("Consumidor " + id + " error: " + e.getMessage());
                return;
            }
        }
    }

    @GetMapping("/estado")
    public Map<String, Object> estado() {
        return Map.of("ensambladas", ensambladas.size(),
                      "bloqueadosEsperandoPieza", esperandoPieza.get(),
                      "detalle", ensambladas);
    }

    @PreDestroy
    void cerrar() {
        pool.shutdownNow();
    }
}
