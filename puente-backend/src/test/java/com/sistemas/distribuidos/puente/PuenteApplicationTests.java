package com.sistemas.distribuidos.puente;

import com.sistemas.distribuidos.puente.dto.EstadoPuenteDto;
import com.sistemas.distribuidos.puente.dto.VehiculoRequest;
import com.sistemas.distribuidos.puente.model.Direccion;
import com.sistemas.distribuidos.puente.service.PuenteService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class PuenteApplicationTests {

    @Autowired
    private PuenteService puenteService;

    @Test
    void contextLoads() {
        Assertions.assertNotNull(puenteService);
    }

    @Test
    void testExclusionMutuaSentidosOpuestos() throws InterruptedException {
        puenteService.reiniciar();
        int totalHilos = 10;
        CountDownLatch latch = new CountDownLatch(totalHilos);
        ExecutorService executor = Executors.newFixedThreadPool(totalHilos);

        for (int i = 0; i < totalHilos; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    VehiculoRequest req = new VehiculoRequest();
                    req.setId("TestAuto-" + id);
                    req.setDireccion((id % 2 == 0) ? Direccion.NORTE : Direccion.SUR);
                    req.setTiempoCruceMs(200);
                    puenteService.encolarVehiculo(req);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        Thread.sleep(3000);

        EstadoPuenteDto estado = puenteService.obtenerEstado();
        Assertions.assertFalse(estado.isColisionDetectada(), "No debe haber colisiones registradas");
        executor.shutdown();
    }
}
