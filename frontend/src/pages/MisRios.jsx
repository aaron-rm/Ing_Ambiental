import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import PanelRio from '../components/PanelRio.jsx'
import { fmt, visual } from '../estados.js'

export default function MisRios() {
  const { token } = useAuth()
  const [lista, setLista] = useState(null)
  const [estados, setEstados] = useState({})
  const [abierto, setAbierto] = useState(null)
  const [error, setError] = useState('')

  const cargarEstado = useCallback(
    (rioId) => {
      setEstados((e) => ({ ...e, [rioId]: { ...e[rioId], cargando: true, error: '' } }))
      api(`/api/alertas/rio/${rioId}`, { token })
        .then((dto) => setEstados((e) => ({ ...e, [rioId]: { dto, cargando: false, error: '' } })))
        .catch((err) => setEstados((e) => ({ ...e, [rioId]: { ...e[rioId], cargando: false, error: err.message } })))
    },
    [token],
  )

  useEffect(() => {
    let activo = true
    api('/api/usuarios/me/rios', { token })
      .then((l) => {
        if (!activo) return
        setLista(l)
        l.forEach((r) => cargarEstado(r.rioId))
      })
      .catch((e) => activo && setError(e.message))
    return () => {
      activo = false
    }
  }, [token, cargarEstado])

  const dejar = async (id) => {
    try {
      await api(`/api/usuarios/me/rios/${id}`, { method: 'DELETE', token })
      setLista((l) => l.filter((r) => r.rioId !== id))
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <section className="pagina">
      <h1>Mis ríos</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {lista === null && !error && <p className="aviso">Cargando…</p>}
      {lista?.length === 0 && (
        <p className="aviso">
          Todavía no sigues ningún río. <Link to="/">Ve al mapa</Link> y toca uno para suscribirte.
        </p>
      )}
      <ul className="tarjetas">
        {lista?.map((r) => {
          const e = estados[r.rioId]
          const v = visual(e?.dto)
          const abre = abierto === r.rioId
          return (
            <li key={r.rioId} className="tarjeta">
              <div className="tarjeta__cab">
                <h2>{r.nombre}</h2>
                <span className="insignia" style={{ '--est-fondo': v.fondo, '--est-color': v.color }}>{v.etiqueta}</span>
              </div>
              <p className="k">
                Caudal actual {fmt(e?.dto?.caudalActualOInicial)} m³/s · pico previsto {fmt(e?.dto?.caudalMaximoPronosticado)} m³/s
              </p>
              <div className="tarjeta__acciones">
                <button type="button" className="btn btn--sec btn--chico" aria-expanded={abre} onClick={() => setAbierto(abre ? null : r.rioId)}>
                  {abre ? 'Ocultar detalle' : 'Ver estado y pronóstico'}
                </button>
                <Link className="btn btn--sec btn--chico" to={`/?rio=${r.rioId}`}>Ver en el mapa</Link>
                <button type="button" className="btn btn--sec btn--chico" onClick={() => dejar(r.rioId)}>Dejar de seguir</button>
              </div>
              {abre && (
                <PanelRio
                  nombre={r.nombre}
                  estado={e?.dto}
                  cargando={e?.cargando}
                  error={e?.error}
                  onReintentar={() => cargarEstado(r.rioId)}
                />
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}
