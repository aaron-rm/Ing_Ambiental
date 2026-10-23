# Contrato de API para el frontend definitivo

**Backend:** Spring Boot en `http://localhost:8081`<br>
**PWA de pruebas:** `src/main/resources/static/` — login, catálogo, panel y
push por usuario; no es el frontend definitivo<br>
**Frontend definitivo:** lo construirá otro equipo desde cero

> Este documento describe los contratos públicos del backend. El equipo de
> frontend puede elegir libremente su tecnología, diseño y estructura. No debe
> reutilizar obligatoriamente la PWA de pruebas ni extraer lógica de
> `ConsolaDemoRunner`.
>
> Las reglas de separación entre backend, consola y clientes están en
> [`ARQUITECTURA.md`](ARQUITECTURA.md).
>
> **Estado:** el avance de autenticación, catálogo de cuatro ríos, geometría,
> selección usuario–ríos y push asociado al usuario **ya está implementado**.
> El diseño y las decisiones están en [`GUIA_FRONTEND.md`](GUIA_FRONTEND.md) y
> [`CONTINUIDAD_SESION.md`](CONTINUIDAD_SESION.md).

---

## 1. Situación actual

El backend expone:

- registro e inicio de sesión con un solo `identificador` (correo o nombre de
  usuario) y JWT de 24 horas;
- roles `USER` y `ADMIN`;
- catálogo cerrado de cuatro ríos con geometría GeoJSON de GEOGloWS V2;
- selección múltiple de ríos por usuario;
- consulta de caudal y lluvia de cualquier río del catálogo, esté o no seguido;
- push asociado al usuario, enrutado por los ríos que sigue;
- panel de administración mínimo con JWT (`ADMIN`).

Los clientes incluidos en el repositorio son la **consola** (`ConsolaDemoRunner`)
y la **PWA de prueba**. Ambos consumen la misma API REST y ya usan JWT. El
frontend definitivo se desarrollará aparte.

---

## 2. Arranque local

### Opción automática

```powershell
Copy-Item .env.demo.example .env.demo
# completar .env.demo con credenciales reales
.\scripts\iniciar-demo.ps1
```

Abre la app y un túnel HTTPS. La URL del túnel cambia en cada ejecución.

### Opción manual

```powershell
$env:DB_PASSWORD = "su-contraseña"
$env:ALERTA_JWT_SECRET = "un-secreto-de-al-menos-32-caracteres"
$env:ALERTA_ADMIN_IDENTIFICADOR = "admin"
$env:ALERTA_ADMIN_PASSWORD = "la-contraseña-local-acordada"
$env:FIREBASE_CREDENTIALS_PATH = "C:\ruta\service-account.json"
# ...resto de variables públicas de Firebase
.\mvnw.cmd spring-boot:run
```

Abrir `http://localhost:8081`.

### Requisito para push en celular

`localhost` funciona en la PC. En el celular se necesita **HTTPS**: use la URL
del túnel. `http://192.168.x.x:8081` **no** habilita Service Workers ni push.

---

## 3. Mapa de endpoints

Base: `/api`. Todos devuelven JSON salvo algunos errores en texto plano.

### Autenticación

| Método | Ruta | Auth | Uso |
|---|---|---|---|
| POST | `/api/auth/registro` | pública | Crea un usuario `USER` y devuelve JWT |
| POST | `/api/auth/login` | pública | Inicia sesión y devuelve JWT |
| GET | `/api/auth/me` | JWT | Sesión actual |

### Catálogo y geometría

| Método | Ruta | Auth | Uso |
|---|---|---|---|
| GET | `/api/rios` | pública | Catálogo de ríos (`rioId`, `nombre`, `reachId`, coordenadas, `tieneGeometria`) |
| GET | `/api/rios/mapa` | pública | `FeatureCollection` GeoJSON con la geometría de cada río |
| POST | `/api/admin/rios` | JWT + `ADMIN` | Mantenimiento del catálogo (no se usa en esta entrega) |
| PUT | `/api/admin/rios/{rioId}` | JWT + `ADMIN` | Mantenimiento del catálogo (no se usa en esta entrega) |

### Selección de ríos

| Método | Ruta | Auth | Uso |
|---|---|---|---|
| GET | `/api/usuarios/me/rios` | JWT | Ríos que sigue el usuario |
| POST | `/api/usuarios/me/rios/{rioId}` | JWT | Seguir un río (idempotente) |
| DELETE | `/api/usuarios/me/rios/{rioId}` | JWT | Dejar de seguir un río |

### Alertas y riesgo

| Método | Ruta | Auth | Uso |
|---|---|---|---|
| GET | `/api/alertas/rio/{rioId}` | pública | Estado por ID de río (panel del mapa) |
| GET | `/api/alertas/tramo/{reachId}` | pública | Estado por reachId GEOGloWS |
| GET | `/api/alertas/config-riesgo` | pública | Umbrales y aviso experimental |
| GET | `/api/alertas/historial/{reachId}?limite=20` | pública | Historial de eventos |
| GET | `/api/alertas/evento/{eventoId}` | pública | Detalle de un evento |
| POST | `/api/alertas/simular?reachId=&caudal=` | JWT + `ADMIN` | Simulación; no persiste evento ni envía push |
| POST | `/api/alertas/evaluar/{reachId}` | JWT + `ADMIN` | Evaluación manual real (puede crear evento y push) |
| POST | `/api/alertas/suscribir` | pública | Suscripción heredada por correo/teléfono (**no es push**) |
| GET | `/api/alertas/suscripciones/{reachId}` | `X-Admin-Token` | Contactos heredados (dato sensible, en retiro) |

### Push

| Método | Ruta | Auth | Uso |
|---|---|---|---|
| GET | `/api/push/config` | pública | Configuración pública de Firebase |
| POST | `/api/push/registrar` | JWT | Alta/actualización del token push del usuario |
| DELETE | `/api/push/registrar` | JWT | Baja del dispositivo (`token` + `deviceId` en el cuerpo) |
| POST | `/api/push/prueba` | JWT | Prueba al dispositivo (`token` + `deviceId`; límite 60 s) |
| POST | `/api/admin/push/demo` | JWT + `ADMIN` | Simulacro a todos los dispositivos activos |
| POST | `/api/push/demo` | `X-Admin-Token` | Heredado; se retirará. El frontend no debe usarlo |

> Las rutas heredadas `POST /api/alertas/suscribir`,
> `GET /api/alertas/suscripciones/{reachId}` y `POST /api/push/demo` se retirarán
> del flujo de usuario y de la consola. El modelo definitivo es usuario–ríos con
> push.

### Códigos de error esperados

| Código | Significado |
|---|---|
| 400 | Datos inválidos o falta un campo |
| 401 | Credenciales incorrectas o token ausente/expirado |
| 403 | El usuario no tiene rol suficiente para esa operación |
| 404 | Río, tramo o evento inexistente |
| 409 | Identificador duplicado, token push en conflicto u operación incompatible |
| 410 | Token FCM inválido; hay que volver a activar notificaciones |
| 429 | Límite de 60 s entre pruebas push |
| 502 | Fallo de GEOGloWS/Open-Meteo o error de envío FCM |

---

## 4. Contrato de datos

### `POST /api/auth/registro` y `POST /api/auth/login`

```json
{ "identificador": "juan@correo.com", "password": "una-clave-de-8-o-mas" }
```

Respuesta `200`/`201`:

```json
{
  "token": "<jwt>",
  "usuario": { "id": 1, "identificador": "juan@correo.com", "rol": "USER" }
}
```

### `GET /api/rios`

```json
[
  {
    "rioId": 1,
    "nombre": "Río Abajo",
    "reachId": 710039498,
    "latitud": 9.00933,
    "longitud": -79.46716,
    "tieneGeometria": true
  }
]
```

El catálogo está cerrado en cuatro ríos:

| Río | `rioId` (varía por BD) | `reachId` principal |
|---|---:|---:|
| Río Abajo | — | `710039498` |
| Río Curundú | — | `710037516` |
| Río Juan Díaz | — | `710051399` |
| Río Tocumen | — | `670053261` |

El `rioId` se asigna al sembrar la base de datos, así que el frontend debe
leerlo de `GET /api/rios` o de `GET /api/rios/mapa`; no debe asumirlo.

### `GET /api/rios/mapa`

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": { "type": "MultiLineString", "coordinates": [[[-79.4, 9.0]]] },
      "properties": { "rioId": 1, "nombre": "Río Tocumen", "reachId": 670053261 }
    }
  ]
}
```

- `features[].properties.rioId` es la clave para seleccionar.
- Las coordenadas son `[longitud, latitud]` en WGS84.
- La geometría es una simplificación de la red TDX-Hydro de GEOGloWS V2. No es
  un mapa topográfico ni un límite de inundación.

### `GET /api/alertas/rio/{rioId}` → `AlertaRioDTO`

> Consultar el caudal y la lluvia de un río **no requiere que el usuario lo
> siga**. La selección de ríos solo controla qué notificaciones push recibe.

Campos principales:

| Campo | Tipo | Notas |
|---|---|---|
| `reachId` | number | ID GEOGloWS |
| `rioId` | number/null | ID interno cuando la consulta fue por río |
| `nombreRio` | string | |
| `nivelAlerta` | string | `NORMAL`, `VIGILANCIA`, `ALERTA`, `EMERGENCIA` |
| `caudalActualOInicial` | number | m³/s |
| `caudalMaximoPronosticado` | number | m³/s |
| `umbralVigilancia` / `umbralAlerta` / `umbralEmergencia` | number | m³/s |
| `mensaje` | string | Texto para el usuario |
| `pronostico` | array | `{fecha, caudalMedio}` — sirve para el hidrograma |
| `pronosticoDiario` | array | Resumen diario |
| `simulacion` | boolean | No persistir como alerta real |
| `estadoDatos` | string | `COMPLETOS`, `PARCIALES`, `DESACTUALIZADOS`, `INSUFICIENTES` |
| `datosConfiables` | boolean | Si es `false`, no hay evento ni push |
| `fuenteCaudal` / `fuentePrecipitacion` / `fuenteUmbrales` | string | Trazabilidad visible |
| `nivelRiesgoCaudal` / `nivelRiesgoLluvia` | string | Nivel por cada factor |
| `lluvia24hMm` / `lluvia72hMm` / `maxLluviaHorariaMm` | number/null | Open-Meteo |
| `factoresRiesgo` / `advertencias` / `recomendaciones` | string[] | Mostrar en la UI |
| `alertaEventoId` | number/null | Evento asociado, si existe |
| `pronosticoLluvia` | array | `{fecha, mm}` |
| `fechaEvaluacion` / `fechaEmisionCaudal` / `fechaActualizacionPrecipitacion` | ISO-8601/null | Frescura de datos |

### `GET /api/usuarios/me/rios`

```json
[
  {
    "rioId": 1,
    "nombre": "Río Abajo",
    "reachId": 710039498,
    "latitud": 9.00933,
    "longitud": -79.46716,
    "fechaSeleccion": "2026-10-22T15:00:00Z"
  }
]
```

### `GET /api/alertas/historial/{reachId}` → `AlertaEventoDTO[]`

Campos principales: `id`, `reachId`, `nombreRio`, `nivel`, `nivelAnterior`,
`nivelCaudal`, `nivelLluvia`, `estadoDatos`, `datosConfiables`,
`caudalMaximo`, umbrales, `lluvia24hMm`, `mensaje`, `factores`,
`advertencias`, fuentes, fechas, `estadoEnvio`, contadores de envío, `origen`.

`estadoEnvio` puede ser: `PENDIENTE`, `ENVIANDO`, `ENVIADA`, `PARCIAL`,
`FALLIDA`, `SIN_DISPOSITIVOS`, `DESHABILITADO`, `OMITIDA`.

### `GET /api/alertas/evento/{id}`

Mismo formato que un elemento del historial. Es el destino del deep link.

---

## 5. Integración push

### 5.1 Flujo en el navegador

1. `GET /api/push/config`.
   - Si `habilitado: false`, mostrar `motivo` y no pedir permisos.
   - La respuesta solo contiene datos públicos de Firebase (API key, VAPID key,
     etc.). Ya **no** incluye un `reachId`: el dispositivo se asocia al usuario
     y las alertas dependen de los ríos que sigue.
2. Registrar el Service Worker en `/firebase-messaging-sw.js` con scope `/`.
3. **Solo tras un clic del usuario**, pedir `Notification.requestPermission()`.
   No solicitar permiso al cargar la página.
4. Cargar el SDK web de Firebase y obtener el token con la `vapidKey`.
5. `POST /api/push/registrar` **con JWT**:

```json
{
  "token": "<token FCM>",
  "deviceId": "<uuid local>",
  "plataforma": "web"
}
```

Respuesta `200`:

```json
{
  "id": 1,
  "activo": true,
  "mensaje": "Dispositivo registrado para recibir alertas de tus ríos.",
  "fechaActualizacion": "2026-10-22T22:00:00Z"
}
```

6. Guardar `token` y `deviceId` en `localStorage` (o en el almacenamiento que
   elija el frontend).
7. Escuchar mensajes en primer plano con `onMessage`; en segundo plano los
   maneja el Service Worker.
8. Al cerrar sesión, llamar a `DELETE /api/push/registrar` con el `token` y el
   `deviceId` para que el dispositivo deje de recibir alertas de ese usuario.

### 5.2 Payload de la notificación

El backend envía mensajes **data-only**. Campos:

| Campo | Ejemplo | Uso |
|---|---|---|
| `title` | `ALERTA DE INUNDACIÓN` | Título |
| `body` | `Nivel ALERTA en Río Juan Díaz...` | Cuerpo |
| `nivel` | `ALERTA` / `EMERGENCIA` / `PRUEBA` | Lógica de UI |
| `reachId` | `710051399` | Tramo |
| `eventoId` | `12` | Deep link |
| `url` | `/?alerta=12` | Destino del clic |
| `tag` | `alerta-inundacion-710051399` | Evita duplicados visuales |
| `icon` / `badge` | `/icons/...` | Iconos |
| `critico` | `true`/`false` | Prioridad alta |
| `requiereInteraccion` | `true`/`false` | No cerrar sola |
| `vibrar` | `true`/`false` | Vibración (si el SO lo permite) |
| `timestamp` | epoch ms | Orden |

### 5.3 Deep links

- `/?alerta={id}`: el frontend puede abrir el detalle del evento.
- `/?prueba=1`: aviso de prueba.
- `/?demo=1`: aviso de simulacro.

La PWA de pruebas solo muestra un mensaje breve; el frontend definitivo decide
cómo presentar esos destinos.

### 5.4 Reglas que no se deben romper

- No pedir permiso de notificaciones al cargar la página: solo por clic.
- No registrar el token antes de tener permiso ni sin sesión.
- No enviar `reachId` en el registro push: el enrutamiento depende de los ríos
  que sigue el usuario.
- No mostrar credenciales administrativas en el cliente.
- No enviar el JSON de la cuenta de servicio al navegador.
- Manejar `410` borrando el token local y pidiendo reactivación.
- Manejar `401` volviendo al login y `403` mostrando que falta permiso.
- Confirmar el éxito solo después de que `/api/push/registrar` responda
  `activo: true`.

---

## 6. Demostración y prueba controlada

1. Iniciar sesión en la PWA con un usuario normal y seguir al menos un río.
2. Activar las notificaciones y enviar una prueba desde la propia PWA.
3. En la consola del backend (sesión `ADMIN`), usar la opción de push de
   demostración. O ejecutar:

```powershell
$cuerpo = @{ identificador = "admin"; password = "la-contraseña-local" } | ConvertTo-Json
$sesion = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/auth/login" `
  -ContentType "application/json" -Body $cuerpo
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/admin/push/demo" `
  -Headers @{ Authorization = "Bearer $($sesion.token)" }
```

Respuesta:

```json
{ "dispositivos": 3, "enviados": 3, "fallidos": 0 }
```

El endpoint envía a todos los dispositivos activos sin crear un evento de
riesgo. Para probar el enrutamiento real, un administrador puede evaluar un río
con `POST /api/alertas/evaluar/{reachId}`: si el nivel cambia, solo reciben la
notificación los usuarios que siguen ese río.

---

## 7. Checklist para el frontend definitivo

- [ ] La app abre en `localhost` o HTTPS.
- [ ] El registro y el login devuelven JWT y el rol.
- [ ] Las rutas protegidas envían `Authorization: Bearer <jwt>`.
- [ ] Ante `401` se borra la sesión y se vuelve al login.
- [ ] `/api/rios/mapa` dibuja las líneas y el clic lee `properties.rioId`.
- [ ] El panel se abre para cualquier río, esté o no seguido.
- [ ] Seguir y dejar de seguir solo cambian las notificaciones.
- [ ] `/api/push/config` devuelve `habilitado: true`.
- [ ] El botón de activación pide permiso solo al pulsarlo.
- [ ] El registro devuelve `200` y `activo: true` sin enviar `reachId`.
- [ ] El panel admin solo aparece con `rol === "ADMIN"` y llama a
      `/api/admin/push/demo`.
- [ ] No hay credenciales ni tokens administrativos en el repositorio.
- [ ] `.\mvnw.cmd package` termina con `BUILD SUCCESS`.

---

## 8. Contexto para agentes de IA

Antes de modificar código, leer:

1. [`ARQUITECTURA.md`](ARQUITECTURA.md) — reglas permanentes.
2. [`CONTINUIDAD_SESION.md`](CONTINUIDAD_SESION.md) — estado y decisiones.
3. `GEMINI.md` — resumen operativo.
4. `IMPLEMENTATION_NOTES.md` — historia y decisiones.
5. `INFORME_ALERTAS_INUNDACION.md` — estado, pruebas y pendientes.
6. [`CONFIGURACION_PUSH.md`](CONFIGURACION_PUSH.md) — entorno y FCM.
7. Este documento — contrato con el frontend.

### Archivos clave

| Archivo | Responsabilidad |
|---|---|
| `auth/` | Usuarios, JWT, roles y seguridad |
| `usuario/` | Selección usuario–ríos |
| `rio/` | Catálogo, geometría y mapa |
| `alerta/AlertaController.java` | Endpoints de alertas |
| `alerta/AlertaService.java` | Consulta, historial y detalle |
| `alerta/AlertaMonitoreoService.java` | Evaluación periódica y manual |
| `riesgo/EvaluadorRiesgo.java` | Reglas de caudal y lluvia |
| `clima/OpenMeteoCliente.java` | Precipitación y caché |
| `push/PushService.java` | Registro y envío por seguidores |
| `demo/BackendApiClient.java` | Cliente HTTP de la consola |
| `src/main/resources/static/` | PWA de prueba |
| `src/main/resources/catalogo/rios.json` | Catálogo con geometría real |
| `tools/generar_catalogo_geoglows.py` | Generación del catálogo desde GeoPackage |

### Reglas para agentes

- No inventar endpoints, campos, ríos ni umbrales oficiales.
- Los umbrales de lluvia son **experimentales**.
- Credenciales solo por variables de entorno.
- No mover lógica de negocio a la consola ni a la PWA.
- No imponer a la PWA ni a la consola como diseño obligatorio del frontend.
- Mantener mensajes data-only y el manejo del clic.
- Ejecutar `.\mvnw.cmd package` después de cambios.
- No hacer commits automáticamente.

### Limitaciones conocidas

- Sin credenciales Firebase reales no se puede probar la entrega push.
- La entrega push de extremo a extremo aún no está verificada en un dispositivo.
- `returnperiods` de GEOGloWS falla (500); se usan umbrales de referencia
  15/30/60 m³/s. La calibración por temporada queda pendiente.
- iOS requiere 16.4+ y la PWA instalada en la pantalla de inicio.
- Brave puede bloquear push; para la demo usar Chrome en Android.
- El túnel HTTPS es temporal y cambia de URL.
- La geometría es una simplificación de la red TDX-Hydro; no representa
  planicies de inundación.
- Si el frontend se sirve desde otro origen, hay que agregarlo a
  `ALERTA_CORS_ORIGENES`.
