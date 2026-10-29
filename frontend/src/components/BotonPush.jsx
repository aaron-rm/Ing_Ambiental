import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth.jsx'
import {
  activarPush,
  cargarConfigPush,
  comprobarSoportePush,
  permisoNotificaciones,
} from '../push.js'

const MENSAJE_BLOQUEADO = 'El navegador bloqueó las notificaciones. Habilítalas en los permisos del sitio y recarga la página.'
const MENSAJE_ACTIVO = 'Activadas. Recibirás avisos de los ríos que sigues.'

export default function BotonPush() {
  const { token } = useAuth()
  const [config, setConfig] = useState(null)
  const [estado, setEstado] = useState('cargando')
  const [mensaje, setMensaje] = useState('')

  const registrar = useCallback(async (cfg) => {
    setEstado('activando')
    setMensaje('')
    try {
      await activarPush(cfg, token)
      setEstado('activo')
      setMensaje(MENSAJE_ACTIVO)
    } catch (e) {
      setEstado(permisoNotificaciones() === 'denied' ? 'bloqueado' : 'error')
      setMensaje(e.message)
    }
  }, [token])

  useEffect(() => {
    let activo = true

    const iniciar = async () => {
      const soporte = comprobarSoportePush()
      if (!soporte.ok) {
        if (activo) {
          setEstado('no-soportado')
          setMensaje(soporte.motivo)
        }
        return
      }

      try {
        const cfg = await cargarConfigPush()
        if (!activo) return
        setConfig(cfg)

        if (!cfg || !cfg.habilitado) {
          setEstado('no-disponible')
          setMensaje((cfg && cfg.motivo) || 'Las notificaciones no están habilitadas en el servidor.')
          return
        }
        if (permisoNotificaciones() === 'denied') {
          setEstado('bloqueado')
          setMensaje(MENSAJE_BLOQUEADO)
          return
        }
        if (permisoNotificaciones() === 'granted') {
          // Al abrir la app se vuelve a registrar el token. Así el dispositivo
          // queda asociado aunque se haya limpiado la base o cambiado de usuario.
          setEstado('activando')
          try {
            await activarPush(cfg, token)
            if (activo) {
              setEstado('activo')
              setMensaje(MENSAJE_ACTIVO)
            }
          } catch (e) {
            if (activo) {
              setEstado('inactivo')
              setMensaje(e.message)
            }
          }
          return
        }
        setEstado('inactivo')
      } catch (e) {
        if (activo) {
          setEstado('error')
          setMensaje(e.message)
        }
      }
    }

    iniciar()
    return () => {
      activo = false
    }
  }, [token])

  const puedeActivar = estado === 'inactivo' || estado === 'error'
  const etiqueta = {
    cargando: 'Comprobando…',
    activando: 'Activando…',
    activo: 'Notificaciones activadas',
    bloqueado: 'Notificaciones bloqueadas',
    'no-soportado': 'Notificaciones no disponibles',
    'no-disponible': 'Notificaciones no disponibles',
    error: 'Reintentar',
    inactivo: 'Activar notificaciones',
  }[estado] ?? 'Activar notificaciones'

  const reintentar = async () => {
    if (config?.habilitado) {
      registrar(config)
      return
    }
    setEstado('cargando')
    setMensaje('')
    try {
      const cfg = await cargarConfigPush()
      setConfig(cfg)
      if (!cfg?.habilitado) {
        setEstado('no-disponible')
        setMensaje((cfg && cfg.motivo) || 'Las notificaciones no están habilitadas en el servidor.')
        return
      }
      if (permisoNotificaciones() === 'denied') {
        setEstado('bloqueado')
        setMensaje(MENSAJE_BLOQUEADO)
        return
      }
      if (permisoNotificaciones() === 'granted') {
        await registrar(cfg)
        return
      }
      setEstado('inactivo')
    } catch (e) {
      setEstado('error')
      setMensaje(e.message)
    }
  }

  const conError = ['bloqueado', 'no-soportado', 'no-disponible', 'error'].includes(estado)
    || (estado === 'inactivo' && Boolean(mensaje))

  return (
    <section className="push">
      <h3>Notificaciones en este dispositivo</h3>
      {mensaje && <p className={conError ? 'error' : 'aviso'} role={conError ? 'alert' : 'status'}>{mensaje}</p>}
      <button
        type="button"
        className="btn btn--sec btn--chico"
        disabled={!puedeActivar}
        onClick={reintentar}
      >
        {etiqueta}
      </button>
    </section>
  )
}
