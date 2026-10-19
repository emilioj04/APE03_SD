package com.sistemas.distribuidos.puente.service;

import com.sistemas.distribuidos.puente.dto.ConfiguracionRequest;
import com.sistemas.distribuidos.puente.dto.EstadoPuenteDto;
import com.sistemas.distribuidos.puente.dto.SimulacionRequest;
import com.sistemas.distribuidos.puente.dto.VehiculoRequest;
import com.sistemas.distribuidos.puente.model.Direccion;
import com.sistemas.distribuidos.puente.model.EstadoVehiculo;
import com.sistemas.distribuidos.puente.model.EventoLog;
import com.sistemas.distribuidos.puente.model.Vehiculo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PuenteService {

    private static final Logger logger = LoggerFactory.getLogger(PuenteService.class);

    private Direccion direccionActual = Direccion.NINGUNA;
    private int vehiculosNorteEnPuente = 0;
    private int vehiculosSurEnPuente = 0;
    private int vehiculosEsperandoNorte = 0;
    private int vehiculosEsperandoSur = 0;
    private int totalCruzados = 0;
    private boolean colisionDetectada = false;

    private final ReentrantLock mutex = new ReentrantLock(true);
    private final Condition condicionNorte = mutex.newCondition();
    private final Condition condicionSur = mutex.newCondition();

    private boolean modoEquitativo = true;
    private int limiteConsecutivos = 5;
    private int consecutivosCruzados = 0;

    private final Map<String, Vehiculo> vehiculosActivos = new ConcurrentHashMap<>();
    private final List<EventoLog> bufferLogs = Collections.synchronizedList(new LinkedList<>());
    private final int MAX_LOGS = 120;
    private final AtomicInteger generadorId = new AtomicInteger(1);
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public PuenteService() {
        registrarEvento("INICIALIZACION", null, Direccion.NINGUNA,
                "Sistema de Cruce de Puente inicializado. Mutex activo, Direccion inicial: NINGUNA.");
    }

    public void cruzarNorte(Vehiculo vehiculo) {
        String vehiculoId = vehiculo.getId();
        long inicioEspera = System.currentTimeMillis();

        mutex.lock();
        try {
            vehiculosEsperandoNorte++;
            vehiculo.setEstado(EstadoVehiculo.ESPERANDO);
            vehiculosActivos.put(vehiculoId, vehiculo);

            registrarEvento("LLEGADA", vehiculoId, Direccion.NORTE,
                    String.format("Vehiculo %s llega a la orilla SUR con destino al NORTE. [En espera Norte: %d, Direccion activa: %s]",
                            vehiculoId, vehiculosEsperandoNorte, direccionActual));

            while (debeEsperarNorte()) {
                if (direccionActual == Direccion.SUR) {
                    registrarEvento("BLOQUEO", vehiculoId, Direccion.NORTE,
                            String.format("Vehiculo %s BLOQUEADO en wait: El puente esta ocupado en sentido SUR.", vehiculoId));
                } else if (modoEquitativo && vehiculosEsperandoSur > 0 && consecutivosCruzados >= limiteConsecutivos) {
                    registrarEvento("CONTROL_INANICION", vehiculoId, Direccion.NORTE,
                            String.format("Vehiculo %s en espera por limite de monopolio (%d/%d autos). Se cede el paso a la cola SUR.",
                                    vehiculoId, consecutivosCruzados, limiteConsecutivos));
                }
                condicionNorte.await();
            }

            vehiculosEsperandoNorte--;
            direccionActual = Direccion.NORTE;
            vehiculosNorteEnPuente++;
            consecutivosCruzados++;

            vehiculo.setEstado(EstadoVehiculo.CRUZANDO);
            vehiculo.setHoraEntrada(LocalDateTime.now());
            vehiculo.setTiempoEsperaMs(System.currentTimeMillis() - inicioEspera);

            verificarInvarianteSeguridad();

            registrarEvento("ENTRADA", vehiculoId, Direccion.NORTE,
                    String.format("Vehiculo %s INGRESA al puente hacia el NORTE. [En puente: %d, Consecutivos: %d]",
                            vehiculoId, vehiculosNorteEnPuente, consecutivosCruzados));

            if (puedeDespertarSiguienteNorte()) {
                condicionNorte.signal();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registrarEvento("ERROR", vehiculoId, Direccion.NORTE, "Hilo interrumpido durante la espera.");
            return;
        } finally {
            mutex.unlock();
        }

        try {
            Thread.sleep(vehiculo.getTiempoCruceMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        mutex.lock();
        try {
            vehiculosNorteEnPuente--;
            totalCruzados++;
            vehiculo.setEstado(EstadoVehiculo.COMPLETADO);
            vehiculo.setHoraSalida(LocalDateTime.now());

            registrarEvento("SALIDA", vehiculoId, Direccion.NORTE,
                    String.format("Vehiculo %s COMPLETA el cruce hacia el NORTE y sale del puente. [Restantes en puente: %d]",
                            vehiculoId, vehiculosNorteEnPuente));

            if (vehiculosNorteEnPuente == 0) {
                if (vehiculosEsperandoSur > 0) {
                    direccionActual = Direccion.SUR;
                    consecutivosCruzados = 0;
                    registrarEvento("CAMBIO_DIRECCION", null, Direccion.SUR,
                            String.format("Puente LIBRE. Direccion cambia a SUR. Despertando a %d vehiculos en espera SUR.",
                                    vehiculosEsperandoSur));
                    condicionSur.signalAll();
                } else if (vehiculosEsperandoNorte > 0) {
                    direccionActual = Direccion.NORTE;
                    consecutivosCruzados = 0;
                    registrarEvento("CONTINUACION", null, Direccion.NORTE,
                            String.format("Puente despejado. Continuando con vehiculos en espera NORTE (%d esperando).",
                                    vehiculosEsperandoNorte));
                    condicionNorte.signalAll();
                } else {
                    direccionActual = Direccion.NINGUNA;
                    consecutivosCruzados = 0;
                    registrarEvento("PUENTE_LIBRE", null, Direccion.NINGUNA,
                            "El puente queda totalmente LIBRE. Direccion restablecida a NINGUNA.");
                }
            }

        } finally {
            mutex.unlock();
        }
    }

    public void cruzarSur(Vehiculo vehiculo) {
        String vehiculoId = vehiculo.getId();
        long inicioEspera = System.currentTimeMillis();

        mutex.lock();
        try {
            vehiculosEsperandoSur++;
            vehiculo.setEstado(EstadoVehiculo.ESPERANDO);
            vehiculosActivos.put(vehiculoId, vehiculo);

            registrarEvento("LLEGADA", vehiculoId, Direccion.SUR,
                    String.format("Vehiculo %s llega a la orilla NORTE con destino al SUR. [En espera Sur: %d, Direccion activa: %s]",
                            vehiculoId, vehiculosEsperandoSur, direccionActual));

            while (debeEsperarSur()) {
                if (direccionActual == Direccion.NORTE) {
                    registrarEvento("BLOQUEO", vehiculoId, Direccion.SUR,
                            String.format("Vehiculo %s BLOQUEADO en wait: El puente esta ocupado en sentido NORTE.", vehiculoId));
                } else if (modoEquitativo && vehiculosEsperandoNorte > 0 && consecutivosCruzados >= limiteConsecutivos) {
                    registrarEvento("CONTROL_INANICION", vehiculoId, Direccion.SUR,
                            String.format("Vehiculo %s en espera por limite de monopolio (%d/%d autos). Se cede el paso a la cola NORTE.",
                                    vehiculoId, consecutivosCruzados, limiteConsecutivos));
                }
                condicionSur.await();
            }

            vehiculosEsperandoSur--;
            direccionActual = Direccion.SUR;
            vehiculosSurEnPuente++;
            consecutivosCruzados++;

            vehiculo.setEstado(EstadoVehiculo.CRUZANDO);
            vehiculo.setHoraEntrada(LocalDateTime.now());
            vehiculo.setTiempoEsperaMs(System.currentTimeMillis() - inicioEspera);

            verificarInvarianteSeguridad();

            registrarEvento("ENTRADA", vehiculoId, Direccion.SUR,
                    String.format("Vehiculo %s INGRESA al puente hacia el SUR. [En puente: %d, Consecutivos: %d]",
                            vehiculoId, vehiculosSurEnPuente, consecutivosCruzados));

            if (puedeDespertarSiguienteSur()) {
                condicionSur.signal();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            registrarEvento("ERROR", vehiculoId, Direccion.SUR, "Hilo interrumpido durante la espera.");
            return;
        } finally {
            mutex.unlock();
        }

        try {
            Thread.sleep(vehiculo.getTiempoCruceMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        mutex.lock();
        try {
            vehiculosSurEnPuente--;
            totalCruzados++;
            vehiculo.setEstado(EstadoVehiculo.COMPLETADO);
            vehiculo.setHoraSalida(LocalDateTime.now());

            registrarEvento("SALIDA", vehiculoId, Direccion.SUR,
                    String.format("Vehiculo %s COMPLETA el cruce hacia el SUR y sale del puente. [Restantes en puente: %d]",
                            vehiculoId, vehiculosSurEnPuente));

            if (vehiculosSurEnPuente == 0) {
                if (vehiculosEsperandoNorte > 0) {
                    direccionActual = Direccion.NORTE;
                    consecutivosCruzados = 0;
                    registrarEvento("CAMBIO_DIRECCION", null, Direccion.NORTE,
                            String.format("Puente LIBRE. Direccion cambia a NORTE. Despertando a %d vehiculos en espera NORTE.",
                                    vehiculosEsperandoNorte));
                    condicionNorte.signalAll();
                } else if (vehiculosEsperandoSur > 0) {
                    direccionActual = Direccion.SUR;
                    consecutivosCruzados = 0;
                    registrarEvento("CONTINUACION", null, Direccion.SUR,
                            String.format("Puente despejado. Continuando con vehiculos en espera SUR (%d esperando).",
                                    vehiculosEsperandoSur));
                    condicionSur.signalAll();
                } else {
                    direccionActual = Direccion.NINGUNA;
                    consecutivosCruzados = 0;
                    registrarEvento("PUENTE_LIBRE", null, Direccion.NINGUNA,
                            "El puente queda totalmente LIBRE. Direccion restablecida a NINGUNA.");
                }
            }

        } finally {
            mutex.unlock();
        }
    }

    private boolean debeEsperarNorte() {
        if (direccionActual == Direccion.SUR) {
            return true;
        }
        if (modoEquitativo && vehiculosEsperandoSur > 0 && consecutivosCruzados >= limiteConsecutivos) {
            return true;
        }
        return false;
    }

    private boolean debeEsperarSur() {
        if (direccionActual == Direccion.NORTE) {
            return true;
        }
        if (modoEquitativo && vehiculosEsperandoNorte > 0 && consecutivosCruzados >= limiteConsecutivos) {
            return true;
        }
        return false;
    }

    private boolean puedeDespertarSiguienteNorte() {
        if (!modoEquitativo || vehiculosEsperandoSur == 0) {
            return vehiculosEsperandoNorte > 0;
        }
        return vehiculosEsperandoNorte > 0 && consecutivosCruzados < limiteConsecutivos;
    }

    private boolean puedeDespertarSiguienteSur() {
        if (!modoEquitativo || vehiculosEsperandoNorte == 0) {
            return vehiculosEsperandoSur > 0;
        }
        return vehiculosEsperandoSur > 0 && consecutivosCruzados < limiteConsecutivos;
    }

    private void verificarInvarianteSeguridad() {
        if (vehiculosNorteEnPuente > 0 && vehiculosSurEnPuente > 0) {
            this.colisionDetectada = true;
            String errorMsg = String.format("Colision detectada en el puente: %d al Norte y %d al Sur simultaneamente.",
                    vehiculosNorteEnPuente, vehiculosSurEnPuente);
            registrarEvento("COLISION", null, direccionActual, errorMsg);
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    public Vehiculo encolarVehiculo(VehiculoRequest request) {
        String id = (request.getId() != null && !request.getId().isBlank())
                ? request.getId()
                : (request.getDireccion() == Direccion.NORTE ? "N-" : "S-") + generadorId.getAndIncrement();

        Vehiculo vehiculo = new Vehiculo(id, request.getDireccion());
        vehiculo.setTiempoCruceMs(request.getTiempoCruceMs() > 0 ? request.getTiempoCruceMs() : 2000);

        executorService.submit(() -> {
            Thread.currentThread().setName("Hilo-" + vehiculo.getId());
            if (vehiculo.getDireccion() == Direccion.NORTE) {
                cruzarNorte(vehiculo);
            } else {
                cruzarSur(vehiculo);
            }
        });

        return vehiculo;
    }

    public void iniciarSimulacion(SimulacionRequest request) {
        registrarEvento("INICIO_SIMULACION", null, Direccion.NINGUNA,
                String.format("Lanzando simulacion concurrente: %d vehiculos NORTE y %d vehiculos SUR. [Modo equitativo: %s]",
                        request.getCantidadNorte(), request.getCantidadSur(), modoEquitativo ? "ACTIVADO" : "DESACTIVADO"));

        executorService.submit(() -> {
            Random random = new Random();
            int total = request.getCantidadNorte() + request.getCantidadSur();
            int lanzadosNorte = 0;
            int lanzadosSur = 0;

            for (int i = 0; i < total; i++) {
                Direccion dir;
                if (lanzadosNorte < request.getCantidadNorte() && lanzadosSur < request.getCantidadSur()) {
                    dir = random.nextBoolean() ? Direccion.NORTE : Direccion.SUR;
                } else if (lanzadosNorte < request.getCantidadNorte()) {
                    dir = Direccion.NORTE;
                } else {
                    dir = Direccion.SUR;
                }

                if (dir == Direccion.NORTE) {
                    lanzadosNorte++;
                } else {
                    lanzadosSur++;
                }

                long tiempoCruce = request.getTiempoCruceMinMs() +
                        (long) (random.nextDouble() * (request.getTiempoCruceMaxMs() - request.getTiempoCruceMinMs()));

                VehiculoRequest vReq = new VehiculoRequest();
                vReq.setDireccion(dir);
                vReq.setTiempoCruceMs(tiempoCruce);
                encolarVehiculo(vReq);

                try {
                    Thread.sleep(request.getIntervaloGeneracionMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void reiniciar() {
        mutex.lock();
        try {
            direccionActual = Direccion.NINGUNA;
            vehiculosNorteEnPuente = 0;
            vehiculosSurEnPuente = 0;
            vehiculosEsperandoNorte = 0;
            vehiculosEsperandoSur = 0;
            totalCruzados = 0;
            consecutivosCruzados = 0;
            colisionDetectada = false;
            vehiculosActivos.clear();
            bufferLogs.clear();

            registrarEvento("REINICIO", null, Direccion.NINGUNA,
                    "Estado del puente reiniciado. Contadores en 0, puente libre.");
        } finally {
            mutex.unlock();
        }
    }

    public void configurar(ConfiguracionRequest request) {
        mutex.lock();
        try {
            if (request.getModoEquitativo() != null) {
                this.modoEquitativo = request.getModoEquitativo();
            }
            if (request.getLimiteConsecutivos() != null && request.getLimiteConsecutivos() > 0) {
                this.limiteConsecutivos = request.getLimiteConsecutivos();
            }
            registrarEvento("CONFIGURACION", null, direccionActual,
                    String.format("Configuracion actualizada: Modo Equitativo %s, Limite por Lote %d",
                            modoEquitativo ? "ACTIVADO" : "DESACTIVADO", limiteConsecutivos));
        } finally {
            mutex.unlock();
        }
    }

    public EstadoPuenteDto obtenerEstado() {
        mutex.lock();
        try {
            EstadoPuenteDto dto = new EstadoPuenteDto();
            dto.setDireccionActual(this.direccionActual);
            dto.setVehiculosNorteEnPuente(this.vehiculosNorteEnPuente);
            dto.setVehiculosSurEnPuente(this.vehiculosSurEnPuente);
            dto.setTotalEnPuente(this.vehiculosNorteEnPuente + this.vehiculosSurEnPuente);
            dto.setVehiculosEsperandoNorte(this.vehiculosEsperandoNorte);
            dto.setVehiculosEsperandoSur(this.vehiculosEsperandoSur);
            dto.setTotalEsperando(this.vehiculosEsperandoNorte + this.vehiculosEsperandoSur);
            dto.setTotalCruzados(this.totalCruzados);
            dto.setColisionDetectada(this.colisionDetectada);
            dto.setModoEquitativo(this.modoEquitativo);
            dto.setLimiteConsecutivos(this.limiteConsecutivos);
            dto.setConsecutivosCruzados(this.consecutivosCruzados);
            dto.setPuenteLibre(this.vehiculosNorteEnPuente == 0 && this.vehiculosSurEnPuente == 0);

            List<Vehiculo> enPuente = new ArrayList<>();
            List<Vehiculo> colaNorte = new ArrayList<>();
            List<Vehiculo> colaSur = new ArrayList<>();

            for (Vehiculo v : vehiculosActivos.values()) {
                if (v.getEstado() == EstadoVehiculo.CRUZANDO) {
                    enPuente.add(v);
                } else if (v.getEstado() == EstadoVehiculo.ESPERANDO) {
                    if (v.getDireccion() == Direccion.NORTE) {
                        colaNorte.add(v);
                    } else {
                        colaSur.add(v);
                    }
                }
            }

            dto.setEnPuente(enPuente);
            dto.setColaNorte(colaNorte);
            dto.setColaSur(colaSur);

            synchronized (bufferLogs) {
                dto.setUltimosEventos(new ArrayList<>(bufferLogs));
            }

            return dto;
        } finally {
            mutex.unlock();
        }
    }

    private void registrarEvento(String tipo, String vehiculoId, Direccion direccion, String mensaje) {
        EventoLog log = new EventoLog(tipo, vehiculoId, direccion, mensaje);
        synchronized (bufferLogs) {
            bufferLogs.add(0, log);
            if (bufferLogs.size() > MAX_LOGS) {
                bufferLogs.remove(bufferLogs.size() - 1);
            }
        }
        logger.info("[{}] {}", tipo, mensaje);
    }
}
