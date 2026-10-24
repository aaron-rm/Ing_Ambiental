# Sistema de alerta temprana de inundaciones

Backend Spring Boot para consultar riesgo de inundación con GEOGloWS y
Open-Meteo, persistir eventos y enviar notificaciones push por Firebase Cloud
Messaging (FCM).

### Componentes del sistema y separación de roles

1. **Backend (Spring Boot):** Motor central independiente. Ejecuta la lógica de negocio, ingesta y caché de GEOGloWS y Open-Meteo, persistencia en PostgreSQL, seguridad JWT y despacho de notificaciones push vía Firebase Cloud Messaging (FCM). Expone una API REST pública.
2. **Consola interactiva (`ConsolaDemoRunner`):** Cliente HTTP de consola por terminal interactiva. Permite autenticación JWT, consultar el panel y ejecutar simulaciones administrativas desde el servidor. No recibe notificaciones push directamente ya que una terminal no puede registrar Service Workers.
3. **PWA de pruebas (`src/main/resources/static/`):** Herramienta web mínima servida por Spring Boot. Existe separadamente de la consola por la **necesidad técnica de validar el ciclo de vida de Web Push** (registro de Service Worker, suscripción FCM en navegador, permisos del sistema operativo y recepción en primer/segundo plano). **No es el frontend definitivo**; este último será desarrollado desde cero por otro equipo.
4. **Frontend definitivo:** Aplicación externa que construirá otro equipo consumiendo los contratos públicos de la API REST.

> Proyecto académico y orientativo. No emite declaraciones oficiales de
> emergencia ni sustituye a SINAPROC, IMHPA u otra autoridad competente.

> **Estado (Entrega 75 %):** El backend está **preparado y verificado para la entrega del 75 %**, con **79 pruebas automatizadas en verde** (0 fallos). Cuenta con autenticación JWT, roles `USER`/`ADMIN`, catálogo cerrado de cuatro ríos con geometría real (GeoJSON), selección usuario–ríos, enrutamiento push a seguidores, caché de GEOGloWS con tolerancia a fallos y campo `siguiendo` en el panel.
>
> Para retomar el proyecto o consultar la historia de sesiones, ver [docs/CONTINUIDAD_SESION.md](docs/CONTINUIDAD_SESION.md).
> Para una explicación funcional sin tecnicismos, ver [docs/RESUMEN_PROYECTO.md](docs/RESUMEN_PROYECTO.md).

## Arranque rápido

Requisitos: Java 21+, PostgreSQL y, para push, un proyecto Firebase.

```powershell
Copy-Item .env.demo.example .env.demo
# completar .env.demo con las credenciales locales
.\scripts\iniciar-demo.ps1
```

Abrir `http://localhost:8081`. El script también abre un túnel HTTPS para probar
desde el celular.

Arranque manual en Windows (PowerShell):

```powershell
$env:DB_PASSWORD = "su-contraseña-local"
$env:ALERTA_JWT_SECRET = "un-secreto-de-al-menos-32-caracteres"
# Credenciales de desarrollo local:
$env:ALERTA_ADMIN_IDENTIFICADOR = "admin"
$env:ALERTA_ADMIN_PASSWORD = "admin-local-clave"
.\mvnw.cmd spring-boot:run
```

> **Credenciales de desarrollo:** Para pruebas locales se utiliza por convención el identificador `admin` y la contraseña `admin-local-clave`. **Esta credencial es de uso exclusivo para desarrollo y demostración local; para despliegues en producción DEBE cambiarse o suministrarse mediante secretos seguros de entorno.**

Al arrancar, el backend carga el catálogo de cuatro ríos desde
`src/main/resources/catalogo/rios.json` (upsert por `reachId`). Las respuestas de
GEOGloWS se cachean en memoria durante `GEOGLOWS_CACHE_MINUTOS` (15 por defecto)
y se reutiliza la última copia si la API falla.

Arranque en Linux (con PostgreSQL en un contenedor Podman):

```bash
podman run -d --name alerta-postgres \
  -e POSTGRES_DB=alerta_inundaciones -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=contrasena-local-de-ejemplo \
  -p 127.0.0.1:5432:5432 -v alerta-pgdata:/var/lib/postgresql/data \
  docker.io/library/postgres:16-alpine

cp .env.demo.example .env.demo
# completar .env.demo; DB_PASSWORD debe coincidir con POSTGRES_PASSWORD

bash scripts/iniciar-demo.sh            # consola interactiva y navegador
bash scripts/iniciar-demo.sh --tunel    # ademas abre cloudflared si esta instalado
```

Con doble clic, el script detecta que no hay terminal y se relanza en Konsole,
GNOME Terminal o xterm; además abre el navegador en `http://localhost:8081`
cuando el backend responde. Si ya hay un backend corriendo en el puerto 8081,
solo abre la página y no lanza una segunda instancia.

En Linux `./mvnw` puede fallar con «Permiso denegado»: usar `bash mvnw`. Si
OneDrive sincroniza la carpeta `target/` desde Windows y Maven falla con
«Unable to find a single main class», ejecutar `bash mvnw clean` y reintentar.

La consola se puede desactivar sin afectar al backend:

```powershell
$env:ALERTA_DEMO_CONSOLA_HABILITADA = "false"
.\mvnw.cmd spring-boot:run
```

## Demo y prueba de notificaciones

1. Cada usuario abre la URL de la aplicación (en PC: `http://localhost:8081`; en celular: la URL HTTPS del túnel).
2. Inicia sesión o registra una cuenta nueva (`USER`), selecciona al menos un río para seguir y pulsa **Activar notificaciones**.
3. Para enviar pruebas como administrador:
   - **Desde la PWA:** Inicie sesión con la cuenta `ADMIN` (`admin` / `admin-local-clave` en local). Al seguir un río, aparecerá el botón **Simular crecida**, el cual envía la notificación de simulacro a los seguidores de ese río.
   - **Desde la consola (`ConsolaDemoRunner`):** Inicie sesión con `ADMIN`:
     - Opción **6: Simular crecida de un rio:** Envía la notificación de simulacro únicamente a los dispositivos de los usuarios que siguen ese río.
     - Opción **7: Enviar notificacion push de demostracion:** Envía un simulacro global a todos los dispositivos activos registrados.

> La simulación de crecida no persiste un evento de riesgo en el historial; solo prueba la clasificación y el enrutamiento del canal push.

## Si ves la PWA antigua o “Sin conexión”

El navegador pudo conservar el Service Worker de una versión anterior. Cuando el
backend está detenido, ese Service Worker puede servir el HTML viejo desde su
caché y mostrar “Sin conexión”, aunque el código actual ya sea la página nueva.

En Chrome de escritorio:

1. Abrir `http://localhost:8081`.
2. `F12` → **Application** → **Service Workers** → **Unregister**.
3. **Application** → **Storage** → **Clear site data**.
4. Cerrar la pestaña y volver a abrir la página con `Ctrl + F5`.

En el celular: borrar los datos del sitio desde la configuración del navegador o
desinstalar la PWA de la pantalla de inicio y volver a abrir la URL. La versión
nueva del Service Worker elimina las cachés `alerta-shell-*` al activarse.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/CONTINUIDAD_SESION.md](docs/CONTINUIDAD_SESION.md) | Estado actual y cómo retomar el proyecto desde otra computadora o sesión |
| [docs/RESUMEN_PROYECTO.md](docs/RESUMEN_PROYECTO.md) | Explicación de alto nivel, flujo, alcance y guía para la presentación |
| [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) | Directrices permanentes y separación backend/clientes |
| [docs/GUIA_PRUEBA_NOTIFICACIONES.md](docs/GUIA_PRUEBA_NOTIFICACIONES.md) | Guía paso a paso para compañeros y agentes de IA |
| [INFORME_ALERTAS_INUNDACION.md](INFORME_ALERTAS_INUNDACION.md) | Informe histórico del 21–22/10; estado vigente en `CONTINUIDAD_SESION.md` |
| [docs/CONFIGURACION_PUSH.md](docs/CONFIGURACION_PUSH.md) | Variables de entorno, PostgreSQL y Firebase/FCM |
| [docs/INTEGRACION_FRONTEND.md](docs/INTEGRACION_FRONTEND.md) | Contrato de API y guía para el equipo de frontend |
| [docs/GUIA_FRONTEND.md](docs/GUIA_FRONTEND.md) | Diseño del frontend definitivo y flujo mínimo de la PWA |
| [docs/AJUSTES_PANEL_RIO.md](docs/AJUSTES_PANEL_RIO.md) | Estado de los ajustes del panel informativo de río |
| [IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md) | Historia de implementación y decisiones previas |

## Pruebas

```powershell
.\mvnw.cmd test
```

Estado actual: **79 pruebas, 0 fallos**. En Linux, usar `bash mvnw test` si
`./mvnw` falla con “Permiso denegado”.

Validación de sintaxis de la PWA:

```powershell
node --check src/main/resources/static/app.js
node --check src/main/resources/static/firebase-messaging-sw.js
```

## Estructura

```text
src/main/java/.../auth/     Usuarios, JWT, roles y seguridad
src/main/java/.../usuario/  Selección usuario–ríos
src/main/java/.../alerta/   Eventos, monitoreo y API de alertas
src/main/java/.../riesgo/   Reglas de caudal y lluvia
src/main/java/.../clima/    Open-Meteo y caché
src/main/java/.../GEOGLOWS/ GEOGloWS, lectura de datos y caché por tramo
src/main/java/.../push/     FCM, registro y envío por seguidores
src/main/java/.../rio/      Catálogo, geometría y mapa
src/main/java/.../demo/     Consola HTTP de demostración
src/main/resources/catalogo/  Catálogo con geometría real (GeoJSON)
tools/                      Script reproducible del catálogo
src/main/resources/static/  PWA de pruebas
src/test/java/...           79 pruebas automatizadas
docs/                       Arquitectura y guías operativas
scripts/                    Arranque de demo
```

## Estado de la entrega y alcance

### Listo para la entrega del 75 % (Verificado)
- **Backend modular y desacoplado:** Spring Boot con API REST independiente y arquitectura por capas (Controller → Service → Repository / Client).
- **Autenticación y roles:** Registro público (`USER`), login con emisión de JWT HS256 (24 horas) y sembrado de administrador (`ADMIN`).
- **Catálogo cerrado de cuatro ríos:** Upsert automático de Río Abajo, Curundú, Juan Díaz y Tocumen con geometría GeoJSON simplificada real (TDX-Hydro).
- **Selección usuario–ríos:** Seguimiento persistido en base de datos e indicador `siguiendo` en el panel de río.
- **Evaluación y monitoreo de riesgo:** Clasificación combinada de caudal (GEOGloWS v2) y lluvia acumulada en 24h (Open-Meteo) bajo 4 niveles (`NORMAL`, `VIGILANCIA`, `ALERTA`, `EMERGENCIA`). Monitoreo programado cada 15 min e historial con idempotencia.
- **Tolerancia a fallos:** Caché en memoria para GEOGloWS con reutilización de la última respuesta válida en caso de caídas del servicio externo.
- **Notificaciones Push por seguidores:** Registro de token FCM vinculado al usuario y despacho de avisos críticos y simulacros solo a los seguidores del río.
- **Herramientas de prueba:** Consola interactiva CLI con menú por rol y PWA mínima de prueba web para validación de Service Workers.
- **Calidad de software:** 79 pruebas automatizadas pasando al 100% (`BUILD SUCCESS`).

### Pendiente para la entrega final (100 %)
- **Frontend definitivo:** Desarrollo de la interfaz gráfica completa (mapas interactivos, hidrogramas y panel de usuario) por el equipo de frontend.
- **Calibración estacional:** Calibración fina de umbrales hidrológicos y de precipitación con entidades oficiales (SINAPROC/IMHPA).
- **Configuración de producción:** Dominio HTTPS fijo, origen CORS definitivo para el frontend y rotación segura de credenciales de base de datos y Firebase.
- **Limpieza de endpoints heredados:** Retiro de rutas transitorias (`POST /api/push/demo` heredado y `GET /api/alertas/suscripciones/{reachId}`).
