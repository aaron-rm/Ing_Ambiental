# Informe final: alertas de inundación con GEOGloWS, lluvia y notificaciones push

**Fecha del informe:** 21 de octubre de 2026<br>
**Proyecto:** Sistema académico de alerta temprana de inundaciones<br>
**Alcance:** backend Spring Boot + consola cliente HTTP + PWA de prueba + Firebase Cloud Messaging (FCM)

> **Informe histórico.** Este documento registra el estado del sistema entre el
> 21 y el 22 de octubre de 2026. Se conserva como evidencia de las pruebas de esa
> iteración. El estado vigente está en
> [`docs/CONTINUIDAD_SESION.md`](docs/CONTINUIDAD_SESION.md) y el contrato actual
> en [`docs/INTEGRACION_FRONTEND.md`](docs/INTEGRACION_FRONTEND.md).
>
> **Estado vigente (23/10/2026):** autenticación con roles, catálogo de cuatro
> ríos, selección múltiple, caché en memoria de GEOGloWS y campo `siguiendo` **ya
> implementados**. La PWA quedó reducida a login, catálogo, suscripción, push y
> simulación para `ADMIN`; el panel de caudal y lluvia sigue disponible en la API
> para el frontend definitivo, pero ya no se muestra en la PWA. La suite tiene
> **79 pruebas en verde**. Las referencias a 51 pruebas y a una PWA que solo
> verificaba notificaciones describen esta iteración histórica, no el estado
> actual.

> **Actualización 23/10/2026:** la PWA quedó limitada a login, catálogo,
> suscripción con activación de push y simulación de crecida para `ADMIN`. Ya no
> muestra mapa, panel, caudal, lluvia ni hidrogramas; esas vistas pertenecen al
> frontend definitivo. La consola consume la API pública. Las reglas vigentes
> están en [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md).

> Este sistema es académico y orientativo. No emite declaraciones oficiales de
> emergencia ni sustituye a SINAPROC, IMHPA u otra autoridad competente.

## 1. Estado inicial encontrado

El repositorio contenía un backend Spring Boot funcional con:

- Integración con GEOGloWS API v2 para pronóstico de caudal.
- Evaluación de umbrales de caudal y simulación de crecida.
- Entidades y endpoints para ríos, suscripciones y consulta de alertas.
- Consola interactiva de demostración (`ConsolaDemoRunner`).
- PostgreSQL configurado con credenciales escritas directamente en
  `application.properties`.
- No existía una PWA, ni monitoreo periódico, ni notificaciones push, ni
  historial persistente de eventos.

Se conservaron los cambios locales existentes y no se hicieron commits.

## 2. Cambios realizados

### 2.1 Backend

- **Evaluación de riesgo separada y comprobable** (`riesgo/`):
  - Nivel por caudal contra umbrales de GEOGloWS o de referencia.
  - Nivel por lluvia acumulada en 24 h con umbrales **experimentales**.
  - Nivel final = el mayor de ambos.
  - Validación de frescura del pronóstico y de la precipitación.
  - Los datos no confiables no generan eventos ni push.
- **Precipitación** (`clima/`): cliente de Open-Meteo, caché de 15 minutos y
  degradación a datos en caché o a “no disponible” si falla la red.
- **Monitoreo periódico** (`AlertaMonitoreoService`): evalúa todos los ríos,
  usa bloqueo por tramo y evita que un fallo externo detenga los demás.
- **Eventos e idempotencia** (`AlertaEventoService`):
  - Tablas nuevas `alertas_evento`, `estado_riesgo_tramo` y `dispositivos_push`.
  - Estado persistido por tramo y clave única de idempotencia.
  - Solo se notifica en `ALERTA`/`EMERGENCIA` y en la recuperación a `NORMAL`
    desde un nivel crítico.
- **Push FCM** (`push/`):
  - SDK administrativo de Firebase (`firebase-admin 9.11.0`).
  - Inicialización tolerante a fallos: si faltan credenciales, el monitoreo y la
    consulta de riesgo siguen funcionando.
  - Mensajes data-only, prioridad alta solo en eventos críticos.
  - Registro, actualización, baja y prueba por dispositivo.
  - Alerta de demostración a todos los dispositivos activos, sin crear un
    evento de riesgo (`POST /api/push/demo`, protegido con token administrativo).
  - Invalidación de tokens rechazados por FCM.
  - Estados de envío persistidos: `PENDIENTE`, `ENVIANDO`, `ENVIADA`,
    `PARCIAL`, `FALLIDA`, `SIN_DISPOSITIVOS`, `DESHABILITADO`, `OMITIDA`.
- **Endpoints nuevos**:
  - Historial, detalle de evento, configuración de riesgo.
  - Evaluación manual con `X-Admin-Token`.
  - Configuración pública de push, registro/baja y prueba.
- **Seguridad básica**:
  - Listado de suscripciones y evaluación manual protegidos con
    `ALERTA_ADMIN_TOKEN`; si no está configurado responden 403.
  - Credenciales de PostgreSQL movidas a `DB_URL`, `DB_USERNAME` y
    `DB_PASSWORD`.
  - Las credenciales administrativas de Firebase solo se leen del entorno.
- **Correcciones detectadas durante las pruebas reales**:
  - `OpenMeteoCliente` tenía dos constructores y Spring no podía instanciarlo
    al arrancar el JAR; se marcó el constructor de producción con `@Autowired`.
  - `BackendApiClient` inyectaba `com.fasterxml.jackson.databind.ObjectMapper`.
    Spring Boot 4 usa Jackson 3 para MVC y ya no expone ese bean de Jackson 2;
    el backend fallaba al arrancar. El cliente ahora crea su propio mapper
    Jackson 2 con `findAndRegisterModules()`, coherente con Firebase Admin.
  - El endpoint de prueba push devolvía 429 también para una suscripción
    inactiva; ahora el límite de 60 s devuelve 429 y los estados inválidos 409.

### 2.2 PWA de pruebas (estado en esta iteración, 22/10/2026)

En esta iteración la PWA incluida en `src/main/resources/static/` quedó reducida
temporalmente a una página para verificar notificaciones. **El estado actual es
distinto:** la PWA volvió a incluir login, catálogo, suscripción, push y
simulación para `ADMIN`, pero sin panel hidrológico. Se conserva este registro
histórico:

- `index.html`, `styles.css` y `app.js` con título, explicación breve, estado,
  botón de activación y mensaje de éxito/error.
- Consulta únicamente `/api/push/config` y registra con
  `POST /api/push/registrar`.
- No muestra caudales, precipitación, pronósticos, mapas, riesgo, historial ni
  paneles de monitoreo.
- Pide permiso solo al pulsar el botón, registra el Service Worker, obtiene el
  token FCM y confirma la suscripción cuando el backend responde `activo: true`.
- Maneja permisos concedidos, pendientes, denegados, navegador no compatible,
  falta de HTTPS y FCM no configurado.
- Evita registrar de nuevo el mismo token usando `localStorage`.
- `firebase-messaging-sw.js` ya no guarda caché ni app shell; solo recibe FCM y
  abre la URL de la notificación.
- `manifest.json` se actualizó a «Prueba de notificaciones».

La PWA **no es el frontend definitivo**. El equipo de frontend construirá su
propio cliente desde cero usando los contratos de `docs/INTEGRACION_FRONTEND.md`.

> **Historial:** en una iteración anterior la PWA mostraba ríos, estado,
> pronóstico, lluvia e historial, y tenía un panel «Modo demostración». Ese panel
> se eliminó; el envío de la notificación de prueba se movió a la consola
> (opción `0`) y a `POST /api/push/demo`.

### 2.3 Separación del backend y la consola

`ConsolaDemoRunner` dejó de inyectar `RioRepository` y `AlertaService`. Ahora es
un cliente HTTP que usa `demo/BackendApiClient` contra `ALERTA_API_BASE_URL`
(por defecto `http://localhost:8081`):

- Lista, crea y actualiza ríos mediante la API.
- Consulta estado, simula, registra suscripciones y lista contactos por HTTP.
- Envía la demostración push con `POST /api/push/demo`.
- No replica reglas de negocio ni integraciones externas.
- Se puede desactivar con `ALERTA_DEMO_CONSOLA_HABILITADA=false` o
  `-Dskip.demo.console=true`.

Se añadió `PUT /api/rios/{id}` para conservar la corrección del río inicial sin
acceso directo a la base. La consola conserva sus opciones numéricas existentes
y añade la opción `0` para la notificación push de demostración. El bloque de
precipitación se añadió a la salida de la opción 1.

Las reglas arquitectónicas permanentes están en `docs/ARQUITECTURA.md`.

## 3. Configuración

La guía operativa completa está en
[`docs/CONFIGURACION_PUSH.md`](docs/CONFIGURACION_PUSH.md).

Resumen de variables principales:

| Grupo | Variables |
|---|---|
| Base de datos | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Administración | `ALERTA_ADMIN_TOKEN` |
| Consola | `ALERTA_API_BASE_URL`, `ALERTA_DEMO_CONSOLA_HABILITADA` |
| Monitoreo | `ALERTA_MONITOREO_HABILITADO`, `ALERTA_MONITOREO_INTERVALO_MS`, `ALERTA_MONITOREO_RETRASO_INICIAL_MS`, `ALERTA_NOTIFICAR_RECUPERACION` |
| Riesgo | `ALERTA_LLUVIA_VIGILANCIA_MM`, `ALERTA_LLUVIA_ALERTA_MM`, `ALERTA_LLUVIA_EMERGENCIA_MM`, `ALERTA_MAX_EDAD_PRONOSTICO_HORAS`, `ALERTA_MAX_EDAD_PRECIPITACION_HORAS` |
| FCM backend | `FIREBASE_ENABLED`, `FIREBASE_CREDENTIALS_PATH` o `FIREBASE_CREDENTIALS_JSON`, `FIREBASE_PROJECT_ID` |
| FCM frontend | `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`, `FIREBASE_PROJECT_ID`, `FIREBASE_MESSAGING_SENDER_ID`, `FIREBASE_APP_ID`, `FIREBASE_VAPID_KEY` |
| Precipitación | `OPEN_METEO_URL`, `OPEN_METEO_CACHE_MINUTOS` |

El servidor usa el puerto **8081**. Para ejecutar:

```powershell
$env:DB_PASSWORD = "su-contraseña-local"
$env:ALERTA_ADMIN_TOKEN = "un-token-largo-y-aleatorio"
.\mvnw.cmd spring-boot:run
```

Abrir `http://localhost:8081`.

## 4. Pruebas ejecutadas

### 4.1 Compilación y pruebas automatizadas

```powershell
.\mvnw.cmd package
```

Resultado de la suite en esa fecha (registro histórico; hoy son 79 pruebas):

```text
Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Cobertura de las pruebas con mocks:

- `AlertaEventoServiceTest` (8): transiciones, recuperación, datos no
  confiables, idempotencia y reglas de notificación.
- `EvaluadorRiesgoTest` (9): caudal, lluvia, frescura, datos ausentes,
  desactualizados y combinación de niveles.
- `OpenMeteoClienteTest` (4): respuesta válida, error HTTP, caché y datos
  incompletos.
- `PushServiceTest` (15): registro, rotación de token, baja, prueba,
  demostración a todos los dispositivos, tokens inválidos, errores FCM, sin
  dispositivos y estados de envío.
- `NotificacionFactoryTest` (5): títulos, URL de deep link, tag, iconos y
  prioridad.
- `BackendApiClientTest` (4): listado de ríos, token administrativo, errores
  HTTP y demo push contra un servidor HTTP local.
- `EvaluacionRiesgoServiceTest` (3) y `ValidadorContactoTest` (3).

También se validó la sintaxis de la PWA:

```powershell
node --check src/main/resources/static/app.js
node --check src/main/resources/static/firebase-messaging-sw.js
```

### 4.2 Arranque real con PostgreSQL

Antes de la refactorización de clientes se arrancó el JAR empaquetado contra
PostgreSQL 18.6 local, base `alerta_inundaciones`, puerto 8081. El arranque fue
correcto. FCM mostró el aviso esperado:

```text
FCM no está disponible: Faltan credenciales de Firebase Admin
(FIREBASE_CREDENTIALS_PATH o FIREBASE_CREDENTIALS_JSON).
```

La aplicación siguió funcionando; este es el comportamiento tolerante previsto.

> **Verificación posterior a la refactorización (22/10/2026):** se repitió el
> arranque con el JAR actual y las variables de `.env.demo`:
>
> - Tomcat escuchó en `8081` y la aplicación terminó de iniciar.
> - `GET /api/rios` devolvió el Río Pacora (reach `710039498`).
> - `GET /api/push/config` devolvió `habilitado:false` y `reachId`.
> - `GET /` sirvió el HTML nuevo de prueba de notificaciones.
> - `POST /api/push/demo` respondió `409` porque Firebase no está configurado,
>   que es el comportamiento esperado sin credenciales.
> - La consola mostró el menú y la opción `0` sin acceder a repositorios.
>
> La primera ejecución de esta verificación falló porque `BackendApiClient`
> inyectaba un `ObjectMapper` Jackson 2; se corrigió y se volvió a ejecutar con
> éxito. La prueba push real sigue pendiente de credenciales y dispositivo.
>
> **Verificación de independencia (22/10/2026):** se levantó una segunda
> instancia aislada en `8082` con `ALERTA_DEMO_CONSOLA_HABILITADA=false`,
> `ALERTA_MONITOREO_HABILITADO=false`, `ALERTA_CREAR_RIO_INICIAL=false` y
> `FIREBASE_ENABLED=false`. La aplicación arrancó, `/` sirvió la PWA de esa
> iteración y
> `/api/rios` respondió; no apareció el menú de la consola. El proceso se detuvo
> al terminar y no se tocó la instancia del usuario en `8081`.

### 4.3 Endpoints comprobados (registro histórico)

Estas comprobaciones se hicieron antes de la refactorización de la PWA y la
consola; se conservan como evidencia del backend.

| Prueba | Resultado observado |
|---|---|
| `GET /` | 200, HTML de la PWA |
| `GET /manifest.json` | 200, `application/json` |
| `GET /firebase-messaging-sw.js` | 200, `text/javascript` |
| `GET /api/rios` | 200, Río Pacora (reach `710039498`) |
| `GET /api/alertas/rio/1` | 200, caudal máximo 8.50 m³/s; lluvia 24 h 6.40 mm; lluvia 72 h 31.90 mm; nivel NORMAL |
| `GET /api/alertas/config-riesgo` | 200, umbrales 25/50/100 mm y aviso de valores experimentales |
| `GET /api/push/config` | 200, `habilitado:false` con motivo por falta de credenciales |
| `GET /api/alertas/historial/710039498` | 200, lista vacía antes de la prueba manual |
| `GET /api/alertas/suscripciones/710039498` sin token | 403 |

Los datos de caudal y lluvia anteriores son datos reales devueltos por GEOGloWS
y Open-Meteo durante la prueba. GEOGloWS no entregó periodos de retorno
utilizables en ese momento, por lo que se usaron los umbrales de referencia
15/30/60 m³/s y la interfaz lo advirtió.

### 4.4 Evaluación manual, evento e idempotencia (registro histórico)

Para probar el flujo completo sin esperar una crecida real, se reinició la app
con umbrales de lluvia temporales de 1/2/3 mm y un token administrativo de
prueba. **No se modificó la configuración del repositorio.**

1. `POST /api/alertas/evaluar/710039498` creó el evento 1 con nivel
   `EMERGENCIA` (la lluvia real de 6.40 mm superó el umbral temporal de 3 mm).
2. El evento quedó con estado de envío `SIN_DISPOSITIVOS` porque no había
   dispositivos push registrados.
3. Una segunda evaluación devolvió `eventoId:null` y el mensaje
   «Sin cambio de nivel; no se creó un evento nuevo».
4. El historial devolvió exactamente 1 evento y
   `GET /api/alertas/evento/1` devolvió su detalle.
5. En esa iteración, la PWA abrió correctamente `/?alerta=1` y mostró la
   tarjeta de detalle. La PWA actual ya no muestra ese detalle.

Al terminar, se eliminaron de PostgreSQL el evento, el estado y el dispositivo
de prueba para dejar la base como estaba.

### 4.5 Registro push y validaciones

Con un token ficticio (no se intentó un envío real):

- Registro válido: `200`.
- Mismo token con otro `deviceId`: `409`.
- Baja con `deviceId` incorrecto: `409`.
- Baja correcta: `204`.
- Prueba push sobre suscripción inactiva: `409` (corregido; antes devolvía 429).

### 4.6 PWA de prueba (estado en esta iteración)

Después de la refactorización se validó la sintaxis de `app.js` y
`firebase-messaging-sw.js` con `node --check`. También se renderizó la página con
Chrome headless usando un viewport móvil de 390×844: la tarjeta midió 358 px y
`scrollWidth` fue 390, sin desbordamiento horizontal. La página mostró el estado
de error esperado al no existir un backend en ese servidor estático. No se
probaron permisos ni recepción push en un navegador real en esta sesión.

En esta iteración la PWA no consultaba ríos, estado, historial ni lluvia, y no
guardaba datos para funcionamiento offline. Esa reducción fue temporal: el
código actual incluye login, catálogo, suscripción, push y simulación para
`ADMIN`, pero sigue sin mostrar datos hidrológicos. Las pruebas de la iteración
anterior que mostraban datos hidrológicos se conservan como registro histórico.

## 5. Prueba manual pendiente

No fue posible completar la prueba de recepción push real porque no hay
credenciales Firebase ni un dispositivo físico en este entorno. Queda pendiente:

1. Configurar un proyecto Firebase real y las variables de
   [`docs/CONFIGURACION_PUSH.md`](docs/CONFIGURACION_PUSH.md).
2. Publicar la app en HTTPS (o probar en `localhost`).
3. En Android/Chrome: activar notificaciones desde la PWA y simular una crecida
   con la opción `6` de la consola o `POST /api/alertas/simular`; comprobar la
   recepción en primer y segundo plano. La opción `7` sigue disponible para el
   simulacro global.
4. En iPhone/iPad: instalar la PWA en la pantalla de inicio con iOS 16.4+,
   activar notificaciones y repetir la prueba.
5. Hacer clic en una notificación y verificar que abre la URL del payload. La
   PWA de prueba muestra un aviso breve; el detalle del evento corresponde al
   frontend definitivo.
6. Provocar una alerta real o controlada y confirmar el estado de envío
   (`ENVIADA`, `PARCIAL` o `FALLIDA`) y la invalidación de tokens.
7. Repetir la prueba con el servidor reiniciado para confirmar que no se
   duplican eventos ni notificaciones.
8. Repetir el arranque en un entorno limpio. La verificación local del 22/10 ya
   confirmó que el JAR actual inicia, que la consola usa la API y que la PWA se
   sirve correctamente; queda comprobar el flujo completo con Firebase real.

## 6. Limitaciones y riesgos conocidos

- **Umbrales de lluvia experimentales:** los valores 25/50/100 mm son
  provisionales; no provienen de un documento oficial y deben calibrarse.
- **Periodos de retorno de GEOGloWS:** el endpoint `returnperiods` devolvió
  error 500 durante las pruebas. Se mantiene el fallback 15/30/60 m³/s, que son
  valores de referencia no calibrados localmente.
- **FCM no probado de extremo a extremo:** sin credenciales ni dispositivo, no
  se puede afirmar que la entrega real funcione. La lógica está cubierta con
  mocks y las APIs responden, pero la prueba física es obligatoria antes de una
  demostración.
- **PWA de prueba (en esta iteración):** no mostraba información hidrológica y
  no era el frontend definitivo. El código actual mantiene esa decisión: solo
  cubre login, catálogo, suscripción, push y simulación admin.
- **Gestión de ríos sin token:** `POST` y `PUT /api/rios` aún no están
  protegidos; deben restringirse antes de producción.
- **Entrega push del navegador:** el sistema operativo y el navegador deciden si
  la notificación suena, vibra o se muestra; no se puede garantizar.
- **iOS:** Web Push requiere iOS 16.4+ y la app agregada a la pantalla de
  inicio; el comportamiento depende de Safari y de la configuración del usuario.
- **Idempotencia y caídas:** el evento se marca `ENVIANDO` antes de llamar a
  FCM para evitar duplicados. Si el proceso se cae justo después, el evento
  puede quedar en `ENVIANDO` y no reenviarse automáticamente. Es una decisión
  deliberada de seguridad (evitar ráfagas duplicadas), no un reintento exactly-once.
- **Sin migraciones versionadas:** Hibernate usa `ddl-auto=update`. Para
  producción conviene incorporar Flyway o Liquibase.
- **Administración básica:** los endpoints sensibles usan un token compartido;
  no hay usuarios, roles ni auditoría de accesos.
- **Una sola instancia:** los bloqueos por tramo son en memoria. La clave única
  de la base ayuda con la idempotencia, pero un despliegue con varias instancias
  requeriría coordinación adicional.
- **Dependencias externas:** GEOGloWS y Open-Meteo pueden fallar o cambiar. La
  app degrada la confianza y muestra advertencias, pero no sustituye datos
  oficiales.
- **Datos de prueba:** se limpiaron el evento, el estado y el dispositivo
  ficticio usados en las pruebas. No se hicieron commits.

## 7. Archivos principales

- `src/main/java/.../alerta/AlertaEventoService.java`: transiciones e
  idempotencia.
- `src/main/java/.../alerta/AlertaMonitoreoService.java`: ciclo de monitoreo.
- `src/main/java/.../riesgo/EvaluadorRiesgo.java`: reglas de caudal y lluvia.
- `src/main/java/.../clima/OpenMeteoCliente.java`: precipitación y caché.
- `src/main/java/.../push/PushService.java`: registro, prueba y envío.
- `src/main/java/.../push/FirebasePushSender.java`: integración FCM Admin.
- `src/main/java/.../push/NotificacionFactory.java`: construcción de mensajes.
- `src/main/java/.../alerta/AlertaController.java`: endpoints de alertas.
- `src/main/java/.../push/PushController.java`: endpoints de push.
- `src/main/java/.../demo/BackendApiClient.java`: cliente HTTP de la consola.
- `src/main/java/.../demo/ConsolaDemoRunner.java`: consola cliente de la API.
- `src/main/resources/application.properties`: configuración por entorno.
- `src/main/resources/static/`: PWA de prueba.
- `src/test/java/...`: 79 pruebas automatizadas (51 en la fecha de este informe).
- `docs/ARQUITECTURA.md`: directrices permanentes.
- `docs/CONFIGURACION_PUSH.md`: guía de configuración operativa.
- `docs/INTEGRACION_FRONTEND.md`: contrato para el frontend definitivo.

## 8. Conclusión

El backend conserva el flujo principal: datos de GEOGloWS y Open-Meteo,
evaluación de riesgo, eventos persistentes, historial, detalle y registro de
dispositivos push. La consola consume la API HTTP. La PWA quedó como una
herramienta mínima de prueba del canal push: login, catálogo, suscripción,
simulación para `ADMIN` y recepción de notificaciones.

En la fecha del informe pasaban la compilación y las **51 pruebas automatizadas**.
El estado vigente (23/10/2026) tiene **79 pruebas en verde**, caché de GEOGloWS y
el campo `siguiendo`. La entrega sigue condicionada a:

- repetir el arranque en Windows con PostgreSQL y credenciales reales;
- probar la recepción push con credenciales Firebase y un dispositivo real;
- validar localmente los umbrales de lluvia antes de usar el sistema fuera del
  ámbito académico;
- construir el frontend definitivo por separado, usando los contratos públicos.
