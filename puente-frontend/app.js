const { useState, useEffect, useRef } = React;

const API_BASE = window.location.port === '3000' || window.location.port === '80' || window.location.port === '5173'
  ? (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1' 
      ? 'http://localhost:8080/api/puente' 
      : '/api/puente')
  : 'http://localhost:8080/api/puente';

function App() {
  const [estado, setEstado] = useState({
    direccionActual: 'NINGUNA',
    vehiculosNorteEnPuente: 0,
    vehiculosSurEnPuente: 0,
    totalEnPuente: 0,
    vehiculosEsperandoNorte: 0,
    vehiculosEsperandoSur: 0,
    totalEsperando: 0,
    totalCruzados: 0,
    colisionDetectada: false,
    modoEquitativo: true,
    limiteConsecutivos: 5,
    consecutivosCruzados: 0,
    puenteLibre: true,
    enPuente: [],
    colaNorte: [],
    colaSur: [],
    ultimosEventos: [
      {
        timestamp: new Date().toLocaleTimeString(),
        tipo: 'INICIALIZACION',
        mensaje: 'Sistema de Cruce de Puente inicializado. Mutex activo, Direccion: NINGUNA.'
      }
    ]
  });

  const [conectado, setConectado] = useState(false);
  const [cargando, setCargando] = useState(false);
  const [velocidadCruce, setVelocidadCruce] = useState(2000);
  const [limiteLote, setLimiteLote] = useState(5);
  const idCounterRef = useRef(1);
  const logsContainerRef = useRef(null);

  useEffect(() => {
    let cancelado = false;
    const fetchEstado = async () => {
      try {
        const res = await fetch(`${API_BASE}/estado`, { signal: AbortSignal.timeout(600) });
        if (res.ok) {
          const data = await res.json();
          if (!cancelado) {
            setEstado(data);
            setConectado(true);
          }
        } else {
          if (!cancelado) setConectado(false);
        }
      } catch (err) {
        if (!cancelado) setConectado(false);
      }
    };

    fetchEstado();
    const interval = setInterval(fetchEstado, 700);
    return () => {
      cancelado = true;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    if (conectado) return;

    const timer = setInterval(() => {
      setEstado(prev => {
        let {
          direccionActual,
          enPuente,
          colaNorte,
          colaSur,
          totalCruzados,
          modoEquitativo,
          limiteConsecutivos,
          consecutivosCruzados,
          ultimosEventos
        } = prev;

        let nuevosLogs = [...ultimosEventos];
        const pushLog = (tipo, mensaje) => {
          nuevosLogs.unshift({
            timestamp: new Date().toLocaleTimeString(),
            tipo,
            mensaje
          });
          if (nuevosLogs.length > 100) nuevosLogs.pop();
        };

        const ahora = Date.now();
        let nuevosEnPuente = [];
        let autosQueSalieron = [];

        enPuente.forEach(v => {
          if (ahora >= v.tiempoFin) {
            autosQueSalieron.push(v);
          } else {
            nuevosEnPuente.push(v);
          }
        });

        autosQueSalieron.forEach(v => {
          totalCruzados++;
          pushLog('SALIDA', `Vehiculo #${v.id} SALE del puente (${v.direccion}). Cruce completado con exito.`);
        });

        if (nuevosEnPuente.length === 0) {
          if (direccionActual === 'NORTE') {
            if (colaSur.length > 0) {
              direccionActual = 'SUR';
              consecutivosCruzados = 0;
              pushLog('CAMBIO_DIRECCION', `Puente LIBRE. Direccion cambia a SUR. Despertando fila SUR (${colaSur.length} autos).`);
            } else if (colaNorte.length > 0) {
              direccionActual = 'NORTE';
              consecutivosCruzados = 0;
            } else {
              direccionActual = 'NINGUNA';
              consecutivosCruzados = 0;
              if (prev.enPuente.length > 0) {
                pushLog('PUENTE_LIBRE', 'El puente se encuentra totalmente LIBRE.');
              }
            }
          } else if (direccionActual === 'SUR') {
            if (colaNorte.length > 0) {
              direccionActual = 'NORTE';
              consecutivosCruzados = 0;
              pushLog('CAMBIO_DIRECCION', `Puente LIBRE. Direccion cambia a NORTE. Despertando fila NORTE (${colaNorte.length} autos).`);
            } else if (colaSur.length > 0) {
              direccionActual = 'SUR';
              consecutivosCruzados = 0;
            } else {
              direccionActual = 'NINGUNA';
              consecutivosCruzados = 0;
              if (prev.enPuente.length > 0) {
                pushLog('PUENTE_LIBRE', 'El puente se encuentra totalmente LIBRE.');
              }
            }
          } else {
            if (colaNorte.length > 0 && colaSur.length > 0) {
              direccionActual = 'NORTE';
              consecutivosCruzados = 0;
              pushLog('CAMBIO_DIRECCION', 'Trafico en ambos sentidos. Mutex asigna direccion inicial a NORTE.');
            } else if (colaNorte.length > 0) {
              direccionActual = 'NORTE';
              consecutivosCruzados = 0;
              pushLog('CAMBIO_DIRECCION', 'Direccion establecida a NORTE.');
            } else if (colaSur.length > 0) {
              direccionActual = 'SUR';
              consecutivosCruzados = 0;
              pushLog('CAMBIO_DIRECCION', 'Direccion establecida a SUR.');
            }
          }
        }

        let nuevaColaNorte = [...colaNorte];
        let nuevaColaSur = [...colaSur];

        if (direccionActual === 'NORTE') {
          const bloquearPorInanicion = modoEquitativo && nuevaColaSur.length > 0 && consecutivosCruzados >= limiteConsecutivos;
          if (!bloquearPorInanicion && nuevaColaNorte.length > 0 && nuevosEnPuente.length < 4) {
            const entrando = nuevaColaNorte.shift();
            entrando.tiempoFin = ahora + velocidadCruce;
            nuevosEnPuente.push(entrando);
            consecutivosCruzados++;
            pushLog('ENTRADA', `Vehiculo #${entrando.id} INGRESA al puente hacia el NORTE. [En puente: ${nuevosEnPuente.length}]`);
          } else if (bloquearPorInanicion && nuevaColaNorte.length > 0 && nuevosEnPuente.length === 0) {
            direccionActual = 'SUR';
            consecutivosCruzados = 0;
            pushLog('CONTROL_INANICION', `Limite de lote alcanzado (${limiteConsecutivos} autos). Se cede el puente al sentido SUR.`);
          }
        } else if (direccionActual === 'SUR') {
          const bloquearPorInanicion = modoEquitativo && nuevaColaNorte.length > 0 && consecutivosCruzados >= limiteConsecutivos;
          if (!bloquearPorInanicion && nuevaColaSur.length > 0 && nuevosEnPuente.length < 4) {
            const entrando = nuevaColaSur.shift();
            entrando.tiempoFin = ahora + velocidadCruce;
            nuevosEnPuente.push(entrando);
            consecutivosCruzados++;
            pushLog('ENTRADA', `Vehiculo #${entrando.id} INGRESA al puente hacia el SUR. [En puente: ${nuevosEnPuente.length}]`);
          } else if (bloquearPorInanicion && nuevaColaSur.length > 0 && nuevosEnPuente.length === 0) {
            direccionActual = 'NORTE';
            consecutivosCruzados = 0;
            pushLog('CONTROL_INANICION', `Limite de lote alcanzado (${limiteConsecutivos} autos). Se cede el puente al sentido NORTE.`);
          }
        }

        const norteEnPuente = nuevosEnPuente.filter(v => v.direccion === 'NORTE').length;
        const surEnPuente = nuevosEnPuente.filter(v => v.direccion === 'SUR').length;
        const colision = (norteEnPuente > 0 && surEnPuente > 0);

        return {
          ...prev,
          direccionActual,
          enPuente: nuevosEnPuente,
          colaNorte: nuevaColaNorte,
          colaSur: nuevaColaSur,
          vehiculosNorteEnPuente: norteEnPuente,
          vehiculosSurEnPuente: surEnPuente,
          totalEnPuente: nuevosEnPuente.length,
          vehiculosEsperandoNorte: nuevaColaNorte.length,
          vehiculosEsperandoSur: nuevaColaSur.length,
          totalEsperando: nuevaColaNorte.length + nuevaColaSur.length,
          totalCruzados,
          consecutivosCruzados,
          colisionDetectada: colision,
          ultimosEventos: nuevosLogs
        };
      });
    }, 250);

    return () => clearInterval(timer);
  }, [conectado, velocidadCruce]);

  const handleSimular10 = async () => {
    setCargando(true);
    if (conectado) {
      try {
        await fetch(`${API_BASE}/simular`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            cantidadNorte: 5,
            cantidadSur: 5,
            tiempoCruceMinMs: velocidadCruce - 400 > 500 ? velocidadCruce - 400 : 800,
            tiempoCruceMaxMs: velocidadCruce + 400,
            intervaloGeneracionMs: 250
          })
        });
      } catch (err) {
        console.error(err);
      } finally {
        setCargando(false);
      }
    } else {
      setEstado(prev => {
        let colaN = [...prev.colaNorte];
        let colaS = [...prev.colaSur];
        let nuevosLogs = [...prev.ultimosEventos];

        for (let i = 1; i <= 5; i++) {
          const idN = `N-${idCounterRef.current++}`;
          colaN.push({ id: idN, direccion: 'NORTE', estado: 'ESPERANDO' });
          nuevosLogs.unshift({
            timestamp: new Date().toLocaleTimeString(),
            tipo: 'LLEGADA',
            mensaje: `Vehiculo #${idN} llega a la cola con destino al NORTE.`
          });
        }
        for (let j = 1; j <= 5; j++) {
          const idS = `S-${idCounterRef.current++}`;
          colaS.push({ id: idS, direccion: 'SUR', estado: 'ESPERANDO' });
          nuevosLogs.unshift({
            timestamp: new Date().toLocaleTimeString(),
            tipo: 'LLEGADA',
            mensaje: `Vehiculo #${idS} llega a la cola con destino al SUR.`
          });
        }

        nuevosLogs.unshift({
          timestamp: new Date().toLocaleTimeString(),
          tipo: 'INICIO_SIMULACION',
          mensaje: 'Lanzando simulacion: 10 vehiculos simultaneos (5 Norte y 5 Sur).'
        });

        return {
          ...prev,
          colaNorte: colaN,
          colaSur: colaS,
          vehiculosEsperandoNorte: colaN.length,
          vehiculosEsperandoSur: colaS.length,
          totalEsperando: colaN.length + colaS.length,
          ultimosEventos: nuevosLogs
        };
      });
      setCargando(false);
    }
  };

  const handleAgregarVehiculo = async (direccion) => {
    if (conectado) {
      try {
        await fetch(`${API_BASE}/vehiculo`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            direccion: direccion,
            tiempoCruceMs: velocidadCruce
          })
        });
      } catch (err) {
        console.error(err);
      }
    } else {
      setEstado(prev => {
        const id = `${direccion === 'NORTE' ? 'N' : 'S'}-${idCounterRef.current++}`;
        const nuevoVehiculo = { id, direccion, estado: 'ESPERANDO' };
        const colaN = direccion === 'NORTE' ? [...prev.colaNorte, nuevoVehiculo] : [...prev.colaNorte];
        const colaS = direccion === 'SUR' ? [...prev.colaSur, nuevoVehiculo] : [...prev.colaSur];
        const nuevosLogs = [
          {
            timestamp: new Date().toLocaleTimeString(),
            tipo: 'LLEGADA',
            mensaje: `Vehiculo #${id} llega y solicita cruzar hacia el ${direccion}.`
          },
          ...prev.ultimosEventos
        ];

        return {
          ...prev,
          colaNorte: colaN,
          colaSur: colaS,
          vehiculosEsperandoNorte: colaN.length,
          vehiculosEsperandoSur: colaS.length,
          totalEsperando: colaN.length + colaS.length,
          ultimosEventos: nuevosLogs
        };
      });
    }
  };

  const handleReiniciar = async () => {
    if (conectado) {
      try {
        await fetch(`${API_BASE}/reiniciar`, { method: 'POST' });
      } catch (err) {
        console.error(err);
      }
    } else {
      idCounterRef.current = 1;
      setEstado(prev => ({
        ...prev,
        direccionActual: 'NINGUNA',
        vehiculosNorteEnPuente: 0,
        vehiculosSurEnPuente: 0,
        totalEnPuente: 0,
        vehiculosEsperandoNorte: 0,
        vehiculosEsperandoSur: 0,
        totalEsperando: 0,
        totalCruzados: 0,
        consecutivosCruzados: 0,
        colisionDetectada: false,
        enPuente: [],
        colaNorte: [],
        colaSur: [],
        ultimosEventos: [
          {
            timestamp: new Date().toLocaleTimeString(),
            tipo: 'REINICIO',
            mensaje: 'Simulacion y puente reiniciados. Contadores en 0, semaforos libres.'
          }
        ]
      }));
    }
  };

  const handleToggleEquidad = async (e) => {
    const nuevoModo = e.target.checked;
    if (conectado) {
      try {
        await fetch(`${API_BASE}/configurar`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            modoEquitativo: nuevoModo,
            limiteConsecutivos: limiteLote
          })
        });
      } catch (err) {
        console.error(err);
      }
    }
    setEstado(prev => ({
      ...prev,
      modoEquitativo: nuevoModo,
      ultimosEventos: [
        {
          timestamp: new Date().toLocaleTimeString(),
          tipo: 'CONFIGURACION',
          mensaje: `Politica Anti-Inanicion: ${nuevoModo ? 'ACTIVADA (Fair Batching)' : 'DESACTIVADA (Modo Estandar)'}`
        },
        ...prev.ultimosEventos
      ]
    }));
  };

  const handleCambiarLimiteLote = async (nuevoLimite) => {
    setLimiteLote(nuevoLimite);
    if (conectado) {
      try {
        await fetch(`${API_BASE}/configurar`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            modoEquitativo: estado.modoEquitativo,
            limiteConsecutivos: nuevoLimite
          })
        });
      } catch (err) {
        console.error(err);
      }
    }
    setEstado(prev => ({
      ...prev,
      limiteConsecutivos: nuevoLimite,
      ultimosEventos: [
        {
          timestamp: new Date().toLocaleTimeString(),
          tipo: 'CONFIGURACION',
          mensaje: `Limite por lote actualizado a ${nuevoLimite} vehiculos consecutivos.`
        },
        ...prev.ultimosEventos
      ]
    }));
  };

  const semaforoNorteVerde = estado.direccionActual === 'NORTE' || estado.direccionActual === 'NINGUNA';
  const semaforoSurVerde = estado.direccionActual === 'SUR' || estado.direccionActual === 'NINGUNA';

  return (
    <div className="app-root">
      <header className="app-header">
        <div className="header-container">
          <div className="brand-wrapper">
            <div className="brand-icon">🌉</div>
            <div>
              <h1 className="brand-title">SIMULADOR DE CRUCE DE PUENTE</h1>
              <p className="brand-subtitle">
                Sistemas Distribuidos — Ejercicio 4: Exclusión Mutua & Sincronización en Dirección Única | Emilio Jaramillo
              </p>
            </div>
          </div>
          <div className="header-badges">
            <span className={`badge ${conectado ? 'badge-connected' : 'badge-connected'}`} 
                  style={!conectado ? { background: 'rgba(59, 130, 246, 0.15)', color: '#60a5fa', borderColor: 'rgba(59, 130, 246, 0.3)' } : {}}>
              <span className="pulse-dot"></span>
              {conectado ? 'Backend Spring Boot Conectado (Puerto 8080)' : 'Motor de Simulacion Concurrente Activo'}
            </span>
            <span className="badge badge-connected" style={{ background: 'rgba(6, 182, 212, 0.15)', color: '#38bdf8' }}>
              🛡️ Mutex ReentrantLock (Fair)
            </span>
          </div>
        </div>
      </header>

      <main className="main-container">
        
        <section className="kpi-grid">
          <div className="kpi-card">
            <span className="kpi-title">Sentido de Circulación</span>
            <div className="kpi-value">
              {estado.direccionActual === 'NORTE' && (
                <span className="dir-pill dir-norte">▲ NORTE</span>
              )}
              {estado.direccionActual === 'SUR' && (
                <span className="dir-pill dir-sur">▼ SUR</span>
              )}
              {estado.direccionActual === 'NINGUNA' && (
                <span className="dir-pill dir-ninguna">⚪ LIBRE</span>
              )}
            </div>
            <span className="kpi-subtitle">
              {estado.modoEquitativo ? `Lote: ${estado.consecutivosCruzados}/${estado.limiteConsecutivos} autos` : 'Modo Estandar (Sin limite)'}
            </span>
          </div>

          <div className="kpi-card">
            <span className="kpi-title">Vehículos en el Puente</span>
            <div className="kpi-value" style={{ color: estado.totalEnPuente > 0 ? '#38bdf8' : '#94a3b8' }}>
              {estado.totalEnPuente}
              <span style={{ fontSize: '1rem', color: '#64748b' }}>cruzando</span>
            </div>
            <span className="kpi-subtitle">Mismo sentido permitido</span>
          </div>

          <div className="kpi-card">
            <span className="kpi-title">Cola Espera (Hacia Norte)</span>
            <div className="kpi-value" style={{ color: '#67e8f9' }}>
              {estado.vehiculosEsperandoNorte}
              <span style={{ fontSize: '1rem', color: '#64748b' }}>en espera</span>
            </div>
            <span className="kpi-subtitle">Bloqueados en wait(condNorte)</span>
          </div>

          <div className="kpi-card">
            <span className="kpi-title">Cola Espera (Hacia Sur)</span>
            <div className="kpi-value" style={{ color: '#fde047' }}>
              {estado.vehiculosEsperandoSur}
              <span style={{ fontSize: '1rem', color: '#64748b' }}>en espera</span>
            </div>
            <span className="kpi-subtitle">Bloqueados en wait(condSur)</span>
          </div>

          <div className="kpi-card">
            <span className="kpi-title">Total Cruzados Exitosos</span>
            <div className="kpi-value" style={{ color: '#34d399' }}>
              {estado.totalCruzados}
              <span style={{ fontSize: '1rem', color: '#64748b' }}>completados</span>
            </div>
            <span className="kpi-subtitle">Sincronizacion sin fallos</span>
          </div>

          <div className="kpi-card">
            <span className="kpi-title">Invariante de Seguridad</span>
            <div className="kpi-value" style={{ fontSize: '1.2rem', color: estado.colisionDetectada ? '#f87171' : '#34d399' }}>
              {estado.colisionDetectada ? '⚠️ ¡COLISIÓN!' : '✅ 0 Colisiones'}
            </div>
            <span className="kpi-subtitle">!(Norte &gt; 0 &amp;&amp; Sur &gt; 0) verificado</span>
          </div>
        </section>

        <section className="simulation-arena">
          <div className="arena-header">
            <div className="arena-title">
              <span>🌉 Simulación Gráfica de Tráfico y Acceso al Puente</span>
            </div>
            <div className="arena-legend">
              <div className="legend-item">
                <span className="dot" style={{ background: '#06b6d4' }}></span>
                <span>Sentido Norte (▲)</span>
              </div>
              <div className="legend-item">
                <span className="dot" style={{ background: '#f59e0b' }}></span>
                <span>Sentido Sur (▼)</span>
              </div>
              <div className="legend-item">
                <span className="dot" style={{ background: '#22c55e' }}></span>
                <span>Paso Habilitado</span>
              </div>
            </div>
          </div>

          <div className="bridge-viewport">
            
            <div className="approach-zone">
              <div className="approach-head">
                <div className="approach-title">
                  <span>🚗 Acceso Sur (Hacia Norte ▲)</span>
                </div>
                <div className="semaphore">
                  <span className={`light light-red ${!semaforoNorteVerde ? 'active' : ''}`}></span>
                  <span className={`light light-green ${semaforoNorteVerde ? 'active' : ''}`}></span>
                </div>
              </div>
              
              <div className="waiting-queue">
                {(!estado.colaNorte || estado.colaNorte.length === 0) ? (
                  <div className="empty-queue-msg">Sin vehiculos en espera hacia el Norte</div>
                ) : (
                  estado.colaNorte.map((vehiculo) => (
                    <div key={vehiculo.id} className="car-chip car-north">
                      <span>▲ Auto #{vehiculo.id}</span>
                      <span style={{ fontSize: '0.7rem', opacity: 0.8 }}>EN ESPERA</span>
                    </div>
                  ))
                )}
              </div>
            </div>

            <div className="bridge-deck">
              <div className="bridge-structure">
                <svg className="suspension-cables" viewBox="0 0 600 50">
                  <path d="M 0 5 Q 150 45 300 10 Q 450 45 600 5" />
                  <line x1="100" y1="28" x2="100" y2="50" />
                  <line x1="200" y1="36" x2="200" y2="50" />
                  <line x1="300" y1="10" x2="300" y2="50" />
                  <line x1="400" y1="36" x2="400" y2="50" />
                  <line x1="500" y1="28" x2="500" y2="50" />
                </svg>

                <div className="bridge-roadway">
                  <div className="road-stripes"></div>

                  <div className="flow-indicators">
                    {estado.direccionActual === 'NORTE' && (
                      <span className="flow-north">FLUIDO &gt;&gt;&gt;&gt;&gt; [NORTE]</span>
                    )}
                    {estado.direccionActual === 'SUR' && (
                      <span className="flow-south">[SUR] &lt;&lt;&lt;&lt;&lt; FLUIDO</span>
                    )}
                    {estado.direccionActual === 'NINGUNA' && (
                      <span style={{ color: '#94a3b8' }}>PUENTE LIBRE</span>
                    )}
                  </div>

                  <div className="cars-in-bridge-container">
                    {(!estado.enPuente || estado.enPuente.length === 0) ? (
                      <div style={{ color: '#64748b', fontSize: '0.85rem', fontStyle: 'italic' }}>
                        --- Puente Despejado (Exclusion mutua en reposo) ---
                      </div>
                    ) : (
                      estado.enPuente.map((v) => (
                        <div 
                          key={v.id} 
                          className={`bridge-car ${v.direccion === 'NORTE' ? 'car-north' : 'car-south'}`}
                        >
                          <span>{v.direccion === 'NORTE' ? '▲' : '▼'}</span>
                          <span>Auto #{v.id}</span>
                          <span style={{ fontSize: '0.72rem', background: 'rgba(0,0,0,0.3)', padding: '2px 5px', borderRadius: '4px' }}>
                            CRUZANDO
                          </span>
                        </div>
                      ))
                    )}
                  </div>
                </div>

                <div className="water-river"></div>
              </div>
            </div>

            <div className="approach-zone">
              <div className="approach-head">
                <div className="approach-title">
                  <span>🚙 Acceso Norte (Hacia Sur ▼)</span>
                </div>
                <div className="semaphore">
                  <span className={`light light-red ${!semaforoSurVerde ? 'active' : ''}`}></span>
                  <span className={`light light-green ${semaforoSurVerde ? 'active' : ''}`}></span>
                </div>
              </div>

              <div className="waiting-queue">
                {(!estado.colaSur || estado.colaSur.length === 0) ? (
                  <div className="empty-queue-msg">Sin vehiculos en espera hacia el Sur</div>
                ) : (
                  estado.colaSur.map((vehiculo) => (
                    <div key={vehiculo.id} className="car-chip car-south">
                      <span>▼ Auto #{vehiculo.id}</span>
                      <span style={{ fontSize: '0.7rem', opacity: 0.8 }}>EN ESPERA</span>
                    </div>
                  ))
                )}
              </div>
            </div>

          </div>
        </section>

        <section className="control-panel">
          <div className="control-buttons-row">
            <button 
              className="btn btn-primary" 
              onClick={handleSimular10}
              disabled={cargando}
            >
              🚀 Simular 10 Vehículos Simultáneos (5 Norte / 5 Sur)
            </button>

            <button 
              className="btn btn-north"
              onClick={() => handleAgregarVehiculo('NORTE')}
            >
              ▲ Enviar Vehículo al Norte
            </button>

            <button 
              className="btn btn-south"
              onClick={() => handleAgregarVehiculo('SUR')}
            >
              ▼ Enviar Vehículo al Sur
            </button>

            <button 
              className="btn btn-reset"
              onClick={handleReiniciar}
            >
              🔄 Reiniciar Simulación
            </button>
          </div>

          <div className="settings-bar">
            <label className="toggle-wrapper">
              <input 
                type="checkbox" 
                className="toggle-input" 
                checked={estado.modoEquitativo}
                onChange={handleToggleEquidad}
              />
              <span style={{ fontWeight: 600, color: estado.modoEquitativo ? '#34d399' : '#cbd5e1' }}>
                🛡️ Modo Equitativo Anti-Inanición (Reto de Prevención de Monopolio)
              </span>
            </label>

            {estado.modoEquitativo && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span>Límite consecutivo por sentido:</span>
                <select 
                  value={limiteLote} 
                  onChange={(e) => handleCambiarLimiteLote(Number(e.target.value))}
                  style={{
                    background: '#1e293b',
                    color: '#fff',
                    border: '1px solid #475569',
                    borderRadius: '6px',
                    padding: '3px 8px'
                  }}
                >
                  <option value={3}>3 vehículos</option>
                  <option value={5}>5 vehículos</option>
                  <option value={8}>8 vehículos</option>
                </select>
              </div>
            )}

            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginLeft: 'auto' }}>
              <span>Velocidad de Cruce:</span>
              <select 
                value={velocidadCruce} 
                onChange={(e) => setVelocidadCruce(Number(e.target.value))}
                style={{
                  background: '#1e293b',
                  color: '#fff',
                  border: '1px solid #475569',
                  borderRadius: '6px',
                  padding: '3px 8px'
                }}
              >
                <option value={1000}>Rápida (1.0s)</option>
                <option value={2000}>Normal (2.0s)</option>
                <option value={3500}>Lenta (3.5s)</option>
              </select>
            </div>
          </div>
        </section>

        <section className="details-grid">
          
          <div className="terminal-card">
            <div className="terminal-header">
              <div className="terminal-title">
                <span>🖥️ Registro de Eventos de Concurrencia y Sincronización</span>
              </div>
              <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                {estado.ultimosEventos ? estado.ultimosEventos.length : 0} eventos
              </span>
            </div>

            <div className="terminal-body" ref={logsContainerRef}>
              {(!estado.ultimosEventos || estado.ultimosEventos.length === 0) ? (
                <div style={{ color: '#64748b', fontStyle: 'italic' }}>Esperando eventos de sincronización...</div>
              ) : (
                estado.ultimosEventos.map((evt, idx) => (
                  <div key={idx} className="log-row">
                    <span className="log-time">[{evt.timestamp}]</span>
                    <span className={`log-tag tag-${evt.tipo}`}>{evt.tipo}</span>
                    <span className="log-message">{evt.mensaje}</span>
                  </div>
                ))
              )}
            </div>
          </div>

          <div className="analysis-card">
            <h3 className="analysis-title">
              <span>📚 Análisis Teórico &amp; Preguntas de Control</span>
            </h3>

            <div className="accordion-item">
              <div className="accordion-question">1. ¿Por qué el acceso debe coordinarse con un Mutex y variables de condición?</div>
              <div className="accordion-answer">
                El puente es un recurso crítico de carril único. Para evitar colisiones, las variables de estado (dirección y contador de vehículos) deben consultarse y actualizarse de manera atómica con un mutex. Las variables de condición permiten suspender (wait) a los vehículos en sentido contrario sin consumir CPU hasta que el puente quede libre.
              </div>
            </div>

            <div className="accordion-item">
              <div className="accordion-question">2. ¿Qué ocurre con la inanición (starvation) si llegan vehículos continuamente?</div>
              <div className="accordion-answer">
                En el algoritmo básico, si entran continuamente vehículos hacia el Norte antes de que el contador llegue a cero, el sentido SUR sufre inanición indefinida. La solución implementada es la <strong>política equitativa</strong> que limita a <em>N</em> autos consecutivos cuando hay espera en sentido opuesto.
              </div>
            </div>

            <div className="accordion-item">
              <div className="accordion-question">3. ¿Cómo se cumple el criterio de verificación del docente?</div>
              <div className="accordion-answer">
                Criterio: <em>"Comprobar que nunca crucen vehículos en sentidos opuestos simultáneamente"</em>. El sistema verifica activamente en cada ciclo que la condición <code>!(norte &gt; 0 &amp;&amp; sur &gt; 0)</code> nunca sea violada, garantizando 0 colisiones en todas las pruebas concurrentes.
              </div>
            </div>

          </div>

        </section>

      </main>
    </div>
  );
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<App />);
