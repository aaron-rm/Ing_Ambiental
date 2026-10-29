import { api } from './api.js'

// Firebase se carga desde su CDN para no sumar el SDK al bundle principal.
const URL_FIREBASE_APP = 'https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js'
const URL_FIREBASE_MESSAGING = 'https://www.gstatic.com/firebasejs/10.14.1/firebase-messaging.js'
const NOMBRE_APP_FIREBASE = 'alerta-inundaciones-web'
const VERSION_SW = '1'

const CLAVE_TOKEN = 'alerta.pushToken.v1'
const CLAVE_DISPOSITIVO = 'alerta.deviceId.v1'

let modulosFirebase = null
let messaging = null
let oyenteFcm = false
let activacionEnCurso = null

/** Comprueba si este navegador y contexto pueden usar Web Push. */
export function comprobarSoportePush() {
  const esLocal = location.hostname === 'localhost' || location.hostname === '127.0.0.1'
  if (!window.isSecureContext && !esLocal) {
    return {
      ok: false,
      motivo: 'Esta página debe abrirse por HTTPS para activar notificaciones. En el celular usa la URL HTTPS del túnel, no la IP local.',
    }
  }
  const esIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
  if (!('serviceWorker' in navigator)) {
    return { ok: false, motivo: 'Este navegador no admite Service Workers.' }
  }
  if (!('Notification' in window)) {
    if (esIOS) {
      return {
        ok: false,
        motivo: 'En iPhone/iPad agrega esta app a la pantalla de inicio (botón Compartir ⎋ → "Agregar a inicio") y ábrela desde allí para activar notificaciones (requiere iOS 16.4+).',
      }
    }
    return { ok: false, motivo: 'Este navegador no admite notificaciones web.' }
  }
  if (!('PushManager' in window)) {
    if (esIOS) {
      return {
        ok: false,
        motivo: 'En iPhone/iPad agrega esta app a la pantalla de inicio (botón Compartir ⎋ → "Agregar a inicio") y ábrela desde allí para activar notificaciones (requiere iOS 16.4+).',
      }
    }
    return { ok: false, motivo: 'Este navegador no admite notificaciones push.' }
  }
  return { ok: true }
}

export const permisoNotificaciones = () =>
  typeof Notification === 'undefined' ? 'denied' : Notification.permission

export const tokenPushGuardado = () => localStorage.getItem(CLAVE_TOKEN)

export async function cargarConfigPush() {
  return api('/api/push/config')
}

export function obtenerDeviceId() {
  let deviceId = localStorage.getItem(CLAVE_DISPOSITIVO)
  if (deviceId) return deviceId
  if (window.crypto && typeof window.crypto.randomUUID === 'function') {
    deviceId = window.crypto.randomUUID()
  } else {
    deviceId = `dev-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
  }
  localStorage.setItem(CLAVE_DISPOSITIVO, deviceId)
  return deviceId
}

async function cargarModulosFirebase() {
  if (modulosFirebase) return modulosFirebase
  try {
    const [app, messagingModulo] = await Promise.all([
      import(/* @vite-ignore */ URL_FIREBASE_APP),
      import(/* @vite-ignore */ URL_FIREBASE_MESSAGING),
    ])
    modulosFirebase = { app, messaging: messagingModulo }
    return modulosFirebase
  } catch {
    throw new Error('No se pudo cargar el SDK de Firebase. Revisa la conexión o el bloqueador de contenido.')
  }
}

async function registrarServiceWorker(config) {
  const parametros = new URLSearchParams()
  parametros.set('v', VERSION_SW)
  ;['apiKey', 'authDomain', 'projectId', 'messagingSenderId', 'appId'].forEach((clave) => {
    parametros.set(clave, config[clave] || '')
  })
  const registro = await navigator.serviceWorker.register(
    `/firebase-messaging-sw.js?${parametros.toString()}`,
    { scope: '/' },
  )
  await navigator.serviceWorker.ready
  return registro
}

async function mostrarEnPrimerPlano(payload) {
  const datos = (payload && payload.data) || {}
  try {
    const registro = await navigator.serviceWorker.ready
    if (registro.active) {
      registro.active.postMessage({ tipo: 'MOSTRAR_NOTIFICACION', datos })
    }
  } catch {
    // Si el service worker no está disponible, la notificación en segundo plano
    // sigue funcionando cuando la pestaña no está visible.
  }
}

async function activarPushInterno(config, jwt) {
  if (!config || !config.habilitado) {
    throw new Error((config && config.motivo) || 'Las notificaciones no están habilitadas en el servidor.')
  }
  const soporte = comprobarSoportePush()
  if (!soporte.ok) throw new Error(soporte.motivo)

  if (Notification.permission !== 'granted') {
    const permiso = await Notification.requestPermission()
    if (permiso !== 'granted') {
      throw new Error(
        permiso === 'denied'
          ? 'El navegador bloqueó las notificaciones. Habilítalas en los permisos del sitio.'
          : 'No se concedió el permiso de notificaciones.',
      )
    }
  }

  const registro = await registrarServiceWorker(config)
  const modulos = await cargarModulosFirebase()
  if (typeof modulos.messaging.isSupported === 'function'
      && !(await modulos.messaging.isSupported())) {
    throw new Error('Firebase Messaging no es compatible con este navegador o contexto.')
  }

  let app
  try {
    app = modulos.app.getApp(NOMBRE_APP_FIREBASE)
  } catch {
    app = modulos.app.initializeApp({
      apiKey: config.apiKey,
      authDomain: config.authDomain,
      projectId: config.projectId,
      messagingSenderId: config.messagingSenderId,
      appId: config.appId,
    }, NOMBRE_APP_FIREBASE)
  }

  messaging = modulos.messaging.getMessaging(app)
  if (!oyenteFcm) {
    modulos.messaging.onMessage(messaging, mostrarEnPrimerPlano)
    oyenteFcm = true
  }

  const token = await modulos.messaging.getToken(messaging, {
    vapidKey: config.vapidKey,
    serviceWorkerRegistration: registro,
  })
  if (!token) throw new Error('Firebase no devolvió un token para este dispositivo.')

  const respuesta = await api('/api/push/registrar', {
    method: 'POST',
    token: jwt,
    body: { token, deviceId: obtenerDeviceId(), plataforma: 'web' },
  })
  if (!respuesta || respuesta.activo !== true) {
    throw new Error((respuesta && respuesta.mensaje) || 'El servidor no confirmó el registro.')
  }
  localStorage.setItem(CLAVE_TOKEN, token)
  return token
}

/** Activa el push en este dispositivo. Dos llamadas simultáneas comparten la misma promesa. */
export function activarPush(config, jwt) {
  if (activacionEnCurso) return activacionEnCurso
  activacionEnCurso = activarPushInterno(config, jwt).finally(() => {
    activacionEnCurso = null
  })
  return activacionEnCurso
}

/** Desactiva el dispositivo en el backend antes de cerrar sesión. No rompe el logout si falla. */
export async function desactivarPush(jwt) {
  const token = tokenPushGuardado()
  if (!token || !jwt) {
    localStorage.removeItem(CLAVE_TOKEN)
    return
  }
  try {
    await fetch('/api/push/registrar', {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${jwt}`,
      },
      body: JSON.stringify({ token, deviceId: obtenerDeviceId() }),
    })
  } catch {
    // El cierre de sesión local no debe depender de la red.
  }
  localStorage.removeItem(CLAVE_TOKEN)
}
