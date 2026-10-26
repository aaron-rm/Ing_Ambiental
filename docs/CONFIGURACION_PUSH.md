# Configuración de entorno, base de datos y notificaciones push

Este documento describe cómo ejecutar el sistema con PostgreSQL y cómo habilitar
Firebase Cloud Messaging (FCM). No contiene credenciales reales.

## 1. Requisitos

- Java 21 o superior.
- PostgreSQL con una base de datos creada (por ejemplo, `alerta_inundaciones`).
- Una cuenta de Firebase con una app web registrada si se desean notificaciones.
- HTTPS en producción. Los Service Workers y las notificaciones push requieren
  un contexto seguro; `http://localhost` se acepta para desarrollo.

## 2. Base de datos y puerto

`src/main/resources/application.properties` usa variables de entorno:

| Variable | Valor por defecto | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/alerta_inundaciones` | URL JDBC |
| `DB_USERNAME` | `postgres` | Usuario |
| `DB_PASSWORD` | vacío | Contraseña; definirla fuera del repositorio |
| `ALERTA_JWT_SECRET` | sin valor | Secreto HS256 del JWT; mínimo 32 caracteres |
| `ALERTA_ADMIN_IDENTIFICADOR` | `admin` | Identificador del administrador inicial |
| `ALERTA_ADMIN_PASSWORD` | vacío | Contraseña del administrador (`admin-local-clave` en desarrollo local; definir segura en producción) |
| `ALERTA_CORS_ORIGENES` | orígenes locales | Orígenes permitidos para el frontend |
| `ALERTA_CARGAR_CATALOGO` | `true` | Carga el catálogo de cuatro ríos al arrancar |
| `ALERTA_ADMIN_TOKEN` | vacío | Solo para rutas heredadas (`POST /api/push/demo`, `GET /api/alertas/suscripciones/{reachId}`) |
| `ALERTA_API_BASE_URL` | `http://localhost:8081` | URL que usa la consola para consumir la API |
| `ALERTA_DEMO_CONSOLA_HABILITADA` | `true` | Activa o desactiva la consola de demostración |

El servidor escucha en el puerto **8081**. Hibernate crea/actualiza las tablas
con `spring.jpa.hibernate.ddl-auto=update` (no hay migraciones versionadas).

Ejemplo en PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/alerta_inundaciones"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "su-contraseña-local"
$env:ALERTA_JWT_SECRET = "un-secreto-de-al-menos-32-caracteres"
$env:ALERTA_ADMIN_IDENTIFICADOR = "admin"
$env:ALERTA_ADMIN_PASSWORD = "admin-local-clave"
.\mvnw.cmd spring-boot:run
```

> **Credencial de desarrollo:** `admin-local-clave` se utiliza por convención exclusivamente para pruebas y desarrollo local. Para producción debe sustituirse por una contraseña robusta.

`ALERTA_ADMIN_TOKEN` ya solo se usa para las dos rutas heredadas mencionadas en
la tabla; la consola y la PWA usan login con JWT.

Ejemplo en Linux (PostgreSQL en un contenedor Podman):

```bash
podman run -d --name alerta-postgres \
  -e POSTGRES_DB=alerta_inundaciones -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=contrasena-local-de-ejemplo \
  -p 127.0.0.1:5432:5432 -v alerta-pgdata:/var/lib/postgresql/data \
  docker.io/library/postgres:16-alpine

cp .env.demo.example .env.demo
# completar .env.demo; DB_PASSWORD debe coincidir con POSTGRES_PASSWORD
bash scripts/iniciar-demo.sh
```

`.env.demo` no se versiona ni debe subirse al repositorio. Si OneDrive u otro
servicio lo sincroniza entre computadoras, revise `DB_PASSWORD` al cambiar de
máquina: debe coincidir con la contraseña del PostgreSQL local de esa máquina.

Para detener la base: `podman stop alerta-postgres`; para volver a levantarla:
`podman start alerta-postgres`.

Abrir `http://localhost:8081`.

### 2.1 Consola de demostración

La consola es un cliente HTTP opcional. Se inicia junto con la aplicación y usa
`ALERTA_API_BASE_URL` para llamar a la API pública:

```powershell
# Si el backend usa otro puerto u otro host
$env:ALERTA_API_BASE_URL = "http://localhost:8081"

# Desactivar la consola sin afectar al backend
$env:ALERTA_DEMO_CONSOLA_HABILITADA = "false"
```

La consola pide iniciar sesión o registrarse. Con una cuenta `ADMIN` puede
simular una crecida (envía push a los seguidores del río) y enviar el simulacro
global de demostración; con una cuenta `USER` solo consulta el catálogo, el panel
y sus ríos seguidos. `ALERTA_ADMIN_TOKEN` ya no es necesario para la consola:
solo se usa en las dos rutas heredadas de la tabla anterior.

Las notificaciones de simulación usan el mismo texto visible que una alerta real,
con emojis, y no dicen «simulacro» ni «prueba». La consola y la PWA siguen
marcando la operación como simulacro; el cambio es deliberado para la
demostración en clase.

## 3. Variables de monitoreo y riesgo

| Variable | Defecto | Descripción |
|---|---|---|
| `ALERTA_MONITOREO_HABILITADO` | `true` | Activa la evaluación periódica |
| `ALERTA_MONITOREO_INTERVALO_MS` | `900000` | Intervalo (15 min) |
| `ALERTA_MONITOREO_RETRASO_INICIAL_MS` | `60000` | Espera inicial |
| `ALERTA_NOTIFICAR_RECUPERACION` | `true` | Aviso al volver a NORMAL desde ALERTA/EMERGENCIA |
| `ALERTA_LLUVIA_VIGILANCIA_MM` | `25` | Umbral experimental de lluvia 24 h |
| `ALERTA_LLUVIA_ALERTA_MM` | `50` | Umbral experimental de lluvia 24 h |
| `ALERTA_LLUVIA_EMERGENCIA_MM` | `100` | Umbral experimental de lluvia 24 h |
| `ALERTA_MAX_EDAD_PRONOSTICO_HORAS` | `24` | Frescura máxima del caudal |
| `ALERTA_MAX_EDAD_PRECIPITACION_HORAS` | `6` | Frescura máxima de la lluvia |
| `OPEN_METEO_URL` | `https://api.open-meteo.com/v1/forecast` | Fuente de precipitación |
| `OPEN_METEO_CACHE_MINUTOS` | `15` | Caché de Open-Meteo |
| `GEOGLOWS_CACHE_MINUTOS` | `15` | Caché en memoria de GEOGloWS por endpoint y tramo; mínimo 1 minuto |

Los umbrales de lluvia son **provisionales/experimentales**. No son valores
oficiales de IMHPA ni de SINAPROC y deben validarse localmente.

La caché de GEOGloWS reduce las llamadas al consultar la alerta. Si la API falla, el
backend reutiliza la última respuesta cacheada aunque haya expirado; el frontend
recibe el mismo contrato y puede mostrar la antigüedad con `fechaEvaluacion`.

## 4. Firebase Cloud Messaging

### 4.1 Crear la app web

1. Crear o abrir un proyecto en Firebase Console.
2. Agregar una aplicación web.
3. Copiar los datos públicos de configuración que muestra Firebase:
   `apiKey`, `authDomain`, `projectId`, `messagingSenderId`, `appId`.
4. En **Configuración del proyecto → Cloud Messaging**, generar un par de claves
   **Web Push certificate** y copiar la clave pública VAPID.

### 4.2 Credenciales del backend

1. En **Configuración del proyecto → Cuentas de servicio**, generar una nueva
   clave privada en JSON.
2. Guardarla fuera del repositorio.
3. Definir **una** de estas opciones:

```powershell
# Opción A: ruta al JSON
$env:FIREBASE_CREDENTIALS_PATH = "C:\ruta\segura\service-account.json"

# Opción B: contenido JSON en una variable (o su Base64)
$env:FIREBASE_CREDENTIALS_JSON = Get-Content -Raw "C:\ruta\segura\service-account.json"
```

Opcionalmente, definir `FIREBASE_PROJECT_ID`. Si se omite, se toma del JSON.
Para desactivar FCM sin borrar credenciales: `FIREBASE_ENABLED=false`.

**Nunca** subir el JSON de la cuenta de servicio al repositorio ni servirlo al
navegador. El endpoint `/api/push/config` solo devuelve datos públicos.

### 4.3 Datos públicos para la PWA

```powershell
$env:FIREBASE_API_KEY = "..."
$env:FIREBASE_AUTH_DOMAIN = "mi-proyecto.firebaseapp.com"
$env:FIREBASE_PROJECT_ID = "mi-proyecto"
$env:FIREBASE_MESSAGING_SENDER_ID = "..."
$env:FIREBASE_APP_ID = "..."
$env:FIREBASE_VAPID_KEY = "..."
```

La PWA de pruebas consulta `/api/push/config`, carga el SDK web de Firebase solo
cuando el usuario pulsa «Activar notificaciones» y registra el Service Worker en
`/firebase-messaging-sw.js`. La configuración pública se pasa al Service Worker
por parámetros de consulta; no se escribe en el archivo.

La respuesta solo contiene datos públicos de Firebase; ya **no** incluye
`reachId`. El dispositivo se registra con JWT y queda asociado al usuario; las
alertas se enrutan según los ríos que sigue, no según un tramo fijo.

### 4.4 Comprobar la configuración

```powershell
Invoke-RestMethod http://localhost:8081/api/push/config
```

Debe devolver `habilitado: true` y los datos públicos de Firebase. Si falta la
credencial administrativa o algún dato público, devuelve `habilitado: false` con
un motivo, y la PWA muestra «No disponible» sin intentar registrar el
dispositivo.

## 5. Prueba en un dispositivo real

1. Publicar la app en HTTPS (o abrir `localhost` en el equipo de desarrollo).
2. Abrir la PWA de pruebas, iniciar sesión o crear una cuenta, seguir al menos
   un río y pulsar **Activar notificaciones**; aceptar el permiso.
3. En la consola del backend, iniciar sesión con la cuenta `ADMIN` y elegir la
   opción **6** (`Simular una crecida y enviar push a sus seguidores`).
   También se puede ejecutar con JWT:

```powershell
$cuerpo = @{ identificador = "admin"; password = "la-contraseña-local" } | ConvertTo-Json
$sesion = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/auth/login" `
  -ContentType "application/json" -Body $cuerpo
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/alertas/simular?reachId=710039498" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }
```

4. La notificación debe llegar solo a los dispositivos de quienes siguen ese río.
   La respuesta incluye `dispositivos`, `enviados` y `fallidos`.
5. Para el simulacro global, usar la opción **7** o
   `POST /api/admin/push/demo`:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/admin/push/demo" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }
```

6. Para probar una alerta real sin esperar a una crecida, un administrador puede
   ejecutar la evaluación manual (requiere JWT con rol `ADMIN`):

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/alertas/evaluar/710039498" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }
```

La simulación no persiste un evento de riesgo, pero sí envía el push de
simulacro a los seguidores del río. La evaluación manual sí puede crear un
evento y notificar según las reglas normales.

### 5.1 Demostración rápida en clase

1. Copiar `.env.demo.example` a `.env.demo` y completar las credenciales.
2. Ejecutar `.\scripts\iniciar-demo.ps1`; abre la app y el túnel HTTPS.
3. Cada estudiante abre la URL del túnel, inicia sesión o crea una cuenta, sigue
   al menos un río y pulsa **Activar notificaciones**.
4. En la terminal del backend, iniciar sesión en la consola con la cuenta
   `ADMIN` y elegir la opción **6** para simular una crecida sobre un río
   seguido, o la opción **7** para el simulacro global.

Las notificaciones visibles usan el formato de una alerta real con emojis
(`🚨 ALERTA DE INUNDACIÓN 🌊`, `⚠️ AVISO DE CRECIDA...`) y no incluyen
«simulacro» ni «prueba». La consola y la PWA siguen identificando la operación
como simulacro.

La PWA solo puede pedir una notificación de prueba para su propio dispositivo;
el envío masivo se hace desde la consola o directamente contra el endpoint.

El endpoint `POST /api/admin/push/demo` envía un simulacro a todos los
dispositivos activos sin crear un evento de riesgo. Requiere JWT con rol
`ADMIN`; la ruta heredada `POST /api/push/demo` sigue usando `X-Admin-Token`.

## 6. Notas de compatibilidad

- **Android/Chrome:** permiso explícito y notificaciones del sistema; el sonido
  o la vibración dependen del navegador y de los ajustes del dispositivo.
- **iPhone/iPad:** Web Push requiere iOS 16.4 o superior y la app agregada a la
  pantalla de inicio. El comportamiento exacto depende de Safari y del sistema.
- El backend envía mensajes **data-only**; el Service Worker construye y muestra
  la notificación. El clic abre la URL indicada en el payload (`/?alerta={id}`,
  `/?simulacion={reachId}` o `/?demo=1`). La PWA de prueba muestra un aviso
  breve; no presenta el detalle del evento.
- Las simulaciones (opción 6, botón **Simular crecida** y opción 7) reutilizan
  las plantillas de una alerta real, con emojis, y no incluyen «simulacro» ni
  «prueba» en el texto visible. El payload conserva el nivel y la URL
  `/?simulacion=...` o `/?demo=1`. Antes de producción debe decidirse si se
  vuelven a etiquetar como simulacro.
- Si el navegador no soporta Service Workers, notificaciones o Push API, la PWA
  lo indica y deshabilita el botón. El backend y la consola siguen funcionando
  con normalidad.

## 7. Solución de problemas

### `Invoke-RestMethod` falla con un error de referencia o responde 401/403

Las rutas administrativas ahora requieren JWT con rol `ADMIN`: primero hay que
iniciar sesión en `POST /api/auth/login` y enviar
`Authorization: Bearer <token>`. Un `403` indica que la cuenta no es `ADMIN` o
que el token venció.

Si el comando usaba `X-Admin-Token`, ese encabezado ya solo aplica a las rutas
heredadas `POST /api/push/demo` y
`GET /api/alertas/suscripciones/{reachId}`. Para el resto, iniciar sesión:

```powershell
$cuerpo = @{ identificador = "admin"; password = "la-contraseña-local" } | ConvertTo-Json
$sesion = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/auth/login" `
  -ContentType "application/json" -Body $cuerpo
# usar $sesion.token en el encabezado Authorization
```

### La PWA dice que el navegador no soporta Service Workers

Se está abriendo por `http://` en una IP o dominio que no es `localhost`. Los
Service Workers y las notificaciones requieren HTTPS. Use la URL del túnel
`https://...trycloudflare.com`.

### El celular muestra «No tienes permiso para esa operación» o el login responde 403

Si la PWA se abre desde el túnel, el navegador envía
`Origin: https://...trycloudflare.com`. Si ese origen no está autorizado,
Spring rechaza el `POST` con `403 Invalid CORS request` y la PWA lo muestra
como «No tienes permiso para esa operación». Para la demo se puede autorizar
todo el dominio de túneles temporales:

```properties
ALERTA_CORS_ORIGENES=http://localhost:8081,http://127.0.0.1:8081,https://*.trycloudflare.com
```

`SecurityConfig` acepta orígenes exactos y patrones. El patrón
`https://*.trycloudflare.com` es solo para la demo; en producción use la URL
exacta. Reinicie el backend después de cambiar la lista.

`scripts/iniciar-demo.ps1` inicia el túnel primero, lee la URL y la agrega
automáticamente antes de arrancar el backend. **Cada ejecución crea un túnel
nuevo**: si el puerto 8081 ya está ocupado, el script ahora se detiene antes de
abrir el túnel para no dejar una URL que el backend en ejecución no autorizó.
Cierre la instancia anterior y vuelva a ejecutar el script, o use una URL que
ya esté autorizada.

### `Registration failed - push service error`

El navegador no pudo contactar al servicio push. En Brave revise que los Shields
no bloqueen el sitio y que los servicios push de Google estén habilitados; para
la demostración se recomienda Chrome en Android. También revise VPN, proxy o
antivirus.

### La notificación no aparece en la PC

Verifique que el dispositivo esté activo (la PWA debe mostrar «Activadas»), que
la simulación (opción 6) o el simulacro global (opción 7) informen `Enviados`
mayor que cero y que Windows no tenga bloqueadas las notificaciones del navegador
o el Asistente de concentración.

Con la pestaña visible, FCM entrega el mensaje a la página y no al service
worker. La PWA muestra un aviso flotante dentro de la página y reenvía el
contenido al service worker (`MOSTRAR_NOTIFICACION`) para que él muestre la
notificación del sistema con las mismas opciones que en segundo plano. La
notificación usa `renotify`, así que cada simulacro vuelve a avisar aunque
reemplace a la anterior del mismo río.

Si aparece el aviso flotante pero no la notificación del sistema, el push sí
llegó: revise la configuración de notificaciones de Windows o del navegador.
Si no aparece ninguno de los dos, el mensaje no llegó desde FCM; revise
`Enviados`, el token y la configuración de Brave o Chrome.

En Brave, active **«Usar servicios de Google para la mensajería push»** en
`brave://settings/privacy` y recargue la PWA. Brave desactiva FCM por defecto:
el token puede quedar registrado y FCM puede aceptar el envío, pero la
notificación nunca llega al navegador. Tras recargar, la PWA vuelve a inicializar
Firebase Messaging para atender también los mensajes con la pestaña en primer
plano; antes solo reasignaba el token y el aviso se perdía si la página estaba
visible.
