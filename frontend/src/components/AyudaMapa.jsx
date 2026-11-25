/**
 * @file AyudaMapa.jsx
 * Recuadro flotante con instrucciones de uso del mapa y la leyenda de colores.
 * Se puede minimizar; la preferencia se guarda en localStorage. En pantallas
 * angostas empieza minimizado para no tapar el mapa.
 * Exporta el componente AyudaMapa por defecto.
 */

import { useState } from 'react'
import { ESTADOS, SIN_CONSULTAR } from '../estados.js'

const CLAVE = 'ayudaMapaMinimizada'

function inicialMinimizada() {
  try {
    const guardado = localStorage.getItem(CLAVE)
    if (guardado !== null) return guardado === '1'
  } catch {
    // Sin localStorage: se decide por el ancho de pantalla
  }
  return window.innerWidth < 640
}

/**
 * @returns {JSX.Element} Recuadro de ayuda expandido o botón minimizado.
 */
export default function AyudaMapa() {
  const [minimizada, setMinimizada] = useState(inicialMinimizada)

  const cambiar = (valor) => {
    setMinimizada(valor)
    try {
      localStorage.setItem(CLAVE, valor ? '1' : '0')
    } catch {
      // Si no se puede guardar, solo dura en esta visita
    }
  }

  if (minimizada) {
    return (
      <button type="button" className="ayuda ayuda--min" onClick={() => cambiar(false)} aria-expanded="false">
        <span className="ayuda__icono" aria-hidden="true">?</span> Cómo usar
      </button>
    )
  }

  return (
    <section className="ayuda" aria-label="Cómo usar el mapa">
      <div className="ayuda__cab">
        <h2 className="ayuda__titulo">Cómo usar el mapa</h2>
        <button type="button" className="ayuda__minimizar" onClick={() => cambiar(true)} aria-label="Minimizar ayuda" aria-expanded="true">
          –
        </button>
      </div>
      <ol className="ayuda__pasos">
        <li>Toca un río en el mapa, en la cinta de arriba o búscalo para ver su estado y pronóstico.</li>
        <li>Inicia sesión y pulsa <strong>Suscribirme a este río</strong> para recibir avisos cuando suba el caudal.</li>
        <li>Usa <strong>＋ Agregar río</strong> para registrar uno nuevo tocando su ubicación.</li>
      </ol>
      <ul className="ayuda__leyenda" aria-label="Colores del estado">
        {Object.values(ESTADOS).map((e) => (
          <li key={e.nivel}>
            <span className="ayuda__color" style={{ background: e.trazo }} aria-hidden="true" />
            {e.etiqueta}
          </li>
        ))}
        <li>
          <span className="ayuda__color" style={{ background: '#7A8887' }} aria-hidden="true" />
          Sin datos
        </li>
        <li>
          <span className="ayuda__color" style={{ background: SIN_CONSULTAR.trazo }} aria-hidden="true" />
          Sin consultar (tócalo para ver su estado)
        </li>
      </ul>
    </section>
  )
}
