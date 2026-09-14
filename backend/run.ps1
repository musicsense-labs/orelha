# Sobe o backend com o JDK 21 (o JAVA_HOME do sistema aponta para o 1.8).
# Uso: .\run.ps1                 (porta 8080)
#      .\run.ps1 -Port 8081      (quando o 8080 está ocupado — ex.: WildFly do IntelliJ)
param([int]$Port = 8080)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location $PSScriptRoot
mvn -B -ntp spring-boot:run "-Dspring-boot.run.arguments=--server.port=$Port"
