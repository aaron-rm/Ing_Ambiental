'use strict';

/*
 * PWA mínima de prueba del sistema de alertas.
 * Cubre el mismo flujo de usuario que la consola: inicio de sesión o registro,
 * catálogo de cuatro ríos, panel de caudal y lluvia para cualquier río,
 * selección múltiple de ríos para notificaciones y activación del push.
 * No incluye panel de administración ni cálculo de riesgo: todo el cálculo
 * vive en el backend.
 */

const VERSION_SW = '6';
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
    rioActual: null,
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
    el('botonPruebaPush').addEventListener('click', alEnviarPrueba);

    const parametros = new URLSearchParams(location.search);
    if (parametros.has('alerta')) {
        mostrarMensaje('mensajeLogin', 'Notificación abierta correctamente.', 'exito');
    }

    if (!estado.token) {
        mostrarLogin();
        return;
    }
    try {
        const usuario = await api('/api/auth/me');
        estado.usuario = usuario;
        guardarSesion();
        await mostrarApp();
    } catch (error) {
        limpiarSesion();
        mostrarLogin();
        if (error.status !== 401) {
            mostrarMensaje('mensajeLogin', mensajeError(error), 'error');
        }
    }
}

function mostrarLogin() {
    el('vistaLogin').hidden = false;
    el('vistaApp').hidden = true;
}

async function mostrarApp() {
    el('vistaLogin').hidden = true;
    el('vistaApp').hidden = false;
    el('usuarioActual').textContent = `${estado.usuario.identificador} (${estado.usuario.rol})`;

    await Promise.all([cargarCatalogo(), cargarSeguidos()]);
    dibujarListaRios();
    await cargarMapa();
    await cargarConfigPush();
    await reanudarPushGuardado();
}

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
    limpiarSesion();
    estado.rios = [];
    estado.seguidos = new Set();
    estado.rioActual = null;
    estado.configPush = null;
    el('bloquePanel').hidden = true;
    el('listaRios').innerHTML = '';
    el('mapa').innerHTML = '';
    limpiarMensaje('mensajeApp');
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

async function cargarCatalogo() {
    try {
        estado.rios = await api('/api/rios');
    } catch (error) {
        manejarErrorApp(error);
        estado.rios = [];
    }
}

async function cargarSeguidos() {
    try {
        const seguidos = await api('/api/usuarios/me/rios');
        estado.seguidos = new Set(seguidos.map((rio) => rio.rioId));
    } catch (error) {
        manejarErrorApp(error);
        estado.seguidos = new Set();
    }
}

async function cargarMapa() {
    const contenedor = el('mapa');
    try {
        const coleccion = await api('/api/rios/mapa');
        dibujarMapa(coleccion);
    } catch (error) {
        contenedor.innerHTML = '';
        contenedor.appendChild(crear('p', 'ayuda', mensajeError(error)));
    }
}

function dibujarMapa(coleccion) {
    const contenedor = el('mapa');
    contenedor.innerHTML = '';
    const features = (coleccion && coleccion.features) || [];
    const puntos = [];
    features.forEach((feature) => recorrerCoordenadas(feature.geometry, (punto) => puntos.push(punto)));
    if (puntos.length === 0) {
        contenedor.appendChild(crear('p', 'ayuda', 'El catálogo todavía no tiene geometría.'));
        return;
    }

    const ancho = 600;
    const alto = 420;
    const margen = 28;
    const lons = puntos.map((p) => p[0]);
    const lats = puntos.map((p) => p[1]);
    const minLon = Math.min(...lons);
    const maxLon = Math.max(...lons);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);
    const rangoLon = maxLon - minLon || 1e-6;
    const rangoLat = maxLat - minLat || 1e-6;
    const escala = Math.min((ancho - 2 * margen) / rangoLon, (alto - 2 * margen) / rangoLat);
    const proyectar = ([lon, lat]) => [
        margen + (lon - minLon) * escala,
        alto - margen - (lat - minLat) * escala
    ];

    const NS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', `0 0 ${ancho} ${alto}`);
    svg.setAttribute('class', 'mapa-svg');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', 'Vista esquemática de los ríos del catálogo');

    features.forEach((feature) => {
        const rioId = feature.properties && feature.properties.rioId;
        const camino = document.createElementNS(NS, 'path');
        camino.setAttribute('d', geometriaARuta(feature.geometry, proyectar));
        camino.setAttribute('class', 'rio-linea' + (estado.seguidos.has(rioId) ? ' seguido' : ''));
        camino.setAttribute('tabindex', '0');
        camino.setAttribute('role', 'button');
        const nombre = (feature.properties && feature.properties.nombre) || `Río ${rioId}`;
        camino.setAttribute('aria-label', `Abrir panel de ${nombre}`);
        camino.addEventListener('click', () => abrirPanel(rioId));
        camino.addEventListener('keydown', (evento) => {
            if (evento.key === 'Enter' || evento.key === ' ') {
                evento.preventDefault();
                abrirPanel(rioId);
            }
        });
        const titulo = document.createElementNS(NS, 'title');
        titulo.textContent = nombre;
        camino.appendChild(titulo);
        svg.appendChild(camino);
    });

    contenedor.appendChild(svg);
}

function geometriaARuta(geometria, proyectar) {
    if (!geometria) {
        return '';
    }
    const lineas = geometria.type === 'MultiLineString' ? geometria.coordinates : [geometria.coordinates];
    return lineas
        .filter((linea) => Array.isArray(linea) && linea.length > 0)
        .map((linea) => {
            const puntos = linea.map((punto) => {
                const [x, y] = proyectar(punto);
                return `${x.toFixed(1)} ${y.toFixed(1)}`;
            });
            return `M ${puntos.join(' L ')}`;
        })
        .join(' ');
}

function recorrerCoordenadas(geometria, visitar) {
    if (!geometria) {
        return;
    }
    const lineas = geometria.type === 'MultiLineString' ? geometria.coordinates : [geometria.coordinates];
    (lineas || []).forEach((linea) => {
        (linea || []).forEach((punto) => {
            if (Array.isArray(punto) && punto.length >= 2) {
                visitar(punto);
            }
        });
    });
}

function dibujarListaRios() {
    const lista = el('listaRios');
    lista.innerHTML = '';
    if (estado.rios.length === 0) {
        lista.appendChild(crear('li', 'ayuda', 'No se pudo cargar el catálogo.'));
        return;
    }
    estado.rios.forEach((rio) => {
        const seguido = estado.seguidos.has(rio.rioId);
        const item = crear('li', 'rio-item');

        const datos = crear('div', 'rio-datos');
        datos.appendChild(crear('strong', null, rio.nombre));
        datos.appendChild(crear('span', 'rio-detalle',
            `reachId ${rio.reachId} · ${seguido ? 'sigues este río' : 'no lo sigues'}`));
        item.appendChild(datos);

        const acciones = crear('div', 'rio-acciones');
        const ver = crear('button', 'button compacto secundario', 'Panel');
        ver.type = 'button';
        ver.addEventListener('click', () => abrirPanel(rio.rioId));
        acciones.appendChild(ver);

        const boton = crear('button', 'button compacto' + (seguido ? ' secundario' : ''),
            seguido ? 'Dejar de seguir' : 'Seguir');
        boton.type = 'button';
        boton.addEventListener('click', () => (seguido ? dejarDeSeguir(rio.rioId) : seguirRio(rio.rioId)));
        acciones.appendChild(boton);

        item.appendChild(acciones);
        lista.appendChild(item);
    });
}

async function seguirRio(rioId) {
    try {
        const respuesta = await api(`/api/usuarios/me/rios/${rioId}`, { method: 'POST' });
        estado.seguidos.add(rioId);
        mostrarMensaje('mensajeApp', respuesta.mensaje || 'Río agregado a tus alertas.', 'exito');
        actualizarSeleccion();
    } catch (error) {
        manejarErrorApp(error);
    }
}

async function dejarDeSeguir(rioId) {
    try {
        await api(`/api/usuarios/me/rios/${rioId}`, { method: 'DELETE' });
        estado.seguidos.delete(rioId);
        mostrarMensaje('mensajeApp', 'Dejaste de seguir el río. Ya no recibirás sus alertas.', 'exito');
        actualizarSeleccion();
    } catch (error) {
        manejarErrorApp(error);
    }
}

function actualizarSeleccion() {
    dibujarListaRios();
    cargarMapa();
    if (estado.rioActual !== null) {
        abrirPanel(estado.rioActual, { silencioso: true });
    }
}

async function abrirPanel(rioId, opciones = {}) {
    estado.rioActual = rioId;
    const bloque = el('bloquePanel');
    const contenido = el('panelContenido');
    bloque.hidden = false;
    contenido.innerHTML = '';
    contenido.appendChild(crear('p', 'ayuda', 'Consultando caudal y lluvia…'));
    if (!opciones.silencioso) {
        bloque.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    try {
        const alerta = await api(`/api/alertas/rio/${rioId}`);
        pintarPanel(alerta);
    } catch (error) {
        contenido.innerHTML = '';
        contenido.appendChild(crear('p', 'message', mensajeError(error)));
        manejarErrorApp(error);
    }
}

function pintarPanel(alerta) {
    const contenido = el('panelContenido');
    contenido.innerHTML = '';
    el('panelTitulo').textContent = alerta.nombreRio || 'Panel del río';

    const fila = crear('div', 'panel-fila');
    fila.appendChild(crear('span', `chip nivel-${(alerta.nivelAlerta || '').toLowerCase()}`,
        textoNivel(alerta.nivelAlerta)));
    fila.appendChild(crear('span', 'chip', `datos ${(alerta.estadoDatos || 'sin clasificar').toLowerCase()}`));
    fila.appendChild(crear('span', 'chip', alerta.datosConfiables ? 'confiables' : 'no confiables'));
    contenido.appendChild(fila);

    const seguido = estado.seguidos.has(alerta.rioId);
    const boton = crear('button', 'button compacto' + (seguido ? ' secundario' : ''),
        seguido ? 'Dejar de seguir' : 'Seguir este río');
    boton.type = 'button';
    boton.addEventListener('click', () => (seguido ? dejarDeSeguir(alerta.rioId) : seguirRio(alerta.rioId)));
    contenido.appendChild(boton);

    const cifras = crear('div', 'cifras');
    cifras.appendChild(crearCifra(alerta.caudalActualOInicial.toFixed(1), 'm³/s caudal actual'));
    cifras.appendChild(crearCifra(alerta.caudalMaximoPronosticado.toFixed(1), 'm³/s máximo pronosticado'));
    cifras.appendChild(crearCifra(alerta.umbralVigilancia.toFixed(1), 'm³/s vigilancia'));
    cifras.appendChild(crearCifra(alerta.umbralAlerta.toFixed(1), 'm³/s alerta'));
    cifras.appendChild(crearCifra(alerta.umbralEmergencia.toFixed(1), 'm³/s emergencia'));
    contenido.appendChild(cifras);

    contenido.appendChild(crear('h3', null, 'Hidrograma pronosticado (caudal)'));
    const grafico = crear('div', 'grafico');
    dibujarHidrograma(grafico, alerta);
    contenido.appendChild(grafico);

    contenido.appendChild(crear('h3', null, 'Lluvia (Open-Meteo)'));
    if (alerta.lluvia24hMm !== null && alerta.lluvia24hMm !== undefined) {
        contenido.appendChild(crear('p', null,
            `Acumulada 24 h: ${redondear(alerta.lluvia24hMm)} mm · `
            + `72 h: ${redondear(alerta.lluvia72hMm)} mm · `
            + `máxima horaria: ${redondear(alerta.maxLluviaHorariaMm)} mm`));
        contenido.appendChild(crear('p', null, `Nivel por lluvia: ${textoNivel(alerta.nivelRiesgoLluvia)}`));
    } else {
        contenido.appendChild(crear('p', 'ayuda', 'Sin datos de lluvia disponibles.'));
    }
    contenido.appendChild(crear('p', 'footnote',
        'Los umbrales de lluvia son experimentales y no oficiales.'));
    if (alerta.fechaActualizacionPrecipitacion) {
        contenido.appendChild(crear('p', 'footnote', `Lluvia actualizada: ${formatearFecha(alerta.fechaActualizacionPrecipitacion)}`));
    }

    if (alerta.mensaje) {
        contenido.appendChild(crear('p', 'panel-mensaje', alerta.mensaje));
    }

    contenido.appendChild(crear('h3', null, 'Recomendaciones'));
    contenido.appendChild(listaTexto(alerta.recomendaciones, 'Sin recomendaciones.'));
    if (alerta.advertencias && alerta.advertencias.length > 0) {
        contenido.appendChild(crear('h3', null, 'Advertencias'));
        contenido.appendChild(listaTexto(alerta.advertencias, 'Sin advertencias.'));
    }

    const fuentes = [];
    if (alerta.fuenteCaudal) {
        fuentes.push(`Caudal: ${alerta.fuenteCaudal}`);
    }
    if (alerta.fuentePrecipitacion) {
        fuentes.push(`Lluvia: ${alerta.fuentePrecipitacion}`);
    }
    if (alerta.fechaEmisionCaudal) {
        fuentes.push(`Emisión del pronóstico: ${formatearFecha(alerta.fechaEmisionCaudal)}`);
    }
    if (fuentes.length > 0) {
        contenido.appendChild(crear('p', 'footnote', fuentes.join(' · ')));
    }
}

function dibujarHidrograma(contenedor, alerta) {
    const serie = (alerta.pronostico || []).filter((punto) => punto && punto.fecha && typeof punto.caudalMedio === 'number');
    if (serie.length < 2) {
        contenedor.appendChild(crear('p', 'ayuda', 'Sin serie temporal de caudal para graficar.'));
        return;
    }

    const NS = 'http://www.w3.org/2000/svg';
    const ancho = 640;
    const alto = 240;
    const margen = { arriba: 14, derecha: 14, abajo: 30, izquierda: 48 };
    const utilAncho = ancho - margen.izquierda - margen.derecha;
    const utilAlto = alto - margen.arriba - margen.abajo;

    const umbrales = [
        { valor: alerta.umbralVigilancia, clase: 'umbral-vigilancia' },
        { valor: alerta.umbralAlerta, clase: 'umbral-alerta' },
        { valor: alerta.umbralEmergencia, clase: 'umbral-emergencia' }
    ].filter((umbral) => typeof umbral.valor === 'number' && umbral.valor > 0);

    const caudalMaximo = Math.max(
        ...serie.map((punto) => punto.caudalMedio),
        ...umbrales.map((umbral) => umbral.valor),
        1
    );
    const yMax = caudalMaximo * 1.15;
    const x = (indice) => margen.izquierda + (indice / (serie.length - 1)) * utilAncho;
    const y = (caudal) => margen.arriba + utilAlto - (caudal / yMax) * utilAlto;

    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', `0 0 ${ancho} ${alto}`);
    svg.setAttribute('class', 'grafico-svg');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', 'Hidrograma pronosticado con umbrales de caudal');

    const area = serie.map((punto, indice) => `${x(indice).toFixed(1)} ${y(punto.caudalMedio).toFixed(1)}`);
    const relleno = document.createElementNS(NS, 'path');
    relleno.setAttribute('d',
        `M ${x(0).toFixed(1)} ${y(0).toFixed(1)} L ${area.join(' L ')} `
        + `L ${x(serie.length - 1).toFixed(1)} ${y(0).toFixed(1)} Z`);
    relleno.setAttribute('class', 'grafico-area');
    svg.appendChild(relleno);

    umbrales.forEach((umbral) => {
        const linea = document.createElementNS(NS, 'line');
        linea.setAttribute('x1', margen.izquierda);
        linea.setAttribute('x2', ancho - margen.derecha);
        linea.setAttribute('y1', y(umbral.valor).toFixed(1));
        linea.setAttribute('y2', y(umbral.valor).toFixed(1));
        linea.setAttribute('class', `grafico-umbral ${umbral.clase}`);
        svg.appendChild(linea);

        const etiqueta = document.createElementNS(NS, 'text');
        etiqueta.setAttribute('x', ancho - margen.derecha);
        etiqueta.setAttribute('y', (y(umbral.valor) - 4).toFixed(1));
        etiqueta.setAttribute('text-anchor', 'end');
        etiqueta.setAttribute('class', 'grafico-etiqueta');
        etiqueta.textContent = `${umbral.valor.toFixed(0)} m³/s`;
        svg.appendChild(etiqueta);
    });

    const curva = document.createElementNS(NS, 'polyline');
    curva.setAttribute('points', area.join(' '));
    curva.setAttribute('class', 'grafico-curva');
    svg.appendChild(curva);

    const eje = document.createElementNS(NS, 'line');
    eje.setAttribute('x1', margen.izquierda);
    eje.setAttribute('x2', margen.izquierda);
    eje.setAttribute('y1', margen.arriba);
    eje.setAttribute('y2', alto - margen.abajo);
    eje.setAttribute('class', 'grafico-eje');
    svg.appendChild(eje);

    const textoInicio = document.createElementNS(NS, 'text');
    textoInicio.setAttribute('x', margen.izquierda);
    textoInicio.setAttribute('y', alto - 8);
    textoInicio.setAttribute('class', 'grafico-etiqueta');
    textoInicio.textContent = formatearFechaCorta(serie[0].fecha);
    svg.appendChild(textoInicio);

    const textoFin = document.createElementNS(NS, 'text');
    textoFin.setAttribute('x', ancho - margen.derecha);
    textoFin.setAttribute('y', alto - 8);
    textoFin.setAttribute('text-anchor', 'end');
    textoFin.setAttribute('class', 'grafico-etiqueta');
    textoFin.textContent = formatearFechaCorta(serie[serie.length - 1].fecha);
    svg.appendChild(textoFin);

    contenedor.appendChild(svg);
    contenedor.appendChild(crear('p', 'footnote',
        'El hidrograma usa el caudal pronosticado por GEOGloWS. Las líneas punteadas son los '
        + 'umbrales provisionales de vigilancia, alerta y emergencia. No se muestra altura del agua: '
        + 'el nivel en metros dependería de la sección real del cauce.'));
}

function crearCifra(valor, etiqueta) {
    const caja = crear('div', 'cifra');
    caja.appendChild(crear('span', 'cifra-valor', valor));
    caja.appendChild(crear('span', 'cifra-etiqueta', etiqueta));
    return caja;
}

function listaTexto(textos, vacio) {
    const lista = crear('ul', 'lista-texto');
    const items = textos && textos.length > 0 ? textos : [vacio];
    items.forEach((texto) => lista.appendChild(crear('li', null, texto)));
    return lista;
}

/* ------------------------- Notificaciones push ------------------------- */

async function cargarConfigPush() {
    const boton = el('botonPush');
    const soporte = comprobarSoportePush();
    if (!soporte.ok) {
        pintarEstadoPush('error', soporte.estado);
        mostrarMensaje('mensajePush', soporte.mensaje, 'error');
        boton.disabled = true;
        return;
    }
    pintarEstadoPush('comprobando', 'Comprobando…');
    limpiarMensaje('mensajePush');
    try {
        const config = await api('/api/push/config');
        estado.configPush = config;
        if (!config || !config.habilitado) {
            pintarEstadoPush('error', 'No disponibles');
            mostrarMensaje('mensajePush', (config && config.motivo)
                || 'Las notificaciones no están habilitadas en el servidor.', 'error');
            boton.disabled = true;
            return;
        }
        actualizarBotonesPush();
    } catch (error) {
        pintarEstadoPush('error', 'No disponibles');
        mostrarMensaje('mensajePush', mensajeError(error), 'error');
        boton.disabled = true;
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

function actualizarBotonesPush() {
    const activado = Notification.permission === 'granted' && pushTokenGuardado();
    el('botonPush').disabled = estado.activandoPush;
    el('botonPush').textContent = activado ? 'Notificaciones activadas' : 'Activar notificaciones';
    el('botonPush').dataset.estado = activado ? 'activado' : '';
    el('botonPruebaPush').disabled = !activado;
    if (activado) {
        pintarEstadoPush('activado', 'Activadas en este dispositivo');
    } else if (Notification.permission === 'denied') {
        pintarEstadoPush('error', 'Bloqueadas por el navegador');
    } else if (Notification.permission === 'granted') {
        pintarEstadoPush('disponible', 'Permiso concedido; falta registrar');
    } else {
        pintarEstadoPush('disponible', 'Pendientes de autorización');
    }
}

async function alPulsarPush() {
    if (estado.activandoPush) {
        return;
    }
    if (Notification.permission !== 'granted') {
        const permiso = await Notification.requestPermission();
        if (permiso !== 'granted') {
            pintarEstadoPush('error', permiso === 'denied' ? 'Bloqueadas' : 'Sin permiso');
            mostrarMensaje('mensajePush', permiso === 'denied'
                ? 'El navegador bloqueó las notificaciones. Revisa los permisos del sitio.'
                : 'No se concedió el permiso; pulsa de nuevo y acepta la solicitud.', 'error');
            actualizarBotonesPush();
            return;
        }
    }
    await activarNotificaciones();
}

async function activarNotificaciones() {
    estado.activandoPush = true;
    actualizarBotonesPush();
    limpiarMensaje('mensajePush');
    try {
        if (!estado.configPush || !estado.configPush.habilitado) {
            await cargarConfigPush();
            if (!estado.configPush || !estado.configPush.habilitado) {
                return;
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
        actualizarBotonesPush();
    } catch (error) {
        pintarEstadoPush('error', 'Error');
        mostrarMensaje('mensajePush', mensajeError(error), 'error');
        manejarErrorApp(error);
    } finally {
        estado.activandoPush = false;
        actualizarBotonesPush();
    }
}

async function reanudarPushGuardado() {
    if (Notification.permission !== 'granted' || !pushTokenGuardado() || !estado.configPush
        || !estado.configPush.habilitado) {
        actualizarBotonesPush();
        return;
    }
    try {
        // Reasigna el token al usuario que acaba de iniciar sesión.
        await api('/api/push/registrar', {
            method: 'POST',
            body: { token: pushTokenGuardado(), deviceId: obtenerDeviceId(), plataforma: 'web' }
        });
    } catch (error) {
        manejarErrorApp(error);
    }
    actualizarBotonesPush();
}

async function alEnviarPrueba() {
    const token = pushTokenGuardado();
    if (!token) {
        mostrarMensaje('mensajePush', 'Activa las notificaciones antes de enviar una prueba.', 'error');
        return;
    }
    el('botonPruebaPush').disabled = true;
    limpiarMensaje('mensajePush');
    try {
        await api('/api/push/prueba', {
            method: 'POST',
            body: { token, deviceId: obtenerDeviceId() }
        });
        mostrarMensaje('mensajePush', 'Notificación de prueba enviada. Debe aparecer en unos segundos.', 'exito');
    } catch (error) {
        if (error.status === 410) {
            try {
                localStorage.removeItem(CLAVES.pushToken);
            } catch (ignorado) {
                // Sin almacenamiento local disponible.
            }
            mostrarMensaje('mensajePush', 'El token dejó de ser válido. Vuelve a activar las notificaciones.', 'error');
        } else {
            mostrarMensaje('mensajePush', mensajeError(error), 'error');
        }
        manejarErrorApp(error);
    } finally {
        el('botonPruebaPush').disabled = false;
    }
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
    try {
        localStorage.removeItem(CLAVES.pushToken);
    } catch (error) {
        console.warn('No se pudo borrar el token local.', error);
    }
    if (estado.messaging && estado.modulosFirebase) {
        try {
            await estado.modulosFirebase.messaging.deleteToken(estado.messaging);
        } catch (error) {
            console.warn('No se pudo borrar el token de Firebase.', error);
        }
    }
    actualizarBotonesPush();
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
        throw new Error('No se pudo cargar el SDK de Firebase. Revisa la conexión o el bloqueador de contenido.');
    }
}

function manejarMensajeForeground(payload) {
    const datos = (payload && payload.data) || {};
    mostrarMensaje('mensajePush', 'Notificación recibida en este dispositivo.', 'exito');
    if (!estado.sw) {
        return;
    }
    estado.sw.showNotification(datos.title || 'Alerta de inundación', {
        body: datos.body || 'Alerta del sistema de monitoreo.',
        icon: datos.icon || '/icons/icon-192.png',
        badge: datos.badge || '/icons/badge-72.png',
        tag: datos.tag || 'alerta-push',
        data: { url: datos.url || '/' }
    }).catch((error) => console.warn('No se pudo mostrar la notificación local.', error));
}

/* ------------------------------ Utilidades ------------------------------ */

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
        return 'No tienes permiso para esa operación.';
    }
    if (status === 404) {
        return error.message || 'El recurso no existe.';
    }
    if (status === 429) {
        return 'Espera un minuto antes de enviar otra notificación de prueba.';
    }
    if (status === 502) {
        return `${error.message} (puede ser un fallo temporal de GEOGloWS, Open-Meteo o FCM).`;
    }
    return error.message || 'No se pudo completar la operación.';
}

function formatearFecha(iso) {
    try {
        return new Date(iso).toLocaleString('es-PA', {
            day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
        });
    } catch (error) {
        return iso;
    }
}

function formatearFechaCorta(iso) {
    try {
        return new Date(iso).toLocaleString('es-PA', {
            day: '2-digit', month: '2-digit', hour: '2-digit'
        });
    } catch (error) {
        return iso;
    }
}

function textoNivel(nivel) {
    if (!nivel) {
        return 'sin dato';
    }
    const texto = String(nivel).toLowerCase();
    if (texto === 'emergencia' || texto === 'alerta' || texto === 'vigilancia' || texto === 'normal') {
        return texto;
    }
    return String(nivel);
}

function redondear(valor) {
    if (valor === null || valor === undefined) {
        return '—';
    }
    return Number(valor).toFixed(1);
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

function pintarEstadoPush(tipo, texto) {
    const contenedor = el('estadoPush');
    contenedor.dataset.estado = tipo;
    el('estadoPushTexto').textContent = texto;
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
