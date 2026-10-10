# MesApp

MesApp es una aplicación para pedidos de restaurante con servidor local, cliente web, cocina en tiempo real, Outbox/Inbox y un agente independiente de impresión para Windows. La versión implementa los flujos principales para operar localmente; consulta el [plan y checklist](docs/IMPLEMENTATION_PLAN.md) para distinguir lo que se validó en contenedores de lo que requiere impresora física o infraestructura externa.

## Arquitectura

- `backend/`: Java 21 y Spring Boot 3.5.16; los perfiles `local` y `central` comparten el mismo artefacto. Spring indicó que 3.5.16 es el último parche 3.5 con soporte OSS; para producción se debe acordar soporte comercial o migrar a una generación activa antes de salir a operación ([anuncio oficial](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)).
- `frontend/`: React 19, TypeScript estricto, Vite 8, Tailwind CSS 4.3 con plugin oficial de Vite, React Router 7, TanStack Query 5 y STOMP. La interfaz usa únicamente respuestas del backend; un servidor local caído se indica como tal. El plugin sigue la [guía oficial de Tailwind para Vite](https://tailwindcss.com/docs/installation/using-vite).
- `printer-service/`: servicio Java independiente y adaptador simulado. La conexión USB queda pendiente hasta identificar modelo/controlador.
- `deploy/`: Docker Compose con PostgreSQL y bases separadas para los perfiles local y central.
- `scripts/` y `docs/`: copias/restauración, instalación, modelo de datos, reglas, operación y nueve diagramas Mermaid.

## Inicio rápido en Windows

Requisitos: Java 21, Maven 3.9+, Node 24+, npm 11+, Docker Desktop con motor Linux iniciado.

1. Crea `.env` desde `.env.example` y reemplaza cada valor pendiente con credenciales aleatorias privadas. Define un PIN de administrador de 4-12 dígitos; Compose lo usa solo para crear el primer empleado si el restaurante aún no tiene ninguno.
2. Desde la raíz del proyecto:

   ```powershell
   docker compose --env-file .env -f deploy/docker-compose.yml up --build
   ```

3. Abre `http://localhost:5173` e inicia como el nombre configurado en `MESAPP_BOOTSTRAP_ADMIN_NAME` con el PIN privado.
4. Crea categorías y productos; luego crea empleados con PINs personales. No guardes el `.env` en Git.

Para apagar manteniendo datos, usa `docker compose --env-file .env -f deploy/docker-compose.yml down`. **No uses `down -v`** salvo que quieras borrar las bases persistentes.

## Ejecución local sin Docker

Inicia PostgreSQL local y crea DB/usuario. En PowerShell configura el entorno para la terminal actual:

```powershell
$env:MESAPP_DB_URL = 'jdbc:postgresql://localhost:5432/mesapp_local'
$env:MESAPP_DB_USER = 'mesapp'
$env:MESAPP_DB_PASSWORD = '<secreto-local>'
$env:MESAPP_BOOTSTRAP_ADMIN_NAME = 'Administración'
$env:MESAPP_BOOTSTRAP_ADMIN_PIN = '<PIN-privado-4-a-12-dígitos>'
$env:MESAPP_PRINTER_TOKEN = '<token-aleatorio>'
$env:MESAPP_SYNC_TOKEN = '<token-compartido-con-central>'
mvn -f backend/pom.xml spring-boot:run
```

El perfil predeterminado es `local`; Flyway aplica las migraciones y crea, de forma idempotente, el restaurante, la instalación y las 23 mesas. La cuenta inicial solo se crea en una base de empleados vacía. Si existe al menos un empleado, cambia o administra PINs con el proceso operativo antes de iniciar el restaurante.

En otra terminal ejecuta el cliente:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

Vite escucha en `http://localhost:5173` y proxifica API y WebSocket a `http://localhost:8080`. La compilación estática de producción requiere un reverse proxy que sirva `frontend/dist` y proxifique `/api`, `/ws` y `/actuator/health` al backend por el mismo origen; termina HTTPS en ese proxy y establece `MESAPP_COOKIE_SECURE=true`.

## Agente de impresión

Configura `MESAPP_LOCAL_URL` (por defecto `http://localhost:8080`), `MESAPP_PRINTER_TOKEN` y opcionalmente `MESAPP_PRINTER_SPOOL`. Ejecuta:

```powershell
Set-Location printer-service
.\build.ps1
.\run.ps1
```

El adaptador simulado escribe un registro legible en `printer-spool/simulated-print.log` y reporta `PENDIENTE_VERIFICACION`. No afirma que salió papel. `run.ps1` carga el token de impresión desde el `.env` de la raíz si no existe en el entorno. Para iniciar al entrar Windows, ejecuta `.install-logon-task.ps1`. La actualización consiste en cerrar la tarea, guardar copia de configuración, reemplazar/build del artefacto y volver a iniciar; prueba primero el agente simulado.

El agente reclama un trabajo antes de imprimir. Si Windows o el agente cae después de enviarlo, el trabajo no vuelve a la cola automáticamente: el administrador debe verificar la impresora y elegir una reimpresión en el monitor.

## Validación

```powershell
mvn -f backend/pom.xml test
mvn -f printer-service/pom.xml test package dependency:copy-dependencies
Set-Location frontend; npm.cmd run build; npm.cmd run lint; npm.cmd test; npm.cmd run test:e2e
```

Backend usa Testcontainers cuando Docker está disponible para aplicar todas las migraciones y probar Inbox/proyección central, idempotencia y conflictos de versión. Frontend tiene pruebas unitarias y de navegador. Para revisar OpenAPI con el servicio activo, visita `/swagger-ui/index.html`; el JSON está en `/v3/api-docs`.

## Configuración de nube y operación

El perfil central necesita su propia DB PostgreSQL privada, `MESAPP_DB_*` y credenciales de instalación. De forma predeterminada usa `MESAPP_RESTAURANT_ID`, `MESAPP_INSTALLATION_ID` y `MESAPP_SYNC_TOKEN`. Para registrar varias instalaciones configura `MESAPP_SYNC_TENANTS` como un arreglo JSON de objetos con `restaurantId`, `installationId`, `restaurantName`, `installationName` y `token`; el central guarda hashes SHA-256 de los tokens y autoriza cada evento contra la pareja exacta. Cada backend local envía únicamente el token de su instalación mediante `MESAPP_SYNC_TOKEN`. El worker llama al servicio central definido por `MESAPP_CENTRAL_URL`. No existe una cuenta cloud, dominio ni infraestructura administrada en esta entrega.

Los eventos y el historial inmutable quedan en Inbox. Pedidos, mesas, categorías, productos y empleados sincronizados se proyectan por restaurante con control de secuencia; una versión anterior se conserva como historial sin sobrescribir la vigente y las colisiones de versión se reportan como conflicto. La consulta central de proyecciones requiere token de una instalación autorizada de ese restaurante. Nunca expongas PostgreSQL a Internet.

Ver [instrucciones de backup y restauración](docs/OPERATIONS.md), [limitaciones detalladas](docs/IMPLEMENTATION_PLAN.md) y [reglas de negocio/API](docs/ARCHITECTURE.md). No se dispone de modelo de impresora USB; para integrar una se debe conocer fabricante/modelo, protocolo/driver, métodos verificables de confirmación y entorno de Windows.
