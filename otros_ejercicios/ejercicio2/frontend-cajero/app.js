document.getElementById('btn-simular').addEventListener('click', async () => {
    const btn = document.getElementById('btn-simular');
    const logList = document.getElementById('log-list');
    const saldoFinalSpan = document.getElementById('saldo-final');

    btn.disabled = true;
    btn.textContent = "Ejecutando...";
    logList.innerHTML = "";
    saldoFinalSpan.textContent = "Calculando...";

    try {
        // Hacemos la petición al backend en Spring Boot
        const response = await fetch('http://localhost:8080/api/cajero/simular');
        const data = await response.json();

        // Mostramos los logs paso a paso
        data.logs.forEach(log => {
            const li = document.createElement('li');
            li.textContent = log;
            logList.appendChild(li);
        });

        // Mostramos el saldo final
        saldoFinalSpan.textContent = data.saldoFinal;

    } catch (error) {
        console.error("Error al conectar con el backend", error);
        logList.innerHTML = "<li>Error de conexión. Asegúrate de que el backend (Spring Boot) esté corriendo en el puerto 8080.</li>";
    } finally {
        btn.disabled = false;
        btn.textContent = "Ejecutar Simulación";
    }
});