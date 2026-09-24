# Sobe o Orelha inteiro no logon: Docker Desktop (Postgres + extrator via compose) e o backend na 8081,
# que também serve o Angular compilado. Chamado pela tarefa agendada "Orelha" (deploy/install-task.ps1).
# Log em deploy/logs/start-orelha.log. O túnel do Cloudflare é um serviço do Windows à parte.
param([int]$Port = 8081)

$root = Split-Path $PSScriptRoot -Parent
$logDir = Join-Path $PSScriptRoot "logs"
New-Item -ItemType Directory -Force $logDir | Out-Null
$log = Join-Path $logDir "start-orelha.log"
function Log($msg) { "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $msg" | Tee-Object -FilePath $log -Append }

Log "== início (porta $Port)"

# 1. Docker Desktop: inicia se não estiver rodando e espera o daemon responder (até 5 min).
$dockerExe = Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\Docker Desktop.exe"
if (-not (Test-Path $dockerExe)) { $dockerExe = "C:\Program Files\Docker\Docker\Docker Desktop.exe" }
if (-not (Get-Process "Docker Desktop" -ErrorAction SilentlyContinue)) {
    if (Test-Path $dockerExe) { Log "iniciando Docker Desktop"; Start-Process $dockerExe | Out-Null }
    else { Log "Docker Desktop não encontrado em $dockerExe" }
}
$deadline = (Get-Date).AddMinutes(5)
$up = $false
while ((Get-Date) -lt $deadline) {
    docker info 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $up = $true; break }
    Start-Sleep -Seconds 5
}
if ($up) {
    Log "docker pronto; compose up"
    Set-Location $root
    docker compose up -d 2>&1 | ForEach-Object { Log "  $_" }
} else {
    Log "docker não respondeu em 5 min; seguindo só com o backend (a fila e o Postgres podem falhar)"
}

# 2. Um backend antigo ainda na porta faz o novo morrer com "Port already in use" — e como a tarefa
# bloqueia no run.ps1, Stop-ScheduledTask não alcança o java filho. Reiniciar é, então, parar quem está
# escutando. Só processos java: a 8080 desta máquina é de outra coisa e nunca é tocada (o padrão é 8081).
$listening = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique)
foreach ($processId in $listening) {
    $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($proc -and $proc.ProcessName -eq "java") {
        Log "porta $Port ocupada pelo java $processId (backend anterior); encerrando"
        Stop-Process -Id $processId -Force
        Start-Sleep -Seconds 5
    } elseif ($proc) {
        Log "porta $Port ocupada por $($proc.ProcessName) ($processId), que não é nosso: o backend não vai subir"
    }
}

# 3. Backend (bloqueia enquanto roda; run.ps1 carrega o .env e usa o JDK 21).
Log "subindo backend"
& (Join-Path $root "backend\run.ps1") -Port $Port 2>&1 | ForEach-Object { "$_" | Out-File -FilePath (Join-Path $logDir "backend.log") -Append }
Log "backend terminou (código $LASTEXITCODE)"
