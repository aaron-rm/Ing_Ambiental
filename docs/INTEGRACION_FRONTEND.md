# Contrato de API para el frontend definitivo

**Backend:** Spring Boot en `http://localhost:8081`  
**PWA de pruebas:** `src/main/resources/static/` — hoy solo verifica notificaciones; el siguiente avance añade login y selección de ríos<br>
**Frontend definitivo:** lo construirá otro equipo desde cero

> Este documento describe los contratos públicos del backend. El equipo de
> frontend puede elegir libremente su tecnología, diseño y estructura. No debe
> reutilizar obligatoriamente la PWA de pruebas ni extraer lógica de
> `ConsolaDemoRunner`.
>
> Las reglas de separación entre backend, consola y clientes están en
> [`ARQUITECTURA.md`](ARQUITECTURA.md).
>
> **Aviso:** el avance de autenticación, mapa con geometría y selección
> múltiple de ríos se describe en [`GUIA_FRONTEND.md`](GUIA_FRONTEND.md). Ese
> documento es un borrador de diseño; este archivo sigue describiendo la API
> que existe actualmente.
>
> **Cobertura:** este documento enumera todos los endpoints implementados hoy.
> Todavía no incluye autenticación con usuarios, roles, geometría para el mapa,
> selección múltiple de ríos ni el panel de administración con JWT. Esas piezas
> están en la guía y se incorporarán aquí cuando se implementen y prueben.
>
> **Catálogo acordado para la próxima entrega (aún no implementado):** Río
> Abajo (`710039498`), Río Curundú (`710037516`), Río Juan Díaz (`710051399`) y
> Río Tocumen (`670053261`). El seed actual todavía crea un “Río Pacora” con el
> `reachId` de Abajo; se corregirá al implementar el catálogo.

---

## 1. Situación actual

El backend está implementado y expone una API REST. La PWA incluida en este
repositorio es **una página mínima de prueba de notificaciones**:

- activa el permiso;
- registra el Service Worker;
- obtiene el token FCM;
- registra la suscripción en el backend;
- no muestra caudales, lluvia, pronóstico, mapas, riesgo ni historial.

El frontend definitivo se desarrollará aparte. Puede consumir la API desde el
mismo origen (servido por Spring Boot) o desde otro origen; en el segundo caso
se debe habilitar CORS en el backend.

La consola `ConsolaDemoRunner` también consume esta misma API mediante HTTP. Es
una herramienta de demostración, no una fuente de lógica que el frontend deba
copiar.

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
$env:ALERTA_ADMIN_TOKEN = "un-token-largo"
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

| Método | Ruta | Auth | Uso |
|---|---|---|---|
| GET | `/api/rios` | pública | Lista de ríos configurados |
| POST | `/api/rios` | pública* | Crea un río |
| PUT | `/api/rios/{id}` | pública* | Actualiza un río existente |
| GET | `/api/alertas/rio/{rioId}` | pública | Estado actual por ID de río |
| GET | `/api/alertas/tramo/{reachId}` | pública | Estado actual por reachId GEOGloWS |
| GET | `/api/alertas/config-riesgo` | pública | Umbrales y aviso experimental |
| GET | `/api/alertas/historial/{reachId}?limite=20` | pública | Historial de eventos |
| GET | `/api/alertas/evento/{eventoId}` | pública | Detalle de un evento |
| POST | `/api/alertas/suscribir` | pública | Suscripción heredada por correo/teléfono (**no es push**) |
| POST | `/api/alertas/simular?reachId=&caudal=` | pública | Simulación; no persiste evento ni envía push |
| GET | `/api/push/config` | pública | Configuración pública de Firebase y `reachId` de prueba |
| POST | `/api/push/registrar` | pública | Alta/actualización del token push |
| DELETE | `/api/push/registrar` | token+deviceId en el cuerpo | Baja del dispositivo |
| POST | `/api/push/prueba` | token+deviceId en el cuerpo | Prueba a un dispositivo (límite 60 s) |
| POST | `/api/push/demo` | `X-Admin-Token` | Simulacro a todos los dispositivos activos |
| POST | `/api/alertas/evaluar/{reachId}` | `X-Admin-Token` | Evaluación manual real |
| GET | `/api/alertas/suscripciones/{reachId}` | `X-Admin-Token` | Contactos heredados (dato sensible) |

\* La gestión de ríos aún no tiene token administrativo. Es una limitación
conocida que debe corregirse antes de producción; no se debe asumir que esas
rutas son seguras para exponerlas públicamente en un despliegue real.

> **Próximo avance:** las rutas heredadas `POST /api/alertas/suscribir` y
> `GET /api/alertas/suscripciones/{reachId}` se retirarán del flujo de usuario y
> de la consola. El modelo definitivo será usuario–ríos con push. El campo
> `reachId` de `GET /api/push/config` quedará obsoleto para el frontend.

### Códigos de error esperados

| Código | Significado |
|---|---|
| 400 | Datos inválidos o falta un campo |
| 403 | Token administrativo ausente/incorrecto, o `ALERTA_ADMIN_TOKEN` no configurado |
| 404 | Río o evento inexistente |
| 409 | Token ya asociado a otro dispositivo, suscripción inactiva, FCM deshabilitado |
| 410 | Token FCM inválido; hay que volver a activar notificaciones |
| 429 | Límite de 60 s entre pruebas |
| 502 | Fallo de GEOGloWS/Open-Meteo o error de envío FCM |

---

## 4. Contrato de datos

### `GET /api/rios`

```json
[
  {
    "id": 1,
    "nombre": "Río Pacora",
    "geoglowsId": 710039498,
    "latitud": 9.08,
    "longitud": -79.28
  }
]
```

> El ejemplo refleja el seed provisional actual. En la próxima entrega se
> reemplazará por el catálogo de cuatro ríos indicado arriba; no usar “Río
> Pacora” como nombre definitivo.

### `GET /api/alertas/rio/{rioId}` y `/tramo/{reachId}` → `AlertaRioDTO`

Campos principales:

| Campo | Tipo | Notas |
|---|---|---|
| `reachId` | number | ID GEOGloWS |
| `nombreRio` | string | |
| `nivelAlerta` | string | `NORMAL`, `VIGILANCIA`, `ALERTA`, `EMERGENCIA` |
| `caudalActualOInicial` | number | m³/s |
| `caudalMaximoPronosticado` | number | m³/s |
| `umbralVigilancia` / `umbralAlerta` / `umbralEmergencia` | number | m³/s |
| `mensaje` | string | Texto para el usuario |
| `pronostico` | array | `{fecha, caudalMedio}` |
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
   - La respuesta incluye `reachId`, el tramo de prueba por defecto. El
     frontend definitivo puede usar el tramo que el usuario elija.
2. Registrar el Service Worker en `/firebase-messaging-sw.js` con scope `/`.
3. **Solo tras un clic del usuario**, pedir `Notification.requestPermission()`.
   No solicitar permiso al cargar la página.
4. Cargar el SDK web de Firebase y obtener el token con la `vapidKey`.
5. `POST /api/push/registrar`:

```json
{
  "token": "<token FCM>",
  "deviceId": "<uuid local>",
  "reachId": 710039498,
  "plataforma": "web"
}
```

Respuesta `200`:

```json
{
  "id": 1,
  "reachId": 710039498,
  "activo": true,
  "mensaje": "Dispositivo registrado para recibir alertas de este tramo.",
  "fechaActualizacion": "2026-10-21T22:00:00Z"
}
```

6. Guardar `token` y `deviceId` en `localStorage`.
7. Escuchar mensajes en primer plano con `onMessage`; en segundo plano los
   maneja el Service Worker.

### 5.2 Payload de la notificación

El backend envía mensajes **data-only**. Campos:

| Campo | Ejemplo | Uso |
|---|---|---|
| `title` | `ALERTA DE INUNDACIÓN` | Título |
| `body` | `Nivel ALERTA en Río Pacora...` | Cuerpo |
| `nivel` | `ALERTA` / `EMERGENCIA` / `PRUEBA` | Lógica de UI |
| `reachId` | `710039498` | Tramo |
| `eventoId` | `12` | Deep link |
| `url` | `/?alerta=12` | Destino del clic |
| `tag` | `alerta-inundacion-710039498` | Evita duplicados visuales |
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
cómo presentar esos destinos. Si se crea una SPA aparte, debe manejar el clic de
la notificación (`clients.openWindow` o equivalente).

### 5.4 Reglas que no se deben romper

- No pedir permiso de notificaciones al cargar la página: solo por clic.
- No registrar el token antes de tener permiso.
- No mostrar credenciales administrativas en el cliente.
- No enviar el JSON de la cuenta de servicio al navegador.
- Manejar `410` borrando el token local y pidiendo reactivación.
- Manejar `403` indicando que falta el token administrativo.
- Confirmar el éxito solo después de que `/api/push/registrar` responda
  `activo: true`.

---

## 6. Demostración y prueba controlada

La PWA ya no tiene panel de administración ni botón para enviar notificaciones.
Para una prueba controlada:

1. Abrir la PWA de pruebas y activar notificaciones.
2. En la consola del backend, elegir la opción **0**.
3. O ejecutar:

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/push/demo" `
  -Headers @{ "X-Admin-Token" = "el-token-real" }
```

Respuesta:

```json
{ "dispositivos": 3, "enviados": 3, "fallidos": 0 }
```

El endpoint envía a todos los dispositivos activos sin crear un evento de
riesgo. Es la forma recomendada de mostrar el canal push en clase.

---

## 7. Checklist para el frontend definitivo

- [ ] La app abre en `localhost` o HTTPS.
- [ ] `/api/push/config` devuelve `habilitado: true` y `reachId`.
- [ ] El Service Worker aparece activo en DevTools → Application.
- [ ] El botón de activación pide permiso solo al pulsarlo.
- [ ] El registro devuelve `200` y `activo: true`.
- [ ] La notificación de prueba llega con la app en segundo plano.
- [ ] El clic abre el destino definido por el frontend.
- [ ] Se muestran errores claros si FCM no está configurado, el permiso se
      deniega o el navegador no es compatible.
- [ ] No hay credenciales ni tokens administrativos en el repositorio.
- [ ] `.\mvnw.cmd package` termina con `BUILD SUCCESS`.

---

## 8. Contexto para agentes de IA

Antes de modificar código, leer:

1. [`ARQUITECTURA.md`](ARQUITECTURA.md) — reglas permanentes.
2. `GEMINI.md` — resumen operativo.
3. `IMPLEMENTATION_NOTES.md` — historia y decisiones.
4. `INFORME_ALERTAS_INUNDACION.md` — estado, pruebas y pendientes.
5. `CONFIGURACION_PUSH.md` — entorno y FCM.
6. Este documento — contrato con el frontend.

### Archivos clave

| Archivo | Responsabilidad |
|---|---|
| `alerta/AlertaController.java` | Endpoints de alertas |
| `alerta/AlertaService.java` | Consulta, historial y detalle |
| `alerta/AlertaMonitoreoService.java` | Evaluación periódica y manual |
| `alerta/AlertaEventoService.java` | Transiciones e idempotencia |
| `riesgo/EvaluadorRiesgo.java` | Reglas de caudal y lluvia |
| `clima/OpenMeteoCliente.java` | Precipitación y caché |
| `push/PushController.java` | Endpoints push |
| `push/PushService.java` | Registro y envío |
| `push/FirebasePushSender.java` | Integración FCM Admin |
| `push/NotificacionFactory.java` | Construcción del payload |
| `demo/BackendApiClient.java` | Cliente HTTP de la consola |
| `src/main/resources/static/` | PWA mínima de prueba |
| `src/test/java/...` | 51 pruebas automatizadas |

### Reglas para agentes

- No inventar endpoints, campos ni umbrales oficiales.
- Los umbrales de lluvia son **experimentales**.
- Credenciales solo por variables de entorno.
- No mover lógica de negocio a la consola ni a la PWA.
- No imponer a la PWA ni a la consola como diseño obligatorio del frontend.
- Mantener mensajes data-only y el manejo del clic.
- Ejecutar `.\mvnw.cmd package` después de cambios.
- No hacer commits automáticamente.

### Limitaciones conocidas

- Sin credenciales Firebase reales no se puede probar la entrega push.
- `returnperiods` de GEOGloWS falla (500); se usan umbrales de referencia
  15/30/60 m³/s.
- iOS requiere 16.4+ y la PWA instalada en la pantalla de inicio.
- Brave puede bloquear push; para la demo usar Chrome en Android.
- El túnel HTTPS es temporal y cambia de URL.
- La gestión de ríos aún no está protegida con token administrativo.
- Si el frontend se sirve desde otro origen, hay que habilitar CORS; hoy no está
  configurado porque los clientes incluidos se sirven desde el mismo backend.
