# Continuidad de sesión y traspaso de contexto

**Última actualización:** 23/10/2026<br>
**Rama de trabajo:** `backend`<br>
**Último commit de referencia:** el commit de documentación más reciente de la
rama (`git log -1`).<br>
**Estado:** implementado, con **67 pruebas en verde** y **prueba de humo real
con PostgreSQL en Linux (23/10/2026)**. Falta repetir la prueba en Windows y
verificar el push de extremo a extremo en un dispositivo.<br>
**Objetivo de este documento:** permitir que otra persona o una sesión nueva de IA
retome el proyecto desde otra computadora sin necesitar el historial del chat.

---

## 1. Qué leer primero

Al retomar el proyecto, leer en este orden:

1. Este documento.
2. [`RESUMEN_PROYECTO.md`](RESUMEN_PROYECTO.md): explicación funcional y alcance.
3. [`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md): contrato de la API que
   existe hoy.
4. [`GUIA_FRONTEND.md`](GUIA_FRONTEND.md): diseño que se implementó (login,
   mapa, selección múltiple y administración).
5. [`AJUSTES_PANEL_RIO.md`](AJUSTES_PANEL_RIO.md): criterios del panel
   informativo de cada río.
6. [`ARQUITECTURA.md`](ARQUITECTURA.md): separación entre backend, consola, PWA
   y frontend.
7. [`../GEMINI.md`](../GEMINI.md): reglas de trabajo para agentes de IA.

Después revisar:

```powershell
git status
git log -8 --oneline
```

**Importante:** este avance **ya está implementado**. No hay que volver a
diseñarlo, a sembrar los ríos ni a migrar la consola: al arrancar, el backend
carga el catálogo desde `catalogo/rios.json` y la consola pide inicio de sesión.

---

## 2. Qué se implementó

### 2.1. Autenticación, roles y seguridad

- Entidad `Usuario` en el paquete `usuario`, con un solo campo `identificador`
  (correo o nombre de usuario), contraseña con BCrypt y roles `USER` / `ADMIN`.
- Registro público (`POST /api/auth/registro`) siempre crea `USER`; login
  (`POST /api/auth/login`) y sesión actual (`GET /api/auth/me`).
- JWT HS256 de 24 horas (`ALERTA_JWT_SECRET`); sin refresh token. Filtro
  `JwtAuthenticationFilter`; al vencer, el cliente vuelve al login.
- Administrador sembrado desde entorno (`ALERTA_ADMIN_IDENTIFICADOR` y
  `ALERTA_ADMIN_PASSWORD`). Si falta la contraseña, no se crea y se registra una
  advertencia.
- `SecurityConfig` con reglas por rol:
  - `POST /api/alertas/simular` y `POST /api/alertas/evaluar/**` exigen
    `ADMIN`;
  - `/api/admin/**` exige `ADMIN`;
  - `/api/push/**` (excepto `/api/push/config`) exige JWT;
  - catálogo y consultas GET de alertas siguen siendo públicos;
  - CORS configurable con `ALERTA_CORS_ORIGENES`.

### 2.2. Catálogo de cuatro ríos con geometría real

- Catálogo cerrado: Abajo `710039498`, Curundú `710037516` (cadena
  `710035532 → 710037516 → 710039500`), Juan Díaz `710051399` (cadena
  `710051399 → 710035528 → 710055369 → 710057353`) y Tocumen `670053261`
  (cadena `670053261 → 670054285 → 670055309`).
- Geometría real de GEOGloWS V2 (red TDX-Hydro):
  `s3://geoglows-v2/hydrography/vpu=701/streams_701.gpkg` y
  `vpu=614/streams_614.gpkg`. `LINKNO` es el `reachId`; la geometría original
  está en EPSG:3857.
- `tools/generar_catalogo_geoglows.py` (solo biblioteca estándar) lee los
  GeoPackage, convierte a WGS84, simplifica con Douglas-Peucker (8 m) y escribe
  `src/main/resources/catalogo/rios.json` (~40 KB) con el punto representativo
  de cada río. Es reproducible: si falta un `LINKNO`, falla en vez de inventar.
- `InicializadorCatalogo` hace *upsert* por `reachId` al arrancar. Esto corrigió
  el seed heredado “Río Pacora” (que en realidad era Río Abajo) sin duplicar
  filas ni borrar ríos que otros integrantes hayan agregado.
- `Rio` ganó `geometria` (GeoJSON como texto). `GET /api/rios` devuelve el
  resumen y `GET /api/rios/mapa` una `FeatureCollection` con la geometría.
- La escritura del catálogo quedó bajo `/api/admin/rios` (JWT + `ADMIN`).
- Se eliminó `InicializadorDatos` y la propiedad `alerta.demo.reach-id-inicial`;
  ahora se usa `alerta.demo.cargar-catalogo` (`ALERTA_CARGAR_CATALOGO`).

### 2.3. Selección usuario–ríos

- Entidades `UsuarioRio` / `UsuarioRioRepository` y endpoints:
  - `GET /api/usuarios/me/rios`
  - `POST /api/usuarios/me/rios/{rioId}` (idempotente)
  - `DELETE /api/usuarios/me/rios/{rioId}`
- El panel de un río se consulta con `GET /api/alertas/rio/{rioId}` esté o no
  seguido; seguir solo controla las notificaciones push.

### 2.4. Push asociado al usuario

- `DispositivoPush` se ligó a `Usuario`; `reachId` quedó como columna heredada
  con `0L` y ya no participa del enrutamiento.
- Registro y baja con JWT: `POST` y `DELETE /api/push/registrar` (sin
  `reachId`). `GET /api/push/config` ya no expone `reachId`.
- La prueba `POST /api/push/prueba` exige JWT; el demo administrativo pasó a
  `POST /api/admin/push/demo` (JWT + `ADMIN`).
- El envío por evento se enruta a los dispositivos activos de los usuarios que
  siguen el río afectado.
- Si el mismo dispositivo cambia de usuario, el token FCM se reasigna al último
  login; al cerrar sesión, la PWA llama a `DELETE /api/push/registrar`.

### 2.5. Consola (`ConsolaDemoRunner`)

- Pide login o registro al arrancar; guarda el JWT solo en memoria.
- Menú `USER`: catálogo, panel de cualquier río, seguir/dejar de seguir, mis
  ríos, cerrar sesión.
- Menú `ADMIN` añade: simulación (no envía push), evaluación real con datos de
  GEOGloWS/Open-Meteo y push de demostración.
- `BackendApiClient` migró a JWT (`Authorization: Bearer`), con los DTO de la
  API actual. Los dos endpoints heredados con `X-Admin-Token`
  (`POST /api/push/demo` y `GET /api/alertas/suscripciones/{reachId}`) siguen
  existiendo solo por compatibilidad y no se documentan para el frontend.
- Si se arranca sin terminal interactiva (segundo plano, doble clic), la consola
  se omite con un aviso en lugar de repetir el menú; con entrada cerrada (EOF)
  también termina sola.

### 2.6. PWA de prueba (`src/main/resources/static/`)

- Login/registro, catálogo, mapa SVG propio (sin librería externa), panel con
  hidrograma de caudal y umbrales, lluvia de Open-Meteo, seguir/dejar de seguir.
- Activación de push con JWT (sin `reachId`), prueba desde la propia PWA y
  desactivación del dispositivo al cerrar sesión.
- El hidrograma usa caudal pronosticado; no se muestran metros de nivel porque
  dependerían de la sección real del cauce.

### 2.7. Pruebas

- `bash mvnw test` (Linux; `./mvnw` falla con “Permiso denegado”): **67
  pruebas, 0 fallos**.
- Nuevas: `UsuarioRioServiceTest`, `InicializadorCatalogoTest`, `RioServiceTest`,
  `AuthServiceTest`; reescritas `PushServiceTest` y `BackendApiClientTest`.
- Prueba de humo real (Linux, 23/10/2026; PostgreSQL 16 en Podman): el backend
  arrancó en 5 s, cargó los cuatro ríos con geometría, el login `ADMIN` emitió
  JWT, el seguimiento de ríos respondió, `/api/usuarios/me/rios` sin token
  devolvió `401` y el panel de Río Abajo devolvió datos reales de GEOGloWS y
  Open-Meteo (`NORMAL`, 0.9 m³/s, lluvia 13.9/33.4 mm). Sin Firebase, el push
  queda deshabilitado con motivo. Queda pendiente repetirla en Windows.

---

## 3. Decisiones clave aplicadas

- Un solo `identificador` (correo o usuario); BCrypt; JWT de 24 h; sin refresh
  token; el registro público nunca crea administradores.
- El administrador de demostración vive únicamente en `.env.demo` (ignorado por
  Git). **No escribir su contraseña en el repositorio.**
- Catálogo cerrado de cuatro ríos; no se incluyen Matasnillo, Matías Hernández
  ni Tapia porque no tienen un tramo de pronóstico propio que no duplique otro.
- No inventar ríos, `reachId` ni geometrías: todo sale de GEOGloWS V2 y del
  script reproducible.
- El panel de cualquier río se consulta esté o no seguido; seguir solo controla
  el push.
- El gráfico del panel se basa en caudal (m³/s); no se muestran niveles en metros
  ni bordes de inundación inventados.
- CORS queda configurable porque la URL exacta del frontend aún no existe.
- Los umbrales de caudal (fallback 15/30/60 m³/s) y de lluvia siguen siendo
  provisionales; la calibración por temporada queda para después.
- La consola sigue siendo el cliente de demostración; no desaparece en esta
  entrega. No se agregó creación/edición de ríos a la consola.

---

## 4. Estado del repositorio

- Rama: `backend`. Remoto: `https://github.com/aaron-rm/Ing_Ambiental.git`
  (la rama por defecto es `main`).
- Todos los commits del avance están subidos a `origin/backend`:

| Commit | Contenido |
|---|---|
| `7d843c9` | `feat(auth)`: usuarios, JWT, roles y seguridad |
| `96ff6e8` | `feat(usuarios)`: selección usuario–ríos y push por seguidores |
| `6af8015` | `feat(catalogo)`: cuatro ríos con geometría real de GEOGloWS V2 |
| `04f7937` | `feat(seguridad)`: rol `ADMIN` para simular y evaluar |
| `b5f3e28` | `feat(consola)`: consola JWT con login y menús por rol |
| `55c0ccb` | `feat(pwa)`: login, mapa, panel y push por usuario |
| `29d7481` | `fix(pwa)`: orden de carga y textos del manifiesto |
| `ccedeb4` | `docs`: documentar autenticación, catálogo y push por usuario |
| `e97671e` | `feat(scripts)`: arranque de demo en Linux y documentación |
| `5d191ae` | `fix(consola)`: no repetir el menú sin terminal interactiva |
| `9a346ed` | `feat(scripts)`: relanzar en terminal y abrir el navegador en Linux |

- Variables nuevas: `ALERTA_JWT_SECRET`, `ALERTA_ADMIN_IDENTIFICADOR`,
  `ALERTA_ADMIN_PASSWORD`, `ALERTA_CORS_ORIGENES`, `ALERTA_CARGAR_CATALOGO`.
  Retirada: `ALERTA_REACH_ID_INICIAL` (y `ALERTA_CREAR_RIO_INICIAL`).
  `ALERTA_ADMIN_TOKEN` sigue solo para los dos endpoints heredados.
- La geometría de Río Abajo (`710039498`) es un `MultiLineString` con varias
  líneas; el punto del panel corresponde a la línea principal según
  `LengthGeodesicMeters`.

### 4.1. Sesión de prueba del 23/10/2026 (Linux)

- Entorno: Fedora/KDE con Java 25; PostgreSQL 16 en un contenedor Podman
  (`alerta-postgres`, volumen `alerta-pgdata`, puerto `127.0.0.1:5432`).
- `scripts/iniciar-demo.sh` es el equivalente Linux de `iniciar-demo.ps1`:
  carga `.env.demo`, relanza el script en Konsole/GNOME Terminal/xterm si no hay
  terminal (doble clic), abre el navegador cuando el backend responde y no lanza
  una segunda instancia si el puerto 8081 ya está ocupado. `--tunel` inicia
  `cloudflared` si está instalado.
- `.env.demo` de esa máquina recibió `ALERTA_JWT_SECRET`,
  `ALERTA_ADMIN_IDENTIFICADOR`, `ALERTA_ADMIN_PASSWORD`, `ALERTA_CORS_ORIGENES`,
  `ALERTA_CARGAR_CATALOGO` y `FIREBASE_ENABLED=false`. **La contraseña de
  PostgreSQL se reemplazó por una del contenedor local.** Si OneDrive sincroniza
  `.env.demo` de vuelta a Windows, restaurar allí `DB_PASSWORD` con la
  contraseña del PostgreSQL de Windows.
- OneDrive sincronizó `target/` desde Windows con clases `*-safeBackup-0001` y
  Maven falló con «Unable to find a single main class». Se corrigió con
  `bash mvnw clean` (en Windows: `.\mvnw.cmd clean`). Si vuelve a ocurrir, es el
  mismo remedio.
- La consola ahora se omite sola cuando no hay terminal (antes quedaba al 98 %
  de CPU repitiendo el menú) y cierra al recibir EOF. Verificado con CPU en 0 %.

---

## 5. Cómo retomar en otra computadora

1. Clonar o actualizar el repositorio:

```powershell
git fetch
git switch backend
git pull
```

2. Verificar que la documentación esté presente:

```powershell
Test-Path docs/CONTINUIDAD_SESION.md
Test-Path docs/INTEGRACION_FRONTEND.md
```

3. Crear la configuración local (nunca versionarla):

```powershell
Copy-Item .env.demo.example .env.demo
# completar DB_PASSWORD, Firebase, ALERTA_JWT_SECRET y credenciales de demostración
```

Si `.env.demo` ya existe (por ejemplo, sincronizado por OneDrive desde la
laptop Linux), **revise `DB_PASSWORD`**: debe ser la contraseña del PostgreSQL de
esta máquina. Las demás variables nuevas (`ALERTA_JWT_SECRET`,
`ALERTA_ADMIN_IDENTIFICADOR`, `ALERTA_ADMIN_PASSWORD`, `ALERTA_CORS_ORIGENES`)
ya vienen completas y sirven en ambas máquinas.

4. Verificar que `.env.demo` esté ignorado:

```powershell
git check-ignore .env.demo
```

5. Ejecutar pruebas antes de cambiar código:

```powershell
.\mvnw.cmd test
```

Esperado: **67 pruebas, 0 fallos**.

6. Para regenerar el catálogo (opcional; no hace falta para arrancar):

```bash
python3 tools/generar_catalogo_geoglows.py /ruta/a/los/gpkgs
```

Los GeoPackage se descargan de las URLs indicadas en el encabezado del script
(~600 MB entre los dos). El catálogo ya está versionado en
`src/main/resources/catalogo/rios.json`.

7. Arrancar la demo en Windows:

```powershell
.\scripts\iniciar-demo.ps1
```

La prueba de humo ya se completó en Linux (sección 2.7). En Windows confirmar lo
mismo: el backend arranca, `/api/rios` devuelve los cuatro ríos, el login
funciona y la PWA carga en `http://localhost:8081`.

En Linux, para la próxima sesión:

```bash
podman start alerta-postgres   # si el contenedor está detenido
bash scripts/iniciar-demo.sh   # backend, menú y navegador
```

---

## 6. Archivos clave

| Archivo | Para qué sirve |
|---|---|
| `src/main/java/.../auth/` | Usuarios, JWT, roles y `SecurityConfig` |
| `src/main/java/.../usuario/` | Entidad `Usuario`, selección usuario–ríos |
| `src/main/java/.../rio/InicializadorCatalogo.java` | Upsert del catálogo desde `catalogo/rios.json` |
| `src/main/java/.../rio/RioService.java` | Catálogo y GeoJSON del mapa |
| `src/main/resources/catalogo/rios.json` | Catálogo con geometría real |
| `tools/generar_catalogo_geoglows.py` | Generación reproducible del catálogo |
| `src/main/java/.../push/PushService.java` | Registro por usuario y envío por seguidores |
| `src/main/java/.../push/PushController.java` | Registro, baja, prueba y demo |
| `src/main/java/.../demo/BackendApiClient.java` | Cliente HTTP JWT de la consola |
| `src/main/java/.../demo/ConsolaDemoRunner.java` | Consola con login y menús por rol |
| `src/main/java/.../alerta/AlertaController.java` | Alertas, simulación y evaluación |
| `src/main/resources/static/` | PWA de prueba (login, mapa, panel y push) |
| `docs/INTEGRACION_FRONTEND.md` | Contrato vigente para el frontend definitivo |
| `scripts/iniciar-demo.ps1` | Arranque en Windows (PowerShell) |
| `scripts/iniciar-demo.sh` | Arranque en Linux (terminal automática y navegador) |

---

## 7. Reglas que no se deben romper

- No publicar contraseñas, secretos, tokens ni credenciales reales.
- No escribir la contraseña del administrador de demostración en el repositorio.
- No versionar `.env.demo` ni archivos de Firebase.
- No inventar ríos, `reachId`, geometrías ni umbrales oficiales.
- No hacer que la consola o la PWA accedan directamente a repositorios o a la
  lógica de negocio; deben usar la API HTTP.
- No devolver el enrutamiento push al modelo de `reachId` suelto: el push
  depende de los ríos que sigue cada usuario.
- No pedir permiso de notificaciones al cargar la PWA: solo tras un clic.
- No afirmar que el push funciona de extremo a extremo sin una prueba en un
  dispositivo real.
- Mantener las pruebas existentes y agregar las del flujo nuevo.
- Actualizar la documentación cuando cambie un contrato o una decisión.

---

## 8. Pendientes y riesgos

1. **Prueba de humo con PostgreSQL real:** hecha en Linux el 23/10/2026 (ver
   sección 2.7); falta repetirla en Windows con `scripts/iniciar-demo.ps1`.
2. **Push real:** falta verificar la recepción en un dispositivo físico. No
   afirmarlo sin esa prueba.
3. **URL exacta del frontend para CORS:** aún no existe; mantener
   `ALERTA_CORS_ORIGENES` configurable.
4. **Endpoints heredados:** `POST /api/push/demo` y
   `GET /api/alertas/suscripciones/{reachId}` siguen con `X-Admin-Token`; se
   retirarán en una entrega futura.
5. **Umbrales por temporada:** investigación futura; no inventar valores.
6. **Rotación de credenciales locales de PostgreSQL:** pendiente; no registrar
   valores en el repositorio.
7. **Panel de administración del frontend:** la PWA no incluye panel admin; el
   frontend definitivo lo decidirá. La consola ya cubre simulación, evaluación y
   push demo.
8. **Fin de línea en Linux:** algunos archivos con CRLF aparecen como
   modificados sin cambios reales; no mezclarlos en commits de contenido.
9. **OneDrive y `target/` en Linux:** si se sincroniza `target/` desde Windows,
   Maven puede fallar con «Unable to find a single main class» por las clases
   `*-safeBackup-*`; ejecutar `bash mvnw clean` (o `.\mvnw.cmd clean`).

---

## 9. Checklist para actualizar este documento

Al terminar una sesión de trabajo, actualizar:

- [ ] Fecha y rama.
- [ ] Último commit.
- [ ] Qué se implementó realmente.
- [ ] Qué pruebas se ejecutaron y su resultado.
- [ ] Archivos nuevos o modificados.
- [ ] Decisiones nuevas.
- [ ] Pendientes y bloqueos.
- [ ] Confirmar que no se agregaron secretos.
