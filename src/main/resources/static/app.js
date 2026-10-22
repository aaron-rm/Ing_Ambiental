'use strict';

/*
 * Página mínima de prueba de notificaciones.
 * Solo comprueba permiso, registra el Service Worker, obtiene el token FCM y
 * confirma la suscripción con el backend. No consulta datos de ríos, riesgo,
 * precipitación ni historial.
 */

const VERSION_SW = '5';
const URL_FIREBASE_APP = 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
const URL_FIREBASE_MESSAGING = 'https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging.js';
const NOMBRE_APP_FIREBASE = 'alerta-inundaciones-prueba';

const CLAVES = {
    deviceId: 'alerta.deviceId.v1',
    token: 'alerta.pushToken.v1',
    registrado: 'alerta.pushRegistrado.v1'
};

const estado = {
    config: null,
    sw: null,
    modulosFirebase: null,
    messaging: null,
    oyenteFcm: false,
    activando: false
};

document.addEventListener('DOMContentLoaded', iniciar);

async function iniciar() {
    const boton = elemento('activarBoton');
    if (boton) {
        boton.addEventListener('click', alPulsarBoton);
    }

    const soporte = comprobarSoporte();
    if (!soporte.ok) {
        pintarEstado('error', soporte.estado);
        mostrarMensaje(soporte.mensaje, 'error');
        deshabilitarBoton('No disponible');
        return;
    }

    if (Notification.permission === 'denied') {
        pintarEstado('error', 'Bloqueadas');
        mostrarMensaje('El permiso de notificaciones está bloqueado. Revisa los permisos '
            + 'del sitio en el navegador y vuelve a intentarlo.', 'error');
        deshabilitarBoton('Notificaciones bloqueadas');
        return;
    }

    await cargarConfiguracion();

    const parametrosApertura = new URLSearchParams(location.search);
    if (parametrosApertura.has('alerta')) {
        mostrarMensaje('Notificación abierta correctamente.', 'exito');
    } else if (parametrosApertura.has('demo') || parametrosApertura.has('prueba')) {
        mostrarMensaje('Notificación de prueba abierta correctamente.', 'exito');
    }
}

async function alPulsarBoton() {
    if (!estado.config || !estado.config.habilitado) {
        await cargarConfiguracion();
        return;
    }
    await activarNotificaciones();
}

function comprobarSoporte() {
    const esLocal = location.hostname === 'localhost' || location.hostname === '127.0.0.1';
    if (!window.isSecureContext && !esLocal) {
        return {
            ok: false,
            estado: 'No disponible',
            mensaje: 'Esta página debe abrirse por HTTPS para poder usar notificaciones. '
                + 'En el celular usa la URL HTTPS del túnel, no la IP local.'
        };
    }
    if (!('serviceWorker' in navigator)) {
        return {
            ok: false,
            estado: 'No compatible',
            mensaje: 'Este navegador no admite Service Workers, necesarios para las notificaciones.'
        };
    }
    if (!('Notification' in window)) {
        return {
            ok: false,
            estado: 'No compatible',
            mensaje: 'Este navegador no admite notificaciones web.'
        };
    }
    if (!('PushManager' in window)) {
        return {
            ok: false,
            estado: 'No compatible',
            mensaje: 'Este navegador no admite notificaciones push.'
        };
    }
    return { ok: true };
}

async function cargarConfiguracion() {
    pintarEstado('comprobando', 'Comprobando…');
    limpiarMensaje();
    try {
        const config = await pedirJson('/api/push/config', {}, 15000);
        estado.config = config;

        if (!config || !config.habilitado) {
            pintarEstado('error', 'No disponibles');
            mostrarMensaje((config && config.motivo)
                || 'Las notificaciones no están habilitadas en el servidor.', 'error');
            deshabilitarBoton('No disponible');
            return;
        }
        if (!config.reachId) {
            pintarEstado('error', 'No disponibles');
            mostrarMensaje('El servidor no tiene un tramo de prueba configurado.', 'error');
            deshabilitarBoton('No disponible');
            return;
        }

        const token = tokenGuardado();
        const registrado = registradoGuardado();
        if (Notification.permission === 'granted' && token && registrado === token) {
            pintarEstado('activado', 'Activadas');
            habilitarBoton('Notificaciones activadas', true);
            return;
        }

        if (Notification.permission === 'granted') {
            pintarEstado('disponible', 'Permiso concedido; falta activar');
        } else {
            pintarEstado('disponible', 'Pendientes de autorización');
        }
        habilitarBoton('Activar notificaciones', false);
    } catch (error) {
        pintarEstado('error', 'No disponibles');
        mostrarMensaje(`No se pudo comprobar la configuración: ${mensajeError(error)}`, 'error');
        habilitarBoton('Reintentar', false);
    }
}

async function activarNotificaciones() {
    if (estado.activando) {
        return;
    }

    const soporte = comprobarSoporte();
    if (!soporte.ok) {
        pintarEstado('error', soporte.estado);
        mostrarMensaje(soporte.mensaje, 'error');
        deshabilitarBoton('No disponible');
        return;
    }

    limpiarMensaje();
    estado.activando = true;
    habilitarBoton('Activando…', true);

    try {
        let permiso = Notification.permission;
        if (permiso === 'default') {
            permiso = await Notification.requestPermission();
        }
        if (permiso === 'denied') {
            pintarEstado('error', 'Bloqueadas');
            mostrarMensaje('No se concedió el permiso. Revisa los permisos del sitio en el '
                + 'navegador y vuelve a intentarlo.', 'error');
            deshabilitarBoton('Notificaciones bloqueadas');
            return;
        }
        if (permiso !== 'granted') {
            pintarEstado('error', 'Pendientes de autorización');
            mostrarMensaje('El permiso sigue pendiente. Pulsa de nuevo el botón y acepta '
                + 'la solicitud del navegador.', 'error');
            habilitarBoton('Activar notificaciones', false);
            return;
        }

        if (!estado.config || !estado.config.habilitado) {
            await cargarConfiguracion();
            if (!estado.config || !estado.config.habilitado) {
                return;
            }
        }

        const registro = await registrarServiceWorker(estado.config);
        const token = await obtenerTokenFcm(estado.config, registro);

        const yaRegistrado = tokenGuardado() === token && registradoGuardado() === token;
        if (!yaRegistrado) {
            await registrarEnBackend(token, estado.config);
            guardarToken(token);
            guardarRegistrado(token);
        }

        pintarEstado('activado', 'Activadas');
        mostrarMensaje(yaRegistrado
            ? 'Las notificaciones ya estaban activadas en este dispositivo.'
            : 'Notificaciones activadas y confirmadas por el servidor. '
                + 'Puedes comprobar la recepción con una notificación de prueba.', 'exito');
        habilitarBoton('Notificaciones activadas', true);
    } catch (error) {
        pintarEstado('error', 'Error');
        mostrarMensaje(mensajeError(error), 'error');
        habilitarBoton('Reintentar', false);
    } finally {
        estado.activando = false;
    }
}

async function registrarServiceWorker(config) {
    const parametros = new URLSearchParams();
    parametros.set('v', VERSION_SW);
    ['apiKey', 'authDomain', 'projectId', 'messagingSenderId', 'appId'].forEach((clave) => {
        parametros.set(clave, config[clave] || '');
    });

    const registro = await navigator.serviceWorker.register(
        `/firebase-messaging-sw.js?${parametros.toString()}`,
        { scope: '/' }
    );
    await navigator.serviceWorker.ready;
    estado.sw = registro;
    return registro;
}

async function obtenerTokenFcm(config, registro) {
    const modulos = await cargarModulosFirebase();
    if (typeof modulos.messaging.isSupported === 'function'
        && !(await modulos.messaging.isSupported())) {
        throw new Error('Firebase Messaging no es compatible con este navegador o contexto.');
    }

    let app;
    try {
        app = modulos.app.getApp(NOMBRE_APP_FIREBASE);
    } catch (error) {
        app = modulos.app.initializeApp({
            apiKey: config.apiKey,
            authDomain: config.authDomain,
            projectId: config.projectId,
            messagingSenderId: config.messagingSenderId,
            appId: config.appId
        }, NOMBRE_APP_FIREBASE);
    }

    const messaging = modulos.messaging.getMessaging(app);
    if (!estado.oyenteFcm) {
        modulos.messaging.onMessage(messaging, manejarMensajeForeground);
        estado.oyenteFcm = true;
    }
    estado.messaging = messaging;

    const token = await modulos.messaging.getToken(messaging, {
        vapidKey: config.vapidKey,
        serviceWorkerRegistration: registro
    });
    if (!token) {
        throw new Error('Firebase no devolvió un token para este dispositivo.');
    }
    return token;
}

async function cargarModulosFirebase() {
    if (estado.modulosFirebase) {
        return estado.modulosFirebase;
    }
    try {
        const [app, messaging] = await Promise.all([
            import(URL_FIREBASE_APP),
            import(URL_FIREBASE_MESSAGING)
        ]);
        estado.modulosFirebase = { app, messaging };
        return estado.modulosFirebase;
    } catch (error) {
        throw new Error('No se pudo cargar el SDK de Firebase. Revisa la conexión '
            + 'o desactiva el bloqueador de contenido para este sitio.');
    }
}

async function registrarEnBackend(token, config) {
    const respuesta = await pedirJson('/api/push/registrar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            token,
            deviceId: obtenerDeviceId(),
            reachId: config.reachId,
            plataforma: 'web'
        })
    }, 20000);

    if (!respuesta || respuesta.activo !== true) {
        throw new Error((respuesta && respuesta.mensaje)
            || 'El servidor no confirmó la suscripción.');
    }
}

function manejarMensajeForeground(payload) {
    const datos = (payload && payload.data) || {};
    mostrarMensaje('Notificación recibida correctamente en este dispositivo.', 'exito');

    if (!estado.sw) {
        return;
    }
    const opciones = {
        body: datos.body || 'Notificación de prueba del sistema de alertas.',
        icon: datos.icon || '/icons/icon-192.png',
        badge: datos.badge || '/icons/badge-72.png',
        tag: datos.tag || 'prueba-push',
        data: { url: datos.url || '/' }
    };
    estado.sw.showNotification(datos.title || 'Notificación de prueba', opciones)
        .catch((error) => console.warn('No se pudo mostrar la notificación local.', error));
}

async function pedirJson(url, opciones = {}, tiempoMaximo = 20000) {
    const controlador = new AbortController();
    const temporizador = setTimeout(() => controlador.abort(), tiempoMaximo);
    let respuesta;
    try {
        respuesta = await fetch(url, {
            ...opciones,
            signal: controlador.signal,
            headers: {
                Accept: 'application/json',
                ...(opciones.headers || {})
            }
        });
    } catch (error) {
        if (error.name === 'AbortError') {
            throw new Error('El servidor tardó demasiado en responder.');
        }
        throw new Error('No se pudo conectar con el servidor.');
    } finally {
        clearTimeout(temporizador);
    }

    const texto = await respuesta.text();
    let datos = null;
    if (texto) {
        try {
            datos = JSON.parse(texto);
        } catch (error) {
            datos = texto;
        }
    }

    if (!respuesta.ok) {
        let mensaje = typeof datos === 'string'
            ? datos
            : (datos && (datos.message || datos.error));
        if (typeof mensaje === 'string') {
            const limpio = mensaje.trim();
            // Un proxy o servidor mal configurado puede devolver HTML; no se
            // muestra el documento completo al usuario.
            mensaje = limpio.startsWith('<')
                ? null
                : (limpio.length > 300 ? `${limpio.slice(0, 300)}…` : limpio);
        }
        const fallo = new Error(mensaje || `El servidor respondió con el código ${respuesta.status}.`);
        fallo.status = respuesta.status;
        throw fallo;
    }
    return datos;
}

function obtenerDeviceId() {
    let deviceId = leerLocal(CLAVES.deviceId);
    if (deviceId) {
        return deviceId;
    }
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
        deviceId = window.crypto.randomUUID();
    } else {
        deviceId = `dev-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
    }
    escribirLocal(CLAVES.deviceId, deviceId);
    return deviceId;
}

function tokenGuardado() {
    return leerLocal(CLAVES.token);
}

function guardarToken(token) {
    escribirLocal(CLAVES.token, token);
}

function registradoGuardado() {
    return leerLocal(CLAVES.registrado);
}

function guardarRegistrado(token) {
    escribirLocal(CLAVES.registrado, token);
}

function leerLocal(clave) {
    try {
        return localStorage.getItem(clave);
    } catch (error) {
        return null;
    }
}

function escribirLocal(clave, valor) {
    try {
        localStorage.setItem(clave, valor);
    } catch (error) {
        console.warn('No se pudo guardar el estado local.', error);
    }
}

function pintarEstado(tipo, texto) {
    const contenedor = elemento('estado');
    const textoEstado = elemento('estadoTexto');
    if (contenedor) {
        contenedor.dataset.estado = tipo;
    }
    if (textoEstado) {
        textoEstado.textContent = texto;
    }
}

function mostrarMensaje(texto, tipo) {
    const mensaje = elemento('mensaje');
    if (!mensaje) {
        return;
    }
    mensaje.textContent = texto;
    mensaje.dataset.tipo = tipo;
    mensaje.hidden = false;
}

function limpiarMensaje() {
    const mensaje = elemento('mensaje');
    if (!mensaje) {
        return;
    }
    mensaje.textContent = '';
    mensaje.hidden = true;
    delete mensaje.dataset.tipo;
}

function habilitarBoton(texto, deshabilitado) {
    const boton = elemento('activarBoton');
    if (!boton) {
        return;
    }
    boton.textContent = texto;
    boton.disabled = Boolean(deshabilitado);
}

function deshabilitarBoton(texto) {
    habilitarBoton(texto, true);
}

function elemento(id) {
    return document.getElementById(id);
}

function mensajeError(error) {
    if (!error) {
        return 'Ocurrió un error inesperado.';
    }
    if (error.status === 400) {
        return `Datos rechazados por el servidor: ${error.message}`;
    }
    if (error.status === 403) {
        return 'El servidor rechazó la operación. Revisa la configuración administrativa.';
    }
    if (error.status === 409) {
        return error.message || 'La suscripción entra en conflicto con otro dispositivo.';
    }
    if (error.status === 410) {
        return 'La suscripción anterior ya no es válida. Vuelve a activar las notificaciones.';
    }
    return error.message || 'No se pudo completar la operación.';
}
