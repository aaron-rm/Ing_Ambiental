# Frontend - Alerta de ríos de Panamá

React + Vite + Leaflet. Mobile-first: en celular el panel del río es una hoja inferior; en escritorio queda a la derecha.

## Cómo correrlo

1. Levanta el backend (puerto 8081) con sus variables de entorno.
2. En esta carpeta:

```
npm install
npm run dev
```

3. Abre http://localhost:5173 y crea una cuenta (usuario + contraseña de 8 o más caracteres).

Vite reenvía `/api` al backend (`vite.config.js`), así que no hace falta CORS en desarrollo.
Si el backend usa otro puerto: `VITE_BACKEND=http://localhost:PUERTO npm run dev`.

Para probar desde un teléfono en la misma red:

```
npm run dev -- --host
```

y abrir `http://IP_DE_LA_LAPTOP:5173` en el teléfono. El proxy elimina el
header `Origin`, por lo que el backend no rechaza la petición por CORS aunque
la IP no esté en `ALERTA_CORS_ORIGENES`. El teléfono solo necesita alcanzar el
puerto 5173 de la laptop; el backend puede seguir escuchando en localhost.

## Pantallas

- `/login`: iniciar sesión y registro (`/api/auth/login`, `/api/auth/registro`).
- `/`: mapa con los ríos resaltados (`/api/rios/mapa`), buscador y panel con el estado (`/api/alertas/rio/{rioId}`).
- `/mis-rios`: ríos seguidos (`/api/usuarios/me/rios`) con su estado y pronóstico.

## Simulación de crecida (solo ADMIN)

Con una cuenta `ADMIN`, el panel de cada río muestra el botón **Simular crecida
y notificar**. Pide confirmación y llama a
`POST /api/alertas/simular?reachId=...`: el backend calcula una crecida de
emergencia y envía el push a los dispositivos activos de los usuarios que
siguen ese río. El panel indica cuántos dispositivos se notificaron.

El registro de dispositivos sigue en la PWA de pruebas
(`http://localhost:8081`): inicia sesión con la misma cuenta, sigue el río y
pulsa **Activar notificaciones** en cada dispositivo antes de simular. En el
teléfono hace falta HTTPS (túnel); ver `docs/GUIA_PRUEBA_NOTIFICACIONES.md`.

## Pendiente

- Integrar el registro push y el Service Worker en esta app React (hoy se hace en la PWA de 8081).
- Panel de administración completo (el botón de simulación ya está).
