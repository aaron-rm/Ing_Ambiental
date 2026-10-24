'use strict';

/*
 * PWA mínima de prueba del sistema de alertas.
 *
 * Flujo:
 *   1. Iniciar sesión o registrarse.
 *   2. Activar las notificaciones del dispositivo.
 *   3. Suscribirse a uno o varios ríos del catálogo.
 *   4. Si el usuario es ADMIN, simular una crecida de un río seguido y
 *      comprobar que el push llega a los dispositivos suscritos.
 *
 * No muestra caudal, lluvia, hidrogramas ni recomendaciones. Toda la lógica
 * hidrológica y de riesgo vive en el backend.
 */

const VERSION_SW = '8';
const URL_FIREBASE_APP = 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js';
const URL_FIREBASE_MESSAGING = 'https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging.js';
const NOMBRE_APP_FIREBASE = 'alerta-inundaciones-prueba';

const CLAVES = {
    token: 'alerta.jwt.v1',
    usuario: 'alerta.usuario.v1',
    deviceId: 'alerta.deviceId.v1',
    pushToken: 'alerta.pushToken.v1'
};

const estado = {
    token: leerLocal(CLAVES.token),
    usuario: leerJsonLocal(CLAVES.usuario),
    rios: [],
    seguidos: new Set(),
    configPush: null,
    sw: null,
    modulosFirebase: null,
    messaging: null,
    oyenteFcm: false,
    activandoPush: false
};

document.addEventListener('DOMContentLoaded', iniciar);

async function iniciar() {
    el('formLogin').addEventListener('submit', alEntrar);
    el('botonRegistrar').addEventListener('click', alRegistrar);
    el('botonSalir').addEventListener('click', alSalir);
    el('botonPush').addEventListener('click', alPulsarPush);
    if (el('avisoPush')) {
        el('avisoPush').addEventListener('click', ocultarAvisoPush);
    }

    if (!estado.token) {
        mostrarLogin();
        mostrarAvisoNotificacion('mensajeLogin');
        return;
    }

    try {
        const usuario = await api('/api/auth/me');
        estado.usuario = usuario;
        guardarSesion();
        await mostrarApp();
        mostrarAvisoNotificacion('mensajeApp');
    } catch (error) {
        limpiarSesion();
        mostrarLogin();
        if (error.status !== 401) {
            mostrarMensaje('mensajeLogin', mensajeError(error), 'error');
        }
    }
}

function mostrarAvisoNotificacion(idMensaje) {
    const parametros = new URLSearchParams(location.search);
    if (parametros.has('simulacion')) {
        mostrarMensaje(idMensaje, 'Notificación de simulación recibida correctamente.', 'exito');
    } else if (parametros.has('alerta')) {
        mostrarMensaje(idMensaje, 'Notificación de alerta recibida correctamente.', 'exito');
    } else if (parametros.has('demo')) {
        mostrarMensaje(idMensaje, 'Notificación de demostración recibida correctamente.', 'exito');
    }
}

function mostrarLogin() {
    el('vistaLogin').hidden = false;
    el('vistaApp').hidden = true;
}

async function mostrarApp() {
    el('vistaLogin').hidden = true;
    el('vistaApp').hidden = false;
    const rol = String((estado.usuario && estado.usuario.rol) || '').toUpperCase();
    el('usuarioActual').textContent = `${estado.usuario.identificador} · ${rol}`;

    await cargarCatalogo();
    await cargarSeguidos();
    dibujarListaRios();
    await cargarConfigPush();
    dibujarSimulacion();
    await reanudarPushGuardado();
}

/* ------------------------------- Sesión -------------------------------- */

async function alEntrar(evento) {
    evento.preventDefault();
    const identificador = el('identificador').value.trim();
    const password = el('password').value;
    if (!identificador || !password) {
        mostrarMensaje('mensajeLogin', 'Escribe tu identificador y tu contraseña.', 'error');
        return;
    }
    await autenticar('/api/auth/login', identificador, password, 'Entrar');
}

async function alRegistrar() {
    const identificador = el('identificador').value.trim();
    const password = el('password').value;
    if (!identificador || password.length < 8) {
        mostrarMensaje('mensajeLogin',
            'Para crear la cuenta usa un identificador y una contraseña de al menos 8 caracteres.',
            'error');
        return;
    }
    await autenticar('/api/auth/registro', identificador, password, 'Crear cuenta');
}

async function autenticar(ruta, identificador, password, etiqueta) {
    const boton = etiqueta === 'Entrar' ? el('botonEntrar') : el('botonRegistrar');
    boton.disabled = true;
    limpiarMensaje('mensajeLogin');
    try {
        const sesion = await api(ruta, {
            method: 'POST',
            body: { identificador, password }
        });
        estado.token = sesion.token;
        estado.usuario = sesion.usuario;
        guardarSesion();
        el('password').value = '';
        await mostrarApp();
    } catch (error) {
        mostrarMensaje('mensajeLogin', mensajeError(error), 'error');
    } finally {
        boton.disabled = false;
    }
}

async function alSalir() {
    await desactivarDispositivo();
    ocultarAvisoPush();
    limpiarSesion();
    estado.rios = [];
    estado.seguidos = new Set();
    estado.configPush = null;
    el('listaRios').innerHTML = '';
    el('listaSimulacion').innerHTML = '';
    el('bloqueAdmin').hidden = true;
    el('botonPush').disabled = true;
    el('botonPush').textContent = 'Activar notificaciones';
    limpiarMensaje('mensajeApp');
    limpiarMensaje('mensajePush');
    limpiarMensaje('mensajeAdmin');
    mostrarLogin();
}

function guardarSesion() {
    escribirLocal(CLAVES.token, estado.token);
    escribirJsonLocal(CLAVES.usuario, estado.usuario);
}

function limpiarSesion() {
    estado.token = null;
    estado.usuario = null;
    try {
        localStorage.removeItem(CLAVES.token);
        localStorage.removeItem(CLAVES.usuario);
    } catch (error) {
        console.warn('No se pudo limpiar la sesión local.', error);
    }
}

/* ------------------------------- Catálogo ------------------------------ */

async function cargarCatalogo() {
    try {
        const rios = await api('/api/rios');
        estado.rios = Array.isArray(rios) ? rios : [];
    } catch (error) {
        estado.rios = [];
        manejarErrorApp(error);
    }
}

async function cargarSeguidos() {
    try {
        const seguidos = await api('/api/usuarios/me/rios');
        estado.seguidos = new Set((seguidos || []).map((rio) => rio.rioId));
    } catch (error) {
        estado.seguidos = new Set();
        manejarErrorApp(error);
    }
}

function dibujarListaRios() {
    const lista = el('listaRios');
    lista.innerHTML = '';
    if (estado.rios.length === 0) {
        lista.appendChild(crear('li', 'ayuda', 'No se pudo cargar el catálogo de ríos.'));
        return;
    }

    estado.rios.forEach((rio) => {
        const seguido = estado.seguidos.has(rio.rioId);
        const item = crear('li', 'rio-item');

        const datos = crear('div', 'rio-datos');
        datos.appendChild(crear('strong', null, rio.nombre));
        datos.appendChild(crear('span', 'rio-detalle',
            seguido ? 'Recibirás sus notificaciones.' : 'Sin suscripción.'));

        const acciones = crear('div', 'rio-acciones');
        const boton = crear('button', 'button compacto' + (seguido ? ' secundario' : ''),
            seguido ? 'Dejar de seguir' : 'Suscribirse');
        boton.type = 'button';
        boton.addEventListener('click', () => {
            if (seguido) {
                dejarDeSeguir(rio, boton);
            } else {
                suscribirse(rio, boton);
            }
        });
        acciones.appendChild(boton);

        item.appendChild(datos);
        item.appendChild(acciones);
        lista.appendChild(item);
    });
}

async function suscribirse(rio, boton) {
    boton.disabled = true;
    boton.textContent = 'Suscribiendo…';
    limpiarMensaje('mensajeApp');

    // Suscribirse intenta activar el push en este dispositivo. Si el
    // navegador no lo permite, la suscripción se guarda igual y se avisa.
    const pushActivo = await asegurarNotificaciones();

    try {
        const respuesta = await api(`/api/usuarios/me/rios/${rio.rioId}`, { method: 'POST' });
        estado.seguidos.add(rio.rioId);
        dibujarListaRios();
        dibujarSimulacion();
        mostrarMensaje('mensajeApp',
            pushActivo
                ? (respuesta.mensaje || `Te suscribiste a ${rio.nombre}.`)
                : `Te suscribiste a ${rio.nombre}, pero las notificaciones no están activas en este dispositivo.`,
            pushActivo ? 'exito' : 'error');
    } catch (error) {
        boton.disabled = false;
        boton.textContent = 'Suscribirse';
        manejarErrorApp(error);
    }
}

async function dejarDeSeguir(rio, boton) {
    boton.disabled = true;
    boton.textContent = 'Quitando…';
    limpiarMensaje('mensajeApp');
    try {
        await api(`/api/usuarios/me/rios/${rio.rioId}`, { method: 'DELETE' });
        estado.seguidos.delete(rio.rioId);
        dibujarListaRios();
        dibujarSimulacion();
        mostrarMensaje('mensajeApp',
            `Dejaste de seguir ${rio.nombre}. Ya no recibirás sus notificaciones.`, 'exito');
    } catch (error) {
        boton.disabled = false;
        boton.textContent = 'Dejar de seguir';
        manejarErrorApp(error);
    }
}

/* --------------------------- Simulación admin -------------------------- */

function esAdmin() {
    return String((estado.usuario && estado.usuario.rol) || '').toUpperCase() === 'ADMIN';
}

function dibujarSimulacion() {
    const bloque = el('bloqueAdmin');
    const lista = el('listaSimulacion');
    bloque.hidden = !esAdmin();
    lista.innerHTML = '';
    if (!esAdmin()) {
        return;
    }

    const seguidos = estado.rios.filter((rio) => estado.seguidos.has(rio.rioId));
    const pushDisponible = !!(estado.configPush && estado.configPush.habilitado);
    if (!pushDisponible) {
        lista.appendChild(crear('li', 'ayuda',
            'Las notificaciones push no están configuradas en el servidor. '
            + 'No se puede enviar el simulacro hasta habilitar Firebase.'));
        return;
    }
    if (seguidos.length === 0) {
        lista.appendChild(crear('li', 'ayuda',
            'Suscríbete a un río para poder simular una crecida sobre él.'));
        return;
    }

    seguidos.forEach((rio) => {
        const item = crear('li', 'rio-item');
        const datos = crear('div', 'rio-datos');
        datos.appendChild(crear('strong', null, rio.nombre));
        datos.appendChild(crear('span', 'rio-detalle',
            'Enviar un simulacro a quienes siguen este río.'));

        const acciones = crear('div', 'rio-acciones');
        const boton = crear('button', 'button compacto', 'Simular crecida');
        boton.type = 'button';
        boton.addEventListener('click', () => simularCrecida(rio, boton));
        acciones.appendChild(boton);

        item.appendChild(datos);
        item.appendChild(acciones);
        lista.appendChild(item);
    });
}

async function simularCrecida(rio, boton) {
    const textoOriginal = boton.textContent;
    boton.disabled = true;
    boton.textContent = 'Enviando…';
    limpiarMensaje('mensajeAdmin');

    try {
        const resultado = await api(
            `/api/alertas/simular?reachId=${encodeURIComponent(rio.reachId)}`,
            { method: 'POST' });
        const dispositivos = Number(resultado.dispositivos || 0);
        const enviados = Number(resultado.enviados || 0);
        const fallidos = Number(resultado.fallidos || 0);

        if (dispositivos === 0) {
            mostrarMensaje('mensajeAdmin',
                `La simulación de ${rio.nombre} se ejecutó, pero no hay dispositivos activos siguiendo ese río. `
                + 'Si cerraste sesión o desactivaste las notificaciones, vuelve a activarlas en este dispositivo.',
                'error');
        } else if (enviados === 0) {
            mostrarMensaje('mensajeAdmin',
                `No se pudo entregar la notificación de ${rio.nombre}. Fallidos: ${fallidos}.`,
                'error');
        } else {
            mostrarMensaje('mensajeAdmin',
                `Simulación de ${rio.nombre} enviada: ${enviados} de ${dispositivos} dispositivos.`
                + (fallidos > 0 ? ` Fallidos: ${fallidos}.` : ''),
                'exito');
        }
    } catch (error) {
        if (error.status === 409) {
            mostrarMensaje('mensajeAdmin',
                'Las notificaciones push no están configuradas en el servidor.', 'error');
        } else if (error.status === 403) {
            mostrarMensaje('mensajeAdmin', esErrorCors(error)
                ? 'El servidor rechazó el origen de esta página (CORS). '
                    + 'Agrega la URL del túnel a ALERTA_CORS_ORIGENES y reinicia el backend.'
                : 'Solo una cuenta ADMIN puede simular crecidas. '
                    + 'Cierra sesión y entra con la cuenta administradora.', 'error');
        } else {
            mostrarMensaje('mensajeAdmin', mensajeError(error), 'error');
        }
    } finally {
        boton.disabled = false;
        boton.textContent = textoOriginal;
    }
}

/* ------------------------- Notificaciones push ------------------------- */

async function cargarConfigPush() {
    const soporte = comprobarSoportePush();
    if (!soporte.ok) {
        estado.configPush = { habilitado: false, motivo: soporte.mensaje };
        pintarEstadoPush('error', soporte.estado);
        mostrarMensaje('mensajePush', soporte.mensaje, 'error');
        actualizarBotonPush();
        return;
    }

    pintarEstadoPush('comprobando', 'Comprobando…');
    try {
        const config = await api('/api/push/config');
        estado.configPush = config;
        if (!config || !config.habilitado) {
            pintarEstadoPush('error', 'No disponibles');
            mostrarMensaje('mensajePush', (config && config.motivo)
                || 'Las notificaciones no están habilitadas en el servidor.', 'error');
        } else if (notificacionesActivas()) {
            pintarEstadoPush('activado', 'Activadas en este dispositivo');
        } else if (Notification.permission === 'denied') {
            pintarEstadoPush('error', 'Bloqueadas por el navegador');
        } else {
            pintarEstadoPush('disponible', 'Pendientes de autorización');
        }
        actualizarBotonPush();
    } catch (error) {
        estado.configPush = { habilitado: false, motivo: mensajeError(error) };
        pintarEstadoPush('error', 'No disponibles');
        mostrarMensaje('mensajePush', mensajeError(error), 'error');
        actualizarBotonPush();
    }
}

function comprobarSoportePush() {
    const esLocal = location.hostname === 'localhost' || location.hostname === '127.0.0.1';
    if (!window.isSecureContext && !esLocal) {
        return {
            ok: false,
            estado: 'No disponible',
            mensaje: 'Esta página debe abrirse por HTTPS para usar notificaciones. '
                + 'En el celular usa la URL HTTPS del túnel, no la IP local.'
        };
    }
    if (!('serviceWorker' in navigator)) {
        return { ok: false, estado: 'No compatible', mensaje: 'Este navegador no admite Service Workers.' };
    }
    if (!('Notification' in window)) {
        return { ok: false, estado: 'No compatible', mensaje: 'Este navegador no admite notificaciones web.' };
    }
    if (!('PushManager' in window)) {
        return { ok: false, estado: 'No compatible', mensaje: 'Este navegador no admite notificaciones push.' };
    }
    return { ok: true };
}

function notificacionesActivas() {
    return typeof Notification !== 'undefined'
        && Notification.permission === 'granted'
        && !!pushTokenGuardado();
}

function actualizarBotonPush() {
    const boton = el('botonPush');
    if (!boton) {
        return;
    }
    const activo = notificacionesActivas();
    const disponible = !!(estado.configPush && estado.configPush.habilitado);
    boton.disabled = estado.activandoPush || activo || !disponible;
    boton.textContent = activo ? 'Notificaciones activadas' : 'Activar notificaciones';
    boton.dataset.estado = activo ? 'activado' : '';
}

async function alPulsarPush() {
    if (estado.activandoPush || notificacionesActivas()) {
        return;
    }
    await asegurarNotificaciones();
}

/**
 * Intenta dejar el push listo en este dispositivo. No lanza excepciones:
 * devuelve false y muestra el motivo cuando no es posible.
 */
async function asegurarNotificaciones() {
    if (notificacionesActivas()) {
        return true;
    }

    const soporte = comprobarSoportePush();
    if (!soporte.ok) {
        pintarEstadoPush('error', soporte.estado);
        mostrarMensaje('mensajePush', soporte.mensaje, 'error');
        return false;
    }

    if (!estado.configPush) {
        await cargarConfigPush();
    }
    if (!estado.configPush || !estado.configPush.habilitado) {
        return false;
    }

    if (Notification.permission !== 'granted') {
        const permiso = await Notification.requestPermission();
        if (permiso !== 'granted') {
            pintarEstadoPush('error', permiso === 'denied' ? 'Bloqueadas' : 'Sin permiso');
            mostrarMensaje('mensajePush', permiso === 'denied'
                ? 'El navegador bloqueó las notificaciones. Revisa los permisos del sitio.'
                : 'No se concedió el permiso; pulsa de nuevo y acepta la solicitud.', 'error');
            actualizarBotonPush();
            return false;
        }
    }

    return activarNotificaciones();
}

async function activarNotificaciones() {
    if (estado.activandoPush) {
        return false;
    }
    estado.activandoPush = true;
    actualizarBotonPush();
    limpiarMensaje('mensajePush');
    try {
        if (!estado.configPush || !estado.configPush.habilitado) {
            await cargarConfigPush();
            if (!estado.configPush || !estado.configPush.habilitado) {
                return false;
            }
        }
        const registro = await registrarServiceWorker(estado.configPush);
        const token = await obtenerTokenFcm(estado.configPush, registro);
        const respuesta = await api('/api/push/registrar', {
            method: 'POST',
            body: { token, deviceId: obtenerDeviceId(), plataforma: 'web' }
        });
        if (!respuesta || respuesta.activo !== true) {
            throw new Error((respuesta && respuesta.mensaje) || 'El servidor no confirmó el registro.');
        }
        escribirLocal(CLAVES.pushToken, token);
        pintarEstadoPush('activado', 'Activadas en este dispositivo');
        mostrarMensaje('mensajePush',
            'Notificaciones activadas. Recibirás avisos de los ríos que sigues.', 'exito');
        return true;
    } catch (error) {
        pintarEstadoPush('error', 'Error');
        mostrarMensaje('mensajePush', mensajeError(error), 'error');
        if (error.status === 401) {
            manejarErrorApp(error);
        }
        return false;
    } finally {
        estado.activandoPush = false;
        actualizarBotonPush();
    }
}

async function reanudarPushGuardado() {
    if (!estado.configPush || !estado.configPush.habilitado) {
        actualizarBotonPush();
        return;
    }
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') {
        actualizarBotonPush();
        return;
    }
    if (!pushTokenGuardado()) {
        // El permiso sigue concedido, pero el token se borró al cerrar sesión.
        // Vuelve a registrar este navegador para que la simulación lo encuentre.
        await activarNotificaciones();
        return;
    }
    try {
        // No basta con reasignar el token: si la página se recarga, hay que
        // volver a inicializar Firebase Messaging para que onMessage atienda
        // los avisos que llegan con la pestaña en primer plano. Sin esto, el
        // service worker entrega el mensaje a la página y se pierde.
        const registro = await registrarServiceWorker(estado.configPush);
        const token = await obtenerTokenFcm(estado.configPush, registro);
        await api('/api/push/registrar', {
            method: 'POST',
            body: { token, deviceId: obtenerDeviceId(), plataforma: 'web' }
        });
        escribirLocal(CLAVES.pushToken, token);
        pintarEstadoPush('activado', 'Activadas en este dispositivo');
    } catch (error) {
        if (error.status === 401) {
            manejarErrorApp(error);
            return;
        }
        if (error.status === 409) {
            borrarPushToken();
            mostrarMensaje('mensajePush',
                'El token de este dispositivo ya no es válido. Vuelve a activar las notificaciones.',
                'error');
        } else {
            console.warn('No se pudo preparar el push al reanudar la sesión.', error);
        }
    }
    actualizarBotonPush();
}

async function desactivarDispositivo() {
    const token = pushTokenGuardado();
    if (!token) {
        return;
    }
    try {
        await api('/api/push/registrar', {
            method: 'DELETE',
            body: { token, deviceId: obtenerDeviceId() }
        });
    } catch (error) {
        console.warn('No se pudo desactivar el dispositivo en el backend.', error);
    }
    borrarPushToken();
    if (estado.messaging && estado.modulosFirebase) {
        try {
            await estado.modulosFirebase.messaging.deleteToken(estado.messaging);
        } catch (error) {
            console.warn('No se pudo borrar el token de Firebase.', error);
        }
    }
    actualizarBotonPush();
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

    // Si el navegador aún tiene activa una versión anterior del worker, espera
    // a que la nueva tome el control para que entienda MOSTRAR_NOTIFICACION.
    const versionEsperada = `v=${VERSION_SW}`;
    for (let intento = 0; intento < 20; intento++) {
        if (registro.active && registro.active.scriptURL.includes(versionEsperada)) {
            break;
        }
        if (registro.waiting) {
            registro.waiting.postMessage({ tipo: 'SKIP_WAITING' });
        }
        await new Promise((resolver) => setTimeout(resolver, 150));
    }

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
        throw new Error('No se pudo cargar el SDK de Firebase. Revisa la conexión o el bloqueador de contenido.');
    }
}

function manejarMensajeForeground(payload) {
    const datos = (payload && payload.data) || {};
    const titulo = datos.title || 'Alerta de inundación';
    const cuerpo = datos.body || 'Alerta del sistema de monitoreo.';

    // Con la pestaña visible, FCM entrega el mensaje a la página y no al
    // service worker. Se avisa dentro de la página y se pide al service
    // worker que muestre la notificación del sistema con las mismas opciones
    // que usa en segundo plano (incluido renotify para cada simulacro).
    mostrarAvisoPush(titulo, cuerpo);
    mostrarMensaje('mensajePush', 'Notificación recibida en este dispositivo.', 'exito');
    if (esAdmin() && el('bloqueAdmin') && !el('bloqueAdmin').hidden) {
        mostrarMensaje('mensajeAdmin', `${titulo}: ${cuerpo}`, 'exito');
    }
    enviarNotificacionAlServiceWorker(datos);
}

async function enviarNotificacionAlServiceWorker(datos) {
    try {
        const registro = estado.sw || await navigator.serviceWorker.ready;
        estado.sw = registro;
        if (registro.active) {
            registro.active.postMessage({ tipo: 'MOSTRAR_NOTIFICACION', datos });
            return;
        }
        // Sin worker activo se intenta desde la página como último recurso.
        await registro.showNotification(datos.title || 'Alerta de inundación', {
            body: datos.body || 'Alerta del sistema de monitoreo.',
            icon: datos.icon || '/icons/icon-192.png',
            badge: datos.badge || '/icons/badge-72.png',
            tag: datos.tag || 'alerta-push',
            renotify: true,
            requireInteraction: String(datos.requiereInteraccion) === 'true',
            data: { url: datos.url || '/' }
        });
    } catch (error) {
        console.warn('No se pudo mostrar la notificación del sistema.', error);
    }
}

/* ------------------------------ Utilidades ----------------------------- */

async function api(ruta, opciones = {}) {
    const controlador = new AbortController();
    const temporizador = setTimeout(() => controlador.abort(), opciones.tiempoMaximo || 30000);
    const cabeceras = { Accept: 'application/json', ...(opciones.cabeceras || {}) };
    if (estado.token) {
        cabeceras.Authorization = `Bearer ${estado.token}`;
    }
    const configuracion = { method: opciones.method || 'GET', headers: cabeceras, signal: controlador.signal };
    if (opciones.body !== undefined) {
        cabeceras['Content-Type'] = 'application/json';
        configuracion.body = JSON.stringify(opciones.body);
    }

    let respuesta;
    try {
        respuesta = await fetch(ruta, configuracion);
    } catch (error) {
        if (error.name === 'AbortError') {
            throw crearError('El servidor tardó demasiado en responder.', 0);
        }
        throw crearError('No se pudo conectar con el servidor.', 0);
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
        const mensaje = typeof datos === 'string'
            ? datos
            : (datos && (datos.message || datos.error));
        throw crearError(mensaje || `El servidor respondió con el código ${respuesta.status}.`, respuesta.status);
    }
    return datos;
}

function crearError(mensaje, status) {
    const error = new Error(mensaje);
    error.status = status;
    return error;
}

function manejarErrorApp(error) {
    if (error && error.status === 401 && estado.token) {
        limpiarSesion();
        mostrarLogin();
        mostrarMensaje('mensajeLogin', 'La sesión venció. Inicia sesión de nuevo.', 'error');
        return;
    }
    mostrarMensaje('mensajeApp', mensajeError(error), 'error');
}

function esErrorCors(error) {
    return !!error && error.status === 403
        && String(error.message || '').toLowerCase().includes('cors');
}

function mensajeError(error) {
    if (!error) {
        return 'Ocurrió un error inesperado.';
    }
    const status = error.status;
    if (status === 400 || status === 409) {
        return error.message;
    }
    if (status === 401) {
        return 'La sesión venció o las credenciales no son válidas.';
    }
    if (status === 403) {
        if (esErrorCors(error)) {
            return 'El servidor rechazó el origen de esta página (CORS). '
                + 'Agrega la URL del túnel a ALERTA_CORS_ORIGENES y reinicia el backend.';
        }
        return 'No tienes permiso para esa operación.';
    }
    if (status === 404) {
        return error.message || 'El recurso no existe.';
    }
    if (status === 429) {
        return 'Demasiadas solicitudes. Inténtalo de nuevo en un minuto.';
    }
    if (status === 502) {
        return `${error.message} (puede ser un fallo temporal de GEOGloWS o FCM).`;
    }
    return error.message || 'No se pudo completar la operación.';
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

function pushTokenGuardado() {
    return leerLocal(CLAVES.pushToken);
}

function borrarPushToken() {
    try {
        localStorage.removeItem(CLAVES.pushToken);
    } catch (error) {
        console.warn('No se pudo borrar el token local.', error);
    }
}

function pintarEstadoPush(tipo, texto) {
    const contenedor = el('estadoPush');
    contenedor.dataset.estado = tipo;
    el('estadoPushTexto').textContent = texto;
}

let temporizadorAvisoPush = null;

/**
 * Aviso flotante para los push recibidos con la pestaña en primer plano.
 * No depende del bloque de mensajes que se está usando en ese momento.
 */
function mostrarAvisoPush(titulo, texto) {
    const aviso = el('avisoPush');
    if (!aviso) {
        return;
    }
    el('avisoPushTitulo').textContent = titulo;
    el('avisoPushTexto').textContent = texto;
    aviso.hidden = false;
    if (temporizadorAvisoPush) {
        clearTimeout(temporizadorAvisoPush);
    }
    temporizadorAvisoPush = setTimeout(ocultarAvisoPush, 15000);
}

function ocultarAvisoPush() {
    const aviso = el('avisoPush');
    if (aviso) {
        aviso.hidden = true;
    }
    if (temporizadorAvisoPush) {
        clearTimeout(temporizadorAvisoPush);
        temporizadorAvisoPush = null;
    }
}

function mostrarMensaje(id, texto, tipo) {
    const mensaje = el(id);
    mensaje.textContent = texto;
    mensaje.dataset.tipo = tipo;
    mensaje.hidden = false;
}

function limpiarMensaje(id) {
    const mensaje = el(id);
    mensaje.textContent = '';
    mensaje.hidden = true;
    delete mensaje.dataset.tipo;
}

function crear(etiqueta, clase, texto) {
    const nodo = document.createElement(etiqueta);
    if (clase) {
        nodo.className = clase;
    }
    if (texto !== undefined && texto !== null) {
        nodo.textContent = texto;
    }
    return nodo;
}

function leerLocal(clave) {
    try {
        return localStorage.getItem(clave);
    } catch (error) {
        return null;
    }
}

function leerJsonLocal(clave) {
    const texto = leerLocal(clave);
    if (!texto) {
        return null;
    }
    try {
        return JSON.parse(texto);
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

function escribirJsonLocal(clave, valor) {
    escribirLocal(clave, JSON.stringify(valor));
}

function el(id) {
    return document.getElementById(id);
}
