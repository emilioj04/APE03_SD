package ec.unl.distribuidos.productor;

import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/productor")
@CrossOrigin
public class ProductorController {

    private final AlmacenClient almacen;
    private final ExecutorService pool = Executors.newCachedThreadPool();   // Thread Pool
    private final AtomicInteger enviadas = new AtomicInteger();
    private final AtomicInteger esperandoEspacio = new AtomicInteger();

    public ProductorController(AlmacenClient almacen) {
        this.almacen = almacen;
    }

    @PostMapping("/iniciar")
    public Map<String, Object> iniciar(@RequestParam(defaultValue = "2") int productores,
                                       @RequestParam(defaultValue = "5") int piezas) {
        for (int p = 1; p <= productores; p++) {
            final int id = p;
            pool.submit(() -> producir(id, piezas));
        }
        return Map.of("estado", "iniciado", "productores", productores, "piezasCadaUno", piezas);
    }

    private void producir(int id, int piezas) {
        for (int i = 1; i <= piezas; i++) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(200, 600));   // fabricar_pieza()
                esperandoEspacio.incrementAndGet();
                try {
                    almacen.producir(id, "P" + id + "-" + i);
                } finally {
                    esperandoEspacio.decrementAndGet();
                }
                enviadas.incrementAndGet();
            } catch (Exception e) {
                System.err.println("Productor " + id + " error: " + e.getMessage());
                return;
            }
        }
    }

    @GetMapping("/estado")
    public Map<String, Object> estado() {
        return Map.of("enviadas", enviadas.get(), "bloqueadosEsperandoEspacio", esperandoEspacio.get());
    }

    @PreDestroy
    void cerrar() {
        pool.shutdownNow();
    }
}
