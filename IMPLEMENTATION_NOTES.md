# Registro de Implementación (Avance 50 % — Backend y Demo GEOGLOWS)

## 1. Resumen
Se completó la integración funcional del backend en Spring Boot para el avance del 50 % del sistema de alerta temprana de inundaciones con GEOGLOWS. 
Se implementó un flujo completo de detección, evaluación de riesgo de caudales, simulación interactiva de inundación, persistencia en base de datos PostgreSQL y una consola interactiva lista para demostración ante docentes y compañeros, además de endpoints REST correspondientes.

---

## 2. Archivos creados o modificados

### Archivos creados
1. `src/main/java/com/alerta/sistema_alerta_inundaciones/alerta/SuscripcionAlerta.java`:
   - **Propósito**: Entidad JPA para almacenar los contactos (nombre y correo/teléfono) suscritos a alertas por tramo de río en la base de datos PostgreSQL.
2. `src/main/java/com/alerta/sistema_alerta_inundaciones/alerta/SuscripcionAlertaRepository.java`:
   - **Propósito**: Interfaz `JpaRepository` para consultas y persistencia de suscripciones.
3. `src/main/java/com/alerta/sistema_alerta_inundaciones/alerta/AlertaRioDTO.java`:
   - **Propósito**: Objeto de transferencia de datos (DTO) que consolida el caudal inicial, caudal pico, umbrales de retorno, nivel de alerta calculado y la serie temporal del pronóstico extendido.
4. `src/main/java/com/alerta/sistema_alerta_inundaciones/alerta/AlertaService.java`:
   - **Propósito**: Servicio de negocio que orquesta la llamada a GEOGLOWS, la evaluación de umbrales con `GeneradorAlertaFinal`, la lógica de simulación de crecidas y la gestión de suscripciones.
5. `src/main/java/com/alerta/sistema_alerta_inundaciones/alerta/AlertaController.java`:
   - **Propósito**: Endpoints REST (`/api/alertas/tramo/{reachId}`, `/api/alertas/rio/{rioId}`, `/api/alertas/suscribir`, `/api/alertas/suscripciones/{reachId}`, `/api/alertas/simular`).
6. `src/main/java/com/alerta/sistema_alerta_inundaciones/demo/ConsolaDemoRunner.java`:
   - **Propósito**: `CommandLineRunner` que despliega un menú interactivo en consola al iniciar la aplicación para guiar la demostración en vivo.

### Archivos modificados
1. `src/main/java/com/alerta/sistema_alerta_inundaciones/GEOGLOWS/ClienteApiGeoglows.java`:
   - Ajuste de timeout HTTP (a 10 segundos) y manejo de fallback en `obtenerUmbrales()` para tolerancia ante fallas o indisponibilidad en el endpoint upstream `returnperiods` de la API de GEOGLOWS v2.
2. `src/main/java/com/alerta/sistema_alerta_inundaciones/GEOGLOWS/LectorDatosIngresados.java`:
   - Optimización en `sincronizarTramo()` para no bloquear la sincronización con la descarga masiva de décadas de registros del endpoint retrospectivo (desde 1940), manteniendo la respuesta del pronóstico ágil.
3. `src/main/java/com/alerta/sistema_alerta_inundaciones/GEOGLOWS/GeneradorAlertaFinal.java`:
   - Actualización del ID de tramo de prueba a `710039498` (tramo hidrológico real de Panamá).

---

## 3. Flujo de funcionamiento

```text
Consola Demo (ConsolaDemoRunner) o Petición HTTP (AlertaController)
                     ↓
             AlertaService
                     ↓
 ┌───────────────────┴───────────────────┐
 ↓                                       ↓
PostgreSQL                          GEOGLOWS Client (ClienteApiGeoglows)
(Rios / Suscripciones)              (Consumo API REST v2 pública)
                                         ↓
                                    LectorDatosIngresados
                                    (Pronóstico / Umbrales)
                                         ↓
                                    GeneradorAlertaFinal
                                    (Evaluación Caudal vs Umbrales)
                     ↓
             AlertaRioDTO
                     ↓
   - Visualización en Consola
   - Notificación simulada a suscriptores
   - Respuesta JSON en endpoints REST
```

---

## 4. Decisiones técnicas importantes
- **Reutilización del cliente existente**: Se conservó íntegramente la estructura de `ClienteApiGeoglows`, `LectorDatosIngresados` y `GeneradorAlertaFinal` desarrollada por el compañero de equipo, limitándose únicamente a solventar el bloqueo del histórico y proveer resiliencia si la API externa experimenta fallas temporales de cálculo.
- **Evaluación del riesgo**: Se mantiene centralizada en `GeneradorAlertaFinal`, comparando el caudal pico pronosticado contra los umbrales de periodo de retorno (2 años para Vigilancia, 5 años para Alerta, 25 años para Emergencia).
- **Consola interactiva no bloqueante**: La demo en consola se ejecuta en un hilo daemon secundario iniciado desde `CommandLineRunner`. Esto permite al usuario interactuar desde la terminal y al mismo tiempo mantener el servidor web Tomcat activo en el puerto 8080 para atender peticiones REST.
- **Suscripciones y simulación**: Las suscripciones se persisten de manera real en la base de datos PostgreSQL (`suscripciones_alerta`). La opción de simulación inyecta un caudal superior a los umbrales (ej. 75 m³/s), reevalúa el estado a EMERGENCIA y dispara los avisos a los suscriptores registrados.

---

## 5. Integración con GEOGLOWS
- **Componentes reutilizados**: `ClienteApiGeoglows` y `LectorDatosIngresados`.
- **Datos reales obtenidos**: Pronóstico de caudales a través de `forecaststats/{river_id}?format=json` de la API v2 pública del ECMWF GEOGLOWS.
- **Transformación**: La serie temporal de fechas y valores `flow_avg` es parseada a puntos de pronóstico cronológicos (`Instant`, `double`) y evaluada para determinar el caudal máximo esperado del horizonte.
- **Observación/Limitación**: El servicio `retrospectivedaily` de GEOGLOWS devuelve series masivas desde 1940 que producían bloqueos en la petición si se llamaban sincrónicamente. Dicho llamado se desacopló del flujo crítico de alerta para garantizar inmediatez.

---

## 6. Datos y supuestos
- **Río demo predeterminado**: Río Pacora (Panamá), tramo hidrológico GEOGLOWS `reach_id = 710039498` (coordenadas aproximadas 9.08° N, -79.28° W).
- **Umbrales**: Umbrales de referencia de demostración (Vigilancia = 15.0 m³/s, Alerta = 30.0 m³/s, Emergencia = 60.0 m³/s), correspondientes a los órdenes de magnitud típicos de retorno cuando la API externa no provee curvas locales calibradas.
- **Simulación**: Caudal inyectado por defecto de 75.0 m³/s para forzar la condición de `EMERGENCIA` y comprobar el aviso a los contactos.

---

## 7. Cómo ejecutar la demostración

### Opción A: Demostración en Consola (Recomendada para la presentación)
1. Abrir una terminal en la raíz del proyecto.
2. Asegurar que PostgreSQL esté corriendo con la base de datos `alerta_inundaciones` configurada en `application.properties`.
3. Ejecutar:
   ```powershell
   ./mvnw spring-boot:run
   ```
4. Esperar el mensaje de inicio del banner y el menú de opciones:
   - Presionar `1`: Consulta el pronóstico real de GEOGLOWS y muestra los datos del río.
   - Presionar `2`: Registra un nombre y correo/teléfono (se guarda en PostgreSQL).
   - Presionar `3`: Ejecuta la simulación de crecida (ej. 75 m³/s) y muestra en vivo la alerta de emergencia y el despacho a los usuarios suscritos.
   - Presionar `4`: Lista los suscriptores en la base de datos.
   - Presionar `5`: Sale del menú de consola sin detener el backend.

### Opción B: Demostración vía Endpoints REST (cURL o navegador)
- Consultar alerta y pronóstico de un río:
  `GET http://localhost:8080/api/alertas/rio/1`
- Consultar por tramo GEOGLOWS directo:
  `GET http://localhost:8080/api/alertas/tramo/710039498`
- Simular crecida:
  `POST http://localhost:8080/api/alertas/simular?reachId=710039498&caudal=80.0`

---

## Conceptos que debo entender

1. **`CommandLineRunner`**:
   Es una interfaz propia de Spring Boot que permite ejecutar un bloque de código automáticamente justo después de que el contexto de la aplicación se ha inicializado por completo.

2. **Reach ID (ID de Tramo)**:
   Es el identificador numérico único que utiliza GEOGLOWS para representar un segmento específico de río dentro de su red hidrográfica global.

3. **Periodo de Retorno**:
   Es una estimación estadística del intervalo promedio de tiempo en años entre la ocurrencia de eventos hidrológicos de una magnitud igual o superior (ej. 2, 5 o 25 años).

4. **DTO (Data Transfer Object)**:
   Es un objeto simple que transporta únicamente los datos necesarios entre capas del sistema (del servicio al cliente REST o a la consola), evitando exponer entidades completas de base de datos o estructuras crudas de la API externa.

5. **`JpaRepository`**:
   Mecanismo de Spring Data que provee automáticamente métodos listos para usar (`save`, `findAll`, `findById`) para interactuar con tablas de PostgreSQL sin requerir escribir sentencias SQL manuales.

---

## 8. Ajustes realizados después de probar la demo (Sesión 2)

### 8.1 Logs de Hibernate ocultos en consola
- **Problema**: Las consultas SQL generadas por Hibernate (`select`, `insert`) aparecían directamente en la terminal durante la demostración.
- **Corrección**: En `application.properties` se configuró `spring.jpa.show-sql=false`, `spring.jpa.properties.hibernate.format_sql=false` y los niveles de log de Hibernate/SQL a `WARN`.

### 8.2 Coordenadas incorrectas (Lat 0.0, Lon 0.0)
- **Problema**: La entidad `Rio` mostraba `0.0, 0.0` por defecto al no tener coordenadas reales asignadas.
- **Corrección**: Se actualizó el registro del Río Pacora en la base de datos con coordenadas reales (`latitud=9.08`, `longitud=-79.28`). La consola ahora muestra "No disponibles" si las coordenadas siguen en 0.0, evitando mostrar datos falsos.

### 8.3 Validación de contacto en suscripciones
- **Problema**: El formulario aceptaba nombres vacíos, correos malformados (ej. `jaziel@`, `abc`) y entradas sin sentido.
- **Corrección**: Se añadió validación con regex en `ConsolaDemoRunner`:
  - Email: patrón `^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$`
  - Teléfono/WhatsApp: patrón `^\\+?[0-9]{7,15}$`
  - Nombre vacío usa fallback `Usuario Demo`, contacto vacío usa `usuario@demo.com`.
  - Entradas inválidas muestran mensaje de error y permiten reintentar.

### 8.4 Pronóstico diario agregado (multi-día)
- **Problema**: El pronóstico solo mostraba el caudal máximo del horizonte completo, sin desglose por día.
- **Corrección**: En `AlertaService` se agregó lógica de agrupación por fecha UTC usando `LinkedHashMap<LocalDate, List<Double>>`. El resultado es una lista de `PronosticoDiaDTO` (día, fecha, caudal medio, caudal máximo, estado) que se incluye en `AlertaRioDTO.pronosticoDiario`. La consola muestra hasta 15 días.

### 8.5 Corrección del parser de fechas GEOGLOWS
- **Problema**: Las fechas del pronóstico (`"2026-10-12T00:00:00+00:00"`) fallaban al parsearse porque el código intentaba concatenar `Z` a cadenas que ya tenían `+00:00`, produciendo `Instant.now()` como fallback para todos los puntos.
- **Corrección**: `ClienteApiGeoglows.parseFecha()` reescrito para usar `OffsetDateTime.parse()` que maneja correctamente el offset `+00:00`.

### 8.6 Texto ASCII-safe en consola
- **Problema**: Caracteres acentuados (`é`, `á`, `ó`) producían símbolos de reemplazo (`?`) en terminales Windows con codificación CP850/CP1252.
- **Corrección**: Todos los textos de `ConsolaDemoRunner` y mensajes generados por `AlertaService` fueron reescritos usando únicamente caracteres ASCII básicos. El formato de los estados (`EMERGENCIA` → `PELIGRO DE INUNDACION`) se maneja en el método `formatearEstado()`.

### 8.7 Pasos de progreso visibles [1/3], [2/3], [3/3]
- **Mejora**: Al consultar el estado real (opción 1) la consola muestra pasos numerados que representan las etapas reales del proceso: sincronización con GEOGLOWS, procesamiento de datos y evaluación de riesgo. Esto hace visible el flujo técnico durante la presentación.

---

## 9. Arquitectura para integración con frontend

El backend está completamente separado de la consola interactiva. Los mismos métodos de `AlertaService` que alimentan la consola son los que expone `AlertaController` como REST:

```text
Frontend (React / PWA)
        ↓ HTTP
AlertaController  →  /api/alertas/*
        ↓
AlertaService  (misma lógica que usa la consola)
        ↓
GEOGLOWS API / PostgreSQL
```

Endpoints disponibles para el frontend:

| Método | URL | Descripción |
|--------|-----|-------------|
| GET | `/api/alertas/rio/{rioId}` | Estado y pronóstico por ID de río en BD |
| GET | `/api/alertas/tramo/{reachId}` | Estado y pronóstico por reach_id GEOGLOWS |
| POST | `/api/alertas/suscribir?reachId=&nombre=&contacto=` | Registra suscripción |
| GET | `/api/alertas/suscripciones/{reachId}` | Lista suscriptores de un tramo |
| POST | `/api/alertas/simular?reachId=&caudal=` | Simulación de crecida |

---

## 10. Pendientes para avances posteriores
- Desarrollo de la interfaz gráfica web en React / PWA.
- Envío real de correos electrónicos (JavaMailSender) o notificaciones por SMS/WhatsApp.
- Selección dinámica de tramos mediante mapa geográfico.
- Autenticación y roles de usuarios (administradores vs. suscriptores comunitarios).
