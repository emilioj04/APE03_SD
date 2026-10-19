document.getElementById('btn-simular').addEventListener('click', async () => {
    const btn = document.getElementById('btn-simular');
    const logList = document.getElementById('log-list');
    const mensajeFinal = document.getElementById('mensaje-final');

    btn.disabled = true;
    logList.innerHTML = "<li>Sincronizando procesos (Diseño, Revisión, Impresión)...</li>";
    mensajeFinal.textContent = "";

    try {
        const response = await fetch('http://localhost:8081/api/impresion/simular');
        const data = await response.json();

        logList.innerHTML = "";
        data.logs.forEach(log => {
            const li = document.createElement('li');
            li.textContent = log;
            logList.appendChild(li);
        });
        
        mensajeFinal.textContent = "Proceso de 3 documentos completado correctamente.";
    } catch (error) {
        logList.innerHTML = "<li class='error'>Error de conexión con el backend. Verifique el contenedor.</li>";
    } finally {
        btn.disabled = false;
    }
});