```md
# GEOGLOWS — Project Instructions

## 1. Contexto del proyecto

Este proyecto es un sistema de alerta temprana relacionado con inundaciones que utiliza datos de GEOGLOWS.

Stack principal:

- Backend: Java + Spring Boot
- Base de datos: PostgreSQL
- Frontend: React / PWA
- Fuente de datos: GEOGLOWS API

El proyecto es académico y actualmente existe una fecha de entrega cercana.

## 2. Objetivo actual

El objetivo inmediato es dejar listo un **avance funcional equivalente aproximadamente al 50 % del proyecto**.

La prioridad actual es:

1. Backend funcional.
2. Integración con GEOGLOWS.
3. Persistencia en PostgreSQL cuando sea necesaria.
4. Endpoints funcionales.
5. Manejo básico de errores.
6. Probar que el flujo principal funciona.

No intentar completar características futuras que no sean necesarias para este avance.

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

# 11. Frontend

El frontend NO es la prioridad principal para este avance.

No invertir una gran cantidad de tiempo en:

- diseño visual avanzado,
- animaciones,
- responsive perfecto,
- componentes complejos,
- optimización avanzada.

Solamente crear o mantener el frontend necesario para demostrar que el backend funciona.

La prioridad es que exista un flujo demostrable.

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
- Flujo principal funcionando.
- Compilación correcta.

### Secundario
- Validaciones adicionales.
- Mejoras pequeñas de estructura.
- Mejor manejo de errores.

### NO prioritario
- Diseño avanzado del frontend.
- Notificaciones push completas.
- Autenticación avanzada.
- Roles complejos.
- Analytics.
- Optimización avanzada.
- Dockerización si todavía no es necesaria.
- CI/CD.
- Refactorizaciones grandes.

Si una funcionalidad no contribuye al 50 % de avance actual, no la implementes.

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

# 20. Prioridad absoluta esta noche

En caso de duda:

**FUNCIONAMIENTO > COMPLEJIDAD**

**ENTREGA > FEATURES EXTRAS**

**REUTILIZAR > REESCRIBIR**

**SOLUCIÓN SIMPLE > ARQUITECTURA ELEGANTE**

**50 % FUNCIONAL > 100 % INCOMPLETO**

El objetivo es producir un avance funcional y demostrable del proyecto sin sobreingeniería ni consumo innecesario de tiempo o tokens.
```
