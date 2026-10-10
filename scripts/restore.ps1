param([Parameter(Mandatory=$true)][string]$BackupFile)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'No existe .env.' }
$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
if ((Get-Item -LiteralPath $resolvedBackup).PSIsContainer) { throw 'BackupFile debe ser un archivo.' }
$dbUserLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^\s*MESAPP_DB_USER\s*=' } | Select-Object -First 1
$dbUser = if ($dbUserLine) { ($dbUserLine -replace '^\s*MESAPP_DB_USER\s*=\s*', '').Trim() } else { $null }
if ([string]::IsNullOrWhiteSpace($dbUser)) { throw 'MESAPP_DB_USER no está definido.' }
Write-Warning 'Esta acción reemplaza objetos existentes de mesapp_local. Haz primero una copia del estado actual.'
$confirmation = Read-Host 'Escribe RESTAURAR para continuar'
if ($confirmation -cne 'RESTAURAR') { throw 'Restauración cancelada.' }
$compose = @('--env-file', $envFile, '-f', (Join-Path $root 'deploy/docker-compose.yml'))
$containerFile = '/tmp/mesapp-restore.dump'
docker compose @compose cp $resolvedBackup "postgres-local:$containerFile"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar el archivo al contenedor.' }
docker compose @compose exec -T postgres-local pg_restore --clean --if-exists --no-owner -U $dbUser -d mesapp_local $containerFile
if ($LASTEXITCODE -ne 0) { throw 'pg_restore terminó con errores; revisa logs y valida la base antes de iniciar el servicio.' }
docker compose @compose exec -T postgres-local rm -f $containerFile
Write-Host 'Restauración terminada. Inicia backend-local y valida /actuator/health, login y datos.'
