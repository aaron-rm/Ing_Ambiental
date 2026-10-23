# Guía de integración para el frontend

**Estado:** borrador del contrato para el avance de autenticación, mapa y
selección múltiple de ríos. Los endpoints marcados como **propuestos** todavía
no están implementados.

**Contrato vigente:** [`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md)
describe la API que existe hoy. Este documento describe hacia dónde va el
backend y cómo debe integrarse el frontend definitivo.

---

## 1. Regla principal

El frontend es responsable de **la interfaz y la interacción**. El backend es
responsable de **los usuarios, los ríos, la geometría, las selecciones, las
alertas y el envío push**.

```text
Frontend
  ├── dibuja el mapa
  ├── dibuja los ríos del catálogo
  ├── detecta el clic sobre una capa de río
  ├── envía el rioId al backend
  ├── muestra los datos que recibe
  └── muestra el panel de administración si el usuario es ADMIN

Backend
  ├── autentica usuarios
  ├── entrega el catálogo y el GeoJSON de los ríos
  ├── valida que el rioId exista
  ├── guarda qué ríos sigue cada usuario
  ├── evalúa el riesgo con GEOGloWS y Open-Meteo
  └── envía notificaciones a los usuarios que siguen cada río
```

El frontend **no** debe:

- consultar directamente GEOGloWS ni Open-Meteo;
- calcular niveles de riesgo, umbrales o pronósticos;
- buscar el río más cercano a un punto;
- decidir qué `reachId` corresponde a un río;
- usar `X-Admin-Token`;
- guardar credenciales administrativas o la cuenta de servicio de Firebase.

---

## 2. Precisiones importantes

### 2.1. El backend no “ve” el clic

El navegador recibe el evento del mapa. Cuando el usuario hace clic sobre una
línea de río, el frontend lee la propiedad `rioId` de esa feature y la envía al
backend.

```text
clic del usuario
   → el frontend identifica la feature
   → lee properties.rioId
   → llama a /api/usuarios/me/rios/{rioId}
   → el backend valida el río y responde
```

No se envía la coordenada del clic para que el backend busque el río más
cercano. Tampoco se usa el endpoint de “río cercano” para este flujo. La
selección es explícita: **el usuario hace clic sobre un río del catálogo**.

### 2.2. `rioId` no es lo mismo que `reachId`

| Campo | Significado | Uso |
|---|---|---|
| `rioId` | ID interno del río en la base de datos | Seleccionar, consultar y mostrar el río |
| `reachId` | ID del tramo GEOGloWS | Alertas, monitoreo y push |

En la API actual el `reachId` aparece con el nombre `geoglowsId`. En los
endpoints nuevos se puede exponer como `reachId`; durante la transición puede
aparecer con ambos nombres.

**Regla para el frontend:** para suscribirse o consultar un río usa `rioId`.
No uses `reachId` para seleccionar.

### 2.3. Geometría y orden de coordenadas

El backend entregará geometrías simplificadas en formato GeoJSON. Las
coordenadas siguen el estándar GeoJSON:

```text
[longitud, latitud]
```

No las inviertas. Si el mapa usa otra convención, haz la conversión en el
cliente.

---

## 3. Flujo de autenticación

### 3.1. Registro

```http
POST /api/auth/registro
Content-Type: application/json
```

```json
{
  "identificador": "juan",
  "password": "una-clave-de-al-menos-8-caracteres"
}
```

El `identificador` puede ser un nombre de usuario o un correo:

```json
{
  "identificador": "juan@correo.com",
  "password": "una-clave-de-al-menos-8-caracteres"
}
```

Reglas:

- es único;
- no distingue mayúsculas/minúsculas;
- si contiene `@`, se valida como correo;
- si no contiene `@`, se valida como nombre de usuario;
- no hay verificación por correo;
- no hay recuperación de contraseña;
- el registro público siempre crea un usuario con rol `USER`.

Respuesta `201`:

```json
{
  "token": "<jwt>",
  "usuario": {
    "id": 1,
    "identificador": "juan",
    "rol": "USER"
  }
}
```

### 3.2. Login

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "identificador": "juan@correo.com",
  "password": "..."
}
```

Respuesta `200`: el mismo formato del registro.

Si las credenciales son incorrectas, el backend responde `401` con un mensaje
genérico. No debe revelar si el identificador existe.

### 3.3. Sesión actual

```http
GET /api/auth/me
Authorization: Bearer <jwt>
```

```json
{
  "id": 1,
  "identificador": "juan",
  "rol": "USER"
}
```

### 3.4. Uso del token

Todas las rutas protegidas usan:

```http
Authorization: Bearer <jwt>
```

Duración propuesta: **24 horas**, configurable en el backend. No habrá refresh
token en este avance. Cuando el token expire, el frontend recibe `401` y debe
volver a pedir login.

Recomendaciones para el frontend:

- guardar el token en `sessionStorage` o en memoria;
- si se usa `localStorage`, recordar que es vulnerable a XSS;
- ante `401`, borrar la sesión y volver al login;
- ante `403`, mostrar “no tienes permiso”; no significa que la sesión haya
  expirado.

### 3.5. CORS

Si el frontend se sirve desde otro origen, el backend debe permitir ese origen.
El equipo de frontend debe informar la URL exacta de desarrollo y producción.
No se debe resolver con `Access-Control-Allow-Origin: *` si se envían
credenciales o tokens.

---

## 4. Flujo principal del mapa

### 4.1. Cargar el catálogo

```http
GET /api/rios/mapa
```

Respuesta propuesta:

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "LineString",
        "coordinates": [
          [-79.42, 9.03],
          [-79.41, 9.04],
          [-79.40, 9.05]
        ]
      },
      "properties": {
        "rioId": 4,
        "nombre": "Río Tocumen",
        "reachId": 670053261
      }
    }
  ]
}
```

- `features[].properties.rioId` es la clave para seleccionar.
- `features[].geometry` es la línea que se dibuja y sobre la que se hace clic.
- `reachId` es informativo; no se usa para suscribirse.
- El catálogo queda cerrado en **cuatro ríos** cercanos a la UTP:

| Río | `reachId` principal |
|---|---:|
| Río Abajo | `710039498` |
| Río Curundú | `710037516` |
| Río Juan Díaz | `710051399` |
| Río Tocumen | `670053261` |

- Los ríos y sus geometrías son datos del backend. El frontend no debe
  inventarlos ni completarlos con otro servicio.
- No se incluyen Matasnillo, Matías Hernández ni Tapia porque no tienen un
  tramo de pronóstico propio que no duplique el de otro río del catálogo.

La **escritura** del catálogo (crear, editar o desactivar ríos y cambiar su
geometría) será exclusiva de administradores. El usuario normal solo puede leer
el catálogo y seleccionar ríos. Si el panel de administración necesita editar
ríos, usará endpoints bajo `/api/admin/rios` con JWT y rol `ADMIN`.

### 4.2. Dibujar los ríos

El frontend puede usar la librería de mapas que prefiera (Leaflet, MapLibre,
Mapbox GL, etc.). El contrato no impone una librería.

Ejemplo conceptual con MapLibre:

```js
const respuesta = await fetch(`${API}/api/rios/mapa`);
const catalogo = await respuesta.json();

map.addSource("rios", {
  type: "geojson",
  data: catalogo
});

map.addLayer({
  id: "rios-lineas",
  type: "line",
  source: "rios",
  paint: {
    "line-color": "#1565c0",
    "line-width": 4
  }
});
```

### 4.3. Detectar el clic

```js
map.on("click", "rios-lineas", (evento) => {
  const rioId = evento.features[0].properties.rioId;
  abrirPanelRio(rioId);
});
```

El panel se abre para **cualquier río del catálogo**, esté o no seguido. Para
cargar sus datos, el frontend consulta:

```http
GET /api/alertas/rio/{rioId}
```

El panel puede mostrar:

- nombre del río;
- caudal actual y caudal máximo pronosticado;
- lluvia prevista para 24 y 72 horas, y su nivel de riesgo;
- estado actual y nivel de alerta;
- botón **Seguir este río** o **Dejar de seguir**;
- último evento o pronóstico, si el frontend decide mostrarlo.

La consulta de caudal y lluvia **no requiere estar suscrito**. El botón de
seguir solo controla si el usuario recibe notificaciones cuando ese río supera
los umbrales de alerta. El clic en el mapa no debe disparar por sí solo una
suscripción si el diseño pide confirmación. El backend solo recibe la intención
cuando el frontend llama al endpoint correspondiente.

> Los ajustes pendientes del backend para que este panel funcione sin fricción
> están en [`AJUSTES_PANEL_RIO.md`](AJUSTES_PANEL_RIO.md).

### 4.4. Resaltar los ríos que el usuario sigue

```http
GET /api/usuarios/me/rios
Authorization: Bearer <jwt>
```

Respuesta propuesta:

```json
[
  {
    "rioId": 4,
    "nombre": "Río Tocumen",
    "reachId": 670053261,
    "latitud": 9.07,
    "longitud": -79.39,
    "fechaSeleccion": "2026-10-22T15:00:00Z"
  }
]
```

El frontend cruza esta lista con el catálogo y resalta las features cuyo
`rioId` esté en la lista. El backend no necesita saber qué color usa el mapa.

---

## 5. Selección múltiple de ríos

### 5.1. Seguir un río

```http
POST /api/usuarios/me/rios/{rioId}
Authorization: Bearer <jwt>
```

Respuesta `200`:

```json
{
  "rioId": 1,
  "seguido": true,
  "mensaje": "Río agregado a tus alertas."
}
```

La operación es idempotente: si el usuario ya seguía el río, no se duplica la
relación.

### 5.2. Dejar de seguir un río

```http
DELETE /api/usuarios/me/rios/{rioId}
Authorization: Bearer <jwt>
```

Respuesta `204`, aunque el río no estuviera seleccionado.

### 5.3. Varios ríos

Un usuario puede seguir varios ríos. El frontend debe:

- permitir marcar y desmarcar varios;
- mostrar claramente cuáles están seleccionados;
- actualizar el mapa después de cada cambio;
- no asumir que hay un único río “principal”.

El backend enviará las alertas de **todos** los ríos que el usuario siga.
Seguir o dejar de seguir un río **no cambia** la posibilidad de consultar su
caudal y su lluvia en el panel del mapa; solo cambia si se reciben
notificaciones de ese río.

---

## 6. Notificaciones push

### 6.1. Requisito previo

El usuario debe haber iniciado sesión. El dispositivo se asocia a la cuenta, no
a un `reachId` suelto.

### 6.2. Configuración pública

```http
GET /api/push/config
```

El frontend usa esta respuesta para inicializar Firebase. El campo `reachId`
que aparece hoy es heredado de la PWA de prueba: **el frontend definitivo no
debe usarlo para decidir qué ríos recibe**. La selección se hace con
`/api/usuarios/me/rios`.

### 6.3. Registro del dispositivo

```http
POST /api/push/registrar
Authorization: Bearer <jwt>
Content-Type: application/json
```

```json
{
  "token": "<token FCM>",
  "deviceId": "<uuid local>",
  "plataforma": "web"
}
```

Ya no se envía `reachId`. El backend asocia el dispositivo al usuario
autenticado.

Respuesta `200`:

```json
{
  "id": 1,
  "activo": true,
  "mensaje": "Dispositivo registrado.",
  "fechaActualizacion": "2026-10-22T15:00:00Z"
}
```

### 6.4. Reglas de permisos

- Pedir permiso de notificaciones **solo después de un clic del usuario**.
- No pedir permiso al cargar la página.
- No registrar el token antes de tener permiso.
- Si el backend responde `410`, borrar el token local y pedir reactivación.
- Si el backend responde `401`, volver al login.
- Si el usuario no sigue ningún río, no recibirá alertas de ríos.

### 6.5. Qué recibe el dispositivo

El backend envía la alerta a todos los dispositivos activos de los usuarios que
siguen el río afectado. Si un usuario sigue dos ríos, puede recibir alertas de
ambos.

El payload de la notificación mantiene el formato data-only documentado en
[`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md). El frontend decide cómo
mostrar el clic y el detalle del evento.

### 6.6. Cambio de cuenta en el mismo dispositivo

El token FCM identifica una instalación, no a una persona. Si un usuario cierra
sesión y otro inicia sesión en el mismo teléfono, el backend debe **reasignar el
token al usuario nuevo** y dejar de enviar alertas del usuario anterior a ese
dispositivo.

Recomendaciones:

- al cerrar sesión, llamar a `DELETE /api/push/registrar` y pedir al navegador
  que desactive el token local;
- al registrar el mismo token con otro usuario, actualizar el dueño del
  dispositivo;
- no mantener dos usuarios asociados al mismo token, porque el teléfono
  recibiría alertas de ambos.

### 6.7. PWA de prueba con login mínimo

La PWA actual no tiene login y registra el push con un `reachId` fijo. Para que
siga sirviendo en la demo de clase, se actualizará con un flujo mínimo:

1. pantalla de login o registro;
2. lista de los cuatro ríos del catálogo con selección múltiple;
3. botón para activar notificaciones;
4. registro del token FCM con el JWT;
5. opción para cerrar sesión y desactivar el dispositivo.

No será el frontend definitivo ni tendrá panel de administración. Solo permitirá
probar en el celular el mismo flujo de usuario que ya existe en la consola.

---

## 7. Panel de administración

### 7.1. Quién puede usarlo

Solo un usuario autenticado con rol `ADMIN`.

- El registro público siempre crea `USER`.
- El administrador inicial se crea desde el backend con variables de entorno
  (`ALERTA_ADMIN_IDENTIFICADOR` y `ALERTA_ADMIN_PASSWORD`) o un mecanismo de
  siembra; no se crea desde el formulario público.
- El frontend puede ocultar el panel si `usuario.rol !== "ADMIN"`, pero eso es
  solo estética: **el backend debe validar el rol en cada endpoint**.

### 7.2. Enviar notificación de prueba

```http
POST /api/admin/push/demo
Authorization: Bearer <jwt-de-admin>
```

Respuesta:

```json
{
  "dispositivos": 3,
  "enviados": 3,
  "fallidos": 0
}
```

El endpoint envía a todos los dispositivos activos, sin crear un evento real de
riesgo. Es el equivalente del actual `POST /api/push/demo` de la consola.

### 7.3. Relación con la consola

La consola **no desaparece**. A partir de este avance también pedirá inicio de
sesión o registro al arrancar. Eso permite que la consola use el mismo JWT y los
mismos roles que el frontend.

La recomendación técnica es:

| Cliente | Autenticación propuesta | Endpoint de demo |
|---|---|---|
| Consola migrada | JWT con rol `ADMIN` | `POST /api/admin/push/demo` |
| Frontend nuevo | JWT con rol `ADMIN` | `POST /api/admin/push/demo` |

Con la consola autenticada, `X-Admin-Token` deja de ser necesario. Se puede
retirar cuando la consola termine de migrar sus opciones administrativas. Si se
conserva temporalmente, debe quedar claro que es solo compatibilidad y que el
frontend **nunca** debe usarlo.

El token es un secreto compartido, no una credencial de usuario. No debe
enviarse desde el navegador.

---

## 8. Resumen de endpoints del avance

| Método | Ruta | Auth | Estado |
|---|---|---|---|
| POST | `/api/auth/registro` | pública | propuesto |
| POST | `/api/auth/login` | pública | propuesto |
| GET | `/api/auth/me` | JWT | propuesto |
| GET | `/api/rios` | pública | existe |
| GET | `/api/rios/mapa` | pública | propuesto |
| GET | `/api/usuarios/me/rios` | JWT | propuesto |
| POST | `/api/usuarios/me/rios/{rioId}` | JWT | propuesto |
| DELETE | `/api/usuarios/me/rios/{rioId}` | JWT | propuesto |
| GET | `/api/alertas/rio/{rioId}` | pública | existe |
| GET | `/api/push/config` | pública | existe |
| POST | `/api/push/registrar` | JWT | cambiará |
| DELETE | `/api/push/registrar` | JWT | cambiará |
| POST | `/api/push/prueba` | JWT | cambiará |
| POST | `/api/admin/push/demo` | JWT + ADMIN | propuesto |
| POST | `/api/admin/rios` | JWT + ADMIN | reservado para mantenimiento; no se usará en esta entrega |
| PUT | `/api/admin/rios/{rioId}` | JWT + ADMIN | reservado para mantenimiento; no se usará en esta entrega |
| POST | `/api/push/demo` | `X-Admin-Token` | existente; se retirará al migrar la consola |

Las rutas actuales que usan `X-Admin-Token` para evaluación manual y consulta de
suscriptores se migrarán a JWT con rol `ADMIN` cuando la consola adopte el
login. Si se mantienen temporalmente, serán solo compatibilidad para la consola
y no se documentarán como parte del contrato del frontend.

Las rutas heredadas de suscripción por correo o teléfono
(`POST /api/alertas/suscribir` y
`GET /api/alertas/suscripciones/{reachId}`) se retirarán del flujo de usuario y
de la consola. El modelo definitivo es usuario–ríos con notificaciones push.

---

## 9. Códigos de error

| Código | Significado para el frontend |
|---|---|
| 400 | Datos inválidos; mostrar el mensaje del backend |
| 401 | Credenciales incorrectas o token ausente/expirado |
| 403 | El usuario no tiene rol suficiente para esa operación |
| 404 | El río o recurso no existe |
| 409 | Identificador duplicado, token push en conflicto u operación incompatible |
| 410 | Token FCM inválido; hay que reactivar notificaciones |
| 429 | Demasiadas pruebas push; esperar |
| 502 | Fallo de GEOGloWS, Open-Meteo o FCM; mostrar aviso temporal |

Para los errores `400` y `409`, el backend puede devolver texto plano. El
frontend debe tolerar tanto texto como JSON.

---

## 10. Lista de comprobación para el equipo de frontend

- [ ] El registro acepta un solo `identificador` y una contraseña.
- [ ] El login funciona con correo o nombre de usuario.
- [ ] El token se envía como `Authorization: Bearer`.
- [ ] El mapa dibuja el `FeatureCollection` de `/api/rios/mapa`.
- [ ] El clic lee `properties.rioId`, no calcula cercanía.
- [ ] Al hacer clic en cualquier río se muestra el panel con caudal y lluvia,
      aunque el río no esté seguido.
- [ ] Seguir o dejar de seguir solo cambia las notificaciones, no la consulta.
- [ ] El usuario puede seguir y dejar de seguir varios ríos.
- [ ] Los ríos seguidos se resaltan usando `/api/usuarios/me/rios`.
- [ ] El registro push requiere sesión y no envía `reachId`.
- [ ] El panel admin solo se muestra para `rol === "ADMIN"`.
- [ ] El panel admin llama a `/api/admin/push/demo`, nunca a `/api/push/demo`
      con `X-Admin-Token`.
- [ ] Los errores `401`, `403`, `409` y `410` tienen mensajes claros.
- [ ] No hay credenciales ni tokens administrativos en el código del cliente.

---

## 11. Decisiones tomadas y pendientes

Decisiones tomadas:

1. La consola migrará a JWT y `X-Admin-Token` se retirará al terminar la
   migración.
2. El catálogo queda cerrado en cuatro ríos: Abajo, Curundú, Juan Díaz y
   Tocumen. Se cargan predefinidos; no habrá creación ni edición de ríos en la
   interfaz de esta entrega. Los endpoints de escritura quedarán reservados y
   protegidos para mantenimiento.
3. Las suscripciones heredadas por correo/teléfono se retiran del flujo de
   usuario y de la consola.
4. La PWA de prueba tendrá un login mínimo para registrar el push en el celular.
5. La duración inicial del JWT será de 24 horas, configurable, sin refresh
   token.
6. La geometría se obtiene de GEOGloWS V2 y se guarda simplificada como
   GeoJSON; no se consulta a internet en tiempo de ejecución.
7. El panel de administración del frontend solo necesita el push de prueba. La
   consola administrativa tendrá además simulación y evaluación manual.
8. La consulta de caudal y lluvia está disponible para cualquier río del
   catálogo al hacer clic en el mapa, sin necesidad de seguirlo. Seguir un río
   solo habilita sus notificaciones cuando supera los umbrales.
9. El gráfico del panel será un hidrograma de caudal contra tiempo con las
   líneas de umbral. La representación del nivel del agua, si se incluye, será
   ilustrativa y derivada del caudal; no se mostrarán metros ni bordes reales
   del cauce, porque no hay curva de gasto ni batimetría.

Pendientes antes de cerrar el avance:

1. Definir la URL exacta del frontend para CORS.
2. Investigar y calibrar umbrales de lluvia por temporada (invierno/verano).
   No se implementará con valores inventados.
3. Implementar y probar todo el flujo descrito en esta guía.

Hasta que termine la implementación, este documento es una guía de diseño y no
un contrato implementado.
