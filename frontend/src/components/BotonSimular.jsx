import { useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'

const NOTA = 'Administración: simular una crecida envía una notificación real a los dispositivos suscritos a este río.'

export default function BotonSimular({ reachId, nombre, compacto = false, nota = false, onMensaje }) {
  const { token } = useAuth()
  const [simulando, setSimulando] = useState(false)
  const [resultado, setResultado] = useState('')
  const [error, setError] = useState('')

  const avisar = (mensaje) => {
    if (onMensaje) {
      onMensaje(mensaje)
      return
    }
    if (mensaje && mensaje.tipo === 'error') {
      setError(mensaje.texto)
      setResultado('')
    } else {
      setResultado((mensaje && mensaje.texto) || '')
      setError('')
    }
  }

  const simular = async () => {
    if (!reachId || simulando) return
    const confirmar = window.confirm(
      `¿Simular una crecida en ${nombre}? Se enviará una notificación a los dispositivos que siguen este río.`,
    )
    if (!confirmar) return

    setSimulando(true)
    setResultado('')
    setError('')
    if (onMensaje) onMensaje(null)
    try {
      const r = await api(`/api/alertas/simular?reachId=${reachId}`, { method: 'POST', token })
      if (r.dispositivos === 0) {
        avisar({ tipo: 'ok', texto: 'Simulación ejecutada, pero no hay dispositivos con notificaciones activas para este río.' })
      } else {
        avisar({
          tipo: 'ok',
          texto: `Simulación enviada a ${r.dispositivos} dispositivo(s): ${r.enviados} entregada(s), ${r.fallidos} fallida(s).`,
        })
      }
    } catch (e) {
      avisar({ tipo: 'error', texto: e.message })
    } finally {
      setSimulando(false)
    }
  }

  return (
    <div className={`simular ${compacto ? 'simular--compacto' : ''}`}>
      {nota && <p className="k">{NOTA}</p>}
      <button
        type="button"
        className={`btn btn--peligro ${compacto ? 'btn--chico' : ''}`}
        onClick={simular}
        disabled={simulando || !reachId}
      >
        {simulando ? 'Simulando…' : compacto ? 'Simular crecida' : 'Simular crecida y notificar'}
      </button>
      {!onMensaje && resultado && <p className="aviso" role="status">{resultado}</p>}
      {!onMensaje && error && <p className="error" role="alert">{error}</p>}
    </div>
  )
}
