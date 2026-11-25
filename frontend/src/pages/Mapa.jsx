/**
 * @file Mapa.jsx
 * Página principal interactiva con mapa geográfico de Leaflet.
 * Presenta el catálogo de ríos con geometría GeoJSON, buscador predictivo,
 * puntos de referencia, selección de ríos, panel hidrológico lateral y registro de nuevos ríos.
 * Exporta el componente Mapa por defecto.
 */

import 'leaflet/dist/leaflet.css'
import L from 'leaflet'
import { Fragment, useCallback, useEffect, useState } from 'react'
import { CircleMarker, GeoJSON, MapContainer, TileLayer, Tooltip, useMap, useMapEvents, ZoomControl } from 'react-leaflet'
import { useNavigate } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import AgregarRio from '../components/AgregarRio.jsx'
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

// Leaflet programa un setTimeout de 250 ms para cerrar la animación de zoom.
// Si se cambia de ruta antes de que termine, ese callback intenta mover un
// mapPane que ya fue eliminado y lanza «Cannot read properties of undefined
// (reading '_leaflet_pos')». Al desmontar se anula la animación pendiente.
function LimpiarAnimacion() {
  const map = useMap()
  useEffect(() => () => {
    map._animatingZoom = false
  }, [map])
  return null
}

// Invalida el tamaño de Leaflet cuando el contenedor cambia de dimensiones (al abrir/cerrar panel o redimensionar).
function RedimensionarMapa() {
  const map = useMap()
  useEffect(() => {
    const contenedor = map.getContainer()
    if (!contenedor || typeof ResizeObserver === 'undefined') return
    const ro = new ResizeObserver(() => {
      map.invalidateSize()
    })
    ro.observe(contenedor)
    return () => ro.disconnect()
  }, [map])
  return null
}

function ClicMapa({ agregando, onClic }) {
  useMapEvents({
    click(e) {
      if (!agregando) return
      const lat = Number(e.latlng.lat.toFixed(5))
      const lng = Number(e.latlng.lng.toFixed(5))
      onClic({ lat, lng })
    },
  })
  return null
}

/**
 * Vista principal con mapa de ríos interactivo y panel lateral de monitoreo.
 *
 * @returns {JSX.Element} Estructura completa del mapa y sus paneles asociados.
 */
export default function Mapa() {
  const { token, usuario } = useAuth()
  const esAdmin = usuario?.rol === 'ADMIN'
  const navigate = useNavigate()
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
  const [agregando, setAgregando] = useState(false)
  const [punto, setPunto] = useState(null)

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
    setAgregando(false)
    setPunto(null)
    setAviso('')
    setLista(false)
    if (enfocar) enfocarRio(id)
  }

  const alternar = async (id) => {
    if (!token) {
      navigate(`/login?volver=${encodeURIComponent('/?rio=' + id)}`)
      return
    }
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

  const eliminarRio = async () => {
    if (!sel) return
    if (!window.confirm(`¿Eliminar "${selRio?.nombre}"? Se borrarán también sus muestras y quienes lo siguen dejarán de verlo. No se puede deshacer.`)) {
      return
    }
    setOcupado(true)
    setAviso('')
    try {
      await api(`/api/admin/rios/${sel}`, { method: 'DELETE', token })
      setRios((prev) => prev.filter((r) => r.rioId !== sel))
      setGeo((prev) => (prev ? { ...prev, features: prev.features?.filter((f) => f.properties?.rioId !== sel) ?? [] } : prev))
      setEstados((prev) => {
        const next = { ...prev }
        delete next[sel]
        return next
      })
      setSel(null)
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
        <button
          type="button"
          className="btn-agregar-rio"
          onClick={() => {
            setLista(false)
            if (!token) {
              navigate('/login?volver=/')
              return
            }
            setSel(null)
            setAgregando(true)
            setAviso('')
          }}
        >
          ＋ Agregar río
        </button>
        {errorCarga && <p className="error error--mapa" role="alert">{errorCarga}</p>}

        <MapContainer center={[9.0, -79.5]} zoom={10} className="mapa" zoomControl={false}>
          <ZoomControl position="bottomright" />
          <TileLayer attribution="&copy; colaboradores de OpenStreetMap" url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" />
          <LimpiarAnimacion />
          <RedimensionarMapa />
          <Enfocar destino={destino} />
          <ClicMapa agregando={agregando} onClic={(p) => setPunto(p)} />
          {agregando && punto && (
            <CircleMarker
              center={[punto.lat, punto.lng]}
              radius={10}
              pathOptions={{ color: '#d97706', weight: 4, fillColor: '#fef3c7', fillOpacity: 0.95 }}
            >
              <Tooltip permanent>Punto elegido</Tooltip>
            </CircleMarker>
          )}
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
                  style={{ color: v.trazo, weight: elegido ? 8 : 5, opacity: elegido ? 0.95 : 0.8, fill: false, lineCap: 'round' }}
                />
                <GeoJSON
                  key={`h-${id}`}
                  data={f}
                  style={{ color: '#000000', weight: 28, opacity: 0.001, fill: false }}
                  eventHandlers={{
                    click: (e) => {
                      if (agregando) {
                        setPunto({ lat: Number(e.latlng.lat.toFixed(5)), lng: Number(e.latlng.lng.toFixed(5)) })
                      } else {
                        elegir(id)
                      }
                    },
                  }}
                >
                  <Tooltip sticky>{f.properties.nombre}</Tooltip>
                </GeoJSON>
              </Fragment>
            )
          })}
          {rios
            .filter((r) => r.latitud != null && r.longitud != null)
            .map((r) => (
              <CircleMarker
                key={`p-${r.rioId}`}
                center={[r.latitud, r.longitud]}
                radius={9}
                pathOptions={{ color: visual(estados[r.rioId]?.dto).trazo, weight: 4, fillColor: '#ffffff', fillOpacity: 1 }}
                eventHandlers={{
                  click: (e) => {
                    if (agregando) {
                      setPunto({ lat: Number(e.latlng.lat.toFixed(5)), lng: Number(e.latlng.lng.toFixed(5)) })
                    } else {
                      elegir(r.rioId)
                    }
                  },
                }}
              >
                <Tooltip>
                  {r.nombre}: punto de referencia del pronóstico (ubicación aproximada)
                  {r.reachId ? ` · tramo GEOGLOWS #${r.reachId}` : ''}
                </Tooltip>
              </CircleMarker>
            ))}
        </MapContainer>
        <div
          style={{ position: 'absolute', left: 12, bottom: 12, zIndex: 1000, background: '#fff', padding: '6px 10px', borderRadius: 8, fontSize: 12, boxShadow: '0 1px 6px rgba(0,0,0,.25)', display: 'flex', alignItems: 'center', gap: 6, maxWidth: 'calc(100% - 24px)' }}
        >
          <span aria-hidden="true" style={{ width: 12, height: 12, borderRadius: '50%', border: '3px solid #12302F', background: '#fff', display: 'inline-block', flexShrink: 0 }} />
          Punto de referencia del pronóstico (ubicación aproximada)
        </div>
      </div>

      <aside className={`panel ${sel || agregando ? 'panel--abierto' : ''} ${agregando ? 'panel--agregar' : ''}`} aria-label={agregando ? 'Agregar río' : 'Información del río'}>
        {agregando ? (
          <>
            <button
              type="button"
              className="panel__cerrar"
              aria-label="Cerrar panel"
              onClick={() => {
                setAgregando(false)
                setPunto(null)
              }}
            >
              ×
            </button>
            <AgregarRio
              punto={punto}
              onPuntoChange={setPunto}
              onCreado={async (nuevo) => {
                setRios((prev) => [...prev, nuevo])
                cargarEstado(nuevo.rioId)
                elegir(nuevo.rioId)
                if (nuevo?.latitud != null && nuevo?.longitud != null) {
                  setDestino({ centro: [nuevo.latitud, nuevo.longitud] })
                }
                try {
                  const mapa = await api('/api/rios/mapa')
                  setGeo(mapa)
                  const f = mapa?.features?.find((x) => x.properties?.rioId === nuevo.rioId)
                  if (f) {
                    const b = L.geoJSON(f).getBounds()
                    if (b.isValid()) setDestino({ bounds: b })
                  }
                } catch {
                  // Si falla la petición, se queda como está
                }
              }}
              onCancelar={() => {
                setAgregando(false)
                setPunto(null)
              }}
            />
          </>
        ) : sel ? (
          <>
            <button type="button" className="panel__cerrar" aria-label="Cerrar panel" onClick={() => setSel(null)}>×</button>
            <PanelRio
              rioId={sel}
              nombre={selRio?.nombre ?? 'Río'}
              estado={est?.dto}
              cargando={est?.cargando}
              error={est?.error}
              onReintentar={() => cargarEstado(sel)}
              siguiendo={Boolean(est?.dto?.siguiendo)}
              ocupado={ocupado}
              onAlternar={() => alternar(sel)}
              mostrarPush={Boolean(token)}
            />
            {esAdmin && (
              <button type="button" className="btn btn--peligro btn--chico" style={{ display: 'block', margin: '12px auto 16px' }} disabled={ocupado} onClick={eliminarRio}>
                Eliminar río
              </button>
            )}
            {aviso && <p className="error panel__aviso" role="alert">{aviso}</p>}
          </>
        ) : (
          <p className="panel__vacio">Toca un río resaltado en el mapa, o búscalo arriba, para ver su estado.</p>
        )}
      </aside>
    </div>
  )
}
