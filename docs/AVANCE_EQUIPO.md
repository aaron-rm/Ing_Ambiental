# Avance del proyecto para el equipo

**23/10/2026 — el inicio de sesión, el catálogo con mapa y las notificaciones por río ya están implementados.**

## Los cuatro ríos

El catálogo quedó cerrado en **Río Abajo, Curundú, Juan Díaz y Tocumen**.
No incluimos Matasnillo, Matías Hernández ni Tapia porque no tienen un tramo
propio de pronóstico o duplican el de otro río.

## Cómo funciona ahora

- La consola y la PWA piden iniciar sesión o registrarse.
- Cada usuario ve los cuatro ríos y puede hacer clic en cualquiera para abrir un
  panel con su caudal y la lluvia prevista, esté o no suscrito.
- El botón de seguir solo sirve para recibir notificaciones si ese río supera
  los niveles de alerta.
- El usuario puede seguir varios ríos.
- La cuenta administradora de demostración se configura localmente y no se
  publica.
- El usuario normal solo ve las opciones de consulta y selección; el
  administrador ve además simulación, evaluación real y push de demostración.

## Lo que se retiró del flujo

- Las suscripciones por correo o teléfono.
- La creación o edición de ríos desde la interfaz (quedó solo para
  administración).
- La calibración de umbrales por temporada sigue para después.

## Siguiente paso

Probar con PostgreSQL real y validar la recepción del push en un celular. Para
retomar el trabajo, leer [`CONTINUIDAD_SESION.md`](CONTINUIDAD_SESION.md).
