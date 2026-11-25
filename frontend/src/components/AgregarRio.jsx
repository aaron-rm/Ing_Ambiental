import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'

export default function AgregarRio({ punto, onPuntoChange, onCreado, onCancelar }) {
  const { token } = useAuth()
  const [nombre, setNombre] = useState('')
  const [latitud, setLatitud] = useState(punto ? String(punto.lat) : '')
  const [longitud, setLongitud] = useState(punto ? String(punto.lng) : '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!punto) return
    const pLat = punto.lat
    const pLng = punto.lng ?? punto.lon
    if (pLat != null) {
      setLatitud((prev) => {
        if (prev === '') return String(pLat)
        if (prev === '-' || prev === '.' || prev === '-.') return prev
        const n = Number(prev)
        if (!Number.isNaN(n) && n === pLat) return prev
        return String(pLat)
      })
    }
    if (pLng != null) {
      setLongitud((prev) => {
        if (prev === '') return String(pLng)
        if (prev === '-' || prev === '.' || prev === '-.') return prev
        const n = Number(prev)
        if (!Number.isNaN(n) && n === pLng) return prev
        return String(pLng)
      })
    }
  }, [punto])

  const actualizarPunto = (latStr, lngStr) => {
    const lat = Number(latStr)
    const lng = Number(lngStr)
    if (!isNaN(lat) && !isNaN(lng) && latStr !== '' && lngStr !== '') {
      onPuntoChange?.({ lat, lng })
    }
  }

  const usarUbicacion = () => {
    if (!navigator.geolocation) return
    setError('')
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = Number(pos.coords.latitude.toFixed(5))
        const lng = Number(pos.coords.longitude.toFixed(5))
        setLatitud(String(lat))
        setLongitud(String(lng))
        onPuntoChange?.({ lat, lng })
      },
      (err) => {
        setError(`No se pudo obtener la ubicación: ${err.message || 'permiso denegado'}`)
      },
    )
  }

  const enviar = async (e) => {
    e.preventDefault()
    const nom = nombre.trim()
    if (!nom) {
      setError('Escribe el nombre del río.')
      return
    }
    const latNum = Number(latitud)
    const lngNum = Number(longitud)
    if (isNaN(latNum) || isNaN(lngNum) || latitud === '' || longitud === '') {
      setError('Ingresa coordenadas válidas.')
      return
    }
    if (latNum < -90 || latNum > 90) {
      setError('La latitud debe estar entre -90 y 90.')
      return
    }
    if (lngNum < -180 || lngNum > 180) {
      setError('La longitud debe estar entre -180 y 180.')
      return
    }

    setGuardando(true)
    setError('')
    try {
      const nuevo = await api('/api/rios', {
        method: 'POST',
        token,
        body: {
          nombre: nom,
          latitud: latNum,
          longitud: lngNum,
        },
      })
      onCreado?.(nuevo)
    } catch (err) {
      setError(err.message || 'Error al agregar río')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="panel__cuerpo">
      <h2 className="panel__titulo">Agregar río</h2>
      <p className="k">Registra un nuevo río indicando sus coordenadas para asociar el pronóstico de GEOGLOWS.</p>

      <form className="form-rio" onSubmit={enviar}>
        <label htmlFor="rio-nombre">Nombre del río</label>
        <input
          id="rio-nombre"
          type="text"
          placeholder="Ej. Río Caimito"
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          disabled={guardando}
          required
        />

        <label htmlFor="rio-lat">Latitud</label>
        <input
          id="rio-lat"
          type="number"
          step="any"
          placeholder="Ej. 8.87512"
          value={latitud}
          onChange={(e) => {
            setLatitud(e.target.value)
            actualizarPunto(e.target.value, longitud)
          }}
          disabled={guardando}
          required
        />

        <label htmlFor="rio-lng">Longitud</label>
        <input
          id="rio-lng"
          type="number"
          step="any"
          placeholder="Ej. -79.76123"
          value={longitud}
          onChange={(e) => {
            setLongitud(e.target.value)
            actualizarPunto(latitud, e.target.value)
          }}
          disabled={guardando}
          required
        />

        <p className="k">o toca el mapa para elegir el punto</p>

        {typeof navigator !== 'undefined' && 'geolocation' in navigator && (
          <button
            type="button"
            className="btn btn--sec btn--chico"
            onClick={usarUbicacion}
            disabled={guardando}
          >
            Usar mi ubicación
          </button>
        )}

        {guardando && (
          <>
            <p className="aviso">Buscando el tramo de GEOGloWS…</p>
            <div className="carga" role="progressbar" aria-label="Buscando el tramo de GEOGloWS" aria-busy="true">
              <div className="carga__barra" />
            </div>
          </>
        )}

        {error && <p className="error" role="alert">{error}</p>}

        <button type="submit" className="btn" disabled={guardando}>
          {guardando ? 'Guardando…' : 'Guardar río'}
        </button>

        {onCancelar && (
          <button
            type="button"
            className="btn btn--sec"
            onClick={onCancelar}
            disabled={guardando}
          >
            Cancelar
          </button>
        )}
      </form>
    </div>
  )
}
