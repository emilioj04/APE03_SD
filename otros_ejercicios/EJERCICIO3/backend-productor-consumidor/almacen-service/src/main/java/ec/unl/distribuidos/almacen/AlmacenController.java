package ec.unl.distribuidos.almacen;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/almacen")
@CrossOrigin
public class AlmacenController {

    private final BufferAcotado buffer;

    public AlmacenController(BufferAcotado buffer) {
        this.buffer = buffer;
    }

    @PostMapping("/producir")
    public Map<String, Object> producir(@RequestParam String productor,
                                        @RequestParam String pieza) throws InterruptedException {
        buffer.insertar(productor, pieza);
        return buffer.estado();
    }

    @GetMapping("/consumir")
    public Map<String, Object> consumir(@RequestParam String consumidor) throws InterruptedException {
        return Map.of("pieza", buffer.extraer(consumidor));
    }

    @GetMapping("/estado")
    public Map<String, Object> estado() {
        return buffer.estado();
    }
}
