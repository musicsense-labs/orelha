# Sobe o backend com o JDK 21 (o JAVA_HOME do sistema aponta para o 1.8).
# Uso: .\run.ps1                 (porta 8080)
#      .\run.ps1 -Port 8081      (quando o 8080 está ocupado — ex.: WildFly do IntelliJ)
# Segredos: um .env na raiz do repositório (ignorado pelo git), uma linha CHAVE=valor por variável,
# vira variável de ambiente do processo antes do Maven subir. Ex.: ORELHA_HOOKTHEORY_ACTIVKEY=…
param([int]$Port = 8080)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location $PSScriptRoot

$dotenv = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
if (Test-Path $dotenv) {
    foreach ($line in Get-Content $dotenv) {
        $trimmed = $line.Trim()
        if ($trimmed -eq "" -or $trimmed.StartsWith("#")) { continue }
        $eq = $trimmed.IndexOf("=")
        if ($eq -lt 1) { continue }
        $name = $trimmed.Substring(0, $eq).Trim()
        $value = $trimmed.Substring($eq + 1).Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
    Write-Host "carregado .env ($((Get-Content $dotenv | Where-Object { $_ -match '^\s*[^#\s][^=]*=' }).Count) variáveis)"
}

mvn -B -ntp spring-boot:run "-Dspring-boot.run.arguments=--server.port=$Port"
