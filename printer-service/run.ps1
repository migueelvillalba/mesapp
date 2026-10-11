$ErrorActionPreference = 'Stop'
$workspaceEnv = Join-Path $PSScriptRoot '..\.env'
if (Test-Path -LiteralPath $workspaceEnv) {
    foreach ($line in Get-Content -LiteralPath $workspaceEnv) {
        if ($line -match '^\s*(MESAPP_PRINTER_TOKEN|MESAPP_LOCAL_URL|MESAPP_PRINTER_SPOOL|MESAPP_PRINTER_STATION|MESAPP_PRINTER_ADAPTER|MESAPP_PRINTER_NAME)\s*=\s*(.*?)\s*$') {
            $key = $Matches[1]
            $value = $Matches[2].Trim('"').Trim("'")
            if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key))) { [Environment]::SetEnvironmentVariable($key, $value, 'Process') }
        }
    }
}
if ([string]::IsNullOrWhiteSpace($env:MESAPP_PRINTER_TOKEN)) { throw 'Define MESAPP_PRINTER_TOKEN en el entorno.' }
$jar = Join-Path $PSScriptRoot 'target\mesapp-printer-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Ejecuta build.ps1 antes de iniciar el agente.' }
$dependencies = Join-Path $PSScriptRoot 'target\dependency\*'
java -cp "$jar;$dependencies" mx.mesapp.printer.PrinterService
