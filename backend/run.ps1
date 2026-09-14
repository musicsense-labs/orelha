# Sobe o backend com o JDK 21 (o JAVA_HOME do sistema aponta para o 1.8).
# Uso: .\run.ps1            (de dentro de backend\)
#      .\backend\run.ps1    (da raiz do repositório)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location $PSScriptRoot
mvn -B -ntp spring-boot:run
