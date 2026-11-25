/*
 * Service Worker de notificaciones push del frontend React.
 *
 * No guarda datos ni caché de la aplicación. Solo inicializa Firebase
 * Messaging con la configuración pública recibida por query string, muestra
 * las notificaciones data-only y abre la página al pulsarlas.
 */

self.addEventListener('install', () => {
    self.skipWaiting();
});

self.addEventListener('activate', (evento) => {
    evento.waitUntil((async () => {
        // Limpia la caché de versiones anteriores de la PWA.
        const nombres = await caches.keys();
        await Promise.all(
            nombres
                .filter((nombre) => nombre.startsWith('alerta-shell-'))
                .map((nombre) => caches.delete(nombre))
        );
        await self.clients.claim();
    })());
});

self.addEventListener('message', (evento) => {
    const mensaje = evento.data || {};
    if (mensaje.tipo === 'SKIP_WAITING') {
        self.skipWaiting();
        return;
    }
    // Cuando la pestaña está en primer plano, FCM entrega el push a la página.
    // Ella reenvía el contenido aquí para que la notificación del sistema la
    // muestre el service worker, igual que cuando está en segundo plano.
    if (mensaje.tipo === 'MOSTRAR_NOTIFICACION') {
        evento.waitUntil(mostrarNotificacion(mensaje.datos || {}));
    }
});

/* -------------------------------------------------------------------------
 * Firebase Messaging
 * ---------------------------------------------------------------------- */

const parametros = new URL(self.location.href).searchParams;
const configuracion = {
    apiKey: parametros.get('apiKey') || '',
    authDomain: parametros.get('authDomain') || '',
    projectId: parametros.get('projectId') || '',
    messagingSenderId: parametros.get('messagingSenderId') || '',
    appId: parametros.get('appId') || ''
};
const configuracionCompleta = Object.values(configuracion).every((valor) => Boolean(valor));

if (configuracionCompleta) {
    try {
        importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js');
        importScripts('https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging-compat.js');
        if (typeof firebase !== 'undefined' && firebase.messaging) {
            firebase.initializeApp(configuracion);
            const messaging = firebase.messaging();
            messaging.onBackgroundMessage((payload) => {
                // El backend envía mensajes data-only. Si llegara un bloque
                // "notification", FCM ya lo mostraría y duplicarlo sería un error.
                if (payload && payload.notification) {
                    return;
                }
                return mostrarNotificacion((payload && payload.data) || {});
            });
        }
    } catch (error) {
        console.warn('No se pudo inicializar Firebase Messaging en el Service Worker:', error);
    }
}

async function mostrarNotificacion(datos) {
    const vibrar = String(datos.vibrar) === 'true';
    const opciones = {
        body: datos.body || 'Notificación de prueba del sistema de alertas.',
        icon: datos.icon || '/icons/icon-192.png',
        badge: datos.badge || '/icons/badge-72.png',
        tag: datos.tag || 'prueba-push',
        data: {
            url: datos.url || '/',
            eventoId: datos.eventoId || ''
        },
        requireInteraction: String(datos.requiereInteraccion) === 'true',
        timestamp: Number(datos.timestamp) || Date.now()
    };
    if (vibrar) {
        opciones.vibrate = [200, 100, 200, 100, 400];
    }
    // Cada simulacro reutiliza el mismo tag por río. Sin renotify, el
    // navegador reemplaza la notificación anterior en silencio y el usuario
    // cree que no llegó nada.
    opciones.renotify = true;
    return self.registration.showNotification(
        datos.title || 'Notificación de prueba',
        opciones
    );
}

/* -------------------------------------------------------------------------
 * Clic en la notificación
 * ---------------------------------------------------------------------- */

self.addEventListener('notificationclick', (evento) => {
    evento.notification.close();
    const datos = evento.notification.data || {};
    const destino = construirDestino(datos.url);

    evento.waitUntil((async () => {
        const ventanas = await self.clients.matchAll({
            type: 'window',
            includeUncontrolled: true
        });

        for (const ventana of ventanas) {
            try {
                if (new URL(ventana.url).origin !== self.location.origin) {
                    continue;
                }
                if (typeof ventana.navigate === 'function') {
                    await ventana.navigate(destino);
                }
                await ventana.focus();
                return;
            } catch {
                // Se intenta con la siguiente ventana o se abre una nueva.
            }
        }

        await self.clients.openWindow(destino);
    })());
});

function construirDestino(urlNotificacion) {
    try {
        const destino = new URL(urlNotificacion || '/', self.location.origin);
        if (destino.origin !== self.location.origin) {
            return `${self.location.origin}/`;
        }
        return destino.href;
    } catch {
        return `${self.location.origin}/`;
    }
}
