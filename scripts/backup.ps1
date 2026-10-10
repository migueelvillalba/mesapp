$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'No existe .env. Cópialo desde .env.example y completa sus valores.' }
$dbUserLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^\s*MESAPP_DB_USER\s*=' } | Select-Object -First 1
$dbUser = if ($dbUserLine) { ($dbUserLine -replace '^\s*MESAPP_DB_USER\s*=\s*', '').Trim() } else { $null }
if ([string]::IsNullOrWhiteSpace($dbUser)) { throw 'MESAPP_DB_USER no está definido.' }
$compose = @('--env-file', $envFile, '-f', (Join-Path $root 'deploy/docker-compose.yml'))
$destination = Join-Path $root 'backups'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$containerFile = "/tmp/mesapp-local-$stamp.dump"
$hostFile = Join-Path $destination "mesapp-local-$stamp.dump"
docker compose @compose exec -T postgres-local pg_dump -U $dbUser -Fc -f $containerFile mesapp_local
if ($LASTEXITCODE -ne 0) { throw 'pg_dump no pudo crear la copia. Confirma que PostgreSQL esté activo.' }
docker compose @compose cp "postgres-local:$containerFile" $hostFile
if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar el backup al host.' }
docker compose @compose exec -T postgres-local rm -f $containerFile
Write-Host "Copia local creada: $hostFile"
