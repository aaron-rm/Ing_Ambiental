# Directrices de arquitectura

**Última actualización:** 23/10/2026  
**Estado:** vigente. Refleja el avance de autenticación, catálogo de cuatro ríos,
selección usuario–ríos y push asociado al usuario.

Este documento es la referencia permanente para separar responsabilidades entre el
backend, la consola de demostración y los clientes. `GEMINI.md` resume estas
reglas y enlaza aquí; si hay contradicción entre un documento histórico y este
archivo, prevalece este documento para las decisiones de arquitectura.

---

## 1. Objetivo

El backend del sistema de alerta temprana debe poder ejecutarse, evaluarse y
probarse sin la consola ni la PWA de pruebas. Sus operaciones deben quedar
expuestas mediante contratos públicos y estables para que otro equipo pueda
construir el frontend definitivo sin modificar la lógica interna del backend.

La separación buscada es:

```text
Backend (lógica de negocio, integraciones, persistencia, push)
   ├── API REST pública
   ├── ConsolaDemoRunner  → cliente HTTP de demostración y pruebas
   ├── PWA de pruebas     → cliente HTTP de prueba (login, catálogo, suscripción y push)
   └── Frontend definitivo → cliente HTTP desarrollado por otro equipo
```

La consola y la PWA son clientes. No son parte de la implementación del backend
y no deben contener reglas de negocio, integraciones externas ni acceso directo
a repositorios.

---

## 2. Reglas permanentes para el backend

1. **El backend es independiente de la consola y del frontend.**
   Debe iniciar, evaluar riesgo, gestionar suscripciones y enviar notificaciones
   sin que la consola o la PWA estén activas.

2. **La lógica de negocio pertenece al backend.**
   Consultas a GEOGloWS, precipitación, evaluación de riesgo, transiciones de
   alertas, idempotencia, suscripciones, tokens push y envío FCM viven en los
   servicios del backend.

3. **`ConsolaDemoRunner` consume las interfaces públicas del backend.**
   Sirve para demostrar y probar el funcionamiento del backend, no para
   implementarlo. Su cliente HTTP es `BackendApiClient`.

4. **La consola no debe convertirse en el lugar de nuevas reglas o integraciones.**
   Si una funcionalidad nueva debe existir para el sistema, se implementa en el
   backend y se expone por su API. Después se podrá consumir desde la consola si
   aporta valor a la demostración.

5. **Las modificaciones deben preservar lo existente y evitar regresiones.**
   No se reescribe una funcionalidad que ya funciona sin una razón técnica
   concreta. Los cambios deben ser pequeños, verificables y compatibles.

6. **El backend debe ofrecer contratos claros y reutilizables.**
   Controladores delgados, servicios con la lógica, DTOs para las respuestas
   públicas y errores comprensibles. El frontend definitivo no debe necesitar
   conocer clases internas ni repositorios.

7. **Servicios, credenciales y configuración se gestionan de forma segura.**
   Las credenciales se leen de variables de entorno o archivos externos no
   versionados. No se exponen secretos al navegador ni se escriben tokens reales
   en el repositorio.

8. **Las pruebas deben validar la lógica del backend y el consumo de sus interfaces.**
   Como mínimo: reglas de riesgo, eventos, push y el contrato HTTP usado por la
   consola. Las pruebas que requieran Firebase, PostgreSQL o un dispositivo real
   se documentan como pendientes y se ejecutan en un entorno preparado.

9. **La documentación se actualiza cuando cambian contratos o decisiones.**
   Endpoints, variables de entorno, dependencias, flujos de ejecución y
   limitaciones deben reflejarse en `README.md`, `docs/` y este documento según
   corresponda.

---

## 3. Alcance explícito de estas directrices

Estas reglas aplican al **backend y a las integraciones que le corresponden**,
incluida la consola de demostración y la PWA de pruebas.

**No son instrucciones para imponer restricciones de implementación, diseño
visual, estructura o tecnología al equipo que desarrollará el frontend
definitivo.** Ese equipo puede elegir framework, arquitectura de componentes,
sistema de diseño, navegación y presentación de datos, siempre que respete los
contratos HTTP públicos del backend.

La PWA actual es una herramienta mínima de prueba del canal push (login,
catálogo, suscripción y simulación admin). No constituye una referencia
obligatoria de diseño ni una base que el frontend definitivo deba reutilizar. El
frontend definitivo se construirá desde cero y podrá mostrar caudales, lluvia,
pronósticos, mapas, riesgo e historial según sus propias decisiones de producto.

Tampoco debe interpretarse que todo el proyecto debe limitarse a una consola o a
una página minimalista. El backend es el sistema; la consola y la PWA son
clientes de apoyo.

---

## 4. Componentes y responsabilidades

### Backend

Paquete base: `com.alerta.sistema_alerta_inundaciones`.

| Componente | Responsabilidad |
|---|---|
| `auth/` | Registro, login, JWT, roles y `SecurityConfig` |
| `usuario/` | Entidad `Usuario` y selección usuario–ríos |
| `alerta/AlertaController` | API de consulta, historial y simulación/evaluación (`ADMIN`) |
| `alerta/AlertaService` | Consulta a GEOGloWS, simulación y suscripciones heredadas |
| `alerta/AlertaMonitoreoService` | Evaluación periódica y manual de todos los ríos |
| `alerta/AlertaEventoService` | Transiciones, idempotencia y estado de envío |
| `riesgo/EvaluadorRiesgo` | Reglas de caudal y lluvia |
| `clima/OpenMeteoCliente` | Precipitación y caché |
| `GEOGLOWS/ClienteApiGeoglows` | Caudal, umbrales y caché en memoria por tramo |
| `push/PushController` | Configuración, registro, baja y prueba push (JWT) |
| `push/PushService` | Registro por usuario y envío a los seguidores del río |
| `push/FirebasePushSender` | Integración con Firebase Admin |
| `rio/RioController` | Catálogo y GeoJSON del mapa |
| `rio/InicializadorCatalogo` | Upsert del catálogo de cuatro ríos desde `catalogo/rios.json` |

### `ConsolaDemoRunner`

- Es un `CommandLineRunner` opcional.
- Se activa con `alerta.demo.consola.habilitada=true` (valor por defecto).
- Se desactiva con `ALERTA_DEMO_CONSOLA_HABILITADA=false`,
  `alerta.demo.consola.habilitada=false` o `-Dskip.demo.console=true`.
- Usa `BackendApiClient` contra `alerta.api.base-url`
  (`ALERTA_API_BASE_URL`, por defecto `http://localhost:8081`).
- No inyecta repositorios ni servicios de negocio.
- Pide login o registro al arrancar y usa JWT solo en memoria; no guarda el token
  en disco.
- Menú de `USER`: catálogo, panel de cualquier río, seguir/dejar de seguir, mis
  ríos y cerrar sesión.
- Menú de `ADMIN`: lo anterior más simulación de crecida con push a los
  seguidores del río y push de demostración global
  (`POST /api/admin/push/demo`).

### PWA de pruebas

- Archivos en `src/main/resources/static/`.
- Login/registro, catálogo de ríos, suscripción y activación de push con JWT.
- Suscribirse a un río solo controla las notificaciones.
- Para `ADMIN`, botón **Simular crecida** por cada río seguido; envía un push de
  simulacro solo a los dispositivos de quienes siguen ese río.
- No muestra caudal, lluvia, hidrogramas, mapas ni recomendaciones.
- No es el frontend definitivo.

### Frontend definitivo

- Será construido por otro equipo.
- Es libre de elegir tecnología, diseño y organización.
- Debe consumir la API pública y respetar los contratos y códigos de error.
- No debe extraer lógica de `ConsolaDemoRunner`.
- Si se sirve desde otro origen, necesitará configurar CORS en el backend.

### Elementos exclusivamente de demostración

Estos elementos no son necesarios para que el backend evalúe riesgo, persista
eventos o envíe notificaciones:

| Elemento | Uso | Separación |
|---|---|---|
| `rio/InicializadorCatalogo` | Carga inicial del catálogo de cuatro ríos (upsert por `reachId`) | `ALERTA_CARGAR_CATALOGO=false` |
| `demo/ConsolaDemoRunner` | Menú interactivo con login y menús por rol | `ALERTA_DEMO_CONSOLA_HABILITADA=false` |
| `GEOGLOWS/GeneradorAlertaFinal.main` | Demostración independiente con un `reachId` fijo | No es un bean de Spring ni participa en el monitoreo |

El monitoreo programado recorre `rioRepository.findAll()` y la API acepta
cualquier `rioId` o `reachId` registrado. Una instancia aislada en el puerto
`8082`, con consola, monitoreo, siembra inicial y Firebase desactivados, sirvió
la PWA y `/api/rios` sin mostrar el menú. Esto confirma que esos elementos demo
no son una dependencia de arranque del backend.

---

## 5. Contrato público y propiedad de la lógica

La API REST es el contrato entre el backend y todos sus clientes. Las rutas,
métodos, campos JSON y códigos de error se documentan en
[`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md).

Principios del contrato:

- Los controladores reciben y validan la entrada; no implementan reglas de
  negocio.
- Los servicios concentran la lógica y las integraciones.
- Los repositorios solo persisten; no se exponen directamente por HTTP.
- Las respuestas usan DTOs estables, no entidades JPA cuando el DTO aporta
  claridad o evita acoplamiento.
- Los errores usan códigos HTTP coherentes y mensajes comprensibles.
- Las operaciones sensibles se protegen con JWT y rol `ADMIN`. Dos rutas
  heredadas (`POST /api/push/demo` y
  `GET /api/alertas/suscripciones/{reachId}`) conservan `X-Admin-Token` como
  compatibilidad temporal y se retirarán más adelante.
- Los endpoints públicos no revelan credenciales ni datos administrativos.

### Decisiones adoptadas en la refactorización actual

- La consola consume HTTP, no repositorios ni servicios internos. Esto valida el
  mismo contrato que usará el frontend definitivo.
- `BackendApiClient` crea su propio `ObjectMapper` de Jackson 2 porque Spring
  Boot 4 usa Jackson 3 para MVC y no expone el bean de Jackson 2. Firebase Admin
  también usa Jackson 2. No volver a inyectar
  `com.fasterxml.jackson.databind.ObjectMapper` como bean de Spring.
- La escritura del catálogo vive en `AdminRioController` bajo `/api/admin/rios`
  (JWT + `ADMIN`); la consola ya no la usa.
- `/api/push/config` ya no devuelve `reachId`: el dispositivo se registra con
  JWT y las alertas dependen de los ríos que sigue el usuario.
- La consola es un bean condicional y puede desactivarse sin afectar al backend.
- La PWA pasó de ser un receptor mínimo de push a un cliente de prueba del flujo
  mínimo (login, catálogo, suscripción, push y simulación admin), sin panel
  hidrológico.
- `ClienteApiGeoglows` cachea en memoria por endpoint y tramo; ante un fallo de
  GEOGloWS reutiliza la última respuesta. El panel expone `siguiendo` calculado
  con la selección usuario–río, sin volver privada la consulta.

---

## 6. Configuración y seguridad

Variables principales:

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `ALERTA_JWT_SECRET` | Secreto HS256 del JWT (mínimo 32 caracteres) |
| `ALERTA_ADMIN_IDENTIFICADOR` / `ALERTA_ADMIN_PASSWORD` | Administrador inicial; la contraseña solo vive en el entorno o en `.env.demo` |
| `ALERTA_CORS_ORIGENES` | Orígenes permitidos para el frontend definitivo |
| `ALERTA_CARGAR_CATALOGO` | Activa la carga del catálogo de cuatro ríos al arrancar |
| `GEOGLOWS_CACHE_MINUTOS` | TTL de la caché en memoria de GEOGloWS (15 por defecto; mínimo 1) |
| `ALERTA_ADMIN_TOKEN` | Solo compatibilidad: `POST /api/push/demo` y `GET /api/alertas/suscripciones/{reachId}` |
| `ALERTA_API_BASE_URL` | URL que usa la consola para consumir el backend |
| `ALERTA_DEMO_CONSOLA_HABILITADA` | Activa o desactiva la consola |
| `FIREBASE_ENABLED` | Habilita o deshabilita FCM |
| `FIREBASE_CREDENTIALS_PATH` / `FIREBASE_CREDENTIALS_JSON` | Credenciales privadas de Firebase Admin |
| `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`, `FIREBASE_PROJECT_ID`, `FIREBASE_MESSAGING_SENDER_ID`, `FIREBASE_APP_ID`, `FIREBASE_VAPID_KEY` | Configuración pública de la app web |

Reglas:

- No versionar `.env.demo` ni archivos de cuenta de servicio.
- No imprimir tokens ni credenciales en logs.
- No enviar el JSON administrativo de Firebase al navegador.
- Documentar cualquier limitación de seguridad pendiente en lugar de inventar
  autenticación compleja que el proyecto aún no necesita.

Limitación actual: quedan dos rutas heredadas con `X-Admin-Token`
(`POST /api/push/demo` y `GET /api/alertas/suscripciones/{reachId}`);
se retirarán en una entrega futura. El resto de las operaciones
administrativas (escritura de ríos, simulación, evaluación manual y push de
demostración) ya exigen JWT con rol `ADMIN`.

---

## 7. Pruebas

- `.\mvnw.cmd test` ejecuta la suite automatizada.
- `.\mvnw.cmd package` compila y ejecuta las pruebas antes de generar el JAR.
- `BackendApiClientTest` valida el contrato HTTP que consume la consola con un
  servidor local, sin depender del backend levantado.
- Las pruebas con PostgreSQL real, Firebase real o un dispositivo físico se
  documentan como pendientes cuando no hay credenciales o entorno disponible.
- No se debe afirmar que una función push funciona de extremo a extremo sin
  haberla probado en un dispositivo real.

---

## 8. Documentación y mantenimiento

Documentos relacionados:

| Documento | Contenido |
|---|---|
| [`RESUMEN_PROYECTO.md`](RESUMEN_PROYECTO.md) | Explicación funcional de alto nivel y guía de presentación |
| [`../GEMINI.md`](../GEMINI.md) | Resumen de reglas para agentes y enlace a este documento |
| [`GUIA_PRUEBA_NOTIFICACIONES.md`](GUIA_PRUEBA_NOTIFICACIONES.md) | Guía paso a paso para ejecutar y verificar la demo |
| [`../README.md`](../README.md) | Arranque, demo y pruebas |
| [`../IMPLEMENTATION_NOTES.md`](../IMPLEMENTATION_NOTES.md) | Historia de implementación y decisiones previas |
| [`../INFORME_ALERTAS_INUNDACION.md`](../INFORME_ALERTAS_INUNDACION.md) | Informe histórico del 21–22/10; el estado vigente está en `CONTINUIDAD_SESION.md` |
| [`CONFIGURACION_PUSH.md`](CONFIGURACION_PUSH.md) | Entorno, PostgreSQL y Firebase |
| [`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md) | Contrato de API para el frontend |

Actualizar este documento cuando:

- cambie un contrato público;
- se mueva lógica entre backend y clientes;
- se añada o retire una integración;
- cambie la forma de ejecutar o desactivar la consola;
- se tome una decisión arquitectónica que afecte a futuros agentes.

---

## 9. Lista de comprobación para cambios futuros

Antes de implementar:

- [ ] ¿La lógica pertenece al backend o a un cliente?
- [ ] ¿Ya existe un endpoint o servicio que se pueda reutilizar?
- [ ] ¿Se está duplicando una regla de negocio en la consola o en la PWA?
- [ ] ¿El cambio preserva las funcionalidades existentes?
- [ ] ¿Afecta el contrato del frontend definitivo?
- [ ] ¿Requiere una variable de entorno o una credencial nueva?
- [ ] ¿Hay pruebas o una verificación reproducible?

Después de implementar:

- [ ] Compilar y ejecutar las pruebas.
- [ ] Verificar sintaxis de los archivos JavaScript si se tocó la PWA.
- [ ] Actualizar la documentación afectada.
- [ ] Revisar `git status` y el diff.
- [ ] No hacer commit automáticamente.
