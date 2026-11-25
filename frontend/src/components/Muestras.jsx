import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'

function ahoraLocal() {
  const d = new Date()
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
  return d.toISOString().slice(0, 16)
}

function fechaLocalCorta(iso) {
  if (!iso) return ''
  try {
    const d = new Date(iso)
    return d.toLocaleDateString(undefined, {
      day: '2-digit',
      month: '2-digit',
      year: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}

export default function Muestras({ rioId }) {
  const { usuario, token } = useAuth()
  const [muestras, setMuestras] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')

  // Formulario manual
  const [fecha, setFecha] = useState(ahoraLocal)
  const [parametro, setParametro] = useState('')
  const [valor, setValor] = useState('')
  const [unidad, setUnidad] = useState('')
  const [nota, setNota] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [errorForm, setErrorForm] = useState('')

  // CSV
  const [subiendoCsv, setSubiendoCsv] = useState(false)
  const [msgCsv, setMsgCsv] = useState('')
  const [errorCsv, setErrorCsv] = useState('')

  const puedeEditar = usuario?.rol === 'ADMIN' || usuario?.rol === 'INVESTIGADOR'

  const cargarMuestras = useCallback(() => {
    if (!rioId) return
    setCargando(true)
    setError('')
    api(`/api/rios/${rioId}/muestras`)
      .then((data) => {
        setMuestras(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        setError(err.message || 'Error al cargar muestras')
      })
      .finally(() => {
        setCargando(false)
      })
  }, [rioId])

  useEffect(() => {
    let activo = true
    if (!rioId) return
    setCargando(true)
    setError('')
    api(`/api/rios/${rioId}/muestras`)
      .then((data) => {
        if (!activo) return
        setMuestras(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        if (!activo) return
        setError(err.message || 'Error al cargar muestras')
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [rioId])

  const guardarMuestra = async (e) => {
    e.preventDefault()
    if (!parametro.trim() || valor === '') {
      setErrorForm('Indica el parámetro y el valor.')
      return
    }
    const valNum = Number(valor)
    if (isNaN(valNum)) {
      setErrorForm('El valor debe ser un número válido.')
      return
    }

    setGuardando(true)
    setErrorForm('')
    try {
      await api(`/api/rios/${rioId}/muestras`, {
        method: 'POST',
        token,
        body: {
          fecha: fecha || ahoraLocal(),
          parametro: parametro.trim(),
          valor: valNum,
          unidad: unidad.trim() || undefined,
          nota: nota.trim() || undefined,
        },
      })
      setParametro('')
      setValor('')
      setUnidad('')
      setNota('')
      setFecha(ahoraLocal())
      cargarMuestras()
    } catch (err) {
      setErrorForm(err.message || 'Error al registrar muestra')
    } finally {
      setGuardando(false)
    }
  }

  const subirCsv = async (e) => {
    const input = e.target
    const file = input?.files?.[0]
    if (!file) return
    setSubiendoCsv(true)
    setMsgCsv('')
    setErrorCsv('')
    try {
      const texto = await file.text()
      const resp = await api(`/api/rios/${rioId}/muestras/csv`, {
        method: 'POST',
        token,
        raw: texto,
        contentType: 'text/csv',
      })
      const n = resp?.insertadas ?? resp
      setMsgCsv(`${n} muestra${n === 1 ? '' : 's'} guardada${n === 1 ? '' : 's'}`)
      cargarMuestras()
    } catch (err) {
      setErrorCsv(err.message || 'Error al procesar el archivo CSV')
    } finally {
      if (input) input.value = ''
      setSubiendoCsv(false)
    }
  }

  const descargarPlantilla = (e) => {
    e.preventDefault()
    const contenido = 'fecha,parametro,valor,unidad,nota\n2026-11-24T12:00:00Z,pH,7.2,,Muestra en puente\n2026-11-24T12:00:00Z,Caudal,12.5,m³/s,Estación hidrológica\n'
    const blob = new Blob([contenido], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `plantilla_muestras_rio_${rioId}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <section className="muestras-seccion">
      <h3 className="muestras-titulo">Muestras y mediciones</h3>

      {cargando && <p className="aviso">Cargando muestras…</p>}
      {error && <p className="error" role="alert">{error}</p>}

      {!cargando && muestras.length === 0 && !error && (
        <p className="aviso">Aún no hay muestras registradas.</p>
      )}

      {muestras.length > 0 && (
        <div className="tabla-wrap">
          <table className="tabla tabla--muestras">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Parámetro</th>
                <th>Valor</th>
                <th>Responsable</th>
                <th>Nota</th>
              </tr>
            </thead>
            <tbody>
              {muestras.map((m) => (
                <tr key={m.id}>
                  <td>{fechaLocalCorta(m.fecha)}</td>
                  <td><strong>{m.parametro}</strong></td>
                  <td>{m.valor} {m.unidad || ''}</td>
                  <td>{m.responsable || '—'}</td>
                  <td>{m.nota || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {puedeEditar && (
        <div className="muestras-edicion">
          <h4 style={{ margin: '14px 0 6px', fontSize: 15 }}>Registrar muestra</h4>
          <form className="form-muestra" onSubmit={guardarMuestra}>
            <div className="form-fila">
              <div className="campo">
                <label htmlFor={`m-fecha-${rioId}`}>Fecha y hora</label>
                <input
                  id={`m-fecha-${rioId}`}
                  type="datetime-local"
                  value={fecha}
                  onChange={(e) => setFecha(e.target.value)}
                  disabled={guardando}
                  required
                />
              </div>
              <div className="campo">
                <label htmlFor={`m-param-${rioId}`}>Parámetro</label>
                <input
                  id={`m-param-${rioId}`}
                  list="params-sug"
                  placeholder="Parámetro"
                  value={parametro}
                  onChange={(e) => setParametro(e.target.value)}
                  disabled={guardando}
                  required
                />
                <datalist id="params-sug">
                  <option value="Caudal" />
                  <option value="Nivel" />
                  <option value="pH" />
                  <option value="Turbidez" />
                  <option value="Oxígeno disuelto" />
                  <option value="Temperatura" />
                  <option value="Conductividad" />
                  <option value="Coliformes" />
                </datalist>
              </div>
            </div>

            <div className="form-fila">
              <div className="campo">
                <label htmlFor={`m-valor-${rioId}`}>Valor</label>
                <input
                  id={`m-valor-${rioId}`}
                  type="number"
                  step="any"
                  placeholder="Valor"
                  value={valor}
                  onChange={(e) => setValor(e.target.value)}
                  disabled={guardando}
                  required
                />
              </div>
              <div className="campo">
                <label htmlFor={`m-unidad-${rioId}`}>Unidad</label>
                <input
                  id={`m-unidad-${rioId}`}
                  list="unidades-sug"
                  placeholder="Unidad (opcional)"
                  value={unidad}
                  onChange={(e) => setUnidad(e.target.value)}
                  disabled={guardando}
                />
                <datalist id="unidades-sug">
                  <option value="m³/s" />
                  <option value="m" />
                  <option value="NTU" />
                  <option value="mg/L" />
                  <option value="°C" />
                  <option value="µS/cm" />
                  <option value="NMP/100mL" />
                </datalist>
              </div>
            </div>

            <div className="campo">
              <label htmlFor={`m-nota-${rioId}`}>Nota</label>
              <input
                id={`m-nota-${rioId}`}
                type="text"
                placeholder="Observaciones (opcional)"
                value={nota}
                onChange={(e) => setNota(e.target.value)}
                disabled={guardando}
              />
            </div>

            {errorForm && <p className="error" role="alert">{errorForm}</p>}

            <button type="submit" className="btn btn--chico" disabled={guardando}>
              {guardando ? 'Guardando…' : 'Guardar muestra'}
            </button>
          </form>

          <div className="subir-csv-wrap" style={{ marginTop: 14 }}>
            <h4 style={{ margin: '0 0 6px', fontSize: 15 }}>Subir CSV</h4>
            <input
              type="file"
              accept=".csv,text/csv"
              onChange={subirCsv}
              disabled={subiendoCsv}
            />
            {subiendoCsv && <p className="aviso">Procesando archivo CSV…</p>}
            {msgCsv && <p className="aviso" role="status">{msgCsv}</p>}
            {errorCsv && <p className="error" role="alert" style={{ whiteSpace: 'pre-wrap' }}>{errorCsv}</p>}

            <p className="k" style={{ marginTop: 8 }}>
              Cabecera esperada: <code>fecha,parametro,valor,unidad,nota</code> ·{' '}
              <a href="#descargar" onClick={descargarPlantilla}>Descargar plantilla</a>
            </p>
          </div>
        </div>
      )}
    </section>
  )
}
