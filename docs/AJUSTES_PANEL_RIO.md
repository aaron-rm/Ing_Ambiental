# Ajustes necesarios para el panel de río

**Fecha:** 23/10/2026<br>
**Estado:** notas de análisis; no implementado.<br>
**Objetivo:** que al hacer clic en cualquiera de los cuatro ríos se abra un panel
con caudal, lluvia, estado, gráfico, recomendaciones y botón de suscripción.

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

`Rio` solo tiene `latitud` y `longitud`; no tiene la línea del río. Tampoco está
implementado `GET /api/rios/mapa`.

Para que el clic funcione sobre la línea del río hacen falta:

- geometría `LineString` en WGS84;
- endpoint GeoJSON con `properties.rioId` y `properties.nombre`;
- que el `rioId` sea el identificador estable para consultar el panel.

No usar un punto o un marcador si el diseño exige hacer clic sobre el cauce.

### 2.3. El seed actual no coincide con el catálogo

`InicializadorDatos` crea “Río Pacora” con el `reachId` de Río Abajo. Hay que
reemplazarlo por los cuatro ríos acordados antes de probar el panel.

### 2.4. La consulta puede ser lenta al hacer clic

Cada llamada a `GET /api/alertas/rio/{rioId}` consulta GEOGloWS
(`forecaststats` y `returnperiods`) y Open-Meteo. Open-Meteo ya tiene caché de
15 minutos, pero GEOGloWS no.

Para que el panel no tarde varios segundos en cada clic:

- agregar caché por río para la evaluación completa, o
- servir la última evaluación del monitoreo y actualizarla en segundo plano.

También conviene exponer claramente `fechaEvaluacion` para que el frontend
indique si el dato es reciente.

### 2.5. Falta `rioId` y, opcionalmente, el estado de suscripción

`AlertaRioDTO` devuelve `reachId` y `nombreRio`, pero no el `rioId` que el
frontend usa para el clic. No es un bloqueo porque el frontend ya lo conoce,
pero incluirlo evita confusiones.

Para el botón naranja, el frontend puede consultar
`GET /api/usuarios/me/rios` y comprobar si el río está en la lista. Si se quiere
evitar esa llamada adicional, se puede agregar un campo `siguiendo` a la
respuesta del panel cuando la petición incluya sesión.

### 2.6. Separar el estado del caudal del estado final

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
- Textos de recomendación: se muestran, pero primero hay que poblarlos en el
  backend.

## 5. Orden sugerido

1. Usar el hidrograma de caudal con umbrales (decisión tomada en la sección 3).
2. Corregir el catálogo: cuatro ríos, geometría y endpoint del mapa.
3. Poblar `recomendaciones`.
4. Agregar caché o última evaluación para que el clic sea rápido.
5. Agregar `rioId` y, si se decide, `siguiendo`.
6. Implementar usuario–ríos y el botón de suscripción.
7. Documentar el contrato final en `INTEGRACION_FRONTEND.md`.
