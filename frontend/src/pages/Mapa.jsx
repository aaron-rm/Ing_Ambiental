import 'leaflet/dist/leaflet.css'
import L from 'leaflet'
import { Fragment, useCallback, useEffect, useState } from 'react'
import { GeoJSON, MapContainer, TileLayer, Tooltip, useMap } from 'react-leaflet'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import PanelRio from '../components/PanelRio.jsx'
import { visual } from '../estados.js'

const norm = (s) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()

// Mueve el mapa cuando cambia el destino (buscar un río o cargar el mapa).
function Enfocar({ destino }) {
  const map = useMap()
  useEffect(() => {
    if (!destino) return
    if (destino.bounds) map.fitBounds(destino.bounds, { padding: [48, 48], maxZoom: 14 })
    else if (destino.centro) map.flyTo(destino.centro, 13)
  }, [destino, map])
  return null
}

export default function Mapa() {
  const { token } = useAuth()
  const [geo, setGeo] = useState(null)
  const [rios, setRios] = useState([])
  const [errorCarga, setErrorCarga] = useState('')
  const [estados, setEstados] = useState({})
  const [sel, setSel] = useState(() => Number(new URLSearchParams(window.location.search).get('rio')) || null)
  const [destino, setDestino] = useState(null)
  const [consulta, setConsulta] = useState('')
  const [lista, setLista] = useState(false)
  const [ocupado, setOcupado] = useState(false)
  const [aviso, setAviso] = useState('')

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
    Promise.all([api('/api/rios'), api('/api/rios/mapa')])
      .then(([listaRios, mapa]) => {
        if (!activo) return
        setRios(listaRios)
        setGeo(mapa)
        const inicial = Number(new URLSearchParams(window.location.search).get('rio')) || null
        const f = mapa.features?.find((x) => x.properties.rioId === inicial)
        const b = L.geoJSON(f ?? mapa).getBounds()
        if (b.isValid()) setDestino({ bounds: b })
        listaRios.forEach((r) => cargarEstado(r.rioId))
      })
      .catch((e) => activo && setErrorCarga(e.message))
    return () => {
      activo = false
    }
  }, [cargarEstado])

  const enfocarRio = (id) => {
    const f = geo?.features?.find((x) => x.properties.rioId === id)
    if (f) {
      const b = L.geoJSON(f).getBounds()
      if (b.isValid()) return setDestino({ bounds: b })
    }
    const r = rios.find((x) => x.rioId === id)
    if (r?.latitud != null) setDestino({ centro: [r.latitud, r.longitud] })
  }

  const elegir = (id, enfocar = false) => {
    setSel(id)
    setAviso('')
    setLista(false)
    if (enfocar) enfocarRio(id)
  }

  const alternar = async (id) => {
    const siguiendo = Boolean(estados[id]?.dto?.siguiendo)
    setOcupado(true)
    setAviso('')
    try {
      await api(`/api/usuarios/me/rios/${id}`, { method: siguiendo ? 'DELETE' : 'POST', token })
      setEstados((e) => ({ ...e, [id]: { ...e[id], dto: { ...e[id].dto, siguiendo: !siguiendo } } }))
    } catch (err) {
      setAviso(err.message)
    } finally {
      setOcupado(false)
    }
  }

  const sugerencias = rios.filter((r) => norm(r.nombre).includes(norm(consulta)))
  const selRio = rios.find((r) => r.rioId === sel)
  const est = sel ? estados[sel] : null

  return (
    <div className="mapa-layout">
      <div className="mapa-wrap">
        <div
          className="buscador"
          onBlur={(e) => !e.currentTarget.contains(e.relatedTarget) && setLista(false)}
        >
          <input
            type="search"
            placeholder="Buscar río..."
            aria-label="Buscar río"
            autoComplete="off"
            value={consulta}
            onChange={(e) => {
              setConsulta(e.target.value)
              setLista(true)
            }}
            onFocus={() => setLista(true)}
            onKeyDown={(e) => e.key === 'Escape' && setLista(false)}
          />
          {lista && sugerencias.length > 0 && (
            <ul className="sugerencias">
              {sugerencias.map((r) => (
                <li key={r.rioId}>
                  <button
                    type="button"
                    onClick={() => {
                      setConsulta(r.nombre)
                      elegir(r.rioId, true)
                    }}
                  >
                    {r.nombre}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        {errorCarga && <p className="error error--mapa" role="alert">{errorCarga}</p>}

        <MapContainer center={[9.0, -79.5]} zoom={10} className="mapa">
          <TileLayer attribution="&copy; colaboradores de OpenStreetMap" url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" />
          <Enfocar destino={destino} />
          {geo?.features?.map((f) => {
            const id = f.properties.rioId
            const v = visual(estados[id]?.dto)
            const elegido = sel === id
            return (
              <Fragment key={id}>
                <GeoJSON
                  key={`v-${id}-${v.trazo}-${elegido}`}
                  data={f}
                  interactive={false}
                  style={{ color: v.trazo, weight: elegido ? 9 : 6, opacity: 1, fill: false, lineCap: 'round' }}
                />
                <GeoJSON
                  key={`h-${id}`}
                  data={f}
                  style={{ color: '#000000', weight: 28, opacity: 0.001, fill: false }}
                  eventHandlers={{ click: () => elegir(id) }}
                >
                  <Tooltip sticky>{f.properties.nombre}</Tooltip>
                </GeoJSON>
              </Fragment>
            )
          })}
        </MapContainer>
      </div>

      <aside className={`panel ${sel ? 'panel--abierto' : ''}`} aria-label="Información del río">
        {sel ? (
          <>
            <button type="button" className="panel__cerrar" aria-label="Cerrar panel" onClick={() => setSel(null)}>×</button>
            <PanelRio
              nombre={selRio?.nombre ?? 'Río'}
              estado={est?.dto}
              cargando={est?.cargando}
              error={est?.error}
              onReintentar={() => cargarEstado(sel)}
              siguiendo={Boolean(est?.dto?.siguiendo)}
              ocupado={ocupado}
              onAlternar={() => alternar(sel)}
            />
            {aviso && <p className="error panel__aviso" role="alert">{aviso}</p>}
          </>
        ) : (
          <p className="panel__vacio">Toca un río resaltado en el mapa, o búscalo arriba, para ver su estado.</p>
        )}
      </aside>
    </div>
  )
}
