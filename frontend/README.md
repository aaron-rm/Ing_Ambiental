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

## Pantallas

- `/login`: iniciar sesión y registro (`/api/auth/login`, `/api/auth/registro`).
- `/`: mapa con los ríos resaltados (`/api/rios/mapa`), buscador y panel con el estado (`/api/alertas/rio/{rioId}`).
- `/mis-rios`: ríos seguidos (`/api/usuarios/me/rios`) con su estado y pronóstico.

## Pendiente

- Registro del dispositivo para notificaciones push (Firebase, `/api/push/*`) y Service Worker/PWA.
- Panel de administración.
