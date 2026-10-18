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

/**
 * Servicio central de sincronización del puente de doble sentido.
 * Implementa exclusión mutua mediante ReentrantLock (Mutex) y variables
 * de condición para coordinar el sentido del flujo y evitar colisiones e inanición.
 */
@Service
public class PuenteService {

    private static final Logger logger = LoggerFactory.getLogger(PuenteService.class);

    // ==========================================
    // VARIABLES COMPARTIDAS Y MECANISMOS DE SINCRONIZACIÓN
    // ==========================================
    private Direccion direccionActual = Direccion.NINGUNA;
    private int vehiculosNorteEnPuente = 0;
    private int vehiculosSurEnPuente = 0;
    private int vehiculosEsperandoNorte = 0;
    private int vehiculosEsperandoSur = 0;
    private int totalCruzados = 0;
    private boolean colisionDetectada = false;

    // Mutex con política Fair para proteger las secciones críticas
    private final ReentrantLock mutex = new ReentrantLock(true);

    // Variables de condición para pausar/despertar hilos de cada sentido
    private final Condition condicionNorte = mutex.newCondition();
    private final Condition condicionSur = mutex.newCondition();

    // ==========================================
    // RETO: CONTROL DE INANICIÓN (PREVENCIÓN DE MONOPOLIO)
    // ==========================================
    private boolean modoEquitativo = true;
    private int limiteConsecutivos = 5;
    private int consecutivosCruzados = 0;

    // Estructuras de soporte para visualización y métricas
    private final Map<String, Vehiculo> vehiculosActivos = new ConcurrentHashMap<>();
    private final List<EventoLog> bufferLogs = Collections.synchronizedList(new LinkedList<>());
    private final int MAX_LOGS = 120;
    private final AtomicInteger generadorId = new AtomicInteger(1);
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public PuenteService() {
        registrarEvento("INICIALIZACION", null, Direccion.NINGUNA,
                "Sistema de Cruce de Puente inicializado. Mutex activo, Dirección inicial: NINGUNA.");
    }

    /**
     * Procedimiento concurrente para que un vehículo con destino al NORTE cruce el puente.
     */
    public void cruzarNorte(Vehiculo vehiculo) {
        String vehiculoId = vehiculo.getId();
        long inicioEspera = System.currentTimeMillis();

        mutex.lock();
        try {
            vehiculosEsperandoNorte++;
            vehiculo.setEstado(EstadoVehiculo.ESPERANDO);
            vehiculosActivos.put(vehiculoId, vehiculo);

            registrarEvento("LLEGADA", vehiculoId, Direccion.NORTE,
                    String.format("Vehículo %s llega a la orilla SUR con destino al NORTE. [En espera Norte: %d, Dirección activa: %s]",
                            vehiculoId, vehiculosEsperandoNorte, direccionActual));

            // MIENTRAS la dirección sea SUR o se deba ceder el turno por política anti-inanición
            while (debeEsperarNorte()) {
                if (direccionActual == Direccion.SUR) {
                    registrarEvento("BLOQUEO", vehiculoId, Direccion.NORTE,
                            String.format("Vehículo %s BLOQUEADO en wait(condicionNorte): El puente está ocupado en sentido SUR.", vehiculoId));
                } else if (modoEquitativo && vehiculosEsperandoSur > 0 && consecutivosCruzados >= limiteConsecutivos) {
                    registrarEvento("CONTROL_INANICION", vehiculoId, Direccion.NORTE,
                            String.format("Vehículo %s en espera por LÍMITE DE MONOPOLIO (%d/%d autos). Se cede el paso a la cola SUR.",
                                    vehiculoId, consecutivosCruzados, limiteConsecutivos));
                }
                condicionNorte.await();
            }

            // Ha obtenido el permiso para ingresar
            vehiculosEsperandoNorte--;
            direccionActual = Direccion.NORTE;
            vehiculosNorteEnPuente++;
            consecutivosCruzados++;

            vehiculo.setEstado(EstadoVehiculo.CRUZANDO);
            vehiculo.setHoraEntrada(LocalDateTime.now());
            vehiculo.setTiempoEsperaMs(System.currentTimeMillis() - inicioEspera);

            // Verificación formal del invariante de seguridad
            verificarInvarianteSeguridad();

            registrarEvento("ENTRADA", vehiculoId, Direccion.NORTE,
                    String.format("Vehículo %s INGRESA al puente hacia el NORTE. [En puente: %d, Consecutivos: %d]",
                            vehiculoId, vehiculosNorteEnPuente, consecutivosCruzados));

            // Si aún no hemos alcanzado el límite de monopolio y hay más esperando en Norte, despertamos al siguiente
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

        // ==========================================
        // SECCIÓN DE CRUCE (SIMULACIÓN DE TIEMPO EN EL PUENTE)
        // ==========================================
        try {
            Thread.sleep(vehiculo.getTiempoCruceMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ==========================================
        // SALIDA DEL PUENTE (SECCIÓN CRÍTICA DE LIBERACIÓN)
        // ==========================================
        mutex.lock();
        try {
            vehiculosNorteEnPuente--;
            totalCruzados++;
            vehiculo.setEstado(EstadoVehiculo.COMPLETADO);
            vehiculo.setHoraSalida(LocalDateTime.now());

            registrarEvento("SALIDA", vehiculoId, Direccion.NORTE,
                    String.format("Vehículo %s COMPLETA el cruce hacia el NORTE y sale del puente. [Restantes en puente: %d]",
                            vehiculoId, vehiculosNorteEnPuente));

            // Si este era el último vehículo en el puente
            if (vehiculosNorteEnPuente == 0) {
                if (vehiculosEsperandoSur > 0) {
                    // Hay vehículos esperando en la dirección contraria: cambio obligatorio
                    direccionActual = Direccion.SUR;
                    consecutivosCruzados = 0;
                    registrarEvento("CAMBIO_DIRECCION", null, Direccion.SUR,
                            String.format("Puente LIBRE. Dirección cambia a SUR. Despertando a %d vehículos en espera SUR.",
                                    vehiculosEsperandoSur));
                    condicionSur.signalAll();
                } else if (vehiculosEsperandoNorte > 0) {
                    // No hay vehículos en el Sur, continúan los del Norte
                    direccionActual = Direccion.NORTE;
                    consecutivosCruzados = 0;
                    registrarEvento("CONTINUACION", null, Direccion.NORTE,
                            String.format("Puente despejado. Continuando con vehículos en espera NORTE (%d esperando).",
                                    vehiculosEsperandoNorte));
                    condicionNorte.signalAll();
                } else {
                    // No hay vehículos esperando en ninguna dirección
                    direccionActual = Direccion.NINGUNA;
                    consecutivosCruzados = 0;
                    registrarEvento("PUENTE_LIBRE", null, Direccion.NINGUNA,
                            "El puente queda totalmente LIBRE. Dirección restablecida a NINGUNA.");
                }
            }

        } finally {
            mutex.unlock();
        }
    }

    /**
     * Procedimiento concurrente para que un vehículo con destino al SUR cruce el puente.
     * Completa el reto solicitado en la guía implementando la lógica simétrica.
     */
    public void cruzarSur(Vehiculo vehiculo) {
        String vehiculoId = vehiculo.getId();
        long inicioEspera = System.currentTimeMillis();

        mutex.lock();
        try {
            vehiculosEsperandoSur++;
            vehiculo.setEstado(EstadoVehiculo.ESPERANDO);
            vehiculosActivos.put(vehiculoId, vehiculo);

            registrarEvento("LLEGADA", vehiculoId, Direccion.SUR,
                    String.format("Vehículo %s llega a la orilla NORTE con destino al SUR. [En espera Sur: %d, Dirección activa: %s]",
                            vehiculoId, vehiculosEsperandoSur, direccionActual));

            // MIENTRAS la dirección sea NORTE o se deba ceder el turno por política anti-inanición
            while (debeEsperarSur()) {
                if (direccionActual == Direccion.NORTE) {
                    registrarEvento("BLOQUEO", vehiculoId, Direccion.SUR,
                            String.format("Vehículo %s BLOQUEADO en wait(condicionSur): El puente está ocupado en sentido NORTE.", vehiculoId));
                } else if (modoEquitativo && vehiculosEsperandoNorte > 0 && consecutivosCruzados >= limiteConsecutivos) {
                    registrarEvento("CONTROL_INANICION", vehiculoId, Direccion.SUR,
                            String.format("Vehículo %s en espera por LÍMITE DE MONOPOLIO (%d/%d autos). Se cede el paso a la cola NORTE.",
                                    vehiculoId, consecutivosCruzados, limiteConsecutivos));
                }
                condicionSur.await();
            }

            // Ha obtenido el permiso para ingresar
            vehiculosEsperandoSur--;
            direccionActual = Direccion.SUR;
            vehiculosSurEnPuente++;
            consecutivosCruzados++;

            vehiculo.setEstado(EstadoVehiculo.CRUZANDO);
            vehiculo.setHoraEntrada(LocalDateTime.now());
            vehiculo.setTiempoEsperaMs(System.currentTimeMillis() - inicioEspera);

            // Verificación formal del invariante de seguridad
            verificarInvarianteSeguridad();

            registrarEvento("ENTRADA", vehiculoId, Direccion.SUR,
                    String.format("Vehículo %s INGRESA al puente hacia el SUR. [En puente: %d, Consecutivos: %d]",
                            vehiculoId, vehiculosSurEnPuente, consecutivosCruzados));

            // Si aún no hemos alcanzado el límite de monopolio y hay más esperando en Sur, despertamos al siguiente
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

        // ==========================================
        // SECCIÓN DE CRUCE (SIMULACIÓN DE TIEMPO EN EL PUENTE)
        // ==========================================
        try {
            Thread.sleep(vehiculo.getTiempoCruceMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // ==========================================
        // SALIDA DEL PUENTE (SECCIÓN CRÍTICA DE LIBERACIÓN)
        // ==========================================
        mutex.lock();
        try {
            vehiculosSurEnPuente--;
            totalCruzados++;
            vehiculo.setEstado(EstadoVehiculo.COMPLETADO);
            vehiculo.setHoraSalida(LocalDateTime.now());

            registrarEvento("SALIDA", vehiculoId, Direccion.SUR,
                    String.format("Vehículo %s COMPLETA el cruce hacia el SUR y sale del puente. [Restantes en puente: %d]",
                            vehiculoId, vehiculosSurEnPuente));

            // Si este era el último vehículo en el puente
            if (vehiculosSurEnPuente == 0) {
                if (vehiculosEsperandoNorte > 0) {
                    // Hay vehículos esperando en la dirección contraria: cambio obligatorio
                    direccionActual = Direccion.NORTE;
                    consecutivosCruzados = 0;
                    registrarEvento("CAMBIO_DIRECCION", null, Direccion.NORTE,
                            String.format("Puente LIBRE. Dirección cambia a NORTE. Despertando a %d vehículos en espera NORTE.",
                                    vehiculosEsperandoNorte));
                    condicionNorte.signalAll();
                } else if (vehiculosEsperandoSur > 0) {
                    // No hay vehículos en el Norte, continúan los del Sur
                    direccionActual = Direccion.SUR;
                    consecutivosCruzados = 0;
                    registrarEvento("CONTINUACION", null, Direccion.SUR,
                            String.format("Puente despejado. Continuando con vehículos en espera SUR (%d esperando).",
                                    vehiculosEsperandoSur));
                    condicionSur.signalAll();
                } else {
                    // No hay vehículos esperando en ninguna dirección
                    direccionActual = Direccion.NINGUNA;
                    consecutivosCruzados = 0;
                    registrarEvento("PUENTE_LIBRE", null, Direccion.NINGUNA,
                            "El puente queda totalmente LIBRE. Dirección restablecida a NINGUNA.");
                }
            }

        } finally {
            mutex.unlock();
        }
    }

    /**
     * Condiciones para determinar si un vehículo que va al NORTE debe esperar.
     */
    private boolean debeEsperarNorte() {
        if (direccionActual == Direccion.SUR) {
            return true;
        }
        if (modoEquitativo && vehiculosEsperandoSur > 0 && consecutivosCruzados >= limiteConsecutivos) {
            return true;
        }
        return false;
    }

    /**
     * Condiciones para determinar si un vehículo que va al SUR debe esperar.
     */
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

    /**
     * Comprobación estricta del criterio de verificación:
     * "Comprobar que nunca crucen vehículos en sentidos opuestos simultáneamente".
     */
    private void verificarInvarianteSeguridad() {
        if (vehiculosNorteEnPuente > 0 && vehiculosSurEnPuente > 0) {
            this.colisionDetectada = true;
            String errorMsg = String.format("¡COLISIÓN DETECTADA EN EL PUENTE! %d al Norte y %d al Sur simultáneamente.",
                    vehiculosNorteEnPuente, vehiculosSurEnPuente);
            registrarEvento("COLISION", null, direccionActual, errorMsg);
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    /**
     * Encola un vehículo individual mediante un hilo dedicado.
     */
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

    /**
     * Ejecuta una simulación concurrente con N vehículos al Norte y M al Sur.
     */
    public void iniciarSimulacion(SimulacionRequest request) {
        registrarEvento("INICIO_SIMULACION", null, Direccion.NINGUNA,
                String.format("Lanzando simulación concurrente: %d vehículos NORTE y %d vehículos SUR. [Modo equitativo: %s]",
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

    /**
     * Reinicia el estado del puente y la simulación.
     */
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
                    "Estado del puente reiniciado con éxito. Contadores en 0, semáforos libres.");
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Actualiza la configuración de equidad / inanición.
     */
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
                    String.format("Configuración actualizada -> Modo Equitativo: %s, Límite por Lote: %d",
                            modoEquitativo ? "ACTIVADO" : "DESACTIVADO", limiteConsecutivos));
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Obtiene una fotografía completa del estado del puente para el frontend.
     */
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
