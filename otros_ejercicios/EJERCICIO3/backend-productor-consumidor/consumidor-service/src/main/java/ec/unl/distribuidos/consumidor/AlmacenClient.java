package ec.unl.distribuidos.consumidor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/** Patrón Gateway: oculta el acceso HTTP al microservicio almacén. */
@Component
public class AlmacenClient {

    private final RestClient http;

    public AlmacenClient(@Value("${almacen.url}") String url) {
        this.http = RestClient.create(url);
    }

    /** Se bloquea mientras el almacén esté vacío. */
    public String consumir(int consumidor) {
        Map<?, ?> r = http.get()
            .uri("/almacen/consumir?consumidor={c}", consumidor)
            .retrieve().body(Map.class);
        return String.valueOf(r.get("pieza"));
    }
}
