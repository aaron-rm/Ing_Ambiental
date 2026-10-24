# Ajustes necesarios para el panel de río

**Fecha:** 23/10/2026<br>
**Estado:** **implementado en el backend y en la consola**. La caché de GEOGloWS
(2.4), el campo `siguiendo` (2.5), las recomendaciones, el catálogo con geometría
y el hidrograma de caudal ya están en la rama. La PWA de prueba se redujo
después a login, catálogo, suscripción, push y simulación admin, por lo que ya no
muestra este panel; el contrato sigue disponible para el frontend definitivo.
Solo quedan pendientes de validación la prueba de humo en Windows y el push en un
dispositivo real.<br>
**Objetivo:** que el frontend definitivo pueda abrir un panel con caudal, lluvia,
estado, gráfico, recomendaciones y botón de suscripción al elegir cualquiera de
los cuatro ríos.

## 1. Lo que ya sirve

El endpoint `GET /api/alertas/rio/{rioId}` ya existe, es público y **no exige
estar suscrito**. Devuelve un `AlertaRioDTO` con casi todo lo necesario:

- `nivelAlerta`: `NORMAL`, `VIGILANCIA`, `ALERTA` o `EMERGENCIA`;
- `caudalActualOInicial` y `caudalMaximoPronosticado`;
- `umbralVigilancia`, `umbralAlerta` y `umbralEmergencia`;
- `pronostico`: serie de `{fecha, caudalMedio}`;
- `pronosticoDiario`: promedio, mínimo, máximo y estado por día;
- `lluvia24hMm`, `lluvia72hMm` y `pronosticoLluvia`;
- `nivelRiesgoCaudal` y `nivelRiesgoLluvia`;
- `mensaje`, `factoresRiesgo`, `advertencias` y `estadoDatos`;
- fuentes y fechas de actualización.

Por tanto, el gráfico de caudal contra umbrales y la parte de lluvia se pueden
hacer con el contrato actual. La mayor parte del trabajo del panel es frontend.

## 2. Correcciones necesarias en el backend

### 2.1. Las recomendaciones están vacías

> **Implementado.** `AlertaService` ahora genera recomendaciones por nivel y
> añade avisos cuando el riesgo principal es la lluvia o cuando los datos no son
> confiables.

En `AlertaService.mapear()` se ejecuta:

```java
dto.setRecomendaciones(List.of());
```

El campo existe, pero siempre llega vacío. Si el panel debe mostrar
recomendaciones propias del estado del río, hay que generarlas en el backend
según el nivel y el contexto:

- normal: sin acción urgente;
- vigilancia: mantenerse atento;
- alerta: evitar zonas cercanas al cauce;
- emergencia: alejarse y seguir indicaciones de las autoridades.

No conviene que el frontend invente estos textos por su cuenta, porque estaría
duplicando la lógica de riesgo.

### 2.2. Falta el catálogo con geometría para el mapa

> **Implementado.** `Rio` tiene `geometria` y `GET /api/rios/mapa` devuelve la
> `FeatureCollection` con la geometría real simplificada de GEOGloWS V2.

`Rio` solo tiene `latitud` y `longitud`; no tiene la línea del río. Tampoco está
implementado `GET /api/rios/mapa`.

Para que el clic funcione sobre la línea del río hacen falta:

- geometría `LineString` en WGS84;
- endpoint GeoJSON con `properties.rioId` y `properties.nombre`;
- que el `rioId` sea el identificador estable para consultar el panel.

No usar un punto o un marcador si el diseño exige hacer clic sobre el cauce.

### 2.3. El seed actual no coincide con el catálogo

> **Implementado.** `InicializadorDatos` se eliminó y `InicializadorCatalogo`
> hace upsert por `reachId`, con lo que “Río Pacora” se corrigió a Río Abajo sin
> duplicar filas.

`InicializadorDatos` crea “Río Pacora” con el `reachId` de Río Abajo. Hay que
reemplazarlo por los cuatro ríos acordados antes de probar el panel.

### 2.4. La consulta puede ser lenta al hacer clic

> **Implementado.** `ClienteApiGeoglows` cachea en memoria por endpoint y
> `reachId` durante `GEOGLOWS_CACHE_MINUTOS` (15 min por defecto). Open-Meteo ya
> cachea 15 minutos. Si GEOGloWS falla, se reutiliza la última respuesta cacheada
> aunque haya expirado.

Cada llamada a `GET /api/alertas/rio/{rioId}` consulta GEOGloWS
(`forecaststats` y `returnperiods`) y Open-Meteo. Open-Meteo ya tiene caché de
15 minutos, pero GEOGloWS no.

Para que el panel no tarde varios segundos en cada clic:

- agregar caché por río para la evaluación completa, o
- servir la última evaluación del monitoreo y actualizarla en segundo plano.

También conviene exponer claramente `fechaEvaluacion` para que el frontend
indique si el dato es reciente.

### 2.5. Falta `rioId` y, opcionalmente, el estado de suscripción

> **Implementado.** `AlertaRioDTO` incluye `rioId` y `Boolean siguiendo`. Con un
> JWT válido, `siguiendo` es `true` o `false`; sin sesión es `null`. La PWA ya no
> necesita cruzar `/api/usuarios/me/rios` para pintar el botón.

`AlertaRioDTO` devuelve `reachId` y `nombreRio`, pero no el `rioId` que el
frontend usa para el clic. No es un bloqueo porque el frontend ya lo conoce,
pero incluirlo evita confusiones.

Para el botón naranja, el frontend puede consultar
`GET /api/usuarios/me/rios` y comprobar si el río está en la lista. Si se quiere
evitar esa llamada adicional, se puede agregar un campo `siguiendo` a la
respuesta del panel cuando la petición incluya sesión.

### 2.6. Separar el estado del caudal del estado final

> **Aplicado.** El contrato expone `nivelRiesgoCaudal` y `nivelRiesgoLluvia`; el
> hidrograma se dibuja con los umbrales de caudal y el panel muestra ambos.

`nivelAlerta` es el **mayor** entre el riesgo por caudal y el riesgo por lluvia.
En cambio, `pronosticoDiario.estadoDia` se calcula solo con el caudal.

Eso puede producir una apariencia contradictoria: el gráfico del río puede estar
por debajo del último umbral, pero el panel mostrar `ALERTA` o `EMERGENCIA`
porque la lluvia también suma.

Para el panel hay que decidir y documentar:

- usar `nivelRiesgoCaudal` para el color/estado del gráfico del río;
- usar `nivelAlerta` para el estado general del panel;
- mostrar explícitamente si el riesgo viene del caudal, de la lluvia o de ambos.

### 2.7. Evitar devolver entidades JPA en el catálogo

> **Implementado.** `GET /api/rios` devuelve `RioResumenDTO` y el mapa sale como
> GeoJSON; la entidad `Rio` no se expone.

`GET /api/rios` devuelve hoy la entidad `Rio`. Si se le agrega geometría, esa
respuesta puede volverse pesada y acoplar al frontend a la base de datos.

Conviene crear DTOs:

- `RioResumenDTO` para listados;
- `RioMapaDTO`/GeoJSON para el mapa;
- `AlertaRioDTO` para el panel.

### 2.8. La suscripción no debe condicionar la consulta

La regla queda confirmada: el panel de cualquiera de los cuatro ríos se puede
consultar sin estar suscrito. La suscripción solo controla el push.

No agregar una validación del tipo “si no sigue el río, no puede verlo”. Si el
panel exige sesión, debe ser por autenticación general, no por suscripción al
río.

## 3. Decisión: el gráfico se basa en caudal

El backend trabaja con **caudal en m³/s**, no con altura del agua en metros ni
con secciones transversales del cauce. Se decidió que el gráfico del panel sea un
**hidrograma de caudal contra tiempo**:

- la línea principal es `pronostico` (caudal medio por intervalo);
- las líneas horizontales son `umbralVigilancia`, `umbralAlerta` y
  `umbralEmergencia`;
- el color del gráfico usa `nivelRiesgoCaudal`;
- el estado general del panel usa `nivelAlerta`, que es el mayor entre caudal y
  lluvia.

Si el diseño incluye un dibujo del nivel del agua, será una **representación
ilustrativa** derivada del caudal respecto de los umbrales: el agua sube cuando
el caudal se acerca o supera un umbral, y la lluvia pronosticada puede adelantar
visualmente ese ascenso. No se mostrarán metros de altura ni bordes reales del
cauce, porque el modelo no tiene curva de gasto ni batimetría. Esos valores no
deben inventarse ni presentarse como una medición.

## 4. Lo que no requiere cambio de backend

- Colores, textos rojos, signos de exclamación y diseño del panel.
- Botón naranja de suscripción: es frontend; usa los endpoints de usuario–ríos.
- Dibujo del gráfico y de las líneas de umbral: el backend ya entrega los
  valores.
- Textos de recomendación: ya se generan en el backend según el nivel.

## 5. Orden sugerido (estado)

1. ~~Usar el hidrograma de caudal con umbrales~~ — el contrato ya lo entrega; la
   PWA mínima ya no muestra el panel.
2. ~~Corregir el catálogo: cuatro ríos, geometría y endpoint del mapa~~ — hecho.
3. ~~Poblar `recomendaciones`~~ — hecho en `AlertaService`.
4. ~~Agregar caché o última evaluación para que el clic sea rápido~~ — hecho en
   `ClienteApiGeoglows`.
5. ~~Agregar `rioId` y `siguiendo`~~ — hechos; el frontend puede usar
   `siguiendo` en lugar de cruzar `/api/usuarios/me/rios`.
6. ~~Implementar usuario–ríos y el botón de suscripción~~ — hecho.
7. ~~Documentar el contrato final en `INTEGRACION_FRONTEND.md`~~ — hecho.
