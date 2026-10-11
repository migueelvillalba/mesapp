param([string]$TaskName = 'MesApp Printer Agent')
$ErrorActionPreference = 'Stop'
$runScript = Join-Path $PSScriptRoot 'run.ps1'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$runScript`"" -WorkingDirectory $PSScriptRoot
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Description 'Agente local de impresión MesApp (adaptador simulado)' -Force | Out-Null
Write-Host "Tarea '$TaskName' registrada para iniciar al entrar $env:USERNAME."
