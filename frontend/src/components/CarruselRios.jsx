/**
 * @file CarruselRios.jsx
 * Cinta deslizante sobre el mapa con el nombre de cada río y su estado de alerta.
 * Se desplaza sola y se pausa al pasar el cursor o enfocar un elemento. La flecha
 * del extremo derecho despliega la lista completa de ríos registrados.
 * Cada elemento selecciona su río.
 * Exporta el componente CarruselRios por defecto.
 */

import { useEffect, useRef, useState } from 'react'
import { visualRio } from '../estados.js'

/**
 * @param {Object} props
 * @param {Array<{rioId: number, nombre: string}>} props.rios - Catálogo de ríos.
 * @param {Object.<number, {dto?: Object}>} props.estados - Estado cargado de cada río.
 * @param {number|null} props.sel - Río seleccionado.
 * @param {(rioId: number) => void} props.onElegir - Se llama al tocar un río.
 * @returns {JSX.Element|null}
 */
export default function CarruselRios({ rios, estados, sel, onElegir }) {
  const [abierto, setAbierto] = useState(false)
  const raiz = useRef(null)

  // Cierra la lista al tocar fuera o pulsar Escape.
  useEffect(() => {
    if (!abierto) return
    const fuera = (e) => raiz.current && !raiz.current.contains(e.target) && setAbierto(false)
    const tecla = (e) => e.key === 'Escape' && setAbierto(false)
    document.addEventListener('pointerdown', fuera)
    document.addEventListener('keydown', tecla)
    return () => {
      document.removeEventListener('pointerdown', fuera)
      document.removeEventListener('keydown', tecla)
    }
  }, [abierto])

  if (!rios.length) return null

  const elegir = (id) => {
    setAbierto(false)
    onElegir(id)
  }

  const contenido = (r, v) => (
    <>
      <span className="carrusel__punto" style={{ background: v.trazo }} aria-hidden="true" />
      <span className="carrusel__nombre">{r.nombre}</span>
      <span className="carrusel__estado" style={{ color: v.color, background: v.fondo }}>
        {v.etiqueta}
      </span>
    </>
  )

  const elementos = (copia) =>
    rios.map((r) => {
      const v = visualRio(estados[r.rioId])
      return (
        <li key={`${copia}-${r.rioId}`}>
          <button
            type="button"
            className={`carrusel__rio ${sel === r.rioId ? 'carrusel__rio--sel' : ''}`}
            onClick={() => elegir(r.rioId)}
            tabIndex={copia ? -1 : undefined}
            title={`${r.nombre}: ${v.etiqueta}`}
          >
            {contenido(r, v)}
          </button>
        </li>
      )
    })

  const ordenados = [...rios].sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'))

  // La cinta se duplica para que el desplazamiento sea continuo; la copia no se anuncia.
  return (
    <nav className="carrusel" aria-label="Estado de los ríos" ref={raiz}>
      <div className="carrusel__ventana">
        <div className="carrusel__pista" style={{ '--duracion': `${Math.max(20, rios.length * 6)}s` }}>
          <ul className="carrusel__lista">{elementos(0)}</ul>
          <ul className="carrusel__lista carrusel__lista--copia" aria-hidden="true">
            {elementos(1)}
          </ul>
        </div>
      </div>
      <button
        type="button"
        className={`carrusel__flecha ${abierto ? 'carrusel__flecha--abierta' : ''}`}
        onClick={() => setAbierto((a) => !a)}
        aria-expanded={abierto}
        aria-controls="lista-rios"
        aria-label={abierto ? 'Ocultar lista de ríos' : 'Ver todos los ríos'}
        title="Ver todos los ríos"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {abierto && (
        <div className="carrusel__menu" id="lista-rios">
          <p className="carrusel__menu-titulo">Ríos registrados ({rios.length})</p>
          <ul>
            {ordenados.map((r) => {
              const v = visualRio(estados[r.rioId])
              return (
                <li key={r.rioId}>
                  <button
                    type="button"
                    className={`carrusel__rio carrusel__rio--fila ${sel === r.rioId ? 'carrusel__rio--sel' : ''}`}
                    onClick={() => elegir(r.rioId)}
                  >
                    {contenido(r, v)}
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </nav>
  )
}
