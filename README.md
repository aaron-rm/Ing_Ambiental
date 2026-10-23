# Sistema de alerta temprana de inundaciones

Backend Spring Boot para consultar riesgo de inundación con GEOGloWS y
Open-Meteo, persistir eventos y enviar notificaciones push por Firebase Cloud
Messaging (FCM).

Incluye dos clientes de apoyo:

- `ConsolaDemoRunner`: cliente HTTP de demostración y pruebas del backend.
- PWA mínima de pruebas: solo sirve para activar notificaciones y comprobar su
  recepción. **No es el frontend definitivo**; ese frontend lo construirá otro
  equipo desde cero.

> Proyecto académico y orientativo. No emite declaraciones oficiales de
> emergencia ni sustituye a SINAPROC, IMHPA u otra autoridad competente.

> **Estado:** el siguiente avance (login, catálogo de cuatro ríos y selección
> múltiple) está documentado, pero todavía no implementado. Para retomarlo en
> otra sesión o computadora, empezar por
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
$env:ALERTA_ADMIN_TOKEN = "un-token-largo"
.\mvnw.cmd spring-boot:run
```

La consola se puede desactivar sin afectar al backend:

```powershell
$env:ALERTA_DEMO_CONSOLA_HABILITADA = "false"
.\mvnw.cmd spring-boot:run
```

## Demo de notificaciones en clase

1. Cada estudiante abre la URL HTTPS del túnel, pulsa **Activar notificaciones**
   y acepta el permiso.
2. En la terminal donde corre el backend, elegir la opción **0** de la consola:
   `Enviar notificacion push de demostracion a todos`.
3. La notificación debe aparecer en los dispositivos suscritos.

La consola usa `ALERTA_ADMIN_TOKEN` para autenticarse contra
`POST /api/push/demo`. El simulacro no crea un evento real de riesgo.

## Si ves la PWA antigua o “Sin conexión”

El navegador pudo conservar el Service Worker de una versión anterior. Cuando el
backend está detenido, ese Service Worker puede servir el HTML viejo desde su
caché y mostrar “Sin conexión”, aunque el código actual ya sea la página mínima.

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
| [docs/GUIA_FRONTEND.md](docs/GUIA_FRONTEND.md) | Guía del avance: login, mapa, selección múltiple y admin (borrador) |
| [IMPLEMENTATION_NOTES.md](IMPLEMENTATION_NOTES.md) | Historia de implementación y decisiones previas |

## Pruebas

```powershell
.\mvnw.cmd package
```

Estado actual: **51 pruebas, 0 fallos**.

Validación de sintaxis de la PWA:

```powershell
node --check src/main/resources/static/app.js
node --check src/main/resources/static/firebase-messaging-sw.js
```

## Estructura

```text
src/main/java/.../alerta/   Eventos, monitoreo y API de alertas
src/main/java/.../riesgo/   Reglas de caudal y lluvia
src/main/java/.../clima/    Open-Meteo y caché
src/main/java/.../push/     FCM, registro y envío
src/main/java/.../rio/      Ríos configurados
src/main/java/.../demo/     Consola HTTP e inicialización de demo
src/main/resources/static/  PWA mínima de prueba de notificaciones
src/test/java/...           51 pruebas automatizadas
docs/                       Arquitectura y guías operativas
scripts/                    Arranque de demo
```

## Limitaciones

- Umbrales de lluvia experimentales (25/50/100 mm).
- `returnperiods` de GEOGloWS falla; se usa el fallback 15/30/60 m³/s.
- Push real pendiente de credenciales Firebase y dispositivo físico.
- iOS requiere 16.4+ y la PWA instalada en la pantalla de inicio.
- El túnel HTTPS es temporal; para producción se necesita un dominio estable.
- La gestión de ríos (`POST`/`PUT /api/rios`) aún no está protegida con token;
  debe restringirse antes de producción.
