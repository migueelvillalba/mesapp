# Arquitectura y reglas de MesApp

## Componentes implementados

- **Backend Spring Boot** (`backend/`) comparte DTO, controlador de negocio, validación de rol y SQL entre empaquetados. El perfil `local` activa autenticación de empleados, mesas, catálogo, pedidos, WebSocket, cola de impresión y worker Outbox. El perfil `central` activa el receptor de eventos idempotentes y no expone las rutas operativas locales.
- **PostgreSQL** mantiene la instalación local. El perfil central apunta a otra base; Compose crea dos bases separadas.
- **Frontend React/TypeScript** (`frontend/`) consulta la API en el mismo origen. Vite proxifica `/api` y `/ws` al backend local. La detección de servidor local usa una solicitud HTTP periódica real.
- **Agente Windows** (`printer-service/`) reclama un trabajo antes de procesarlo, escribe una representación legible al spool de desarrollo y reporta `PENDIENTE_VERIFICACION`. No traduce el resultado simulado en confirmación de papel.

## Reglas del negocio disponibles

- El restaurante e instalación provienen de configuración del servidor; el cliente no elige su tenant.
- Categoría y producto deben pertenecer al restaurante autenticado.
- El pedido de mesa exige mesa activa; domicilio no admite mesa y exige destino.
- El importe, nombre y precio de la línea se obtienen del catálogo del servidor. La solicitud no aporta importes.
- Un índice parcial de PostgreSQL y validación en API permiten como máximo un pedido activo por mesa.
- Cada pedido creado llega en estado `CONFIRMADO`; cambios posteriores permitidos: `CONFIRMADO → EN_PREPARACION/CANCELADO`, `EN_PREPARACION → LISTO/CANCELADO`, `LISTO → ENTREGADO`.
- Cocina puede iniciar preparación y marcar listo. Domiciliario solo puede entregar el pedido actualmente asignado. Mesero/administrador pueden operar estados según el rol.
- La desactivación de mesa es lógica y está prohibida si hay pedido activo. El historial conserva pedidos previos.
- Una clave `Idempotency-Key` repetida recupera el pedido ya creado. No cambia las reglas de una colisión de pedido de mesa.
- Crear el pedido, las líneas con precio histórico, historial inicial, trabajo de impresión y evento Outbox ocurre en una transacción local.
- Reimpresión es una acción administrativa. Un trabajo en estado enviado/verificación exige una decisión humana antes de volver a reclamarlo.

## Persistencia y sincronización

Flyway administra PostgreSQL; Hibernate está en modo `validate`. `V1` crea la base relacional; `V2` agrega idempotencia y control de PIN, `V3-V5` añaden Inbox y reintentos, `V6` agrega proyección central e impresoras, `V7` recupera reclamos, `V8` registra credenciales multi-instalación y `V9-V10` versionan catálogo y materializan proyecciones centrales. No edites una migración ya ejecutada: agrega una nueva.

El worker local transmite lotes Outbox al perfil central. Los fallos de transporte esperan 5, 10, 20... segundos hasta un máximo de una hora. El backend central registra el UUID de evento con `payload_hash`; una repetición idéntica devuelve `DUPLICADO`, un mismo UUID con carga distinta se rechaza y una colisión de versión se guarda en historial como `CONFLICTO`. Pedidos, mesas, categorías, productos y empleados se proyectan por restaurante; eventos atrasados permanecen en el historial y no reemplazan una secuencia superior. La respuesta central y la marca local `SINCRONIZADO` son dos transacciones separadas, por eso la idempotencia evita duplicados si el proceso cae entre ambas.

`MESAPP_SYNC_TENANTS` configura varios restaurantes/instalaciones en el central. Cada evento valida su token hasheado contra su restaurante e instalación; la API de lectura central limita cada token al restaurante asociado. Los tokens de instalación tienen permiso de sincronización/lectura de ese restaurante y deben almacenarse como secretos.

## Seguridad

- PINs de 4 a 12 dígitos con BCrypt (coste 12), sin registro en logs. Cinco fallos bloquean la cuenta por cinco minutos. Una sesión HTTP renueva el ID al iniciar; vence a las ocho horas; la cookie es HttpOnly y SameSite Strict. Las operaciones web requieren cookie CSRF; los agentes Bearer tienen rutas separadas.
- Para HTTPS detrás de proxy, configurar TLS en proxy y `MESAPP_COOKIE_SECURE=true`; establecer `MESAPP_ALLOWED_ORIGINS` con los orígenes de la red y no publicar PostgreSQL.
- Sesiones en memoria: un reinicio cierra sesiones activas. La API local no necesita depender de Internet para validar credenciales.
- Token Bearer separado para impresión y sincronización, hash SHA-256 almacenado en el central. El central valida el restaurante y la instalación autorizada para cada evento.
- Domiciliarios ven solamente pedidos asignados y activos.

## API y errores

Base `/api`; JSON UTF-8; errores uniformes incluyen `timestamp`, `status` y `error`, errores de validación incluyen `fields`. OpenAPI está en `/v3/api-docs`; Swagger UI en `/swagger-ui/index.html`.

| Método y ruta | Acceso | Uso |
|---|---|---|
| `GET /api/health` | público | disponibilidad HTTP del servidor local y pendientes Outbox |
| `GET /api/auth/employees`, `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout` | lista pública; sesión para acciones | selección de empleado, PIN y sesión |
| `GET /api/auth/csrf`, `POST /api/auth/change-pin` | público para token; sesión para cambio | token CSRF de navegador y cambio de PIN propio |
| `POST /api/auth/employees` | administrador | alta de empleado con PIN hasheado |
| `GET /api/tables`, `POST /api/tables`, `PATCH/PUT/DELETE /api/tables/{id}` | sesión; escritura administrador | estado, alta, edición y activación lógica |
| `GET/POST /api/categories`, `PATCH /api/categories/{id}` | sesión / administrador | consulta, alta y edición lógica |
| `GET/POST /api/products`, `PATCH/PUT/DELETE /api/products/{id}` | sesión / administrador | catálogo, precios y desactivación lógica |
| `GET/POST /api/orders`, `GET /api/orders/{id}`, `POST /api/orders/{id}/state` | sesión y rol | pedidos e historial reciente; `Idempotency-Key` al crear |
| `GET /api/orders/{id}/history`, `GET /api/orders/{id}/assignments`, `POST /api/orders/{id}/assignment` | sesión y recurso autorizado | historial de estados y entregas |
| `GET /api/audit`, `PATCH /api/auth/employees/{id}`, `POST /api/auth/employees/{id}/pin` | administrador | auditoría, permisos y reinicio de PIN |
| `GET /api/sync/status` | mesero/administrador | pendientes, conflictos y último error |
| `GET /api/printer/admin/jobs`, `POST /api/printer/jobs/{id}/retry`, `/stations`, `/devices` | administrador | monitor, reimpresión autorizada y configuración |
| `POST /api/printer/jobs/claim`, `POST /api/printer/jobs/{id}/result` | token de agente | reclamar antes de enviar y guardar resultado |
| `POST /api/sync/events`, `GET /api/sync/entities/{restaurantId}` | token asociado a restaurante/instalación | Inbox, proyecciones y lectura limitada a ese restaurante |
| STOMP `/ws`, tópico `/topic/kitchen` | sesión autenticada; rol cocina/mesero/admin | evento para invalidar/refrescar pedidos |

No hay pagos, caja ni facturación electrónica.

## Carpetas

- `backend/src/main/java/mx/mesapp/api`: controladores HTTP, DTOs y formato de error.
- `backend/src/main/java/mx/mesapp/config`: seguridad, WebSocket y aprovisionamiento inicial.
- `backend/src/main/java/mx/mesapp/domain`: política de estados.
- `backend/src/main/java/mx/mesapp/sync`: worker Outbox local.
- `backend/src/main/resources/db/migration`: esquema Flyway.
- `frontend/src`: interfaz React, queries y estilos.
- `printer-service/src`: servicio Java independiente y `PrinterAdapter`.
- `deploy`: Compose de desarrollo; `docs/diagrams`: Mermaid.

## Operación, copias y recuperación

Usa los scripts de `scripts/backup.ps1` y `scripts/restore.ps1` desde la raíz. Guarda copias fuera del computador y prueba la restauración periódicamente. Los contenedores mantienen datos en volúmenes Docker; `docker compose down -v` los borra. Tras actualizar, realizar backup, desplegar el nuevo backend, observar health checks y verificar migraciones y login antes de volver al servicio.

Registros de Spring van a stdout. No se incluyen PINs, tokens ni payloads completos en logs. Para diagnóstico, revisar health check, logs de backend, `evento_outbox.ultimo_error`, estado de trabajos y `printer-spool/simulated-print.log`.
