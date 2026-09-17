# Registra (ou substitui) a tarefa agendada "Orelha": no logon deste usuário, roda deploy/start-orelha.ps1
# em janela oculta. Não precisa de administrador. Rode uma vez: .\deploy\install-task.ps1
$script = Join-Path $PSScriptRoot "start-orelha.ps1"
# Alias estável do pwsh (a pasta do pacote da Store muda a cada atualização); senão o Windows PowerShell 5.
$shell = Join-Path $env:LOCALAPPDATA "Microsoft\WindowsApps\pwsh.exe"
if (-not (Test-Path $shell)) { $shell = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe" }

$action = New-ScheduledTaskAction -Execute $shell -Argument "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$trigger.Delay = "PT30S"   # dá tempo ao Docker Desktop e à rede
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) `
    -StartWhenAvailable -DontStopIfGoingOnBatteries -AllowStartIfOnBatteries
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName "Orelha" -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Get-ScheduledTask -TaskName "Orelha" | Select-Object TaskName, State
Write-Host "Tarefa 'Orelha' registrada: no logon de $env:USERNAME, $shell -File $script"
