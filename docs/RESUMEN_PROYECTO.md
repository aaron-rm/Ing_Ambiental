# Sistema de alerta temprana de inundaciones

## Explicación de alto nivel y guía para la presentación

**Última actualización:** 23/10/2026<br>
**Para:** equipo de trabajo, agentes de IA y exposición final<br>
**Documentación técnica relacionada:** [`ARQUITECTURA.md`](ARQUITECTURA.md), [`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md), [`GUIA_FRONTEND.md`](GUIA_FRONTEND.md), [`CONTINUIDAD_SESION.md`](CONTINUIDAD_SESION.md) y [`GUIA_PRUEBA_NOTIFICACIONES.md`](GUIA_PRUEBA_NOTIFICACIONES.md)

> Este documento explica qué problema resuelve el proyecto y cómo funciona, sin
> entrar en clases, frameworks ni detalles de implementación. La documentación
> técnica sigue siendo la referencia para modificar el código.

> **Estado del avance:** implementado. El catálogo quedó cerrado en cuatro
> ríos (Abajo, Curundú, Juan Díaz y Tocumen) con geometría real, y ya existen
> login con roles, selección múltiple de ríos y push asociado al usuario. Para
> retomar el trabajo, leer [`CONTINUIDAD_SESION.md`](CONTINUIDAD_SESION.md).

---

## 1. El proyecto en una frase

El sistema combina el **pronóstico de caudal de los ríos** con el **pronóstico de
lluvia** para estimar el riesgo de inundación, guardar un historial de eventos y
enviar **notificaciones push** a las personas suscritas.

No es una aplicación que “predice” la inundación con inteligencia artificial.
Es un sistema de apoyo a la decisión que aplica **reglas transparentes** sobre
datos públicos.

---

## 2. El problema que resuelve

En una cuenca hidrográfica, el riesgo de inundación depende de dos cosas
principales:

- cuánta agua puede llegar al río;
- cuánta lluvia se espera en la zona.

La información existe, pero suele estar separada, en portales técnicos y sin una
traducción sencilla para la población. El proyecto integra esas señales y las
convierte en un estado comprensible:

```text
NORMAL  →  VIGILANCIA  →  ALERTA  →  EMERGENCIA
```

Además, deja un registro de lo ocurrido y puede avisar a los dispositivos
suscritos cuando el nivel cambia.

> **Alcance importante:** es un proyecto académico y orientativo. No emite
> declaraciones oficiales de emergencia ni sustituye a SINAPROC, IMHPA u otra
> autoridad competente.

---

## 3. Qué hace el sistema

1. **Consulta el caudal previsto** de cada río registrado usando GEOGloWS.
2. **Consulta la lluvia prevista** para la zona usando Open-Meteo.
3. **Calcula un nivel de riesgo** comparando los datos con umbrales.
4. **Registra un evento** cuando el nivel cambia y evita duplicados.
5. **Expone el estado y el historial** mediante una API HTTP.
6. **Envía notificaciones push** a los dispositivos registrados cuando el riesgo
   es alto.
7. **Permite probar el flujo** con una consola y una PWA de prueba, sin
   convertirlas en el producto final.

---

## 4. Cómo funciona, paso a paso

```text
   GEOGloWS ─────────┐
   (caudal)          │
                     ├──> BACKEND ───> PostgreSQL
   Open-Meteo ───────┘       │          (ríos, eventos,
   (lluvia)                   │           estados y dispositivos)
                              │
                              ├──> API REST ──> consola / frontend definitivo
                              │
                              └──> Firebase ──> notificaciones push
```

1. Un río del catálogo se registra con un identificador de tramo de GEOGloWS
   (`reachId`), sus coordenadas y su geometría simplificada.
2. El backend evalúa los ríos automáticamente cada **15 minutos** por defecto.
   También existe una evaluación manual para pruebas.
3. Para cada río obtiene:
   - el pronóstico de caudal;
   - la lluvia acumulada prevista para 24 y 72 horas.
4. Comprueba que los datos sean recientes. Si el pronóstico de caudal no es
   confiable, **no genera una alerta ni envía push**.
5. Calcula dos niveles: uno por caudal y otro por lluvia. El nivel final es el
   **más alto de los dos**.
6. Si el nivel cambia, crea un evento en la base de datos. Una clave de
   idempotencia evita guardar o notificar varias veces el mismo cambio.
7. Si el nivel es `ALERTA` o `EMERGENCIA`, envía una notificación push. También
   puede avisar cuando se vuelve a `NORMAL` desde un nivel crítico.
8. El estado actual, el historial y el detalle de cada evento quedan disponibles
   por la API para la consola y para el frontend definitivo.

---

## 5. Qué significa cada nivel

| Nivel | Interpretación | ¿Envía push? |
|---|---|---|
| `NORMAL` | No se superan los umbrales configurados. | No, salvo que sea una recuperación desde un nivel crítico y esté habilitado. |
| `VIGILANCIA` | Hay condiciones que ameritan monitoreo preventivo. | No por defecto; el evento queda registrado. |
| `ALERTA` | Puede haber afectación en zonas ribereñas. | Sí. |
| `EMERGENCIA` | Nivel crítico según los umbrales configurados. | Sí, con prioridad alta. |

Los niveles no son una declaración oficial: son una estimación técnica basada en
los datos y umbrales disponibles.

---

## 6. De dónde salen los datos

| Fuente | Qué aporta | Consideración |
|---|---|---|
| **GEOGloWS** | Pronóstico de caudal por tramo de río. | Se cachea en memoria por tramo durante 15 minutos; si la API falla, se reutiliza la última respuesta. Si no entrega periodos de retorno utilizables, se usan umbrales de referencia de 15/30/60 m³/s y se muestra una advertencia. |
| **Open-Meteo** | Lluvia prevista y acumulados de 24/72 h. | Se guarda una caché de 15 minutos para no consultar en exceso. |
| **PostgreSQL** | Ríos, eventos, estado por tramo y dispositivos push. | Es la memoria del sistema. |
| **Firebase Cloud Messaging** | Entrega de notificaciones push. | Si faltan credenciales, el resto del backend sigue funcionando; el envío queda deshabilitado. |

### Reglas de riesgo actuales

- **Caudal:** umbrales provenientes de GEOGloWS o, como respaldo, 15/30/60 m³/s.
- **Lluvia:** umbrales **experimentales** de 25/50/100 mm en 24 horas.
- **Nivel final:** el mayor entre el nivel por caudal y el nivel por lluvia.
- **Frescura:** el pronóstico de caudal se considera válido hasta 24 horas; la
  lluvia, hasta 6 horas.
- Estos valores son configurables por variables de entorno.

> Los umbrales de lluvia y los valores de respaldo de caudal **no están
> calibrados localmente**. Antes de usar el sistema para decisiones reales deben
> validarse con registros históricos y con las autoridades competentes.

### Cómo aporta la lluvia al aviso

La lluvia es una señal **anticipatoria**: puede advertir que el río recibirá más
agua antes de que el caudal medido o pronosticado reaccione. El sistema consulta
la lluvia horaria prevista con Open-Meteo, acumula las próximas 24 y 72 horas y
usa el acumulado de 24 horas para calcular un **nivel por lluvia**.

El nivel final es el mayor entre el nivel por caudal y el nivel por lluvia:

```text
nivel final = max(nivel por caudal, nivel por lluvia)
```

Por eso, si se espera mucha lluvia, el sistema puede subir a `ALERTA` o
`EMERGENCIA` aunque el caudal pronosticado todavía esté normal. El mensaje del
evento muestra ambos factores. Para que se genere una notificación push, el
pronóstico de caudal también debe ser reciente y confiable; si no lo es, el
sistema evita alertar con datos incompletos.

La lluvia de Open-Meteo es un **pronóstico**, no una medición de pluviómetro. Es
útil para anticipar, pero tiene incertidumbre y no sustituye a una red local de
observación.

### Invierno, verano y temporadas

En Panamá, “invierno” suele referirse a la **temporada lluviosa** y “verano” a
la **temporada seca**. La diferencia importa porque el mismo acumulado de lluvia
puede tener efectos distintos según la humedad previa del suelo:

- en temporada lluviosa el suelo puede estar saturado y responder más rápido;
- en temporada seca el suelo puede absorber más agua, aunque una tormenta muy
  intensa todavía puede provocar crecidas repentinas.

El sistema actual usa los mismos umbrales de lluvia durante todo el año
(25/50/100 mm en 24 h, experimentales). **No distingue estaciones ni usa un
pronóstico climático estacional.** Una mejora futura sería calibrar umbrales por
temporada con datos locales, pero no se deben inventar esos valores. Para la
presentación, conviene explicar esta limitación con claridad.

---

## 7. Arquitectura explicada en lenguaje sencillo

El proyecto tiene un **backend central** y varios clientes que se comunican con
él por HTTP.

```text
Backend (lógica, datos y notificaciones)
   ├── API REST
   ├── Consola de demostración
   ├── PWA de prueba (login, catálogo, suscripción y push)
   └── Frontend definitivo (otro equipo)
```

### La idea central

El backend es el sistema. La consola y la PWA son herramientas de apoyo, no el
producto final. Esto permite que otro equipo construya el frontend definitivo
sin tener que reescribir la lógica de riesgo, las consultas a GEOGloWS o el
envío de notificaciones.

### Qué es cada pieza

| Pieza | Función |
|---|---|
| **Backend** | Consulta fuentes externas, evalúa riesgo, guarda eventos y envía push. |
| **Base de datos** | Conserva ríos, historial, estado por tramo y dispositivos. |
| **API REST** | Contrato para que cualquier cliente consulte o registre información. |
| **Consola** | Cliente HTTP para demostrar y probar el backend desde la terminal. |
| **PWA de prueba** | Cliente de prueba con login, catálogo, suscripción, push y simulación admin. No muestra datos hidrológicos ni es el frontend definitivo. |
| **Frontend definitivo** | Lo construirá otro equipo desde cero usando la API. |

### Qué es demostración y qué es producto

- La **consola** y la **PWA de prueba** existen para probar y exponer el
  proyecto.
- El **backend**, la base de datos, las reglas de riesgo y el envío push son el
  núcleo funcional.
- El **frontend definitivo** es una entrega separada. Puede usar otra tecnología
  y otro diseño; solo debe respetar los contratos HTTP.
- El sistema puede arrancar y evaluar riesgo sin la consola y sin la PWA.

---

## 8. Cómo se construyó el proyecto

El trabajo se organizó en tres bloques:

1. **Base del backend y GEOGloWS:** consulta de caudal, ríos, simulación y
   primeros endpoints.
2. **Evaluación de riesgo y datos complementarios:** separación de las reglas de
   caudal y lluvia, integración con Open-Meteo, monitoreo periódico, eventos,
   historial e idempotencia.
3. **Notificaciones y separación de clientes:** registro de dispositivos,
   integración con Firebase, consola como cliente HTTP, PWA de prueba y pruebas
   automatizadas.
4. **Autenticación y catálogo:** login con roles, cuatro ríos con geometría real,
   selección múltiple, panel de caudal y lluvia en la API, push por usuario,
   caché de GEOGloWS y campo `siguiendo`.
5. **Cierre del avance:** PWA reducida a login, catálogo, suscripción, push y
   simulación admin; la simulación envía el simulacro solo a los seguidores del
   río.

### Decisiones importantes

- **Una sola fuente de verdad:** la lógica de negocio vive en el backend.
- **Clientes intercambiables:** consola, PWA y frontend futuro consumen la misma
  API.
- **Reglas explicables:** no hay caja negra; el nivel se puede justificar con los
  datos y los umbrales.
- **Calidad del dato:** si el pronóstico está vencido, el sistema no alerta.
- **Sin duplicados:** los cambios de nivel se registran una sola vez.
- **Degradación controlada:** si falla una fuente o Firebase, el sistema informa
  el problema y sigue funcionando en lo posible.
- **Seguridad:** credenciales por variables de entorno; JWT con roles `USER` y
  `ADMIN` para las rutas administrativas y el push.

---

## 9. Estado actual y límites

| Aspecto | Estado |
|---|---|
| Backend, API, PostgreSQL y monitoreo | Implementado. |
| Evaluación de caudal y lluvia | Implementada con reglas y pruebas. |
| Historial y detalle de eventos | Implementado. |
| Registro de dispositivos y lógica push | Implementado y probado con mocks. |
| Push real de extremo a extremo | Pendiente de credenciales Firebase y dispositivo físico. |
| PWA de prueba | Con login, catálogo, suscripción, push y simulación admin; falta probar la recepción en un dispositivo real. |
| Frontend definitivo | Lo desarrolla otro equipo. |
| Umbrales | Experimentales; requieren calibración local. La calibración estacional (invierno/verano) está pendiente de investigación y no se implementará con valores inventados. |
| Autenticación | Implementada: login con identificador, JWT de 24 horas y roles `USER`/`ADMIN`. |
| Gestión de ríos | Catálogo cerrado de cuatro ríos con geometría real: Abajo, Curundú, Juan Díaz y Tocumen. La escritura está protegida bajo `/api/admin/rios`. |
| Consulta de ríos | Implementada en la API: el frontend definitivo puede mostrar caudal y lluvia de cualquier río. La PWA mínima ya no la muestra. |
| Selección múltiple | Implementada: cada usuario sigue los ríos que quiera y el push se envía según sus selecciones. |
| Caché de caudal | Implementada en memoria por endpoint y tramo (15 min por defecto); si GEOGloWS falla, se reutiliza la última respuesta. |
| Estado de suscripción en el panel | Implementado: `siguiendo` es `true`/`false` con JWT y `null` sin sesión. |
| Pruebas automatizadas | 79 pruebas, 0 fallos. |

Otras limitaciones conocidas:

- no hay migraciones versionadas de base de datos;
- los bloqueos de monitoreo son en memoria y el despliegue actual es de una sola
  instancia;
- el frontend definitivo requerirá configurar CORS si se sirve desde otro origen;
- la entrega push depende del navegador, del sistema operativo y de que el
  usuario haya dado permiso.

---

## 10. Guía para la presentación final

### Guion sugerido de 5 minutos

1. **Problema (30 s).** Las inundaciones requieren información anticipada, pero
   los datos técnicos están dispersos y no siempre se traducen en una señal
   clara.
2. **Solución (45 s).** El sistema combina caudal y lluvia, calcula un nivel de
   riesgo y avisa a las personas suscritas.
3. **Funcionamiento (60 s).** Mostrar el diagrama de fuentes → backend → base de
   datos → API/push. Explicar que el nivel final es el más alto entre caudal y
   lluvia.
4. **Demostración (60 s).** Iniciar sesión en la consola como usuario, mostrar
   el catálogo y seguir varios ríos. Aclarar que seguir un río solo controla las
   notificaciones. Si hay una cuenta administradora, simular una crecida y
   mostrar que el push llega solo a los seguidores de ese río. Si no hay
   Firebase configurado, explicar que el envío real está pendiente de
   credenciales y dispositivo.
5. **Resultados y límites (90 s).** Mencionar las pruebas, el historial de
   eventos, la separación del frontend y los umbrales experimentales.
6. **Cierre (30 s).** “Es un apoyo académico y orientativo; no sustituye a las
   autoridades. El siguiente paso es calibrar los umbrales y validar el push en
   dispositivos reales”.

### Frases clave

- “No predice la inundación: estima riesgo con datos públicos y reglas
  transparentes”.
- “Combina caudal y lluvia; el nivel final es el más alto de los dos”.
- “Solo avisa cuando el nivel cambia y los datos son confiables”.
- “El backend está separado del frontend para que otro equipo pueda construir la
  interfaz sin reescribir la lógica”.
- “Es un sistema académico de apoyo, no una alerta oficial”.

### Preguntas previsibles

| Pregunta | Respuesta breve |
|---|---|
| ¿Esto sustituye a SINAPROC o IMHPA? | No. Es un apoyo académico y orientativo. |
| ¿Cómo evitan falsas alarmas? | Se valida la frescura de los datos; si el caudal no es reciente, no se alerta. |
| ¿Por qué no usan inteligencia artificial? | Las reglas son explicables y auditables. La calibración con datos locales es el siguiente paso. |
| ¿Qué pasa si falla GEOGloWS? | Se usan umbrales de referencia y se muestra una advertencia; si el dato es viejo, no se genera alerta. |
| ¿Cómo evitan notificaciones repetidas? | Solo se notifica un cambio de nivel y se usa una clave de idempotencia. |
| ¿La lluvia por sí sola puede generar una alerta? | Sí, el nivel final es el mayor entre caudal y lluvia, siempre que el pronóstico de caudal sea reciente y confiable. |
| ¿Se ajustan los umbrales para invierno o verano? | Todavía no. Los umbrales son fijos y experimentales; la calibración estacional es una mejora pendiente. |
| ¿Qué falta para producción? | Calibrar umbrales, probar el push real en dispositivos, construir el frontend definitivo y retirar los endpoints heredados. |
| ¿Se puede usar en el celular? | Sí, mediante HTTPS y una PWA; en iOS requiere 16.4+ e instalación en la pantalla de inicio. |

### Qué no afirmar

- Que el sistema es una alerta oficial.
- Que los umbrales están calibrados para Panamá.
- Que el push ya fue probado de extremo a extremo si no hay credenciales y un
  dispositivo real.
- Que la PWA actual es el frontend definitivo.
- Que el sistema garantiza que una inundación ocurrirá o no ocurrirá.

---

## 11. Glosario mínimo

| Término | Significado |
|---|---|
| **Backend** | Programa central que contiene la lógica y los datos. |
| **API REST** | Forma estándar en que los clientes piden datos al backend. |
| **PostgreSQL** | Base de datos donde se guarda el historial. |
| **GEOGloWS** | Servicio que entrega pronósticos de caudal por tramo de río. |
| **Open-Meteo** | Servicio que entrega pronósticos de lluvia. |
| **Reach / tramo** | Identificador de un segmento de río usado por GEOGloWS. |
| **Caudal** | Cantidad de agua que pasa por el río por segundo (m³/s). |
| **Umbral** | Valor límite que marca el paso a un nivel de riesgo. |
| **FCM** | Servicio de Firebase para enviar notificaciones push. |
| **PWA** | Página web instalable; aquí se usa para probar el flujo mínimo (login, catálogo, suscripción y notificaciones). No es el frontend definitivo. |
| **Idempotencia** | Evitar que una misma operación se registre o notifique dos veces. |

---

## 12. Para agentes de IA que trabajen en el proyecto

Antes de modificar código:

1. Leer [`CONTINUIDAD_SESION.md`](CONTINUIDAD_SESION.md) para conocer el estado
   exacto, las decisiones cerradas y el siguiente paso.
2. Leer este documento para entender el propósito y el alcance.
3. Leer [`ARQUITECTURA.md`](ARQUITECTURA.md) para respetar la separación entre
   backend, consola, PWA y frontend.
4. Leer [`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md) antes de cambiar
   endpoints o contratos.
5. Leer [`CONFIGURACION_PUSH.md`](CONFIGURACION_PUSH.md) antes de tocar
   credenciales o Firebase.

Reglas que no se deben romper:

- La lógica de negocio pertenece al backend, no a la consola ni a la PWA.
- Los clientes deben consumir la API HTTP; no deben acceder a repositorios.
- No versionar credenciales ni archivos `.env`.
- No afirmar que el push funciona sin una prueba real.
- Mantener las pruebas y actualizar la documentación cuando cambie un contrato.
- No hacer commits automáticamente si el usuario no lo pidió.

---

## 13. Documentos técnicos

| Documento | Para qué sirve |
|---|---|
| [`CONTINUIDAD_SESION.md`](CONTINUIDAD_SESION.md) | Estado actual y cómo retomar el trabajo en otra sesión. |
| [`ARQUITECTURA.md`](ARQUITECTURA.md) | Reglas de separación y responsabilidades. |
| [`INTEGRACION_FRONTEND.md`](INTEGRACION_FRONTEND.md) | Contrato de API que existe hoy. |
| [`GUIA_FRONTEND.md`](GUIA_FRONTEND.md) | Diseño del frontend definitivo y flujo mínimo de la PWA. |
| [`GUIA_PRUEBA_NOTIFICACIONES.md`](GUIA_PRUEBA_NOTIFICACIONES.md) | Cómo ejecutar y verificar la demo. |
| [`CONFIGURACION_PUSH.md`](CONFIGURACION_PUSH.md) | Variables, PostgreSQL y Firebase. |
| [`../INFORME_ALERTAS_INUNDACION.md`](../INFORME_ALERTAS_INUNDACION.md) | Informe histórico del avance del 21–22/10 |
| [`../IMPLEMENTATION_NOTES.md`](../IMPLEMENTATION_NOTES.md) | Historia de decisiones de implementación. |
| [`../README.md`](../README.md) | Arranque rápido y comandos. |
