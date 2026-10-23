# Continuidad de sesión y traspaso de contexto

**Última actualización:** 23/10/2026<br>
**Rama de trabajo:** `backend`<br>
**Último commit de referencia:** el commit de documentación más reciente de la
rama (`git log -1`).<br>
**Estado:** planificación cerrada; implementación del siguiente avance **no iniciada**.<br>
**Objetivo de este documento:** permitir que otra persona o una sesión nueva de IA
retome el proyecto desde otra computadora sin necesitar el historial del chat.

---

## 1. Qué leer primero

Al retomar el proyecto, leer en este orden:

1. Este documento.
2. [`RESUMEN_PROYECTO.md`](RESUMEN_PROYECTO.md): explicación funcional y alcance.
3. [`GUIA_FRONTEND.md`](GUIA_FRONTEND.md): diseño del avance de login, mapa,
   selección múltiple y administración.
4. [`AJUSTES_PANEL_RIO.md`](AJUSTES_PANEL_RIO.md): correcciones necesarias para
   el panel informativo de cada río.
5. [`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md): contrato de la API que
   existe hoy.
6. [`ARQUITECTURA.md`](ARQUITECTURA.md): separación entre backend, consola, PWA
   y frontend.
7. [`../GEMINI.md`](../GEMINI.md): reglas de trabajo para agentes de IA.

Después revisar:

```powershell
git status
git log -5 --oneline
```

**Importante:** no asumir que la autenticación, el catálogo de cuatro ríos ni la
selección múltiple ya existen en el código. Hasta la fecha de este documento
solo hay documentación y decisiones; no se ha implementado ese avance.

---

## 2. Objetivo del siguiente avance

Convertir el backend actual en una aplicación con:

- registro e inicio de sesión con un solo identificador (correo o nombre de
  usuario) y contraseña;
- dos roles: `USER` y `ADMIN`;
- catálogo cerrado de cuatro ríos cercanos a la UTP, con geometría;
- selección múltiple de ríos por usuario;
- consulta de caudal, lluvia y pronóstico de cualquiera de los cuatro ríos,
  esté o no seguido;
- push asociado a los ríos que sigue cada usuario;
- consola con login y menús distintos según el rol;
- PWA mínima de prueba con login, selección de ríos y activación de push.

La consola **no desaparece** en esta entrega. Sigue siendo el cliente de
demostración hasta que el frontend definitivo cubra sus funciones.

---

## 3. Decisiones cerradas

### 3.1. Autenticación y roles

- Un solo campo `identificador`: correo o nombre de usuario.
- Contraseña cifrada con BCrypt.
- Registro público siempre crea rol `USER`.
- El administrador se crea desde configuración de entorno; no desde el
  formulario público.
- JWT de 24 horas, configurable, sin refresh token.
- Al expirar la sesión, el cliente vuelve al login.
- `X-Admin-Token` se retira cuando la consola migre a JWT.
- Los endpoints administrativos validan el rol en el backend, no solo en la
  interfaz.

### 3.2. Cuenta de demostración

- La cuenta de demostración será `admin` con la contraseña local acordada.
- **No escribir esa contraseña en el repositorio, README ni documentación.**
- La contraseña vive únicamente en `.env.demo` local, que está ignorado por Git.
- `.env.demo.example` solo contiene placeholders.
- Si el backend arranca sin `ALERTA_ADMIN_PASSWORD`, no debe crear el
  administrador; debe registrar una advertencia.
- No es necesario sembrar usuarios normales: se pueden crear desde el registro
  al probar.

### 3.3. Catálogo de ríos

El catálogo queda cerrado en cuatro ríos. No se crearán ni editarán ríos desde
la interfaz en esta entrega.

| Río | `reachId` principal | Distancia aproximada a la UTP | Geometría modelada | VPU |
|---|---|---:|---:|---:|
| **Río Abajo** | `710039498` | ~1.5 km | ~4.2 km | 701 |
| **Río Curundú** | `710037516` | ~3.7 km al cauce; ~8.0 km al tramo modelado | ~6.0 km | 701 |
| **Río Juan Díaz** | `710051399` | ~8.9 km | ~13.4 km | 701 |
| **Río Tocumen** | `670053261` | ~14.3 km | ~3.7 km | 614 |

Los cuatro `reachId` principales fueron verificados contra:

```text
https://geoglows.ecmwf.int/api/v2/forecaststats/{reachId}
```

Ríos descartados y por qué:

- **Matasnillo:** ~1 km, pero GEOGloWS no tiene un tramo propio; el punto más
  cercano se resuelve contra Abajo o Curundú.
- **Matías Hernández:** su tramo modelado coincide con el de Río Abajo; incluirlo
  duplicaría el mismo pronóstico.
- **Tapia:** se resuelve contra Juan Díaz o Tocumen; no tiene un tramo
  independiente claro.

### 3.4. Geometría

- Fuente: GEOGloWS V2, red TDX-Hydro.
- Capas oficiales usadas:
  - `s3://geoglows-v2/hydrography/vpu=701/streams_701.gpkg`
  - `s3://geoglows-v2/hydrography/vpu=614/streams_614.gpkg`
- El campo `LINKNO` es el `reachId`.
- `DSLINKNO` indica el tramo aguas abajo.
- La geometría está en EPSG:3857; hay que convertirla a WGS84.
- Se agruparán tramos conectados para dibujar el cauce principal:
  - Abajo: `710039498`.
  - Curundú: `710035532` → `710037516` → `710039500`.
  - Juan Díaz: `710051399` → `710035528` → `710055369` → `710057353`.
  - Tocumen: `670053261` → `670054285` → `670055309`.
- Guardar una geometría simplificada en GeoJSON dentro de la base de datos. No
  consultar la geometría a internet en tiempo de ejecución.
- La app no debe buscar el río más cercano a coordenadas arbitrarias: el usuario
  hace clic sobre una línea del catálogo.

### 3.5. Consola, PWA y push

- La consola pedirá login o registro al arrancar.
- Menú de usuario normal: catálogo, panel de cualquier río (caudal/lluvia),
  ríos seguidos, seguir o dejar de seguir, cerrar sesión.
- Menú de administrador: lo anterior más simulación, evaluación manual y push de
  prueba.
- No habrá opción de crear o editar ríos en la consola.
- La PWA de prueba tendrá login, selección múltiple y activación de push.
- El token FCM se asocia al usuario, no a un `reachId` suelto.
- Al iniciar sesión en un dispositivo que ya tenía otro usuario, el token se
  reasigna al último usuario.
- Al cerrar sesión, el dispositivo se desactiva.
- Las suscripciones heredadas por correo/teléfono se retiran del flujo y de la
  consola.

### 3.6. Consulta de información y suscripción

- El panel de información se abre al hacer clic en **cualquier río del
  catálogo**, esté o no seguido.
- El panel muestra caudal y precipitación usando los datos de GEOGloWS y
  Open-Meteo. El backend ya expone la consulta en
  `GET /api/alertas/rio/{rioId}`.
- La consulta **no depende de la suscripción**: seguir un río no habilita el
  panel ni dejar de seguirlo lo bloquea.
- El botón **Seguir** o **Dejar de seguir** solo controla si se reciben
  notificaciones cuando ese río supera los umbrales de alerta.
- La selección múltiple sigue existiendo para definir qué ríos generan push,
  no para limitar qué ríos se pueden consultar.

### 3.7. CORS y configuración

- CORS será configurable por variable de entorno.
- Como aún no existe el frontend, se dejarán orígenes locales comunes por
  defecto y se ajustará cuando el equipo de frontend informe la URL exacta.
- No usar `Access-Control-Allow-Origin: *` si se envían credenciales o tokens.

### 3.8. Umbrales

- Los umbrales actuales de caudal y lluvia siguen siendo provisionales.
- La calibración por temporada (invierno/verano) queda documentada como trabajo
  futuro. No se implementará con valores inventados.
- `returnperiods` de GEOGloWS sigue fallando; se mantiene el fallback
  15/30/60 m³/s.

---

## 4. Estado actual del repositorio

- Rama actual: `backend`.
- Último commit: el más reciente de `backend` (`git log -1`).
- El repositorio remoto es `https://github.com/aaron-rm/Ing_Ambiental.git`.
- La rama por defecto es `main`; el trabajo de este avance va en `backend`.
- No hay código de autenticación, usuarios, roles, JWT, BCrypt ni CORS.
- No hay modelo usuario–ríos.
- `DispositivoPush` todavía tiene un solo `reachId` y no está ligado a usuarios.
- `Rio` todavía no tiene geometría.
- La PWA actual solo prueba notificaciones; no tiene login ni selección de ríos.
- La consola actual usa `X-Admin-Token` y suscripciones heredadas.

### 4.1. Inconsistencia conocida del seed actual

`InicializadorDatos` crea un río llamado **“Río Pacora”** con
`reachId = 710039498`. Ese `reachId` corresponde en realidad al **Río Abajo**.
El mismo nombre y `reachId` se repiten en `ConsolaDemoRunner` y en el valor por
defecto de `alerta.demo.reach-id-inicial`.

Esto se corregirá al implementar el catálogo de cuatro ríos. Hay dos cuidados:

- el seed solo actúa si la tabla `rios` está vacía, así que una base que ya tenga
  “Río Pacora” no se corrige sola; la implementación debe hacer *upsert* por
  `reachId` o migrar esa fila;
- las pruebas y la consola que mencionan “Río Pacora” deben actualizarse en el
  mismo cambio.

### 4.2. Documentación de este avance

La documentación de este avance está versionada en la rama `backend`. Antes de
empezar a implementar, confirmar con `git status` que no haya cambios de código
reales pendientes.

**Nota para Linux:** varios archivos aparecen como modificados solo por
diferencias de fin de línea (CRLF en Windows frente a LF en Linux). No son
cambios de contenido y no deben mezclarse con el commit de implementación.

### 4.3. Pruebas

- Último estado conocido: **51 pruebas, 0 fallos**.
- No se ejecutaron pruebas después de estos cambios de documentación porque no
  se tocó código.
- Al iniciar la implementación, ejecutar:

```powershell
.\mvnw.cmd test
```

---

## 5. Orden recomendado de implementación

1. **Autenticación**
   - entidad de usuario, repositorio, servicio y controlador;
   - BCrypt;
   - registro y login;
   - JWT de 24 horas;
   - filtro de seguridad y roles.

2. **Catálogo de ríos**
   - agregar geometría a `Rio`;
   - sembrar los cuatro ríos;
   - endpoint de catálogo GeoJSON;
   - proteger escritura para `ADMIN`, aunque no se use en la interfaz.

3. **Relación usuario–ríos**
   - entidad y repositorio;
   - endpoints para listar, seguir y dejar de seguir;
   - validar que el río exista.

4. **Push por usuario**
   - asociar `DispositivoPush` al usuario;
   - registro con JWT;
   - reasignación de token al último login;
   - desactivación al cerrar sesión;
   - envío solo a usuarios que siguen el río afectado.

5. **Consola**
   - login/registro;
   - menús por rol;
   - catálogo y selección múltiple;
   - panel de caudal/lluvia para cualquier río del mapa, sin exigir suscripción;
   - retirar suscripciones heredadas y `X-Admin-Token`.

6. **PWA mínima**
   - login/registro;
   - selección múltiple;
   - activación de push con JWT;
   - cierre de sesión.

7. **Pruebas y documentación**
   - actualizar pruebas;
   - actualizar `INTEGRACION_FRONTEND.md`;
   - actualizar `RESUMEN_PROYECTO.md`, `GUIA_FRONTEND.md` y `README.md`;
   - actualizar este documento al final de la sesión.

---

## 6. Cómo retomar en otra computadora

1. Clonar o actualizar el repositorio:

```powershell
git fetch
git switch backend
git pull
```

2. Verificar que los documentos nuevos estén presentes:

```powershell
Test-Path docs/CONTINUIDAD_SESION.md
Test-Path docs/AVANCE_EQUIPO.md
```

3. Crear la configuración local (nunca versionarla):

```powershell
Copy-Item .env.demo.example .env.demo
# completar DB_PASSWORD, Firebase y credenciales de demostración
```

4. Verificar que `.env.demo` esté ignorado:

```powershell
git check-ignore .env.demo
```

5. Ejecutar pruebas antes de cambiar código:

```powershell
.\mvnw.cmd test
```

6. Abrir una sesión nueva de IA y pedirle que lea, en orden:

```text
docs/CONTINUIDAD_SESION.md
docs/RESUMEN_PROYECTO.md
docs/GUIA_FRONTEND.md
docs/INTEGRACION_FRONTEND.md
docs/ARQUITECTURA.md
GEMINI.md
```

7. Pedirle que revise `git status` y que confirme que la implementación no ha
   comenzado antes de tocar código.

---

## 7. Archivos clave

| Archivo | Para qué sirve |
|---|---|
| `src/main/java/.../demo/ConsolaDemoRunner.java` | Consola que se adaptará con login y menús por rol. |
| `src/main/java/.../demo/BackendApiClient.java` | Cliente HTTP de la consola; deberá usar JWT. |
| `src/main/java/.../demo/InicializadorDatos.java` | Seed actual; se reemplazará por los cuatro ríos. |
| `src/main/java/.../rio/Rio.java` | Entidad de río; falta geometría. |
| `src/main/java/.../rio/RioController.java` | Lectura pública y escritura aún pública. |
| `src/main/java/.../push/DispositivoPush.java` | Modelo actual con un solo `reachId`; debe migrarse. |
| `src/main/java/.../push/PushService.java` | Lógica de envío; debe pasar a usuario–ríos. |
| `src/main/java/.../push/PushController.java` | Registro push y demo con token. |
| `src/main/java/.../alerta/AlertaController.java` | Simulación y endpoints administrativos actuales. |
| `src/main/java/.../GEOGLOWS/ClienteApiGeoglows.java` | Cliente GEOGloWS; contiene `buscarReachIdCercano`. |
| `src/main/resources/application.properties` | Configuración actual; faltan CORS, JWT y admin. |
| `pom.xml` | Falta Spring Security, BCrypt y JWT. |
| `src/main/resources/static/` | PWA mínima actual; se actualizará con login. |

---

## 8. Reglas que no se deben romper

- No publicar contraseñas, secretos, tokens ni credenciales reales.
- No escribir la contraseña del administrador de demostración en este documento.
- No versionar `.env.demo` ni archivos de Firebase.
- No inventar ríos, `reachId` ni geometrías.
- No hacer que la consola o la PWA accedan directamente a repositorios o a la
  lógica de negocio; deben usar la API HTTP.
- No hacer commits automáticamente si el usuario no lo pidió.
- Actualizar la documentación cuando cambie un contrato o una decisión.
- Mantener las pruebas existentes y agregar las del flujo nuevo.
- No afirmar que el push funciona de extremo a extremo sin una prueba en un
  dispositivo real.

---

## 9. Pendientes y riesgos

1. **URL exacta del frontend para CORS:** aún no existe; se dejará configurable.
2. **Push real:** falta verificar Firebase y recepción en un dispositivo físico.
3. **Rotación de credenciales locales de PostgreSQL:** pendiente; no registrar
   valores en el repositorio.
4. **Umbrales por temporada:** investigación futura; no inventar valores.
5. **Seed actual inconsistente:** “Río Pacora” con `reachId` de Abajo; corregir
   al implementar el catálogo.
6. **Fin de línea en Linux:** algunos archivos con CRLF aparecen como
   modificados sin cambios reales de contenido; no incluirlos en commits de
   documentación o implementación.
7. **Panel de administración del frontend:** por ahora solo necesita el push de
   prueba; la consola administrativa tendrá además simulación y evaluación
   manual.
8. **Panel de río:** revisar [`AJUSTES_PANEL_RIO.md`](AJUSTES_PANEL_RIO.md)
   antes de implementar el mapa, el gráfico o las recomendaciones.

---

## 10. Checklist para actualizar este documento

Al terminar una sesión de trabajo, actualizar:

- [ ] Fecha y rama.
- [ ] Último commit.
- [ ] Qué se implementó realmente.
- [ ] Qué pruebas se ejecutaron y su resultado.
- [ ] Archivos nuevos o modificados.
- [ ] Decisiones nuevas.
- [ ] Pendientes y bloqueos.
- [ ] Confirmar que no se agregaron secretos.
