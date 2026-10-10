# Estado de implementación de MesApp

Actualizado el 10 de octubre de 2026. Este documento distingue código disponible, validación automática y dependencias externas.

## Implementado

- Backend Spring Boot con perfiles local y central; PostgreSQL y diez migraciones Flyway.
- Inicio de sesión por empleado y PIN BCrypt, límite temporal de intentos, sesión y CSRF para navegador.
- Administración de mesas, categorías, productos y empleados con permisos, validación de pedidos activos y registro de auditoría.
- Pedidos de mesa y domicilio, líneas con precios históricos, reglas de estado, asignación de entregas e historial.
- Actualizaciones a cocina por WebSocket, estado del servidor y persistencia local independiente de Internet.
- Outbox local con reintentos e Inbox central idempotente, aislamiento por restaurante e instalación, proyección por versión y registro de eventos en conflicto/anteriores.
- Cola de impresión durable, estaciones/dispositivos configurables, reclamo con lease y agente Windows con adaptador simulado y revisión humana antes de reimprimir.
- Interfaz React en español, documentación OpenAPI, diagramas Mermaid, Compose y scripts PowerShell de copia/restauración.

## Validación automática

- Backend: mvn -f backend/pom.xml test terminó con código 0: 4 pruebas, incluidas reglas, aplicación de las diez migraciones y recibos/proyección central con PostgreSQL Testcontainers.
- Agente: mvn -f printer-service/pom.xml test package dependency:copy-dependencies terminó con código 0. El adaptador simulado procesó un trabajo real tras aprobar la reimpresión; el backend lo dejó en PENDIENTE_VERIFICACION.
- Frontend: npm run build, npm run lint y npm test pasaron; Vitest reportó 1 prueba aprobada. Playwright reportó 1 prueba aprobada.
- Compose: configuración validada; se reconstruyeron y arrancaron los cinco contenedores. Los backends local/central y ambas bases PostgreSQL están saludables; el frontend responde HTTP 200.
- Prueba operativa: login administrativo, 23 mesas, alta de categoría/producto, pedido de mesa y cambio a EN_PREPARACION. El central materializó categoría, producto y pedido; la Outbox quedó sin pendientes.
- Flyway terminó en V10 en ambas bases. OpenAPI activo (32 rutas) exportado a docs/openapi.json.
- Copias: scripts/backup.ps1 creó un dump. Se restauró en una base temporal aislada y se consultaron 23 mesas y el pedido guardado. No se sobrescribió la base activa.
- Manejo de errores: JSON malformado devuelve HTTP 400.

## Requisitos de infraestructura externa

- **Impresora física:** falta identificar marca/modelo, conexión USB, controlador de Windows y método fiable para confirmar que el papel salió. El adaptador actual es simulado; deja la comanda como PENDIENTE_VERIFICACION.
- **Nube de producción:** falta elegir proveedor, dominio, TLS, base de datos administrada, secretos y política de copias/retención. La API central multi-tenant se puede ejecutar y probar localmente en Compose.
- **Windows de restaurante:** el servicio y la tarea de inicio están preparados, pero deben instalarse en el computador y probarse junto a la impresora seleccionada.

## Límites funcionales explícitos

No se incluyen cobros, caja ni facturación electrónica. Las sesiones viven en memoria y se cierran al reiniciar el backend; para varios nodos se requiere un repositorio compartido de sesiones. La operación sin Internet cubre la LAN local siempre que el servidor local y PostgreSQL sigan disponibles; el navegador no almacena pedidos offline si cae el servidor local.

## Comandos principales

    docker compose --env-file .env -f deploy/docker-compose.yml up --build -d
    docker compose --env-file .env -f deploy/docker-compose.yml ps
    mvn -f backend/pom.xml test
    mvn -f printer-service/pom.xml test package dependency:copy-dependencies
    Set-Location frontend; npm.cmd run build; npm.cmd run lint; npm.cmd test; npm.cmd run test:e2e
    .\scripts\backup.ps1

Usa docker compose ... down para parar y conservar las bases. down -v elimina permanentemente los volúmenes de datos.
