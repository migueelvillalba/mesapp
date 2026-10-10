# Operación y recuperación

## Health y logs

- Comprueba `/actuator/health` en cada perfil. En el cliente, la etiqueta de conexión se basa en `/api/health`, no en `navigator.onLine`.
- En Compose: `docker compose --env-file .env -f deploy/docker-compose.yml ps` y `logs --tail 100 backend-local backend-central`.
- Fallos de sync quedan en `evento_outbox.ultimo_error` y la pantalla Sincronización muestra los pendientes/errores. El payload no se escribe al log.
- Los trabajos conservan estado e intentos en PostgreSQL. Un `ENVIADO`/`PENDIENTE_VERIFICACION` requiere verificación humana; reimprimir puede producir papel duplicado.
- Si el agente deja vencer un reclamo de dos minutos, el servidor cambia el trabajo a `PENDIENTE_VERIFICACION`. El administrador revisa el equipo antes de reencolarlo.

## Copia local

Con `.env` válido y Compose activo, desde la raíz ejecuta `scripts/backup.ps1`. El script crea un dump custom-format de `mesapp_local` en `backups/`. Copia los archivos fuera del computador del restaurante y protege su acceso: contienen información de pedidos y domicilios.

## Restauración

Primero detén el tráfico de tablets/cocina y haz una copia del estado actual. Ejecuta `scripts/restore.ps1 -BackupFile ./backups/mesapp-local-<fecha>.dump`; la herramienta exige escribir `RESTAURAR` y reemplaza objetos existentes. Al terminar, inicia backend y valida health, login, pedidos/mesas y outbox antes de reabrir el servicio.

## Actualización

1. Crear y copiar backup fuera del equipo.
2. Construir y revisar el nuevo artefacto en entorno de ensayo.
3. Detener el backend, desplegar la imagen/version y dejar que Flyway aplique migraciones nuevas.
4. Consultar health y logs; validar login y flujo simulado de pedido/cola.
5. Conservar el artefacto anterior y el backup hasta completar el periodo de observación.

## Red y nube

En producción, segmenta la LAN del restaurante, limita API a la red del local y expón solo una entrada HTTPS/WebSocket a clientes externos. TLS debe terminar en reverse proxy configurado para `Upgrade` de WebSocket y SameSite/secure cookie. La base central debe usar red privada, credenciales exclusivas, rotación de tokens y copias automáticas del proveedor. No se ha desplegado una nube en esta entrega.

## Agente Windows

El agente lee `MESAPP_PRINTER_TOKEN` y opcionalmente `MESAPP_LOCAL_URL`/`MESAPP_PRINTER_SPOOL` desde el entorno. `printer-service/run.ps1` también carga esos valores desde el `.env` de la raíz. Para iniciar al entrar Windows, ejecuta `printer-service/install-logon-task.ps1` desde PowerShell con el mismo usuario que tiene acceso al `.env`; revisa el Historial de la tarea y `printer-spool/simulated-print.log`. Para ejecución automática en producción, instala como servicio con una cuenta Windows dedicada y almacena el token fuera del árbol del proyecto. El adaptador USB real no puede elegirse hasta conocer el modelo de impresora.

Compose publica `MESAPP_BACKEND_PORT` (8080 por defecto) solo para el backend local, necesario para el agente Windows y pruebas desde el host. PostgreSQL y el backend central permanecen dentro de la red privada de Compose.
