package ec.unl.distribuidos.productor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Patrón Gateway: oculta el acceso HTTP al microservicio almacén. */
@Component
public class AlmacenClient {

    private final RestClient http;

    public AlmacenClient(@Value("${almacen.url}") String url) {
        this.http = RestClient.create(url);
    }

    /** Se bloquea mientras el almacén esté lleno. */
    public void producir(int productor, String pieza) {
        http.post()
            .uri("/almacen/producir?productor={p}&pieza={z}", productor, pieza)
            .retrieve().toBodilessEntity();
    }
}
