# Guía para ejecutar la prueba de notificaciones

**Dirigida a:** compañeros del equipo y agentes de IA que retomen el proyecto.  
**Última actualización:** 22/10/2026.  
**Alcance:** arrancar el backend, comprobar la PWA mínima, ejecutar la demo push y verificar que la arquitectura no depende de la consola.

> La PWA de `src/main/resources/static/` **no es el frontend definitivo**. Solo
> sirve para comprobar permisos, Service Worker, token FCM y registro en el
> backend. El frontend definitivo lo construirá otro equipo desde cero.

---

## 1. Antes de empezar

Necesitas:

- Java 21 o superior.
- PostgreSQL iniciado y la base `alerta_inundaciones` disponible.
- PowerShell en Windows.
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
ALERTA_ADMIN_TOKEN=un-token-largo-y-aleatorio
FIREBASE_ENABLED=false
```

Deja las variables de Firebase vacías. El backend arrancará igual; solo
deshabilitará FCM.

Reglas:

- No subas `.env.demo` al repositorio.
- No escribas tokens reales en la PWA ni en la documentación.
- No subas el JSON de la cuenta de servicio.
- El token administrativo debe ser el mismo en el backend y en la consola.

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
4. Avisa si falta el token administrativo o la ruta de Firebase.

Si PowerShell bloquea el script:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar-demo.ps1
```

### Arranque manual

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/alerta_inundaciones"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "tu_contraseña_local"
$env:ALERTA_ADMIN_TOKEN = "un-token-largo-y-aleatorio"
.\mvnw.cmd spring-boot:run
```

Abre:

```text
http://localhost:8081
```

En la consola del backend debe aparecer el menú:

```text
  1. Consultar estado y pronostico real (GEOGLOWS)
  2. Suscribirse a alertas de este rio
  3. Simular crecida / inundacion (Deteccion de riesgo)
  4. Ver suscriptores registrados
  0. Enviar notificacion push de demostracion a todos
  5. Salir
```

El menú puede variar si ya existe una simulación activa. La opción `0` es la
demostración push.

---

## 4. Prueba A: PWA mínima sin Firebase

Esta prueba no necesita Firebase ni celular.

1. Abre `http://localhost:8081`.
2. Comprueba que solo aparece:
   - título «Prueba de notificaciones»;
   - explicación breve;
   - estado;
   - botón de activación;
   - mensaje de éxito o error.
3. No deben aparecer caudales, lluvia, pronósticos, mapas, riesgo, historial ni
   paneles de monitoreo.

Como Firebase no está configurado, es normal ver:

```text
No disponibles
No se pudo inicializar Firebase; revise los logs del servidor.
```

El botón queda deshabilitado. Esto demuestra que la PWA es solo un receptor de
prueba y que el backend sigue funcionando sin FCM.

### Comprobar la API

```powershell
Invoke-RestMethod http://localhost:8081/api/rios
Invoke-RestMethod http://localhost:8081/api/push/config
```

Resultados esperados:

- `/api/rios` devuelve al menos un río.
- `/api/push/config` devuelve `habilitado: false` y un `motivo` si FCM no está
  configurado.
- La respuesta incluye `reachId` (por defecto `710039498`), que la PWA de prueba
  usa para registrarse.

### Comprobar la opción 0 sin Firebase

En la consola del backend elige `0`.

- Si falta `ALERTA_ADMIN_TOKEN`, verás un aviso de configuración.
- Si el token está presente pero FCM no está configurado, la API puede devolver
  `409` con el motivo. Es el comportamiento esperado.
- Si no hay dispositivos, la consola indicará que no hay suscriptores.

---

## 5. Limpiar la PWA vieja o el aviso «Sin conexión»

Si el navegador muestra la interfaz anterior, datos hidrológicos o «Sin
conexión», está usando el Service Worker viejo. El backend actual ya sirve la
página nueva, pero el navegador puede conservar la copia anterior.

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
  "reachId": 710039498
}
```

Si sigue en `false`, revisa el `motivo`, la ruta del JSON y las claves públicas.

### 6.3 Activar en el dispositivo

En la PC:

1. Abre `http://localhost:8081`.
2. Pulsa **Activar notificaciones**.
3. Acepta el permiso.
4. Debe aparecer **Activadas**.

En el celular:

1. Abre la URL HTTPS del túnel, no `http://192.168.x.x:8081`.
2. Pulsa **Activar notificaciones**.
3. Acepta el permiso.
4. Debe aparecer **Activadas**.

Android: usa Chrome.  
iPhone/iPad: requiere iOS 16.4+ y agregar la página a la pantalla de inicio.

### 6.4 Enviar la demostración

En la consola del backend, elige `0`.

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
$env:ALERTA_ADMIN_TOKEN = "el-mismo-token-de-.env.demo"

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8081/api/push/demo `
  -Headers @{ "X-Admin-Token" = $env:ALERTA_ADMIN_TOKEN }
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
ALERTA_CREAR_RIO_INICIAL=false
FIREBASE_ENABLED=false
```

Reinicia el backend. Debe ocurrir:

- no aparece el menú interactivo;
- `http://localhost:8081` sigue mostrando la PWA;
- `/api/rios` y `/api/push/config` siguen respondiendo;
- el monitoreo sigue funcionando si se deja habilitado, y los endpoints siguen
  disponibles.

La consola no accede a repositorios ni a la base: usa `BackendApiClient` contra
la API HTTP.

Verificación de referencia (22/10/2026): una instancia aislada en `8082` con
esas cuatro variables desactivadas arrancó, sirvió la PWA y respondió
`/api/rios` sin mostrar el menú. Se detuvo al terminar y no se tocó la instancia
principal en `8081`.

---

## 8. Prueba D: pruebas automatizadas

```powershell
.\mvnw.cmd package
```

Resultado esperado:

```text
Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
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
# Río inicial
Invoke-RestMethod http://localhost:8081/api/rios

# Configuración pública de push (incluye reachId de prueba)
Invoke-RestMethod http://localhost:8081/api/push/config

# Estado por id interno del río
Invoke-RestMethod http://localhost:8081/api/alertas/rio/1

# Estado por reachId de GEOGloWS
Invoke-RestMethod http://localhost:8081/api/alertas/tramo/710039498

# Umbrales actuales
Invoke-RestMethod http://localhost:8081/api/alertas/config-riesgo
```

Actualizar un río:

```powershell
$cuerpo = @{
  nombre = "Río Pacora"
  geoglowsId = 710039498
  latitud = 9.08
  longitud = -79.28
} | ConvertTo-Json

Invoke-RestMethod -Method Put `
  -Uri http://localhost:8081/api/rios/1 `
  -ContentType "application/json" `
  -Body $cuerpo
```

Crear otro río:

```powershell
$cuerpo = @{
  nombre = "Río de ejemplo"
  geoglowsId = 710039499
  latitud = 8.50
  longitud = -80.00
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8081/api/rios `
  -ContentType "application/json" `
  -Body $cuerpo
```

Reemplace `710039499` y las coordenadas por un tramo real de GEOGloWS. El
backend acepta cualquier `reachId` de GEOGloWS y sus coordenadas. El monitoreo
programado recorre todos los ríos configurados. La consola de demostración sigue
usando el primer río; eso es una limitación de la consola, no del backend.

---

## 10. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `APPLICATION FAILED TO START` por `ObjectMapper` | Se está usando un JAR viejo o se volvió a inyectar `com.fasterxml.jackson.databind.ObjectMapper` | Recompilar con `.\mvnw.cmd package`; el cliente crea su propio mapper Jackson 2 |
| `No se pudieron cargar las credenciales de Firebase: C:\ruta\segura\...` | Ruta de ejemplo sin cambiar | Completar `FIREBASE_CREDENTIALS_PATH` o dejar FCM deshabilitado |
| Página vieja o «Sin conexión» | Service Worker anterior | Desregistrar el SW y limpiar datos del sitio |
| `403` en opción 0 o suscripciones | Token ausente o distinto | Definir el mismo `ALERTA_ADMIN_TOKEN` |
| `409` en `/api/push/demo` | FCM no configurado | Configurar Firebase; es esperado sin credenciales |
| `409` al registrar token | Token asociado a otro `deviceId` | Limpiar datos del sitio o usar el dispositivo original |
| `410` | Token FCM inválido | Volver a activar notificaciones |
| `429` | Límite de 60 s entre pruebas | Esperar y reintentar |
| No llega la notificación | Permiso, FCM o dispositivo | Revisar permisos, claves y logs; probar con la app en segundo plano |
| `8081` ocupado | Proceso anterior | Detener el proceso y volver a arrancar |
| `localhost` funciona, celular no | Falta HTTPS | Usar la URL del túnel, no la IP local |

---

## 11. Checklist de aceptación

- [ ] El backend inicia y queda escuchando en `8081`.
- [ ] `GET /api/rios` responde con un río.
- [ ] `GET /api/push/config` responde e incluye `reachId`.
- [ ] La PWA muestra solo título, explicación, estado, botón y mensaje.
- [ ] La PWA no muestra datos hidrológicos.
- [ ] El Service Worker viejo fue eliminado.
- [ ] Sin Firebase, la PWA muestra «No disponibles» y el backend sigue vivo.
- [ ] La opción `0` existe en la consola.
- [ ] Con Firebase, `/api/push/config` devuelve `habilitado: true`.
- [ ] El dispositivo muestra «Activadas».
- [ ] La opción `0` informa dispositivos, enviados y fallidos.
- [ ] Con `ALERTA_DEMO_CONSOLA_HABILITADA=false` el backend sigue funcionando.
- [ ] `.\mvnw.cmd package` termina con 51 pruebas y `BUILD SUCCESS`.
- [ ] `node --check` no reporta errores.
- [ ] No se versionaron `.env.demo` ni credenciales.

---

## 12. Relación con el frontend definitivo

La PWA de prueba **no debe reutilizarse como frontend definitivo**. El backend
expone contratos HTTP que el equipo de frontend puede consumir:

- `GET /api/rios` y `POST`/`PUT /api/rios`;
- `GET /api/alertas/rio/{rioId}` y `/tramo/{reachId}`;
- historial, detalle, configuración de riesgo y endpoints push.

El frontend definitivo puede elegir cualquier río llamando a `/api/rios` y
usando su `id` o `geoglowsId`. La PWA de prueba usa el `reachId` de
`/api/push/config` solo para simplificar la demostración.

Fricciones conocidas para la integración:

- CORS no está configurado para un frontend servido desde otro origen.
- Los umbrales de caudal y lluvia son globales, no por río.
- Algunos errores pueden volver en texto plano.
- La autenticación de usuarios y la protección de `POST`/`PUT /api/rios` aún no
  existen.
- La consola solo opera sobre el primer río; el backend sí soporta varios.

El contrato detallado está en [`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md).
Las reglas de arquitectura están en [`ARQUITECTURA.md`](ARQUITECTURA.md).
