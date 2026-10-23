# Guía para ejecutar la prueba de notificaciones

**Dirigida a:** compañeros del equipo y agentes de IA que retomen el proyecto.  
**Última actualización:** 23/10/2026.  
**Alcance:** arrancar el backend con el catálogo de cuatro ríos, iniciar sesión,
comprobar la PWA, ejecutar la demo push y verificar que la arquitectura no
depende de la consola.

> La PWA de `src/main/resources/static/` **no es el frontend definitivo**. Sirve
> para probar el flujo completo (login, mapa, panel y notificaciones). El
> frontend definitivo lo construirá otro equipo desde cero.

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
ALERTA_ADMIN_PASSWORD=una_contraseña_local
FIREBASE_ENABLED=false
```

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

### Opción recomendada

```powershell
.\scripts\iniciar-demo.ps1
```

El script:

1. Carga `.env.demo` en el proceso.
2. Abre el backend en una ventana nueva.
3. Si `cloudflared` está instalado, abre un túnel HTTPS.
4. Avisa si faltan credenciales o la ruta de Firebase.

Si PowerShell bloquea el script:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-demo.ps1
```

### Arranque manual

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/alerta_inundaciones"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "tu_contraseña_local"
$env:ALERTA_JWT_SECRET = "un-secreto-de-al-menos-32-caracteres"
$env:ALERTA_ADMIN_IDENTIFICADOR = "admin"
$env:ALERTA_ADMIN_PASSWORD = "tu_contraseña_local"
.\mvnw.cmd spring-boot:run
```

### Arranque en Linux

El script de PowerShell requiere `pwsh`, que no viene instalado en la mayoría de
distribuciones. El equivalente en bash carga `.env.demo` y arranca la aplicación
con la consola interactiva:

```bash
bash scripts/iniciar-demo.sh            # consola interactiva y navegador
bash scripts/iniciar-demo.sh --tunel    # ademas abre cloudflared si esta instalado
```

Con doble clic (sin terminal) el script se relanza en Konsole, GNOME Terminal o
xterm, y abre el navegador en `http://localhost:8081` cuando el backend
responde. Si el puerto 8081 ya está ocupado, solo abre la página y no lanza una
segunda instancia.

Si no hay PostgreSQL local, se puede usar un contenedor:

```bash
podman run -d --name alerta-postgres \
  -e POSTGRES_DB=alerta_inundaciones -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=alerta_local_dev \
  -p 127.0.0.1:5432:5432 -v alerta-pgdata:/var/lib/postgresql/data \
  docker.io/library/postgres:16-alpine
```

`DB_PASSWORD` en `.env.demo` debe coincidir con `POSTGRES_PASSWORD`. En Linux,
`./mvnw` puede fallar con «Permiso denegado»: usar `bash mvnw`.

Abre:

```text
http://localhost:8081
```

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
  6. Simular una crecida (no envia push)
  7. Evaluar rios con datos reales (puede enviar push)
  8. Enviar notificacion push de demostracion
```

La opción `8` es la demostración push. El menú se repite hasta cerrar sesión o
salir.

---

## 4. Prueba A: PWA sin Firebase

Esta prueba no necesita Firebase ni celular.

1. Abre `http://localhost:8081`.
2. Inicia sesión o crea una cuenta; el registro siempre crea un `USER`.
3. Comprueba que la PWA muestra:
   - el mapa y la lista de los cuatro ríos del catálogo;
   - el panel de caudal y lluvia al elegir cualquier río, esté o no seguido;
   - botones para seguir o dejar de seguir un río;
   - una sección para activar notificaciones.
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

### Comprobar la opción 8 sin Firebase

Inicia sesión en la consola con la cuenta `ADMIN` y elige `8`.

- Si falta `ALERTA_ADMIN_PASSWORD`, no se crea el administrador y verás una
  advertencia al arrancar; usa una cuenta `USER` o configura la contraseña.
- Si FCM no está configurado, la API puede devolver `409` con el motivo. Es el
  comportamiento esperado.
- Si no hay dispositivos registrados, la consola indicará que no hay
  suscriptores.

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

1. Abre `http://localhost:8081` e inicia sesión o crea una cuenta.
2. Sigue al menos un río (el push depende de los ríos que sigue el usuario).
3. Pulsa **Activar notificaciones**.
4. Acepta el permiso.
5. Debe aparecer **Activadas**.

En el celular:

1. Abre la URL HTTPS del túnel, no `http://192.168.x.x:8081`.
2. Inicia sesión o crea una cuenta y sigue al menos un río.
3. Pulsa **Activar notificaciones**.
4. Acepta el permiso.
5. Debe aparecer **Activadas**.

Android: usa Chrome.  
iPhone/iPad: requiere iOS 16.4+ y agregar la página a la pantalla de inicio.

### 6.4 Enviar la demostración

En la consola del backend, inicia sesión con la cuenta `ADMIN` y elige `8`.

Debe aparecer algo similar a:

```text
Dispositivos activos: 1
Enviados:             1
Fallidos:             0
```

La notificación debe llegar al dispositivo. Si no llega:

- revisa que la PWA muestre **Activadas**;
- revisa el permiso de notificaciones del sistema;
- revisa que no esté activo el modo concentración o No molestar;
- confirma que `FIREBASE_CREDENTIALS_PATH` y las claves públicas son del mismo
  proyecto;
- mira los logs de `FirebasePushSender` y `PushService`.

### 6.5 Alternativa por API

```powershell
$cuerpo = @{ identificador = "admin"; password = "tu_contraseña_local" } | ConvertTo-Json
$sesion = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/auth/login" `
  -ContentType "application/json" -Body $cuerpo

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/admin/push/demo" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }
```

Respuesta esperada:

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
- `http://localhost:8081` sigue mostrando la PWA (login, mapa y panel);
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
Tests run: 67, Failures: 0, Errors: 0, Skipped: 0
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
```

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
| `403` en opción 6, 7 u 8 | La cuenta no tiene rol `ADMIN` | Iniciar sesión con la cuenta administradora |
| `403` en las rutas heredadas | `ALERTA_ADMIN_TOKEN` ausente o distinto | Definir el mismo token en backend y cliente; no aplica al resto de la API |
| `409` en `/api/admin/push/demo` | FCM no configurado | Configurar Firebase; es esperado sin credenciales |
| `409` al registrar token | Token asociado a otro `deviceId` | Limpiar datos del sitio o usar el dispositivo original |
| `410` | Token FCM inválido | Volver a activar notificaciones |
| `429` | Límite de 60 s entre pruebas | Esperar y reintentar |
| No llega la notificación | Permiso, FCM o dispositivo | Revisar permisos, claves y logs; probar con la app en segundo plano |
| `8081` ocupado | Proceso anterior | Detener el proceso y volver a arrancar |
| `localhost` funciona, celular no | Falta HTTPS | Usar la URL del túnel, no la IP local |

---

## 11. Checklist de aceptación

- [ ] El backend inicia y queda escuchando en `8081`.
- [ ] `GET /api/rios` responde con los cuatro ríos y `GET /api/rios/mapa` con la
      geometría real.
- [ ] El login `ADMIN` devuelve un JWT; el registro público crea solo `USER`.
- [ ] `GET /api/push/config` responde sin `reachId`.
- [ ] La PWA pide iniciar sesión y muestra el mapa y el panel de cualquier río.
- [ ] Seguir un río es idempotente y se refleja en `/api/usuarios/me/rios`.
- [ ] El Service Worker viejo fue eliminado.
- [ ] Sin Firebase, la PWA muestra «No disponibles» y el backend sigue vivo.
- [ ] La opción `8` existe para el rol `ADMIN`.
- [ ] Con Firebase, `/api/push/config` devuelve `habilitado: true`.
- [ ] El dispositivo muestra «Activadas».
- [ ] La opción `8` informa dispositivos, enviados y fallidos.
- [ ] Con `ALERTA_DEMO_CONSOLA_HABILITADA=false` el backend sigue funcionando.
- [ ] `.\mvnw.cmd package` termina con 67 pruebas y `BUILD SUCCESS`.
- [ ] `node --check` no reporta errores.
- [ ] No se versionaron `.env.demo` ni credenciales.

---

## 12. Relación con el frontend definitivo

La PWA de prueba **no debe reutilizarse como frontend definitivo**. El backend
expone contratos HTTP que el equipo de frontend puede consumir:

- `POST /api/auth/registro`, `POST /api/auth/login` y `GET /api/auth/me`;
- `GET /api/rios` y `GET /api/rios/mapa` (GeoJSON con `properties.rioId`);
- `GET /api/usuarios/me/rios` y `POST`/`DELETE /api/usuarios/me/rios/{rioId}`;
- `GET /api/alertas/rio/{rioId}` y `/tramo/{reachId}`;
- historial, detalle, configuración de riesgo y endpoints push con JWT;
- mantenimiento del catálogo bajo `/api/admin/rios` (JWT + rol `ADMIN`).

El frontend definitivo elige cualquier río llamando a `/api/rios` y usa su
`rioId`. El push se registra con JWT (sin `reachId`) y el backend lo enruta
según los ríos que sigue cada usuario.

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
