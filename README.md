# Sistema de alerta temprana de inundaciones

Backend Spring Boot para consultar riesgo de inundación con GEOGloWS y
Open-Meteo, persistir eventos y enviar notificaciones push por Firebase Cloud
Messaging (FCM).

Incluye dos clientes de apoyo:

- `ConsolaDemoRunner`: cliente HTTP de demostración y pruebas del backend, con
  login y menús según el rol (`USER`/`ADMIN`).
- PWA de pruebas: login, mapa del catálogo, panel de caudal y lluvia de
  cualquier río y activación de notificaciones. **No es el frontend
  definitivo**; ese frontend lo construirá otro equipo desde cero.

> Proyecto académico y orientativo. No emite declaraciones oficiales de
> emergencia ni sustituye a SINAPROC, IMHPA u otra autoridad competente.

> **Estado:** el avance de autenticación, catálogo de cuatro ríos con geometría
> real, selección usuario–ríos y push asociado al usuario está implementado y
> con 67 pruebas en verde. Falta validar con PostgreSQL real y probar la
> recepción push en un dispositivo. Para retomarlo en otra sesión o
> computadora, empezar por
> [docs/CONTINUIDAD_SESION.md](docs/CONTINUIDAD_SESION.md).

> Para una explicación funcional breve, sin tecnicismos, ver
> [docs/RESUMEN_PROYECTO.md](docs/RESUMEN_PROYECTO.md).

## Arranque rápido

Requisitos: Java 21+, PostgreSQL y, para push, un proyecto Firebase.

```powershell
Copy-Item .env.demo.example .env.demo
# completar .env.demo con las credenciales locales
.\scripts\iniciar-demo.ps1
```

Abrir `http://localhost:8081`. El script también abre un túnel HTTPS para probar
desde el celular.

Arranque manual:

```powershell
$env:DB_PASSWORD = "su-contraseña-local"
$env:ALERTA_JWT_SECRET = "un-secreto-de-al-menos-32-caracteres"
$env:ALERTA_ADMIN_IDENTIFICADOR = "admin"
$env:ALERTA_ADMIN_PASSWORD = "la-contraseña-local-acordada"
.\mvnw.cmd spring-boot:run
```

Al arrancar, el backend carga el catálogo de cuatro ríos desde
`src/main/resources/catalogo/rios.json` (upsert por `reachId`).

Arranque en Linux (con PostgreSQL en un contenedor Podman):

```bash
podman run -d --name alerta-postgres \
  -e POSTGRES_DB=alerta_inundaciones -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=alerta_local_dev \
  -p 127.0.0.1:5432:5432 -v alerta-pgdata:/var/lib/postgresql/data \
  docker.io/library/postgres:16-alpine

cp .env.demo.example .env.demo
# completar .env.demo; DB_PASSWORD debe coincidir con POSTGRES_PASSWORD

bash scripts/iniciar-demo.sh            # backend con consola interactiva
bash scripts/iniciar-demo.sh --tunel    # ademas abre cloudflared si esta instalado
```

En Linux `./mvnw` puede fallar con «Permiso denegado»: usar `bash mvnw`. Si
OneDrive sincroniza la carpeta `target/` desde Windows y Maven falla con
«Unable to find a single main class», ejecutar `bash mvnw clean` y reintentar.

La consola se puede desactivar sin afectar al backend:

```powershell
$env:ALERTA_DEMO_CONSOLA_HABILITADA = "false"
.\mvnw.cmd spring-boot:run
```

## Demo de notificaciones en clase

1. Cada estudiante abre la URL HTTPS del túnel, inicia sesión o crea una cuenta,
   sigue al menos un río y pulsa **Activar notificaciones**.
2. En la terminal donde corre el backend, iniciar sesión en la consola con la
   cuenta `ADMIN` y elegir la opción **8: Enviar notificacion push de
   demostracion**.
3. La notificación debe aparecer en los dispositivos suscritos.

El simulacro no crea un evento real de riesgo. Para probar el enrutamiento por
río, el administrador puede evaluar un río con datos reales (opción 7): solo
reciben la notificación los usuarios que siguen ese río.

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
| [docs/AVANCE_EQUIPO.md](docs/AVANCE_EQUIPO.md) | Resumen breve de las decisiones para los compañeros del equipo |
| [docs/RESUMEN_PROYECTO.md](docs/RESUMEN_PROYECTO.md) | Explicación de alto nivel, flujo, alcance y guía para la presentación |
| [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) | Directrices permanentes y separación backend/clientes |
| [docs/GUIA_PRUEBA_NOTIFICACIONES.md](docs/GUIA_PRUEBA_NOTIFICACIONES.md) | Guía paso a paso para compañeros y agentes de IA |
| [INFORME_ALERTAS_INUNDACION.md](INFORME_ALERTAS_INUNDACION.md) | Informe: cambios, pruebas reales, pendientes y limitaciones |
| [docs/CONFIGURACION_PUSH.md](docs/CONFIGURACION_PUSH.md) | Variables de entorno, PostgreSQL y Firebase/FCM |
| [docs/INTEGRACION_FRONTEND.md](docs/INTEGRACION_FRONTEND.md) | Contrato de API y guía para el equipo de frontend |
| [docs/GUIA_FRONTEND.md](docs/GUIA_FRONTEND.md) | Diseño implementado: login, mapa, selección múltiple y admin |
| [docs/AJUSTES_PANEL_RIO.md](docs/AJUSTES_PANEL_RIO.md) | Estado de los ajustes del panel informativo de río |
| [IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md) | Historia de implementación y decisiones previas |

## Pruebas

```powershell
.\mvnw.cmd test
```

Estado actual: **67 pruebas, 0 fallos**. En Linux, usar `bash mvnw test` si
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
src/main/java/.../push/     FCM, registro y envío por seguidores
src/main/java/.../rio/      Catálogo, geometría y mapa
src/main/java/.../demo/     Consola HTTP de demostración
src/main/resources/catalogo/  Catálogo con geometría real (GeoJSON)
tools/                      Script reproducible del catálogo
src/main/resources/static/  PWA de pruebas
src/test/java/...           67 pruebas automatizadas
docs/                       Arquitectura y guías operativas
scripts/                    Arranque de demo
```

## Limitaciones

- Umbrales de lluvia experimentales (25/50/100 mm).
- `returnperiods` de GEOGloWS falla; se usa el fallback 15/30/60 m³/s.
- Push real pendiente de credenciales Firebase y dispositivo físico; no está
  probado de extremo a extremo.
- La aplicación no se ha arrancado todavía contra PostgreSQL real en el entorno
  de desarrollo usado; solo hay compilación y pruebas unitarias.
- iOS requiere 16.4+ y la PWA instalada en la pantalla de inicio.
- El túnel HTTPS es temporal; para producción se necesita un dominio estable.
- Dos rutas heredadas (`POST /api/push/demo` y
  `GET /api/alertas/suscripciones/{reachId}`) siguen con `X-Admin-Token` y se
  retirarán; el resto de las operaciones administrativas ya exige JWT `ADMIN`.
