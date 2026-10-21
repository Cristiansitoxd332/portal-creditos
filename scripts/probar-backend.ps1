<#
  PRUEBA AUTOMATICA DE LA API - Portal de Consulta de Creditos (prototipo, datos ficticios)

  Antes de ejecutar:
    1. Reinicie los datos de prueba (CMD, en la carpeta del proyecto):
         mysql -u portal_user -p portal_creditos < database\02_datos_ficticios.sql
    2. Arranque el backend:  cd backend  y  gradlew.bat bootRun

  Ejecutar (desde la carpeta del proyecto):
    powershell -ExecutionPolicy Bypass -File scripts\probar-backend.ps1

  Los PDF y plantillas descargados quedan en la carpeta scripts\salida
#>

$ErrorActionPreference = "Stop"
$api = "http://localhost:8080/api"
$raiz = Split-Path -Parent $PSScriptRoot
$salida = Join-Path $PSScriptRoot "salida"
New-Item -ItemType Directory -Force -Path $salida | Out-Null
$script:ok = 0
$script:fallas = 0

function Exito($nombre, $detalle = "") {
    $script:ok++
    Write-Host ("[OK]    " + $nombre) -ForegroundColor Green -NoNewline
    if ($detalle) { Write-Host ("  -> " + $detalle) -ForegroundColor DarkGray } else { Write-Host "" }
}
function Falla($nombre, $detalle) {
    $script:fallas++
    Write-Host ("[FALLA] " + $nombre + "  -> " + $detalle) -ForegroundColor Red
}
function Seccion($titulo) { Write-Host ""; Write-Host ("===== " + $titulo + " =====") -ForegroundColor Cyan }

function Login($usuario, $clave) {
    $body = @{ username = $usuario; password = $clave } | ConvertTo-Json
    $r = Invoke-RestMethod -Method Post -Uri "$api/auth/login" -ContentType "application/json" -Body $body
    return @{ Authorization = "Bearer " + $r.token }
}
function ApiGet($ruta, $h) { Invoke-RestMethod -Uri "$api$ruta" -Headers $h }
function ApiSend($metodo, $ruta, $h, $obj) {
    $json = if ($null -ne $obj) { $obj | ConvertTo-Json -Depth 5 } else { "{}" }
    Invoke-RestMethod -Method $metodo -Uri "$api$ruta" -Headers $h -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
}
function CodigoError($bloque) {
    try { & $bloque | Out-Null; return 200 } catch { return [int]$_.Exception.Response.StatusCode }
}
function Descargar($ruta, $h, $archivo) {
    $destino = Join-Path $salida $archivo
    Invoke-WebRequest -Uri "$api$ruta" -Headers $h -OutFile $destino -UseBasicParsing
    return (Get-Item $destino).Length
}
function Subir($ruta, $h, $archivo) {
    $json = & curl.exe -s -H ("Authorization: " + $h.Authorization) -F ("archivo=@" + $archivo) "$api$ruta"
    return ($json | ConvertFrom-Json)
}

# ---------------------------------------------------------------------------
Seccion "1. Disponibilidad y documentacion"
try { $s = Invoke-RestMethod "$api/salud"; if ($s.estado -eq "OK") { Exito "API en linea y conectada a MySQL" $s.versionMySQL } else { Falla "Salud" $s.detalle } } catch { Falla "API en linea" "El backend no responde. Arranquelo con gradlew.bat bootRun"; exit 1 }
try { $d = Invoke-RestMethod "http://localhost:8080/v3/api-docs"; Exito "Documentacion OpenAPI (Swagger)" ("" + @($d.paths.PSObject.Properties).Count + " rutas documentadas") } catch { Falla "Swagger" $_.Exception.Message }

Seccion "2. Seguridad"
$cAndres = Login "1075200202" "Cliente2026*"; Exito "Login cliente (Andres, en mora)"
$cLaura  = Login "1075200101" "Cliente2026*"; Exito "Login cliente (Laura, al dia)"
$cDiana  = Login "1075200303" "Cliente2026*"; Exito "Login cliente (Diana, credito cancelado)"
$adm     = Login "admin" "Admin2026*";        Exito "Login administrador"
$c = CodigoError { Login "1075200101" "ClaveMala1*" };          if ($c -eq 401) { Exito "Contrasena incorrecta rechazada" "401" } else { Falla "Contrasena incorrecta" $c }
$c = CodigoError { ApiGet "/cliente/creditos" @{} };               if ($c -eq 401) { Exito "Sin token no hay acceso" "401" } else { Falla "Sin token" $c }
$c = CodigoError { ApiGet "/admin/tablero" $cLaura };              if ($c -eq 403) { Exito "Cliente no entra al panel admin" "403" } else { Falla "Rol cliente en admin" $c }
$c = CodigoError { ApiGet "/cliente/creditos/1" $cAndres };        if ($c -eq 404) { Exito "Cliente no ve creditos ajenos (IDOR)" "404" } else { Falla "IDOR" $c }

Seccion "3. Informacion publica"
$canales = Invoke-RestMethod "$api/publico/canales";                 Exito "Canales de contacto" ("" + @($canales).Count + " canales")
$req = Invoke-RestMethod "$api/publico/requisitos-levantamiento";    Exito "Requisitos de levantamiento" ("" + @($req).Count + " requisitos")
$v = Invoke-RestMethod "$api/publico/paz-y-salvo/PYS-2026-7F3A9C21"
if ($v.valido) { Exito "Verificacion publica de paz y salvo" ($v.titular + " / " + $v.documento) } else { Falla "Verificacion paz y salvo" "no valido" }
$v = Invoke-RestMethod "$api/publico/paz-y-salvo/PYS-FALSO-000"
if (-not $v.valido) { Exito "Codigo falso detectado como invalido" } else { Falla "Codigo falso" "lo acepto" }

Seccion "4. Creditos y cuotas (cliente)"
$m = (ApiGet "/cliente/creditos" $cAndres)[0]
if ($m.semaforo -eq "EN_MORA" -and $m.cuotasVencidas -eq 3) { Exito "Andres en mora" ("3 cuotas vencidas, " + $m.diasMora + " dias") } else { Falla "Credito en mora" ($m.semaforo + " / " + $m.cuotasVencidas) }
$l = (ApiGet "/cliente/creditos" $cLaura)[0]
if ($l.semaforo -eq "AL_DIA") { Exito "Laura al dia" ("" + $l.porcentajePagado + " % pagado") } else { Falla "Credito al dia" $l.semaforo }
$plan = ApiGet "/cliente/creditos/2/cuotas" $cAndres
Exito "Plan de pagos" ("" + $plan.totalCuotas + " cuotas, " + $plan.cuotasVencidas + " vencidas")

Seccion "5. Pagos simulados"
$op = ApiGet "/cliente/creditos/2/pagos/opciones" $cAndres; Exito "Opciones de pago" ("vencido $" + $op.valorVencido)
$r = ApiSend Post "/cliente/creditos/2/pagos" $cAndres @{ valor = 2100403; medioPago = "TARJETA_SIMULADA"; simularRechazo = $true }
if ($r.estado -eq "RECHAZADO") { Exito "Pago rechazado (simulado)" $r.referencia } else { Falla "Pago rechazado" $r.estado }
$r = ApiSend Post "/cliente/creditos/2/pagos" $cAndres @{ valor = $op.valorVencido; medioPago = "PSE_SIMULADO" }
if ($r.estado -eq "APROBADO") { Exito "Pago aprobado de lo vencido" ($r.referencia + " " + $r.codigoAutorizacion) } else { Falla "Pago aprobado" $r.estado }
$m = (ApiGet "/cliente/creditos" $cAndres)[0]
if ($m.semaforo -eq "AL_DIA") { Exito "El credito paso de EN_MORA a AL_DIA" } else { Falla "Estado despues del pago" $m.semaforo }
$c = CodigoError { ApiSend Post "/cliente/creditos/2/pagos" $cAndres @{ valor = 999999999; medioPago = "PSE_SIMULADO" } }
if ($c -eq 400) { Exito "Pago mayor al saldo rechazado" "400" } else { Falla "Pago excesivo" $c }
$c = CodigoError { ApiSend Post "/cliente/creditos/2/pagos" $cAndres @{ valor = 50000; medioPago = "EFECTIVO" } }
if ($c -eq 400) { Exito "Cliente no puede usar medio EFECTIVO" "400" } else { Falla "Medio no permitido" $c }
$tam = Descargar ("/cliente/creditos/2/pagos/" + $r.pagoId + "/pdf") $cAndres "comprobante-pago.pdf"
Exito "Comprobante de pago en PDF" ("" + $tam + " bytes")
$h = ApiGet "/cliente/creditos/2/pagos" $cAndres; Exito "Historial de pagos" ("" + @($h).Count + " pagos")

Seccion "6. Simulador de abono a capital"
$s = ApiSend Post "/cliente/creditos/1/simulador-abono" $cLaura @{ valorAbono = 5000000 }
Exito "Reducir plazo" ("ahorra $" + $s.reducirPlazo.ahorroIntereses + " y " + $s.reducirPlazo.cuotasMenos + " cuotas menos")
Exito "Reducir cuota" ("nueva cuota $" + $s.reducirCuota.valorCuota + ", ahorra $" + $s.reducirCuota.ahorroIntereses)

Seccion "7. Paz y salvo"
$e = ApiGet "/cliente/creditos/1/paz-y-salvo/estado" $cLaura
if (-not $e.disponible) { Exito "Laura (con saldo) no puede descargar paz y salvo" } else { Falla "Paz y salvo con saldo" "disponible" }
$c = CodigoError { Descargar "/cliente/creditos/1/paz-y-salvo" $cLaura "no-debe-existir.pdf" }
if ($c -eq 409) { Exito "Descarga bloqueada con saldo pendiente" "409" } else { Falla "Bloqueo paz y salvo" $c }
$tam = Descargar "/cliente/creditos/3/paz-y-salvo" $cDiana "paz-y-salvo-diana.pdf"
Exito "Paz y salvo con QR en PDF (credito cancelado)" ("" + $tam + " bytes")

Seccion "8. Levantamiento de prenda"
$lv = ApiGet "/cliente/creditos/3/levantamiento" $cDiana
Exito "Seguimiento de solicitud de Diana" ($lv.estadoPrenda + " / " + $lv.solicitudes[0].estado)
$c = CodigoError { ApiSend Post "/cliente/creditos/3/levantamiento" $cDiana @{ observacion = "duplicada" } }
if ($c -eq 409) { Exito "No permite dos solicitudes activas" "409" } else { Falla "Solicitud duplicada" $c }
$c = CodigoError { ApiSend Post "/cliente/creditos/1/levantamiento" $cLaura @{ observacion = "x" } }
if ($c -eq 409) { Exito "No permite solicitar con saldo pendiente" "409" } else { Falla "Solicitud con saldo" $c }

Seccion "9. Panel administrativo"
$t = ApiGet "/admin/tablero" $adm
Exito "Tablero" ("cartera $" + $t.carteraTotal + ", mora " + $t.indiceMora + " %, " + $t.solicitudesPendientes + " solicitudes pendientes")
$sol = (ApiGet "/admin/solicitudes?estado=EN_REVISION" $adm)[0]
$r = ApiSend Patch ("/admin/solicitudes/" + $sol.id) $adm @{ estado = "APROBADA"; respuesta = "Documentos verificados (prueba)" }
if ($r.estado -eq "APROBADA") { Exito "Admin aprueba levantamiento" $r.numeroRadicado } else { Falla "Aprobar solicitud" $r.estado }
$lv = ApiGet "/cliente/creditos/3/levantamiento" $cDiana
if ($lv.estadoPrenda -eq "LEVANTADA") { Exito "Diana ve su prenda LEVANTADA" } else { Falla "Prenda levantada" $lv.estadoPrenda }

$doc = "88" + (Get-Random -Minimum 10000000 -Maximum 99999999)
$cred = ApiSend Post "/admin/clientes" $adm @{ tipoDocumento = "CC"; numeroDocumento = $doc; nombres = "Cliente"; apellidos = "De Prueba"; correo = "prueba@correo-ficticio.com"; telefono = "3001234567"; ciudad = "Neiva" }
Exito "Crear cliente con clave temporal" ($cred.usuario + " / " + $cred.passwordTemporal)
$placa = "ZZ" + [char](65 + (Get-Random -Maximum 26)) + (Get-Random -Minimum 100 -Maximum 999)
$nc = ApiSend Post "/admin/creditos" $adm @{ clienteId = $cred.clienteId; placa = $placa; marca = "Chevrolet"; linea = "Tracker"; modelo = 2026; color = "Gris"; clase = "CAMIONETA"; valorVehiculo = 110000000; cuotaInicial = 30000000; tasaInteresMensual = 1.5; plazoMeses = 60; fechaDesembolso = "2026-10-01" }
Exito "Crear credito con plan automatico" ($nc.resumen.numeroCredito + ", cuota $" + $nc.resumen.valorCuota)
$nuevo = Login $doc $cred.passwordTemporal
$p = Invoke-RestMethod -Uri "$api/auth/perfil" -Headers $nuevo
if ($p.debeCambiarPassword) { Exito "El cliente nuevo debe cambiar su clave al ingresar" } else { Falla "Cambio de clave obligatorio" "no" }
$c = CodigoError { ApiSend Post "/admin/canales" $adm @{ tipo = "SITIO_WEB"; nombre = "Malicioso"; valor = "x"; enlace = "javascript:alert(1)" } }
if ($c -eq 400) { Exito "Rechaza enlaces inseguros (javascript:)" "400" } else { Falla "Enlace inseguro" $c }
$a = ApiGet "/admin/auditoria?cantidad=5" $adm; Exito "Bitacora de auditoria" ($a[0].accion + " por " + $a[0].usuario)

Seccion "10. Importacion de archivos"
$tam = Descargar "/admin/importaciones/plantillas/clientes" $adm "plantilla-clientes.xlsx"; Exito "Plantilla Excel de clientes" ("" + $tam + " bytes")
$dir = Join-Path $raiz "plantillas-importacion"
$v = Subir "/admin/importaciones/clientes/vista-previa" $adm (Join-Path $dir "clientes_ejemplo.csv")
Exito "Vista previa clientes (no guarda)" ("" + $v.filasValidas + " validas, " + $v.filasConError + " con error")
$v = Subir "/admin/importaciones/clientes" $adm (Join-Path $dir "clientes_ejemplo.csv")
Exito "Importar clientes" ("" + $v.filasValidas + " guardados, estado " + $v.estado)
$v = Subir "/admin/importaciones/creditos" $adm (Join-Path $dir "creditos_ejemplo.csv")
Exito "Importar creditos" ("" + $v.filasValidas + " guardados, " + $v.filasConError + " con error")
$v = Subir "/admin/importaciones/pagos" $adm (Join-Path $dir "pagos_ejemplo.csv")
Exito "Importar pagos" ("" + $v.filasValidas + " aplicados, " + $v.filasConError + " con error")
foreach ($e in $v.errores) { Write-Host ("          fila " + $e.fila + ": " + $e.mensaje) -ForegroundColor DarkYellow }
$hist = ApiGet "/admin/importaciones" $adm; Exito "Historial de importaciones" ("" + @($hist).Count + " cargas")

# ---------------------------------------------------------------------------
Write-Host ""
$color = if ($script:fallas -eq 0) { "Green" } else { "Red" }
Write-Host ("RESULTADO: " + $script:ok + " pruebas exitosas, " + $script:fallas + " fallidas") -ForegroundColor $color
Write-Host ("Archivos descargados en: " + $salida)
