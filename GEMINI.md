```md
# GEOGLOWS — Project Instructions

## 1. Contexto del proyecto

Este proyecto es un sistema de alerta temprana relacionado con inundaciones que utiliza datos de GEOGLOWS.

Stack principal:

- Backend: Java + Spring Boot
- Base de datos: PostgreSQL
- Frontend definitivo: lo desarrollará otro equipo; no forma parte de este repositorio
- PWA de pruebas: página estática mínima servida por Spring Boot; hoy solo verifica notificaciones y en el siguiente avance añade login y selección de ríos
- Fuente de datos: GEOGLOWS API + Open-Meteo (precipitación)
- Notificaciones: Firebase Cloud Messaging (FCM)

El proyecto es académico y actualmente existe una fecha de entrega cercana.

> **Estado actual (23/10/2026):** el backend, el monitoreo y las notificaciones
> push están implementados. El siguiente avance (autenticación, catálogo de
> cuatro ríos, selección múltiple y consola con login) está **planificado pero
> todavía no implementado**. La PWA actual es solo una herramienta mínima de
> prueba de notificaciones; el frontend definitivo lo construirá otro equipo
> desde cero. Antes de modificar algo, leer primero
> `docs/CONTINUIDAD_SESION.md`, luego `docs/RESUMEN_PROYECTO.md`,
> `docs/GUIA_FRONTEND.md`, `docs/ARQUITECTURA.md`,
> `docs/INTEGRACION_FRONTEND.md`, `docs/CONFIGURACION_PUSH.md` y
> `INFORME_ALERTAS_INUNDACION.md`.

## 1.1 Directrices permanentes (resumen)

El detalle está en [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md). En resumen:

1. El backend es independiente de la consola y del frontend.
2. La lógica de negocio, las integraciones y la persistencia pertenecen al backend.
3. `ConsolaDemoRunner` consume la API pública mediante `BackendApiClient`; no accede a repositorios ni replica reglas.
4. La consola no es el lugar para implementar funcionalidades nuevas del sistema.
5. La PWA de pruebas hoy solo verifica notificaciones; en el siguiente avance tendrá un login mínimo y selección de ríos, pero seguirá sin ser el frontend definitivo.
6. El backend debe mantener contratos HTTP claros para el frontend futuro.
7. Las credenciales se gestionan por variables de entorno, nunca en el repositorio.
8. Los cambios deben preservar funcionalidades, compilar y pasar las pruebas.
9. La documentación se actualiza cuando cambian contratos, configuración o decisiones.

**Alcance:** estas reglas aplican al backend y a sus clientes de apoyo. No imponen
diseño visual, tecnología ni estructura al equipo que desarrollará el frontend
definitivo; ese equipo solo debe respetar los contratos públicos de la API.

## 2. Objetivo actual

El objetivo inmediato es implementar el siguiente avance acordado:

1. Registro e inicio de sesión con identificador y contraseña.
2. Roles simples `USER` y `ADMIN`.
3. Catálogo cerrado de cuatro ríos cercanos a la UTP, con geometría.
4. Selección múltiple de ríos por usuario.
5. Consola con login y menús distintos según el rol.
6. PWA mínima con login y selección de ríos.
7. Push asociado a los ríos que sigue cada usuario.

Antes de implementar, leer `docs/CONTINUIDAD_SESION.md`. No asumir que estas
piezas ya existen: a la fecha de este documento solo están documentadas.

Mantener el backend funcional, la integración con GEOGLOWS y las pruebas
existentes. No intentar características futuras que no sean necesarias para este
avance (recuperación de contraseña, verificación por correo, refresh tokens,
frontend definitivo, etc.).

---

# 3. Regla principal: NO sobreingeniería

Trabaja con una mentalidad de **MVP académico**.

NO:

- Crear arquitecturas complejas innecesariamente.
- Introducir patrones de diseño que no sean necesarios.
- Agregar librerías solamente porque "podrían ser útiles".
- Crear múltiples capas o abstracciones sin necesidad.
- Refactorizar código que ya funciona si no es necesario.
- Cambiar tecnologías existentes.
- Rehacer partes completas del proyecto sin motivo.
- Crear funcionalidades futuras que todavía no se necesitan.
- Generar documentación extensa durante la implementación.
- Hacer investigación extensa si la información necesaria ya está disponible en el proyecto.

SÍ:

- Reutilizar el código existente.
- Mantener la arquitectura actual siempre que sea razonable.
- Implementar solamente lo necesario.
- Preferir soluciones simples y fáciles de explicar.
- Mantener el código legible.
- Hacer cambios pequeños y concretos.

---

# 4. Uso de IA y nivel de esfuerzo

Trabaja con **low effort / low reasoning** siempre que sea suficiente.

No gastes tokens:

- explicando cosas obvias,
- repitiendo el estado completo del proyecto,
- proponiendo muchas alternativas,
- haciendo planes excesivamente detallados,
- explorando archivos irrelevantes,
- realizando análisis arquitectónicos innecesarios.

Primero inspecciona solamente los archivos relevantes para la tarea actual.

Después implementa.

No necesito una explicación larga antes de cada cambio.

Cuando una solución sencilla funcione, úsala.

---

# 5. Forma de trabajar

Antes de modificar código:

1. Inspecciona la estructura del proyecto.
2. Identifica el backend existente.
3. Identifica los endpoints, servicios, entidades y repositorios que ya existen.
4. Identifica la integración actual con GEOGLOWS.
5. Identifica la configuración de PostgreSQL.
6. Determina qué falta para cumplir el objetivo inmediato.

Después de eso, empieza a implementar.

No hagas una auditoría completa del proyecto si no es necesaria.

---

# 6. Arquitectura esperada del backend

Siempre que sea compatible con el código existente, utilizar una estructura sencilla:

Controller
→ Service
→ Repository / Client
→ Database / External API

Por ejemplo:

- `Controller`: recibe las solicitudes HTTP.
- `Service`: contiene la lógica de negocio.
- `Repository`: acceso a PostgreSQL.
- `Client/Service`: comunicación con GEOGLOWS.

No crear capas adicionales sin una razón concreta.

---

# 7. GEOGLOWS

La integración con GEOGLOWS es una parte importante del proyecto.

Cuando se necesiten datos de GEOGLOWS:

- reutiliza la integración existente si ya está implementada;
- respeta el formato real de la API;
- no inventes respuestas;
- maneja errores básicos de conexión o respuestas inválidas;
- transforma los datos al formato que necesite nuestro backend.

Si la API ya fue probada anteriormente, no reemplazarla por datos falsos.

Los datos mock solamente deben utilizarse cuando sean necesarios para desarrollar o probar una parte que todavía no puede conectarse con la API real.

---

# 8. PostgreSQL

Utilizar la base de datos existente.

No modificar el esquema de forma innecesaria.

Si se necesita una entidad nueva:

1. Crear solamente los campos necesarios.
2. Crear la entidad correspondiente.
3. Crear repository solamente si realmente se necesita.
4. Conectar la entidad con el flujo principal.

Evitar tablas o relaciones que todavía no tengan utilidad en el MVP.

---

# 9. API REST

Los endpoints deben ser:

- sencillos,
- consistentes,
- fáciles de probar,
- fáciles de explicar durante una presentación.

Usar los métodos HTTP apropiados:

- GET para consultar.
- POST para crear/procesar.
- PUT/PATCH solamente cuando sea necesario.
- DELETE solamente cuando sea necesario.

No crear endpoints duplicados.

---

# 10. Manejo de errores

Implementar únicamente manejo de errores razonable para el MVP.

Como mínimo considerar:

- parámetros inválidos;
- recurso inexistente;
- error de comunicación con GEOGLOWS;
- error inesperado del backend.

No implementar todavía un sistema avanzado de observabilidad, logging distribuido, tracing, etc.

---

# 11. Frontend y PWA de pruebas

La PWA ubicada en `src/main/resources/static/` es **una página mínima de
prueba**. No es el frontend definitivo ni una referencia obligatoria de diseño.

- Hoy solo activa notificaciones y confirma su recepción.
- En el siguiente avance tendrá login/registro, selección múltiple de los cuatro
  ríos y cierre de sesión.
- No mostrar en ella caudales, lluvia, pronósticos, mapas, riesgo, historial ni
  paneles de monitoreo; esas funciones pertenecen al frontend definitivo.
- La prueba push controlada se lanzará desde la consola administrativa o
  mediante el endpoint administrativo con JWT. `X-Admin-Token` se retirará.

El **frontend definitivo** lo desarrollará otro equipo desde cero. Es libre de
elegir diseño, tecnología, estructura y forma de presentar los datos, siempre
que consuma la API pública del backend y respete sus contratos. No se debe
reutilizar la PWA mínima como base obligatoria ni extraer lógica desde
`ConsolaDemoRunner`.

La prioridad del backend es que exista un flujo demostrable y contratos claros.

---

# 12. Flujo mínimo esperado

El sistema debería ser capaz de demostrar un flujo parecido a:

Usuario
→ solicita información
→ backend
→ consulta/procesa datos de GEOGLOWS
→ backend transforma la información
→ devuelve respuesta
→ frontend muestra el resultado.

Si alguna parte del flujo ya existe, reutilizarla.

---

# 13. Testing

Después de realizar cambios importantes:

- compilar el backend;
- ejecutar las pruebas existentes;
- verificar los endpoints principales;
- corregir errores de compilación;
- comprobar que los cambios no rompan funcionalidades existentes.

No crear decenas de pruebas para cada clase.

Priorizar pruebas del flujo principal.

---

# 14. Git

IMPORTANTE:

No eliminar ni sobrescribir trabajo existente de otros integrantes.

Antes de modificar archivos importantes:

- inspeccionar el estado actual;
- reutilizar lo existente;
- preservar cambios de otros miembros.

No ejecutar comandos destructivos como:

- `git reset --hard`
- `git clean -fd`
- eliminar ramas
- sobrescribir trabajo ajeno

a menos que se solicite explícitamente.

No hacer commits automáticamente a menos que se solicite.

---

# 15. Alcance

Para este avance, prioriza:

### Obligatorio
- Backend funcional.
- Integración con GEOGLOWS.
- Endpoints principales.
- Persistencia necesaria.
- Login/registro y roles simples `USER`/`ADMIN`.
- Catálogo de los cuatro ríos con geometría.
- Selección múltiple de ríos y push por usuario.
- Consola con login y menús por rol.
- PWA mínima con login y selección de ríos.
- Flujo principal funcionando.
- Compilación correcta.

### Secundario
- Validaciones adicionales.
- Mejoras pequeñas de estructura.
- Mejor manejo de errores.

### NO prioritario
- Construir el frontend definitivo: es responsabilidad de otro equipo.
- Recuperación de contraseña, verificación por correo o refresh tokens.
- Roles complejos o permisos granulares.
- Analytics.
- Optimización avanzada.
- Dockerización si todavía no es necesaria.
- CI/CD.
- Refactorizaciones grandes.

Si una funcionalidad no contribuye al avance actual, no la implementes.

---

# 16. Regla para código generado

Puedes generar código directamente cuando sea necesario.

Sin embargo:

- reutiliza nombres y estructuras existentes;
- sigue el estilo del proyecto;
- no inventes APIs;
- no agregues dependencias innecesarias;
- no reemplaces una implementación funcional por otra solamente por preferencia.

Todo código nuevo debe poder ser explicado posteriormente por el estudiante.

Prioriza soluciones simples y transparentes.

---

# 17. Cuando encuentres problemas

Si encuentras un error:

1. Identifica la causa concreta.
2. Corrige la causa.
3. Ejecuta nuevamente la compilación o prueba correspondiente.
4. Continúa.

No conviertas un error pequeño en una refactorización completa.

Si existe una solución sencilla y otra mucho más compleja, utiliza la sencilla.

---

# 18. Comunicación

Durante la implementación, mantén las respuestas breves.

Formato preferido:

- qué se cambió;
- qué falta;
- si compila / funciona;
- cualquier bloqueo real.

No necesito explicaciones extensas sobre cada archivo modificado.

---

# 19. Criterio de finalización

Considera terminada una tarea cuando:

- el código compila;
- el flujo correspondiente funciona;
- no existen errores evidentes relacionados con el cambio;
- la implementación cumple el requisito solicitado.

No continuar mejorando indefinidamente una funcionalidad que ya funciona.

---

# 20. Prioridad absoluta del avance

En caso de duda:

**FUNCIONAMIENTO > COMPLEJIDAD**

**ENTREGA > FEATURES EXTRAS**

**REUTILIZAR > REESCRIBIR**

**SOLUCIÓN SIMPLE > ARQUITECTURA ELEGANTE**

**AVANCE ACTUAL FUNCIONAL > 100 % INCOMPLETO**

El objetivo es producir un avance funcional y demostrable del proyecto sin sobreingeniería ni consumo innecesario de tiempo o tokens.
```
