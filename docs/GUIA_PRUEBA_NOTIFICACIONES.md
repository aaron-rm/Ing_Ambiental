# Guía para ejecutar la prueba de notificaciones

**Dirigida a:** compañeros del equipo y agentes de IA que retomen el proyecto.  
**Última actualización:** 11/11/2026.<br>
**Alcance:** arrancar el backend con el catálogo de cuatro ríos, iniciar sesión,
comprobar la PWA, ejecutar la demo push y verificar que la arquitectura no
depende de la consola.

> **Para la presentación del 75 % (rama `frontend-final`):** la interfaz nueva
> es la aplicación React/Vite de `frontend/`, servida en
> `http://localhost:5173`. El lanzador principal en Fedora es
> `bash iniciar.sh`, que también abre el túnel HTTPS hacia `5173`. La guía
> específica está en [`GUIA_PRESENTACION_FEDORA.md`](GUIA_PRESENTACION_FEDORA.md).
> Las secciones de esta guía que mencionan `8081` describen el backend y la PWA
> de prueba antigua; siguen siendo válidas para validar el canal push.

> La PWA de `src/main/resources/static/` **no es el frontend definitivo**. Sirve
> para probar el flujo mínimo (login, catálogo, suscripción y notificaciones).
> El frontend definitivo es la aplicación React de `frontend/`.

---

## 1. Antes de empezar

Necesitas:

- Java 21 o superior.
- PostgreSQL iniciado y la base `alerta_inundaciones` disponible.
- PowerShell en Windows o una terminal bash en Linux.
- Un navegador Chrome para la prueba de escritorio.
- Opcional para push real:
  - proyecto Firebase con Cloud Messaging;
  - cuenta de servicio de Firebase;
  - clave VAPID;
  - un celular Android o un iPhone con iOS 16.4 o superior;
  - `cloudflared` para exponer el backend por HTTPS.

Comprueba que PostgreSQL escucha:

```powershell
Get-NetTCPConnection -LocalPort 5432 -State Listen
```

Comprueba que el puerto del backend está libre:

```powershell
Get-NetTCPConnection -LocalPort 8081 -State Listen
```

Si hay un proceso viejo ocupando `8081`, ciérralo antes de continuar.

---

## 2. Crear `.env.demo`

El archivo `.env.demo` no se versiona. Se crea a partir del ejemplo:

```powershell
Copy-Item .env.demo.example .env.demo
notepad .env.demo
```

Para una prueba mínima sin push, completa solo:

```properties
DB_URL=jdbc:postgresql://localhost:5432/alerta_inundaciones
DB_USERNAME=postgres
DB_PASSWORD=tu_contraseña_local
ALERTA_JWT_SECRET=un-secreto-de-al-menos-32-caracteres
ALERTA_ADMIN_IDENTIFICADOR=admin
ALERTA_ADMIN_PASSWORD=admin-local-clave
FIREBASE_ENABLED=false
```

> **Credencial de desarrollo:** `admin-local-clave` se usa por convención únicamente para desarrollo y pruebas locales. Para producción debe cambiarse y proveerse de forma segura.

Deja las variables de Firebase vacías. El backend arrancará igual; solo
deshabilitará FCM.

Reglas:

- No subas `.env.demo` al repositorio.
- No escribas contraseñas, secretos ni tokens reales en la PWA ni en la
  documentación.
- No subas el JSON de la cuenta de servicio.
- La cuenta administradora de demostración vive solo en `.env.demo`; el registro
  público de la PWA y de la consola siempre crea usuarios `USER`.
- `ALERTA_ADMIN_TOKEN` ya no es necesario para la consola ni para la PWA: solo
  se usa en las dos rutas heredadas `POST /api/push/demo` y
  `GET /api/alertas/suscripciones/{reachId}`.

---

## 3. Arranque

### Fedora / Linux (recomendado para la presentación)

```bash
cp .env.demo.example .env.demo   # solo si aún no existe
bash iniciar.sh
```

El lanzador instala las dependencias de `frontend/` si faltan, arranca el backend
en `8081`, Vite en `5173`, verifica el catálogo y abre el túnel HTTPS hacia
`5173`. Abre `http://localhost:5173` en la PC y la URL del túnel en el celular.
Para una prueba solo de PC: `bash iniciar.sh --sin-tunel`.

La guía detallada es
[`GUIA_PRESENTACION_FEDORA.md`](GUIA_PRESENTACION_FEDORA.md).

### Windows (apoyo)

```powershell
.\scripts\iniciar-demo.ps1
```

El script carga `.env.demo`, inicia el frontend React, el backend y el túnel
hacia `5173`. Si PowerShell bloquea el script:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-demo.ps1
```

### Arranque manual (solo backend)

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/alerta_inundaciones"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "tu_contraseña_local"
$env:ALERTA_JWT_SECRET = "un-secreto-de-al-menos-32-caracteres"
$env:ALERTA_ADMIN_IDENTIFICADOR = "admin"
$env:ALERTA_ADMIN_PASSWORD = "admin-local-clave"
.\mvnw.cmd spring-boot:run
```

### Lanzador Linux antiguo

`scripts/iniciar-demo.sh` servía la PWA de prueba en `8081`; para la interfaz
React de la presentación use `iniciar.sh`. Se conserva por compatibilidad. El
lanzador nuevo también relanza en Konsole/GNOME Terminal/xterm si se ejecuta con
doble clic y abre `http://localhost:5173`.

Si no hay PostgreSQL local, se puede usar un contenedor:

```bash
podman run -d --name alerta-postgres \
  -e POSTGRES_DB=alerta_inundaciones -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=contrasena-local-de-ejemplo \
  -p 127.0.0.1:5432:5432 -v alerta-pgdata:/var/lib/postgresql/data \
  docker.io/library/postgres:16-alpine
```

`DB_PASSWORD` en `.env.demo` debe coincidir con `POSTGRES_PASSWORD`. En Linux,
`./mvnw` puede fallar con «Permiso denegado»: usar `bash mvnw`.

Abre:

```text
http://localhost:5173
```

Esa es la interfaz React de la presentación. La PWA de prueba antigua sigue en
`http://localhost:8081` y no debe usarse como pantalla principal.

En la consola del backend debe aparecer el menú de acceso:

```text
  1. Iniciar sesion
  2. Registrarme
  0. Salir de la consola
```

Tras iniciar sesión con una cuenta `USER`, el menú es:

```text
  1. Ver catalogo de rios y mis rios seguidos
  2. Consultar panel de un rio (caudal y lluvia)
  3. Seguir un rio
  4. Dejar de seguir un rio
  5. Ver mis rios seguidos
  0. Cerrar sesion
  9. Salir de la consola
```

Con una cuenta `ADMIN` aparecen además:

```text
  6. Simular una crecida y enviar push a sus seguidores
  7. Enviar notificacion push de demostracion
```

La opción `6` es la prueba principal: simula una crecida en un río y envía el
push solo a los dispositivos de quienes lo siguen. La opción `7` es el simulacro
global. El menú se repite hasta cerrar sesión o salir.

> **Texto visible de las notificaciones:** desde el 25/10/2026 las simulaciones
> (opción `6`, botón **Simular crecida** y opción `7`) usan el mismo texto
> visible que una alerta real, con emojis. La notificación ya no dice
> «simulacro» ni «prueba». La consola, la PWA y la URL
> (`/?simulacion=...`, `/?demo=1`) siguen identificando la operación como
> simulacro. Es un comportamiento deliberado para la demostración en clase;
> antes de producción debe revisarse si conviene volver a etiquetarlas.

---

## 4. Prueba A: PWA sin Firebase

Esta prueba no necesita Firebase ni celular.

1. Abre `http://localhost:8081`.
2. Inicia sesión o crea una cuenta; el registro siempre crea un `USER`.
3. Comprueba que la PWA muestra:
   - la lista de los cuatro ríos del catálogo;
   - botones para suscribirse o dejar de seguir un río;
   - una sección para activar notificaciones;
   - ninguna vista de caudal, lluvia, hidrograma ni recomendaciones.
4. Como Firebase no está configurado, al pulsar **Activar notificaciones** es
   normal ver:

```text
No disponibles
No se pudo inicializar Firebase; revise los logs del servidor.
```

Esto demuestra que la PWA es solo un cliente de prueba y que el backend sigue
funcionando sin FCM.

### Comprobar la API

```powershell
Invoke-RestMethod http://localhost:8081/api/rios
Invoke-RestMethod http://localhost:8081/api/push/config
```

Resultados esperados:

- `/api/rios` devuelve los cuatro ríos con `rioId`, `reachId` y
  `tieneGeometria: true`.
- `/api/push/config` devuelve `habilitado: false` y un `motivo` si FCM no está
  configurado. La respuesta **ya no incluye `reachId`**: el dispositivo se
  registra con JWT y las alertas se enrutan según los ríos que sigue el usuario.

### Comprobar la opción 7 sin Firebase

Inicia sesión en la consola con la cuenta `ADMIN` y elige `7`.

- Si falta `ALERTA_ADMIN_PASSWORD`, no se crea el administrador y verás una
  advertencia al arrancar; usa una cuenta `USER` o configura la contraseña.
- Si FCM no está configurado, la API puede devolver `409` con el motivo. Es el
  comportamiento esperado.
- Si no hay dispositivos registrados, la consola indicará que no hay
  suscriptores.

### Verificación de referencia (23/10/2026, Linux)

Esta sección se comprobó de extremo a extremo en modo local con PostgreSQL 16 en
un contenedor Podman. Resultados obtenidos, útiles como referencia:

- `/api/rios`: los cuatro ríos (`Río Abajo` 710039498, `Río Curundú` 710037516,
  `Río Juan Díaz` 710051399, `Río Tocumen` 670053261), todos con
  `tieneGeometria: true`.
- `/api/push/config`: `habilitado: false`, con motivo «FCM está deshabilitado
  por configuración» y sin `reachId` en la respuesta.
- Login con la cuenta `ADMIN` y seguimiento del primer río correctos;
  `/api/usuarios/me/rios` sin JWT devuelve `401`.
- Panel de Río Abajo con datos reales de GEOGloWS y Open-Meteo: nivel `NORMAL`,
  0.9 m³/s, máximo pronosticado 13.5 m³/s, lluvia 13.9 mm (24 h) y 33.4 mm
  (72 h), y 2 recomendaciones.
- `GET /api/alertas/rio/{rioId}` con JWT devolvió `siguiendo: true` para el río
  seleccionado; sin JWT devolvió `siguiendo: null`.
- La segunda consulta del mismo panel dentro de los 15 minutos usó la caché de
  GEOGloWS (no se repitió la llamada externa).

Queda pendiente repetir la misma verificación en Windows con
`scripts/iniciar-demo.ps1`.

---

## 5. Limpiar la PWA vieja o el aviso «Sin conexión»

Si el navegador muestra la interfaz anterior o «Sin conexión», está usando el
Service Worker viejo. El backend actual ya sirve la página nueva, pero el
navegador puede conservar la copia anterior.

En Chrome de escritorio:

1. Abre `http://localhost:8081`.
2. Pulsa `F12`.
3. Ve a **Application → Service Workers**.
4. Pulsa **Unregister** para el Service Worker de `localhost:8081`.
5. Ve a **Application → Storage → Clear site data**.
6. Cierra la pestaña y abre de nuevo `http://localhost:8081` con `Ctrl + F5`.

En el celular:

- borra los datos del sitio desde la configuración del navegador; o
- desinstala la PWA de la pantalla de inicio y vuelve a abrir la URL.

Para una comprobación rápida, abre una ventana de incógnito. Si allí aparece la
página nueva, el problema es la caché del navegador.

---

## 6. Prueba B: push real con Firebase

### 6.1 Configurar Firebase

1. Crea un proyecto en Firebase Console.
2. Agrega una app web.
3. Activa Cloud Messaging.
4. En **Cloud Messaging → Web Push certificates**, genera una clave VAPID.
5. En **Project settings → Service accounts**, genera una cuenta de servicio y
   descarga el JSON.
6. Guarda el JSON fuera del repositorio.

Completa `.env.demo`:

```properties
FIREBASE_ENABLED=true
FIREBASE_CREDENTIALS_PATH=C:\ruta\segura\service-account.json
FIREBASE_API_KEY=...
FIREBASE_AUTH_DOMAIN=...
FIREBASE_PROJECT_ID=...
FIREBASE_MESSAGING_SENDER_ID=...
FIREBASE_APP_ID=...
FIREBASE_VAPID_KEY=...
```

Los seis datos públicos deben ser los de la app web de Firebase. La ruta del
JSON es privada y no debe versionarse.

### 6.2 Arrancar y comprobar FCM

```powershell
.\scripts\iniciar-demo.ps1
```

Comprueba:

```powershell
Invoke-RestMethod http://localhost:8081/api/push/config
```

Debe devolver:

```json
{
  "habilitado": true,
  "motivo": null
}
```

El resto de campos son los datos públicos de Firebase. Si sigue en `false`,
revisa el `motivo`, la ruta del JSON y las claves públicas.

### 6.3 Activar en el dispositivo

En la PC:

1. Abre `http://localhost:5173` e inicia sesión como `ADMIN` (o con una cuenta
   distinta de la del celular).
2. Si vas a probar el push en la PC, sigue al menos un río y pulsa **Activar
   notificaciones**; acepta el permiso y verifica **Activadas**.
3. Para la demo con celular, la PC se usa como `ADMIN` y el celular como `USER`.

En el celular:

1. Abre la URL HTTPS del túnel que apunta a `5173`, no
   `http://192.168.x.x:5173`.
2. Registra una cuenta `USER` distinta de la del `ADMIN`, sigue al menos un río.
3. Pulsa **Activar notificaciones**.
4. Acepta el permiso.
5. Debe aparecer **Activadas**.

> El backend desactiva los tokens web anteriores del mismo usuario al registrar
> uno nuevo. Por eso conviene usar una cuenta `USER` en el celular y la cuenta
> `ADMIN` solo en la PC.

Android: usa Chrome.  
iPhone/iPad: requiere iOS 16.4+ y agregar la página a la pantalla de inicio.

### 6.4 Simular una crecida por río

La forma principal de probar el push es simular una crecida sobre un río que
tenga seguidores:

1. En el celular (o en la PWA antigua), suscríbete a un río con la cuenta que
   recibirá la notificación.
2. En la consola, inicia sesión con `ADMIN` y elige la opción `6`.
3. Selecciona el mismo río. El caudal por defecto del menú es `75.0 m³/s`.
4. La consola debe mostrar cuántos dispositivos siguen el río, cuántos envíos
   salieron y cuántos fallaron.
5. La notificación debe llegar solo a los dispositivos de los usuarios que
   siguen ese río. Si nadie lo sigue, la simulación se ejecuta pero no envía.
   El texto visible usa el formato de una alerta real con emojis
   (`🚨 ALERTA DE INUNDACIÓN 🌊` o `⚠️ AVISO DE CRECIDA...` según el nivel
   simulado); no incluye «simulacro» ni «prueba».
6. En la interfaz React (`http://localhost:5173`), el administrador también
   puede pulsar **Simular crecida y notificar** en el panel del río o **Simular
   crecida** en **Mis ríos**, sin usar la consola. Si FCM no está configurado,
   la interfaz muestra el aviso y no ofrece el botón: no tiene sentido simular
   un envío que no puede salir.

### 6.5 Enviar la demostración global

En la consola del backend, inicia sesión con la cuenta `ADMIN` y elige `7`.

Debe aparecer algo similar a:

```text
Dispositivos activos: 1
Enviados:             1
Fallidos:             0
```

La notificación debe llegar al dispositivo. Usa el mismo formato visible de una
emergencia real (`🚨 ALERTA DE INUNDACIÓN 🌊`) y tampoco incluye «simulacro» ni
«demostración» en el texto. Si no llega:

- revisa que la PWA muestre **Activadas**;
- revisa el permiso de notificaciones del sistema;
- revisa que no esté activo el modo concentración o No molestar;
- en Brave, activa **«Usar servicios de Google para la mensajería push»** en
  `brave://settings/privacy` y recarga la PWA: Brave bloquea FCM por defecto, así
  que FCM puede aceptar el envío sin que la notificación llegue;
- confirma que `FIREBASE_CREDENTIALS_PATH` y las claves públicas son del mismo
  proyecto;
- mira los logs de `FirebasePushSender` y `PushService`.

### 6.6 Alternativa por API

```powershell
$cuerpo = @{ identificador = "admin"; password = "tu_contraseña_local" } | ConvertTo-Json
$sesion = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/auth/login" `
  -ContentType "application/json" -Body $cuerpo

# Simulación dirigida a los seguidores de un río (reachId real del catálogo)
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/alertas/simular?reachId=710039498" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }

# Demostración global
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/admin/push/demo" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }
```

La simulación responde con `alerta`, `dispositivos`, `enviados` y `fallidos`.
La demostración global responde:

```json
{
  "dispositivos": 1,
  "enviados": 1,
  "fallidos": 0
}
```

---

## 7. Prueba C: backend sin consola

Esta prueba confirma que la consola es un cliente opcional y que el backend no
depende de ella.

En `.env.demo`:

```properties
ALERTA_DEMO_CONSOLA_HABILITADA=false
```

Opcionalmente, para una instancia totalmente aislada y sin dobles
evaluaciones sobre la misma base:

```properties
ALERTA_MONITOREO_HABILITADO=false
ALERTA_CARGAR_CATALOGO=false
FIREBASE_ENABLED=false
```

Reinicia el backend. Debe ocurrir:

- no aparece el menú interactivo;
- `http://localhost:8081` sigue mostrando la PWA (login, catálogo y push);
- `/api/rios` y `/api/push/config` siguen respondiendo;
- el monitoreo sigue funcionando si se deja habilitado, y los endpoints siguen
  disponibles.

La consola no accede a repositorios ni a la base: usa `BackendApiClient` contra
la API HTTP.

Verificación de referencia (22/10/2026, con las variables equivalentes de esa
fecha): una instancia aislada en `8082` con la consola, el monitoreo, la carga
del catálogo y FCM desactivados arrancó, sirvió la PWA y respondió `/api/rios`
sin mostrar el menú. Se detuvo al terminar y no se tocó la instancia principal
en `8081`.

---

## 8. Prueba D: pruebas automatizadas

```powershell
.\mvnw.cmd package
```

Resultado esperado:

```text
Tests run: 82, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

En Linux, si `./mvnw test` falla con «Permiso denegado», usar:

```bash
bash mvnw test
```

Validar JavaScript:

```powershell
node --check src/main/resources/static/app.js
node --check src/main/resources/static/firebase-messaging-sw.js
```

---

## 9. Prueba E: API básica

Con el backend corriendo:

```powershell
# Catálogo de cuatro ríos (rioId, reachId, tieneGeometria)
Invoke-RestMethod http://localhost:8081/api/rios

# Geometría real para el mapa (GeoJSON simplificado)
Invoke-RestMethod http://localhost:8081/api/rios/mapa

# Configuración pública de push (sin reachId)
Invoke-RestMethod http://localhost:8081/api/push/config

# Estado por id interno del río (toma un rioId del catálogo)
Invoke-RestMethod http://localhost:8081/api/alertas/rio/1

# Estado por reachId de GEOGloWS
Invoke-RestMethod http://localhost:8081/api/alertas/tramo/710039498

# Umbrales actuales
Invoke-RestMethod http://localhost:8081/api/alertas/config-riesgo
```

El `rioId` del catálogo no tiene por qué ser `1`; se obtiene de `/api/rios`.

Acceso y selección de ríos:

```powershell
# Registro (siempre crea USER) y login
$cuerpo = @{ identificador = "alumno@ejemplo.com"; password = "clave-de-prueba-123" } | ConvertTo-Json
$sesion = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/auth/login" `
  -ContentType "application/json" -Body $cuerpo

# Mis ríos seguidos
Invoke-RestMethod -Uri "http://localhost:8081/api/usuarios/me/rios" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }

# Seguir un río (idempotente)
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/usuarios/me/rios/1" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }

# Panel con JWT: debe incluir siguiendo = true/false
Invoke-RestMethod -Uri "http://localhost:8081/api/alertas/rio/1" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }

# Panel sin JWT: siguiendo = null y la consulta sigue siendo pública
Invoke-RestMethod -Uri "http://localhost:8081/api/alertas/rio/1"
```

Para comprobar la caché, consulta dos veces el mismo panel dentro de
`GEOGLOWS_CACHE_MINUTOS` (15 por defecto). La segunda respuesta debe conservar la
misma `fechaEmisionCaudal` y el log no debe repetir la llamada externa. Si
GEOGloWS falla, el backend reutiliza la última respuesta y registra una
advertencia.

Mantenimiento del catálogo (solo rol `ADMIN`). Antes hay que iniciar sesión con
la cuenta administradora:

```powershell
$admin = @{ identificador = "admin"; password = "tu_contraseña_local" } | ConvertTo-Json
$sesionAdmin = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/auth/login" `
  -ContentType "application/json" -Body $admin

# Actualizar un río
$cuerpo = @{
  nombre = "Río de ejemplo"
  geoglowsId = 710039498
  latitud = 9.08
  longitud = -79.28
} | ConvertTo-Json

Invoke-RestMethod -Method Put `
  -Uri http://localhost:8081/api/admin/rios/1 `
  -Headers @{ Authorization = "Bearer $($sesionAdmin.token)" } `
  -ContentType "application/json" `
  -Body $cuerpo

# Crear otro río
$cuerpo = @{
  nombre = "Río de ejemplo 2"
  geoglowsId = 710039499
  latitud = 8.50
  longitud = -80.00
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8081/api/admin/rios `
  -Headers @{ Authorization = "Bearer $($sesionAdmin.token)" } `
  -ContentType "application/json" `
  -Body $cuerpo
```

Reemplace `710039499` y las coordenadas por un tramo real de GEOGloWS. El
backend acepta cualquier `reachId` de GEOGloWS, pero el catálogo acordado no se
debe ampliar con valores inventados. El monitoreo programado recorre todos los
ríos configurados. Las rutas heredadas `POST /api/push/demo` y
`GET /api/alertas/suscripciones/{reachId}` siguen existiendo solo con
`X-Admin-Token` por compatibilidad; no se usan en esta guía.

---

## 10. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `APPLICATION FAILED TO START` por `ObjectMapper` | Se está usando un JAR viejo o se volvió a inyectar `com.fasterxml.jackson.databind.ObjectMapper` | Recompilar con `.\mvnw.cmd package`; el cliente crea su propio mapper Jackson 2 |
| `No se pudieron cargar las credenciales de Firebase: C:\ruta\segura\...` | Ruta de ejemplo sin cambiar | Completar `FIREBASE_CREDENTIALS_PATH` o dejar FCM deshabilitado |
| Página vieja o «Sin conexión» | Service Worker anterior | Desregistrar el SW y limpiar datos del sitio |
| `401` en la consola o en la PWA | Token ausente o vencido (24 h) | Volver a iniciar sesión |
| `403` en opción 6 o 7 | La cuenta no tiene rol `ADMIN` | Iniciar sesión con la cuenta administradora |
| `403` en el celular o al entrar por el túnel | El origen del túnel no está autorizado; Spring responde `Invalid CORS request` | Agregar la URL exacta o `https://*.trycloudflare.com` (solo demo) y reiniciar. El script se detiene si `8081` está ocupado para no dejar un túnel sin autorizar |
| `403` en las rutas heredadas | `ALERTA_ADMIN_TOKEN` ausente o distinto | Definir el mismo token en backend y cliente; no aplica al resto de la API |
| `409` en `/api/admin/push/demo` o `/api/alertas/simular` | FCM no configurado | Configurar Firebase; es esperado sin credenciales |
| `409` al registrar token | Token asociado a otro `deviceId` | Limpiar datos del sitio o usar el dispositivo original |
| `410` | Token FCM inválido | Volver a activar notificaciones |
| `429` | Límite de 60 s entre pruebas | Esperar y reintentar |
| La simulación informa 0 dispositivos | El dispositivo se desactivó al cerrar sesión o se borró el token | Volver a iniciar sesión: la PWA reactiva el push si el permiso sigue concedido; si no, pulsar «Activar notificaciones» |
| No llega la notificación | Permiso, FCM o dispositivo | Revisar permisos, claves y logs; con la pestaña visible debe aparecer un aviso flotante y «Notificación recibida en este dispositivo» |
| No llega en Brave | Brave bloquea FCM por defecto | `brave://settings/privacy` → activar «Usar servicios de Google para la mensajería push», recargar y repetir |
| `8081` ocupado | Proceso anterior | Detener el proceso y volver a arrancar |
| `localhost` funciona, celular no | Falta HTTPS | Usar la URL del túnel, no la IP local |

---

## 11. Checklist de aceptación

- [ ] El backend inicia y queda escuchando en `8081`.
- [ ] La interfaz React abre en `http://localhost:5173` y el proxy `/api`
      responde a través de Vite.
- [ ] `GET /api/rios` responde con los cuatro ríos y `GET /api/rios/mapa` con la
      geometría real.
- [ ] El login `ADMIN` devuelve un JWT; el registro público crea solo `USER`.
- [ ] `GET /api/push/config` responde sin `reachId`.
- [ ] La PWA antigua pide iniciar sesión y muestra el catálogo con botones de
      suscripción.
- [ ] Seguir un río es idempotente y se refleja en `/api/usuarios/me/rios`.
- [ ] El panel devuelve `siguiendo` con JWT y `null` sin sesión.
- [ ] La segunda consulta del panel dentro del TTL usa la caché de GEOGloWS.
- [ ] El Service Worker viejo fue eliminado.
- [ ] Sin Firebase, la PWA muestra «No disponibles» y el backend sigue vivo.
- [ ] Sin Firebase, la sección de administración avisa que no se puede simular y
      no muestra botones de envío.
- [ ] La opción `6` y el botón **Simular crecida** existen para el rol `ADMIN`.
- [ ] Con Firebase, `/api/push/config` devuelve `habilitado: true`.
- [ ] El dispositivo muestra «Activadas».
- [ ] La simulación informa dispositivos, enviados y fallidos y solo notifica a
      los seguidores del río.
- [ ] La notificación de simulación y la demo global usan el texto realista con
      emojis y no incluyen «simulacro» ni «prueba» en el texto visible.
- [ ] La opción `7` envía el simulacro global a todos los dispositivos.
- [ ] Con `ALERTA_DEMO_CONSOLA_HABILITADA=false` el backend sigue funcionando.
- [ ] `.\mvnw.cmd package` termina con 82 pruebas y `BUILD SUCCESS`.
- [ ] `cd frontend && npm run build` termina sin errores.
- [ ] `cd frontend && npm run lint` no reporta errores nuevos.
- [ ] `node --check` no reporta errores en la PWA antigua.
- [ ] No se versionaron `.env.demo` ni credenciales.

---

## 12. Relación con el frontend definitivo

La PWA de prueba **no debe reutilizarse como frontend definitivo**. El backend
expone contratos HTTP que el equipo de frontend puede consumir:

- `POST /api/auth/registro`, `POST /api/auth/login` y `GET /api/auth/me`;
- `GET /api/rios` y `GET /api/rios/mapa` (GeoJSON con `properties.rioId`);
- `GET /api/usuarios/me/rios` y `POST`/`DELETE /api/usuarios/me/rios/{rioId}`;
- `GET /api/alertas/rio/{rioId}` y `/tramo/{reachId}` (incluyen `siguiendo` con
  JWT);
- historial, detalle, configuración de riesgo y endpoints push con JWT;
- mantenimiento del catálogo bajo `/api/admin/rios` (JWT + rol `ADMIN`).

El frontend definitivo elige cualquier río llamando a `/api/rios` y usa su
`rioId`. El push se registra con JWT (sin `reachId`) y el backend lo enruta
según los ríos que sigue cada usuario.

Las notificaciones de simulación usan el mismo texto visible que una alerta
real, con emojis, por requisito de la demostración en clase. El frontend puede
distinguirlas por la URL (`/?simulacion={reachId}` o `/?demo=1`) y mostrar un
aviso de simulación si lo considera necesario. Antes de producción debe
decidirse si se vuelven a etiquetar como simulacro en el texto visible.

Fricciones conocidas para la integración:

- CORS es configurable con `ALERTA_CORS_ORIGENES`, pero la URL exacta del
  frontend aún no existe.
- Los umbrales de caudal y lluvia son globales, no por río.
- Algunos errores pueden volver en texto plano.
- Las dos rutas heredadas con `X-Admin-Token` siguen existiendo por
  compatibilidad y se retirarán en una entrega futura.
- El push real no se ha probado de extremo a extremo en un dispositivo físico
  desde este entorno de desarrollo.

El contrato detallado está en [`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md).
Las reglas de arquitectura están en [`ARQUITECTURA.md`](ARQUITECTURA.md).
