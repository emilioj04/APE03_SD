import React, { useState, useEffect, useRef } from 'react';

export default function App() {
  const CAPACITY = 4;
  const [semaphore, setSemaphore] = useState(4);
  const [insideCount, setInsideCount] = useState(0);
  const [processedCount, setProcessedCount] = useState(0);
  const [baySlots, setBaySlots] = useState([null, null, null, null]);
  const [waitQueue, setWaitQueue] = useState([]);
  const [isSimulating, setIsSimulating] = useState(false);

  const carSeq = useRef(1);

  // Referencias mutables sincronizadas para evitar problemas de cierres (closures)
  const baySlotsRef = useRef(baySlots);
  baySlotsRef.current = baySlots;

  const waitQueueRef = useRef(waitQueue);
  waitQueueRef.current = waitQueue;

  const fetchBackendState = async () => {
    try {
      const res = await fetch('http://localhost:8080/api/estacionamiento/estado');
      if (res.ok) {
        const data = await res.json();
        setSemaphore(data.espaciosDisponibles);
      }
    } catch (e) {
      console.log('Backend offline');
    }
  };

  useEffect(() => {
    fetchBackendState();
  }, []);

  // Función principal: procesa la llegada y recalcula el semáforo basado en bahías libres reales
  const processCarArrival = (carObj) => {
    const freeIdx = baySlotsRef.current.findIndex((b) => b === null);

    // Si las 4 bahías locales están llenas, entra a la cola de espera visual (wait)
    if (freeIdx === -1) {
      setWaitQueue((prevQueue) => [...prevQueue, carObj]);
      setSemaphore(0);
      return;
    }

    // Ocupamos la bahía visualmente de inmediato
    const updatedBays = [...baySlotsRef.current];
    updatedBays[freeIdx] = carObj;
    setBaySlots(updatedBays);

    // Sincronizar métricas y semáforo basado en la cantidad real de espacios libres
    const libres = updatedBays.filter((b) => b === null).length;
    setSemaphore(libres);
    setInsideCount((prev) => Math.min(CAPACITY, prev + 1));

    // LLAMADA ASÍNCRONA EN SEGUNDO PLANO AL BACKEND
    fetch(`http://localhost:8080/api/estacionamiento/ingresar/${carObj.id}?tiempoMs=4000`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      }
    })
      .then((res) => {
        if (!res.ok) console.error("Error en respuesta del backend Java");
      })
      .catch((err) => console.error("Error al conectar con el backend:", err));

    // Temporizador para liberar la bahía (signal) tras 4.5 segundos
    setTimeout(() => {
      releaseBaySlot(freeIdx);
    }, 4500);
  };

  // Función para liberar una bahía (signal / post) recalculando el estado exacto
  const releaseBaySlot = (index) => {
    const updatedBays = [...baySlotsRef.current];
    updatedBays[index] = null;
    setBaySlots(updatedBays);

    setInsideCount((prev) => Math.max(0, prev - 1));
    setProcessedCount((prev) => prev + 1);

    // Si hay algún vehículo esperando en la cola de espera (FIFO)
    if (waitQueueRef.current.length > 0) {
      const nextCar = waitQueueRef.current[0];
      setWaitQueue((prevQueue) => prevQueue.slice(1));

      // El siguiente vehículo en cola intenta ingresar al espacio liberado
      setTimeout(() => {
        processCarArrival(nextCar);
      }, 300);
    } else {
      // Recalcular el semáforo exactamente según cuántas bahías están en null
      const libres = updatedBays.filter((b) => b === null).length;
      setSemaphore(libres);
    }
  };

  // Ráfaga de 10 vehículos
  const simulateBurst10 = () => {
    if (isSimulating) return;
    setIsSimulating(true);

    let count = 0;
    const interval = setInterval(() => {
      const id = carSeq.current++;
      const carObj = { id, name: `Auto #${id}` };

      processCarArrival(carObj);

      count++;
      if (count >= 10) {
        clearInterval(interval);
        setTimeout(() => setIsSimulating(false), 2000);
      }
    }, 120); 
  };

  const resetAll = () => {
    setBaySlots([null, null, null, null]);
    setWaitQueue([]);
    setSemaphore(4);
    setInsideCount(0);
    setProcessedCount(0);
    carSeq.current = 1;
    setIsSimulating(false);
  };

  return (
    <div className="bg-[#0B0F17] font-sans text-slate-100 min-h-screen p-4 md:p-8 pb-12 selection:bg-cyan-500 selection:text-black">
      {/* HEADER */}
      <header className="fixed top-0 inset-x-0 z-50 bg-[#0B0F17]/90 backdrop-blur-xl border-b border-slate-800/80 px-6 py-3">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center shadow-lg shadow-cyan-500/20">
              <span className="material-symbols-outlined text-black font-bold">traffic</span>
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping"></span>
                <span className="font-mono text-[10px] tracking-widest text-emerald-400 uppercase font-bold">ISA</span>
              </div>
              <h1 className="font-bold text-sm text-white uppercase tracking-tight">Simulador de Estacionamiento</h1>
            </div>
          </div>
          <span className="font-mono text-[11px] text-cyan-400 bg-slate-900 border border-slate-800 px-2.5 py-1 rounded-md font-semibold">
            S = {semaphore}
          </span>
        </div>
      </header>

      {/* CONTENEDOR ANCHO DISTRIBUIDO EN 2 COLUMNAS */}
      <main className="max-w-6xl mx-auto pt-20">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* COLUMNA IZQUIERDA: MÉTRICAS, SEMÁFORO Y CONTROLES */}
          <div className="lg:col-span-5 flex flex-col gap-4">
            
            {/* MÉTRICAS */}
            <section className="grid grid-cols-3 gap-2">
              <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800 flex flex-col justify-between">
                <span className="font-mono text-[9px] uppercase text-slate-400">Dentro Parqueadero</span>
                <div className="flex items-baseline gap-1 mt-1">
                  <span className="text-2xl font-bold text-cyan-400">{insideCount}</span>
                  <span className="font-mono text-xs text-slate-500">/ 4</span>
                </div>
              </div>
              <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800 flex flex-col justify-between">
                <span className="font-mono text-[9px] uppercase text-amber-500">Bloqueados (wait)</span>
                <div className="flex items-baseline gap-1 mt-1">
                  <span className="text-2xl font-bold text-amber-500">{waitQueue.length}</span>
                  <span className="font-mono text-[10px] text-slate-500">hilos</span>
                </div>
              </div>
              <div className="p-3 rounded-xl bg-slate-900/80 border border-slate-800 flex flex-col justify-between">
                <span className="font-mono text-[9px] uppercase text-emerald-400">Procesados OK</span>
                <div className="flex items-baseline gap-1 mt-1">
                  <span className="text-2xl font-bold text-emerald-400">{processedCount}</span>
                  <span className="font-mono text-[10px] text-slate-500">total</span>
                </div>
              </div>
            </section>

            {/* WIDGET SEMÁFORO CONTADOR */}
            <section className="p-5 rounded-2xl bg-gradient-to-b from-slate-900 to-slate-950 border border-slate-800 shadow-2xl flex items-center justify-between">
              <div>
                <span className="font-mono text-[11px] text-slate-400 tracking-wider">ESPACIOS DISPONIBLES</span>
                <div className="flex items-baseline gap-2 mt-1">
                  <span className={`text-5xl font-black ${semaphore === 0 ? 'text-red-500 drop-shadow-[0_0_15px_rgba(239,68,68,0.8)]' : 'text-emerald-400 drop-shadow-[0_0_15px_rgba(16,185,129,0.8)]'}`}>
                    {semaphore}
                  </span>
                  <span className="text-2xl font-bold text-slate-600">/ 4</span>
                </div>
                <span className="font-mono text-[10px] text-slate-400 mt-2 block">
                </span>
              </div>

              <div className="flex flex-col items-center p-2.5 rounded-2xl bg-black border-2 border-slate-800 shadow-xl shrink-0">
                <div className={`w-11 h-11 rounded-full flex items-center justify-center font-bold text-[9px] transition-all duration-300 ${semaphore === 0 ? 'bg-red-600 text-white shadow-[0_0_20px_#ef4444]' : 'bg-slate-900 text-slate-700'}`}>
                  STOP
                </div>
                <div className={`w-11 h-11 rounded-full mt-2 flex items-center justify-center font-bold text-[9px] transition-all duration-300 ${semaphore > 0 ? 'bg-emerald-500 text-black shadow-[0_0_20px_#10b981]' : 'bg-slate-900 text-slate-700'}`}>
                  GO
                </div>
              </div>
            </section>

            {/* CONTROLES */}
            <section className="flex flex-col gap-2 pt-1">
              <button
                onClick={simulateBurst10}
                disabled={isSimulating}
                className="w-full py-3.5 px-4 rounded-xl bg-gradient-to-r from-cyan-500 via-blue-500 to-emerald-500 text-black font-extrabold text-sm uppercase tracking-wide shadow-lg shadow-cyan-500/20 active:scale-95 transition-all hover:brightness-110 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                <span></span>
                <span>SIMULAR 10 VEHÍCULOS SIMULTÁNEOS</span>
              </button>

              <button
                onClick={resetAll}
                className="w-full py-2 bg-slate-800 hover:bg-slate-700 text-red-400 font-mono text-xs rounded-lg border border-slate-700 transition-all active:scale-95 flex items-center justify-center gap-1"
              >
                <span></span>
                <span>Reiniciar Simulación</span>
              </button>
            </section>

          </div>

          {/* COLUMNA DERECHA: BAHÍAS Y CARRIL DE COLA */}
          <div className="lg:col-span-7 flex flex-col gap-4">
            
            {/* BAHÍAS */}
            <section className="flex flex-col gap-2 p-4 rounded-2xl bg-slate-900/50 border border-slate-800">
              <div className="flex items-center justify-between">
                <h2 className="font-mono text-xs font-bold uppercase tracking-wider text-slate-300">PARQUADERO</h2>
                <span className="font-mono text-[10px] text-slate-500">Capacidad Max: 4</span>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-1">
                {baySlots.map((slot, idx) => (
                  <div
                    key={idx}
                    className={`min-h-[140px] p-3.5 rounded-xl border flex flex-col justify-between transition-all duration-300 ${
                      slot ? 'border-red-500 bg-red-950/30 shadow-lg shadow-red-950/50' : 'border-dashed border-yellow-500/80 bg-slate-900/80'
                    }`}
                  >
                    <div className="flex justify-between items-center">
                      <span className="font-mono text-[11px] font-bold text-yellow-500">0{idx + 1}</span>
                      <span className={`font-mono text-[9px] px-2 py-0.5 rounded font-semibold ${slot ? 'bg-red-500/30 text-red-300 border border-red-500/40' : 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'}`}>
                        {slot ? 'OCUPADO' : 'LIBRE'}
                      </span>
                    </div>

                    <div className="flex flex-col items-center justify-center py-3">
                      {slot ? (
                        <>
                          <span className="text-3xl filter drop-shadow-[0_0_8px_rgba(239,68,68,0.8)]">🚗</span>
                          <span className="font-mono text-xs font-extrabold text-white mt-1">{slot.name}</span>
                        </>
                      ) : (
                        <div className="w-10 h-10 rounded-full border border-emerald-500/40 bg-slate-800/80 flex items-center justify-center shadow-md">
                          <span className="font-bold text-xl text-emerald-400">P</span>
                        </div>
                      )}
                    </div>

                    <div className="text-[10px] font-mono text-slate-500 border-t border-slate-800 pt-1 flex justify-between">
                      <span>{slot ? 'En Uso' : 'sem_wait(S)'}</span>
                      <span className={slot ? 'text-red-400' : 'text-emerald-400'}>{slot ? 'OCUPADO' : 'DISPONIBLE'}</span>
                    </div>
                  </div>
                ))}
              </div>
            </section>

            {/* CARRIL DE COLA DE ESPERA */}
            <section className="p-4 rounded-2xl bg-slate-900/90 border border-slate-800 shadow-xl">
              <div className="flex justify-between items-center mb-2">
                <span className="font-mono text-[10px] font-bold text-slate-300 uppercase">
                  Carril de Cola // Hilos Bloqueados en sem_wait()
                </span>
                <span className="font-mono text-[10px] bg-slate-800 px-2 py-0.5 rounded text-amber-400 font-semibold">
                  En Cola: {waitQueue.length}
                </span>
              </div>

              <div className="w-full min-h-[68px] p-2 rounded-xl bg-slate-950 border border-dashed border-slate-800 flex items-center gap-2 overflow-x-auto">
                {waitQueue.length === 0 ? (
                  <span className="font-mono text-[11px] text-slate-500 mx-auto flex items-center gap-1.5">
                    <span className="material-symbols-outlined text-sm text-emerald-500">verified</span>
                    Sin contención en cola. Acceso inmediato.
                  </span>
                ) : (
                  waitQueue.map((item, idx) => (
                    <div key={item.id} className="flex items-center gap-2 p-2 rounded-lg bg-slate-900 border border-amber-500/50 shadow-md shrink-0">
                      <span className="text-base">🚗</span>
                      <div className="flex flex-col">
                        <div className="flex items-center gap-1">
                          <span className="font-mono text-[11px] font-bold text-amber-400">{item.name}</span>
                          <span className="font-mono text-[9px] text-slate-500">#{idx + 1}</span>
                        </div>
                        <span className="font-mono text-[8px] text-amber-300 font-semibold">⏳ BLOQUEADO</span>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </section>

          </div>

        </div>
      </main>
    </div>
  );
}