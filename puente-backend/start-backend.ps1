param(
    [int]$Port = 8080
)

$listener = New-Object System.Net.HttpListener
$prefix = "http://localhost:$Port/"
$listener.Prefixes.Add($prefix)

try {
    $listener.Start()
    Write-Host "Servidor backend de sincronizacion activo en $prefix (API REST)"
} catch {
    Write-Host "Error al iniciar backend en puerto $Port : $_"
    exit 1
}

$script:direccionActual = "NINGUNA"
$script:vehiculosNorteEnPuente = 0
$script:vehiculosSurEnPuente = 0
$script:vehiculosEsperandoNorte = 0
$script:vehiculosEsperandoSur = 0
$script:totalCruzados = 0
$script:colisionDetectada = $false
$script:modoEquitativo = $true
$script:limiteConsecutivos = 5
$script:consecutivosCruzados = 0
$script:idCounter = 1

$script:colaNorte = [System.Collections.ArrayList]::new()
$script:colaSur = [System.Collections.ArrayList]::new()
$script:enPuente = [System.Collections.ArrayList]::new()
$script:logs = [System.Collections.ArrayList]::new()

function RegistrarEvento($tipo, $mensaje) {
    $item = [PSCustomObject]@{
        timestamp = (Get-Date).ToString("HH:mm:ss.fff")
        tipo = $tipo
        mensaje = $mensaje
    }
    [void]$script:logs.Insert(0, $item)
    if ($script:logs.Count -gt 100) {
        $script:logs.RemoveAt($script:logs.Count - 1)
    }
    Write-Host "[$($item.timestamp)] [$tipo] $mensaje"
}

RegistrarEvento "INICIALIZACION" "Backend REST iniciado. Mutex de puente activo, Direccion: NINGUNA."

$timer = [System.Diagnostics.Stopwatch]::StartNew()
$lastTick = $timer.ElapsedMilliseconds

while ($listener.IsListening) {
    try {
        $asyncResult = $listener.BeginGetContext($null, $null)
        
        while (-not $asyncResult.AsyncWaitHandle.WaitOne(150)) {
            $now = $timer.ElapsedMilliseconds
            
            $terminados = @()
            foreach ($v in $script:enPuente) {
                if ($now -ge $v.tiempoFin) {
                    $terminados += $v
                }
            }

            foreach ($v in $terminados) {
                [void]$script:enPuente.Remove($v)
                $script:totalCruzados++
                if ($v.direccion -eq "NORTE") { $script:vehiculosNorteEnPuente-- }
                if ($v.direccion -eq "SUR") { $script:vehiculosSurEnPuente-- }
                RegistrarEvento "SALIDA" "Vehiculo #$($v.id) SALE del puente ($($v.direccion)). Cruce completado."
            }

            if ($script:enPuente.Count -eq 0) {
                if ($script:direccionActual -eq "NORTE") {
                    if ($script:colaSur.Count -gt 0) {
                        $script:direccionActual = "SUR"
                        $script:consecutivosCruzados = 0
                        RegistrarEvento "CAMBIO_DIRECCION" "Puente libre. Direccion cambia a SUR ($($script:colaSur.Count) esperando)."
                    } elseif ($script:colaNorte.Count -gt 0) {
                        $script:direccionActual = "NORTE"
                        $script:consecutivosCruzados = 0
                    } else {
                        $script:direccionActual = "NINGUNA"
                        $script:consecutivosCruzados = 0
                    }
                } elseif ($script:direccionActual -eq "SUR") {
                    if ($script:colaNorte.Count -gt 0) {
                        $script:direccionActual = "NORTE"
                        $script:consecutivosCruzados = 0
                        RegistrarEvento "CAMBIO_DIRECCION" "Puente libre. Direccion cambia a NORTE ($($script:colaNorte.Count) esperando)."
                    } elseif ($script:colaSur.Count -gt 0) {
                        $script:direccionActual = "SUR"
                        $script:consecutivosCruzados = 0
                    } else {
                        $script:direccionActual = "NINGUNA"
                        $script:consecutivosCruzados = 0
                    }
                } else {
                    if ($script:colaNorte.Count -gt 0) {
                        $script:direccionActual = "NORTE"
                        $script:consecutivosCruzados = 0
                    } elseif ($script:colaSur.Count -gt 0) {
                        $script:direccionActual = "SUR"
                        $script:consecutivosCruzados = 0
                    }
                }
            }

            if ($script:direccionActual -eq "NORTE") {
                $bloquearInanicion = $script:modoEquitativo -and ($script:colaSur.Count -gt 0) -and ($script:consecutivosCruzados -ge $script:limiteConsecutivos)
                if (-not $bloquearInanicion -and $script:colaNorte.Count -gt 0 -and $script:enPuente.Count -lt 4) {
                    $entrando = $script:colaNorte[0]
                    $script:colaNorte.RemoveAt(0)
                    $entrando.tiempoFin = $now + $entrando.tiempoCruceMs
                    [void]$script:enPuente.Add($entrando)
                    $script:vehiculosNorteEnPuente++
                    $script:consecutivosCruzados++
                    RegistrarEvento "ENTRADA" "Vehiculo #$($entrando.id) INGRESA al puente hacia el NORTE. [En puente: $($script:enPuente.Count)]"
                } elseif ($bloquearInanicion -and $script:colaNorte.Count -gt 0 -and $script:enPuente.Count -eq 0) {
                    $script:direccionActual = "SUR"
                    $script:consecutivosCruzados = 0
                    RegistrarEvento "CONTROL_INANICION" "Limite de lote alcanzado ($($script:limiteConsecutivos) autos). Turno cedido a SUR."
                }
            } elseif ($script:direccionActual -eq "SUR") {
                $bloquearInanicion = $script:modoEquitativo -and ($script:colaNorte.Count -gt 0) -and ($script:consecutivosCruzados -ge $script:limiteConsecutivos)
                if (-not $bloquearInanicion -and $script:colaSur.Count -gt 0 -and $script:enPuente.Count -lt 4) {
                    $entrando = $script:colaSur[0]
                    $script:colaSur.RemoveAt(0)
                    $entrando.tiempoFin = $now + $entrando.tiempoCruceMs
                    [void]$script:enPuente.Add($entrando)
                    $script:vehiculosSurEnPuente++
                    $script:consecutivosCruzados++
                    RegistrarEvento "ENTRADA" "Vehiculo #$($entrando.id) INGRESA al puente hacia el SUR. [En puente: $($script:enPuente.Count)]"
                } elseif ($bloquearInanicion -and $script:colaSur.Count -gt 0 -and $script:enPuente.Count -eq 0) {
                    $script:direccionActual = "NORTE"
                    $script:consecutivosCruzados = 0
                    RegistrarEvento "CONTROL_INANICION" "Limite de lote alcanzado ($($script:limiteConsecutivos) autos). Turno cedido a NORTE."
                }
            }

            $script:vehiculosEsperandoNorte = $script:colaNorte.Count
            $script:vehiculosEsperandoSur = $script:colaSur.Count
            $script:colisionDetectada = ($script:vehiculosNorteEnPuente -gt 0 -and $script:vehiculosSurEnPuente -gt 0)
        }

        $context = $listener.EndGetContext($asyncResult)
        $request = $context.Request
        $response = $context.Response

        $response.AddHeader("Access-Control-Allow-Origin", "*")
        $response.AddHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
        $response.AddHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")

        if ($request.HttpMethod -eq "OPTIONS") {
            $response.StatusCode = 200
            $response.Close()
            continue
        }

        $path = $request.Url.LocalPath.ToLower()

        if ($path -eq "/api/puente/estado" -and $request.HttpMethod -eq "GET") {
            $dto = [PSCustomObject]@{
                direccionActual = $script:direccionActual
                vehiculosNorteEnPuente = $script:vehiculosNorteEnPuente
                vehiculosSurEnPuente = $script:vehiculosSurEnPuente
                totalEnPuente = $script:enPuente.Count
                vehiculosEsperandoNorte = $script:vehiculosEsperandoNorte
                vehiculosEsperandoSur = $script:vehiculosEsperandoSur
                totalEsperando = ($script:vehiculosEsperandoNorte + $script:vehiculosEsperandoSur)
                totalCruzados = $script:totalCruzados
                colisionDetectada = $script:colisionDetectada
                modoEquitativo = $script:modoEquitativo
                limiteConsecutivos = $script:limiteConsecutivos
                consecutivosCruzados = $script:consecutivosCruzados
                puenteLibre = ($script:enPuente.Count -eq 0)
                enPuente = $script:enPuente
                colaNorte = $script:colaNorte
                colaSur = $script:colaSur
                ultimosEventos = $script:logs
            }
            $json = $dto | ConvertTo-Json -Depth 5
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($json)
            $response.ContentType = "application/json; charset=utf-8"
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
        }
        elseif ($path -eq "/api/puente/simular" -and $request.HttpMethod -eq "POST") {
            for ($i = 1; $i -le 5; $i++) {
                $idN = "N-" + $script:idCounter++
                [void]$script:colaNorte.Add([PSCustomObject]@{ id = $idN; direccion = "NORTE"; tiempoCruceMs = 2000 })
                RegistrarEvento "LLEGADA" "Vehiculo #$idN llega a la orilla SUR con destino al NORTE."
            }
            for ($j = 1; $j -le 5; $j++) {
                $idS = "S-" + $script:idCounter++
                [void]$script:colaSur.Add([PSCustomObject]@{ id = $idS; direccion = "SUR"; tiempoCruceMs = 2000 })
                RegistrarEvento "LLEGADA" "Vehiculo #$idS llega a la orilla NORTE con destino al SUR."
            }
            $respJson = '{"mensaje":"Simulacion iniciada con exito","cantidadNorte":5,"cantidadSur":5}'
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($respJson)
            $response.ContentType = "application/json"
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
        }
        elseif ($path -eq "/api/puente/vehiculo" -and $request.HttpMethod -eq "POST") {
            $reader = New-Object System.IO.StreamReader($request.InputStream)
            $body = $reader.ReadToEnd()
            $parsed = $body | ConvertFrom-Json -ErrorAction SilentlyContinue

            $dir = if ($parsed.direccion) { $parsed.direccion.ToUpper() } else { "NORTE" }
            $id = if ($parsed.id) { $parsed.id } else { (if ($dir -eq "NORTE") { "N-" } else { "S-" }) + $script:idCounter++ }
            $tiempo = if ($parsed.tiempoCruceMs) { $parsed.tiempoCruceMs } else { 2000 }

            $veh = [PSCustomObject]@{ id = $id; direccion = $dir; tiempoCruceMs = $tiempo }
            if ($dir -eq "NORTE") {
                [void]$script:colaNorte.Add($veh)
            } else {
                [void]$script:colaSur.Add($veh)
            }
            RegistrarEvento "LLEGADA" "Vehiculo #$id encolado hacia el $dir."

            $json = $veh | ConvertTo-Json
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($json)
            $response.ContentType = "application/json"
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
        }
        elseif ($path -eq "/api/puente/reiniciar" -and $request.HttpMethod -eq "POST") {
            $script:direccionActual = "NINGUNA"
            $script:vehiculosNorteEnPuente = 0
            $script:vehiculosSurEnPuente = 0
            $script:vehiculosEsperandoNorte = 0
            $script:vehiculosEsperandoSur = 0
            $script:totalCruzados = 0
            $script:consecutivosCruzados = 0
            $script:colisionDetectada = $false
            $script:idCounter = 1
            $script:colaNorte.Clear()
            $script:colaSur.Clear()
            $script:enPuente.Clear()
            RegistrarEvento "REINICIO" "Puente reiniciado. Contadores en 0, semaforos en reposo."
            
            $respJson = '{"mensaje":"Simulacion y puente reiniciados"}'
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($respJson)
            $response.ContentType = "application/json"
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
        }
        elseif ($path -eq "/api/puente/configurar" -and $request.HttpMethod -eq "POST") {
            $reader = New-Object System.IO.StreamReader($request.InputStream)
            $body = $reader.ReadToEnd()
            $parsed = $body | ConvertFrom-Json -ErrorAction SilentlyContinue

            if ($parsed.modoEquitativo -ne $null) {
                $script:modoEquitativo = [bool]$parsed.modoEquitativo
            }
            if ($parsed.limiteConsecutivos -ne $null) {
                $script:limiteConsecutivos = [int]$parsed.limiteConsecutivos
            }
            RegistrarEvento "CONFIGURACION" "Politica actualizada: Modo Equitativo=$($script:modoEquitativo), Limite=$($script:limiteConsecutivos)"
            
            $respJson = '{"mensaje":"Configuracion actualizada"}'
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($respJson)
            $response.ContentType = "application/json"
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
        }
        else {
            $response.StatusCode = 404
            $buffer = [System.Text.Encoding]::UTF8.GetBytes("Not Found")
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
        }
        $response.Close()
    } catch {
    }
}
