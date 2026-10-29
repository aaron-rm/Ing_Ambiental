import { useMemo, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { fechaCorta, fmt, visual } from '../estados.js'
import Medidor from './Medidor.jsx'
import Pronostico from './Pronostico.jsx'

function Lista({ titulo, items, clase }) {
  if (!items?.length) return null
  return (
    <section className={`lista ${clase ?? ''}`}>
      <h3>{titulo}</h3>
      <ul>
        {items.map((t) => (
          <li key={t}>{t}</li>
        ))}
      </ul>
    </section>
  )
}

export default function PanelRio({ nombre, estado: d, cargando, error, onReintentar, siguiendo, ocupado, onAlternar }) {
  const { usuario, token } = useAuth()
  const [simulando, setSimulando] = useState(false)
  const [resultadoSimulacion, setResultadoSimulacion] = useState('')
  const [errorSimulacion, setErrorSimulacion] = useState('')
  const v = visual(d)
  const diaPico = useMemo(
    () => (d?.pronosticoDiario ?? []).reduce((m, x) => (!m || x.caudalMaximo > m.caudalMaximo ? x : m), null),
    [d],
  )

  if (!d) {
    return (
      <div className="panel__cuerpo">
        <h2 className="panel__titulo">{nombre}</h2>
        {cargando && <p className="aviso">Consultando el pronóstico… puede tardar unos segundos.</p>}
        {error && (
          <>
            <p className="error" role="alert">{error}</p>
            <button type="button" className="btn btn--sec" onClick={onReintentar}>Reintentar</button>
          </>
        )}
      </div>
    )
  }

  const peligro = d.nivelAlerta === 'ALERTA' || d.nivelAlerta === 'EMERGENCIA'
  const caudalGrafico = diaPico?.caudalMaximo ?? d.caudalMaximoPronosticado
  const esAdmin = usuario?.rol === 'ADMIN'

  const simular = async () => {
    if (!d.reachId || simulando) return
    const nombreSimulado = d.nombreRio || nombre
    const confirmar = window.confirm(
      `¿Simular una crecida en ${nombreSimulado}? Se enviará una notificación a los dispositivos que siguen este río.`,
    )
    if (!confirmar) return

    setSimulando(true)
    setResultadoSimulacion('')
    setErrorSimulacion('')
    try {
      const r = await api(`/api/alertas/simular?reachId=${d.reachId}`, { method: 'POST', token })
      if (r.dispositivos === 0) {
        setResultadoSimulacion('Simulación ejecutada, pero no hay dispositivos con notificaciones activas para este río.')
      } else {
        setResultadoSimulacion(
          `Simulación enviada a ${r.dispositivos} dispositivo(s): ${r.enviados} entregada(s), ${r.fallidos} fallida(s).`,
        )
      }
    } catch (e) {
      setErrorSimulacion(e.message)
    } finally {
      setSimulando(false)
    }
  }

  return (
    <div className="panel__cuerpo" style={{ '--est-color': v.color, '--est-fondo': v.fondo }}>
      {peligro && d.datosConfiables !== false ? (
        <div className="banner" role="alert">
          <span aria-hidden="true">⚠</span> {v.etiqueta.toUpperCase()}
        </div>
      ) : (
        <span className="insignia">{v.etiqueta.toUpperCase()}</span>
      )}
      {d.simulacion && <span className="insignia insignia--sim">SIMULACIÓN</span>}
      <h2 className="panel__titulo">{d.nombreRio || nombre}</h2>

      {esAdmin && (
        <>
          <p className="k">
            Administración: simular una crecida envía una notificación real a los dispositivos suscritos a este río.
          </p>
          <button type="button" className="btn btn--peligro" onClick={simular} disabled={simulando}>
            {simulando ? 'Simulando crecida…' : 'Simular crecida y notificar'}
          </button>
          {resultadoSimulacion && <p className="aviso" role="status">{resultadoSimulacion}</p>}
          {errorSimulacion && <p className="error" role="alert">{errorSimulacion}</p>}
        </>
      )}

      <div className="cifras">
        <div><p className="k">Caudal actual</p><p className="v">{fmt(d.caudalActualOInicial)} m³/s</p></div>
        <div><p className="k">Pico pronosticado</p><p className="v">{fmt(d.caudalMaximoPronosticado)} m³/s</p></div>
        <div><p className="k">Lluvia 24 h</p><p className="v v--chico">{fmt(d.lluvia24hMm, 0)} mm</p></div>
        <div><p className="k">Lluvia 72 h</p><p className="v v--chico">{fmt(d.lluvia72hMm, 0)} mm</p></div>
      </div>

      <p className="k">
        Día de mayor caudal{diaPico ? `: ${fechaCorta(diaPico.fecha)}` : ''} (nivel relativo)
      </p>
      <Medidor caudal={caudalGrafico} t1={d.umbralVigilancia} t2={d.umbralAlerta} t3={d.umbralEmergencia} color={v.trazo} />

      <Lista titulo="Advertencias" items={d.advertencias} />
      <Lista titulo="Recomendaciones" items={d.recomendaciones} clase={peligro ? 'lista--peligro' : ''} />

      <p className="k">Caudal máximo previsto por día</p>
      <Pronostico dias={d.pronosticoDiario} t1={d.umbralVigilancia} t2={d.umbralAlerta} t3={d.umbralEmergencia} />

      {onAlternar && (
        <button
          type="button"
          className={`btn ${siguiendo ? 'btn--sec' : peligro ? 'btn--peligro' : ''}`}
          disabled={ocupado}
          onClick={onAlternar}
        >
          {ocupado ? 'Guardando…' : siguiendo ? 'Dejar de seguir este río' : 'Suscribirme a este río'}
        </button>
      )}
    </div>
  )
}
