# Guía de presentación en Fedora

**Rama:** `frontend-final`<br>
**Interfaz de la demo:** React + Vite en `http://localhost:5173`<br>
**Backend:** Spring Boot en `http://localhost:8081`<br>
**Última actualización:** 12/11/2026

> **Estado al retomar (12/11/2026):** la rama está publicada en
> `origin/frontend-final` (código verificado en `a9f65ff`). Backend (82 pruebas)
> y frontend (build correcto) están verificados; el túnel HTTPS hacia `5173`
> respondió 200, con 4 ríos y FCM habilitado. **Solo falta la prueba con el
> celular.** Empieza por la sección 4.

Esta guía deja lista la presentación del 75 % en una laptop Fedora. El lanzador
principal es `iniciar.sh`; `scripts/iniciar-demo.ps1` es solo el apoyo para la PC
Windows. La PWA antigua de `http://localhost:8081` no es la interfaz nueva: se
conserva para probar el backend y los Service Workers.

> **Regla de oro:** no cambies el diseño visual del frontend para la demo. Los
> ajustes de esta guía son de arranque, configuración y red.

---

## 1. Resumen de 30 segundos

Si la laptop ya tiene todo configurado:

```bash
cd /ruta/al/proyecto
git switch frontend-final
git pull
chmod +x iniciar.sh
bash iniciar.sh
```

Abre en la PC `http://localhost:5173`. En el celular, abre la URL HTTPS que
imprime el lanzador al final (debe apuntar a `5173`).

Para una prueba rápida sin celular:

```bash
bash iniciar.sh --sin-tunel --sin-navegador
```

---

## 2. Requisitos en Fedora

| Componente | Versión / comprobación |
|---|---|
| Java | 21 o superior: `java -version` |
| Node.js | 20.19+ o 22.12+ (Vite 8): `node -v` |
| npm | `npm -v` |
| PostgreSQL | escuchando en `5432` |
| `cloudflared` | opcional para PC; necesario para el celular: `cloudflared --version` |
| Firebase | JSON de cuenta de servicio y claves públicas de la app web |

Instalación rápida en Fedora si falta algo:

```bash
# Node.js y npm (según la versión de Fedora; comprueba que node -v sea >= 20.19)
sudo dnf install nodejs npm

# cloudflared desde el binario oficial (x86_64)
mkdir -p ~/.local/bin
curl -L --fail -o ~/.local/bin/cloudflared \
  https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x ~/.local/bin/cloudflared
```

En ARM64 cambia `cloudflared-linux-amd64` por `cloudflared-linux-arm64`.
`iniciar.sh` agrega `~/.local/bin` al `PATH`, así que no hace falta reiniciar la
sesión.

Si PostgreSQL aún no está configurado:

```bash
sudo dnf install postgresql-server postgresql
sudo postgresql-setup --initdb
sudo systemctl enable --now postgresql
sudo -u postgres psql -c "CREATE DATABASE alerta_inundaciones;"
```

Anota la contraseña que uses para el usuario `postgres`; debe coincidir con
`DB_PASSWORD` en `.env.demo`.

---

## 3. Preparar `.env.demo` en esta máquina

`.env.demo` no se versiona y **no debe subirse a Git**. Si el proyecto llegó por
OneDrive desde Windows, no reutilices tal cual la contraseña de PostgreSQL ni las
rutas de Firebase de Windows: cada máquina tiene las suyas.

```bash
cp .env.demo.example .env.demo
nano .env.demo
```

Valores mínimos para la demo:

```properties
DB_URL=jdbc:postgresql://localhost:5432/alerta_inundaciones
DB_USERNAME=postgres
DB_PASSWORD=la-clave-de-tu-postgresql-local

ALERTA_JWT_SECRET=un-secreto-largo-y-distinto-para-esta-laptop
ALERTA_ADMIN_IDENTIFICADOR=admin
ALERTA_ADMIN_PASSWORD=admin-local-clave

ALERTA_CORS_ORIGENES=http://localhost:5173,http://localhost:8081,https://*.trycloudflare.com

FIREBASE_ENABLED=true
FIREBASE_CREDENTIALS_PATH=service-account.json

FIREBASE_API_KEY=...
FIREBASE_AUTH_DOMAIN=...
FIREBASE_PROJECT_ID=...
FIREBASE_MESSAGING_SENDER_ID=...
FIREBASE_APP_ID=...
FIREBASE_VAPID_KEY=...
```

Notas:

- `admin-local-clave` es **solo** para desarrollo y demostración local. No la
  uses en producción ni la escribas en la documentación del repositorio.
- `FIREBASE_CREDENTIALS_PATH` puede ser absoluta o relativa a la raíz del
  proyecto. El archivo JSON debe existir y ser legible por tu usuario. El
  `.gitignore` ya excluye `service-account*.json` y `*firebase-adminsdk*.json`.
- Las seis variables públicas de Firebase deben ser de la **misma app web** que
  el JSON de la cuenta de servicio. Si no coincides, `/api/push/config` dirá
  `habilitado: false`.
- Si el JSON no está en la laptop, copia el archivo por un medio seguro. No lo
  descargues en una carpeta sincronizada públicamente ni lo agregues al repo.
- Si todavía no tienes Firebase a mano, puedes arrancar con
  `FIREBASE_ENABLED=false` para validar la interfaz; el push real no funcionará.

Comprueba la base de datos antes de arrancar:

```bash
PGPASSWORD='la-clave-de-tu-postgresql-local' \
  psql -h localhost -U postgres -d alerta_inundaciones -c 'select 1;'
```

Debe responder con una fila y `1`.

---

## 4. Arranque

```bash
cd /ruta/al/proyecto
git switch frontend-final
git pull
chmod +x iniciar.sh
bash iniciar.sh
```

Qué hace el lanzador:

1. Carga `.env.demo` y ajusta la ruta relativa de Firebase.
2. Autoriza `https://*.trycloudflare.com` en CORS para la demo.
3. Comprueba Java, Node, npm y PostgreSQL.
4. Instala `frontend/node_modules` si falta. Si la carpeta vino de Windows y no
   tiene los binarios de Rolldown para Linux, la reinstala.
5. Si `target/` trae clases duplicadas `*safeBackup*` de una sincronización
   anterior, ejecuta `bash mvnw clean`.
6. Arranca el backend en `8081` y Vite en `5173` con `--strictPort`.
7. Verifica el proxy `/api` y que el catálogo de cuatro ríos responda.
8. Abre un túnel HTTPS **hacia `5173`** con hasta tres intentos.
9. Abre el navegador local en `http://localhost:5173`.

Opciones útiles:

```bash
bash iniciar.sh --sin-tunel       # solo PC; no abre URL pública
bash iniciar.sh --sin-navegador   # no abre el navegador automáticamente
```

Al final debe aparecer un panel como este:

```text
ACCESOS:
  💻  En este ordenador:    http://localhost:5173
  📱  En el celular (HTTPS): https://algo-algo-algo.trycloudflare.com

CREDENCIALES DE PRESENTACIÓN:
  • Rol ADMIN (Simulaciones): Usuario: admin | Clave: admin-local-clave
  • Rol USER  (Monitoreo):    Puedes registrarte libremente desde la pantalla de login
```

Si el túnel falla, el lanzador sigue funcionando para la PC y avisa en la
terminal. Revisa `target/logs/tunnel.log`.

El túnel se probó el 11/11/2026 con el frontend en `5173`: `/` respondió `200`,
`/api/rios` devolvió los cuatro ríos, `/api/push/config` devolvió
`habilitado: true` y el login desde el origen `trycloudflare.com` respondió
`200`. La primera resolución DNS del subdominio nuevo puede tardar hasta un
minuto; si el celular muestra `DNS_PROBE`, espera y recarga.

Para detener todo: `Ctrl + C` en la terminal del lanzador. Comprueba que los
puertos quedaron libres:

```bash
ss -ltnp | grep -E ':8081|:5173' || echo "Puertos libres"
```

---

## 5. Guion de la demo

### Antes de entrar al salón

- [ ] Ejecutar `bash iniciar.sh` y confirmar que el panel final muestra la URL
      del túnel.
- [ ] Abrir `http://localhost:5173` en la PC y comprobar que carga el login.
- [ ] Abrir la URL del túnel en el celular y comprobar que carga la misma app.
- [ ] Confirmar que `/api/push/config` devuelve `habilitado: true`.
- [ ] Confirmar que el catálogo muestra los cuatro ríos.
- [ ] Tener el celular con batería, datos o Wi-Fi y notificaciones permitidas.

Comprobaciones por terminal:

```bash
curl -s http://localhost:5173/api/push/config
curl -s http://localhost:5173/api/rios
```

### Paso 1: cuenta USER en el celular

1. Abre la URL HTTPS del túnel en **Chrome** (Android) o **Safari** (iPhone).
2. Pulsa **Registrarme** y crea una cuenta `USER` distinta de `admin`. Por
   ejemplo, `invitado` con una clave de al menos 8 caracteres.
3. Entra a un río (por ejemplo, **Río Abajo**), pulsa **Suscribirme a este
   río** y luego **Activar notificaciones**.
4. Acepta el permiso del sistema. Debe aparecer **Activadas**.
5. En iPhone/iPad: iOS 16.4+, abre en Safari, toca **Compartir → Agregar a la
   pantalla de inicio** y abre la app desde el icono instalado.

> Usa una cuenta `USER` distinta de la del `ADMIN`. El backend desactiva los
> tokens web anteriores del mismo usuario para evitar notificaciones duplicadas.
> Si usas la misma cuenta en PC y celular, el token del celular puede quedar
> inactivo.

### Paso 2: cuenta ADMIN en la PC

1. Abre `http://localhost:5173` en la PC.
2. Inicia sesión como `admin` / `admin-local-clave`.
3. Abre el **mismo río** que sigue el celular (Río Abajo).
4. Pulsa **Simular crecida y notificar** y confirma el diálogo.

La respuesta debe indicar al menos un dispositivo y un envío:

```text
Simulación enviada a 1 dispositivo(s): 1 entregada(s), 0 fallida(s).
```

El celular debe mostrar la notificación con el formato realista de alerta
(`🚨 ALERTA DE INUNDACIÓN 🌊` o `⚠️ AVISO DE CRECIDA...`, según el nivel
simulado). El texto visible no dice «simulacro»; la marca interna queda en la
URL (`/?simulacion=...`) y en el payload.

### Paso 3: cierre

- Muestra el panel del río con caudal, lluvia y nivel.
- Menciona que el push se envía solo a quienes siguen ese río.
- Detén el lanzador con `Ctrl + C` al terminar.

---

## 6. Problemas frecuentes en Fedora

| Síntoma | Causa probable | Solución |
|---|---|---|
| El backend falla con `Unable to determine Dialect without JDBC metadata` | Hibernate no pudo conectarse a PostgreSQL; la causa real está antes en el log | Ejecutar `bash scripts/diagnosticar-postgres.sh`; el lanzador ahora valida las credenciales y muestra la causa |
| El backend no arranca y el log menciona `password authentication failed` | `DB_PASSWORD` no coincide con el PostgreSQL de esta laptop | Corregir `.env.demo`; probar con `psql`; volver a arrancar |
| `Port 8081 is already in use` o `5173` ocupado | Proceso anterior | `ss -ltnp \| grep -E ':8081\|:5173'` y cerrar el PID, o reiniciar la sesión |
| Vite falla con un error de `@rolldown/binding` o `Cannot find module` | `node_modules` sincronizado desde Windows | `rm -rf frontend/node_modules && (cd frontend && npm install)`; el lanzador también lo detecta |
| Maven falla con `Unable to find a single main class` | `target/` sincronizado con clases duplicadas | `bash mvnw clean`; el lanzador lo detecta si hay `*safeBackup*` |
| `npm install` falla por archivos bloqueados | OneDrive/antivirus bloqueando `node_modules` | Cerrar OneDrive un momento, borrar `frontend/node_modules` y repetir; si persiste, copiar el proyecto a una carpeta local fuera de OneDrive |
| El túnel responde `429 Too Many Requests` o `context deadline exceeded` | Límite temporal de Cloudflare para la IP | Esperar 5–10 minutos, cambiar de red (por ejemplo, hotspot del celular) y reintentar; para la PC usar `--sin-tunel` |
| El túnel se crea pero el celular muestra `DNS_PROBE_FINISHED_NXDOMAIN` o la URL no abre al instante | Propagación DNS del subdominio nuevo | Esperar 30–60 segundos y recargar; el lanzador avisa si aún está propagando |
| El celular muestra `403 Invalid CORS request` | El origen del túnel no está autorizado | Verificar que `ALERTA_CORS_ORIGENES` incluya `https://*.trycloudflare.com`; reiniciar el backend tras cambiarlo |
| `/api/push/config` devuelve `habilitado: false` | Falta el JSON, la ruta es incorrecta o las claves públicas no coinciden | Revisar `FIREBASE_CREDENTIALS_PATH`, `FIREBASE_ENABLED` y las seis claves públicas; mirar `target/logs/backend.log` |
| La simulación informa 0 dispositivos | El celular no está registrado, se usó la misma cuenta que el ADMIN o cambió la URL del túnel | Usar cuentas separadas, abrir la URL nueva y volver a pulsar **Activar notificaciones** |
| La notificación no llega a Android | Permiso, ahorro de batería, Brave o agrupación de Android | Usar Chrome, permitir notificaciones, desactivar optimización de batería para Chrome y probar con otro río o esperar unos segundos |
| La notificación no llega a iPhone | iOS antiguo, Safari sin agregar a inicio o permiso denegado | iOS 16.4+, agregar a la pantalla de inicio, abrir desde el icono y permitir notificaciones |
| La app se ve vieja en la PC | Se abrió `8081` (PWA antigua) o el navegador cacheó | Abrir `http://localhost:5173`; en la PWA antigua, desregistrar el Service Worker |

### Si el backend falla con «Unable to determine Dialect without JDBC metadata»

Ese mensaje de Hibernate significa que el backend **no pudo conectarse a
PostgreSQL**; la causa real aparece antes en `target/logs/backend.log`. Desde el
12/11/2026, `iniciar.sh` valida las credenciales con `psql` antes de arrancar y
muestra la causa probable. Ejecuta también:

```bash
bash scripts/diagnosticar-postgres.sh
```

El diagnóstico no imprime la contraseña. Casos típicos:

1. **El contenedor `alerta-postgres` está detenido y PostgreSQL del sistema
   ocupa el puerto 5432.** Es lo más frecuente después de reiniciar Fedora.
   Para volver a usar los datos de la demo:

   ```bash
   sudo systemctl stop postgresql
   podman start alerta-postgres
   bash iniciar.sh
   ```

   Si el contenedor no existe, usa PostgreSQL del sistema y configura la base:

   ```bash
   sudo -u postgres psql -c "ALTER USER postgres PASSWORD 'la-clave-de-.env.demo';"
   sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='alerta_inundaciones'" \
     | grep -q 1 || sudo -u postgres createdb alerta_inundaciones
   bash iniciar.sh
   ```

2. **La contraseña de `.env.demo` no coincide.** Si el contenedor está corriendo,
   el lanzador toma automáticamente la contraseña del contenedor para esa
   ejecución. Para dejarla fija en `.env.demo`:

   ```bash
   podman exec alerta-postgres printenv POSTGRES_PASSWORD
   ```

   Copia el valor en `DB_PASSWORD`.

3. **La base `alerta_inundaciones` no existe.** Créala con el comando del caso 1,
   o dentro del contenedor:

   ```bash
   podman exec alerta-postgres createdb -U postgres alerta_inundaciones
   ```

4. **`localhost` resuelve a IPv6 y PostgreSQL solo escucha en IPv4.** El
   lanzador prueba `127.0.0.1` automáticamente; si funciona, usa esa dirección
   para esa ejecución. También puedes fijarla en `.env.demo`:

   ```properties
   DB_URL=jdbc:postgresql://127.0.0.1:5432/alerta_inundaciones
   ```

### Si Cloudflare no coopera

El túnel es solo para que el celular tenga HTTPS. Si no se puede abrir:

1. Espera unos minutos y vuelve a ejecutar `bash iniciar.sh`.
2. Cambia la laptop a otra red (hotspot del celular, otra Wi-Fi).
3. Para la presentación en la PC, usa `bash iniciar.sh --sin-tunel`; el push
   local en Chrome sigue funcionando en `localhost`.
4. Si usas otro túnel (ngrok, etc.), agrega su URL HTTPS exacta a
   `ALERTA_CORS_ORIGENES` en `.env.demo` y reinicia el backend. `iniciar.sh`
   automatiza solo Cloudflare.

---

## 7. Checklist de aceptación

- [ ] `bash iniciar.sh` termina con el panel «APLICACIÓN LISTA».
- [ ] La PC abre `http://localhost:5173` y muestra el login.
- [ ] `http://localhost:5173/api/rios` devuelve los cuatro ríos.
- [ ] `/api/push/config` devuelve `habilitado: true`.
- [ ] El túnel imprime una URL `https://...trycloudflare.com` y abre la app.
- [ ] El celular registra una cuenta `USER` distinta de `admin`.
- [ ] El celular sigue un río y muestra **Activadas**.
- [ ] La PC entra como `admin`, abre el mismo río y pulsa **Simular crecida y
      notificar**.
- [ ] La respuesta informa `dispositivos >= 1` y `enviados >= 1`.
- [ ] La notificación llega al celular con el texto realista y emojis.
- [ ] `Ctrl + C` detiene backend, Vite y túnel; los puertos quedan libres.
- [ ] `.env.demo` y el JSON de Firebase siguen fuera de Git.

---

## 8. Referencias

- [README.md](../README.md): arranque rápido y estructura.
- [CONFIGURACION_PUSH.md](CONFIGURACION_PUSH.md): variables de entorno y
  Firebase/FCM.
- [GUIA_PRUEBA_NOTIFICACIONES.md](GUIA_PRUEBA_NOTIFICACIONES.md): pruebas
  detalladas del backend y de la PWA antigua.
- [INTEGRACION_FRONTEND.md](INTEGRACION_FRONTEND.md): contrato de la API.
