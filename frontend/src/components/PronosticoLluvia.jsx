import { useMemo } from 'react'

const ZONA = 'America/Panama'
const formatoFecha = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})
const formatoDia = new Intl.DateTimeFormat('es-PA', {
  timeZone: ZONA,
  weekday: 'short',
  day: 'numeric',
})

function claveLocal(fecha) {
  const partes = formatoFecha.formatToParts(fecha)
  const valor = (tipo) => partes.find((p) => p.type === tipo)?.value ?? ''
  return `${valor('year')}-${valor('month')}-${valor('day')}`
}

// Barras con la lluvia total prevista para cada día completo.
export default function PronosticoLluvia({ puntos }) {
  const dias = useMemo(() => {
    const mapa = new Map()
    for (const punto of puntos ?? []) {
      if (!punto?.fecha) continue
      const fecha = new Date(punto.fecha)
      if (Number.isNaN(fecha.getTime())) continue
      const clave = claveLocal(fecha)
      const actual = mapa.get(clave) ?? { clave, mm: 0, horas: 0 }
      actual.mm += Number(punto.mm) || 0
      actual.horas += 1
      mapa.set(clave, actual)
    }
    const lista = [...mapa.values()].sort((a, b) => a.clave.localeCompare(b.clave))
    const completos = lista.filter((d) => d.horas >= 24)
    return completos.length >= 2 ? completos : lista
  }, [puntos])

  if (!dias.length) return null

  const maximo = Math.max(...dias.map((d) => d.mm), 1)
  const alto = 90
  const ancho = 360
  const paso = ancho / dias.length
  const y = (valor) => alto - (valor / maximo) * alto

  return (
    <svg className="pronostico" viewBox={`0 0 ${ancho} ${alto + 18}`} role="img" aria-label="Lluvia total prevista por día">
      {dias.map((d, i) => {
        const altura = Math.max(2, alto - y(d.mm))
        const etiqueta = formatoDia.format(new Date(`${d.clave}T12:00:00-05:00`)).replace(',', '')
        const mostrarEtiqueta = dias.length <= 8 || i % 2 === 0
        return (
          <g key={d.clave}>
            <rect x={i * paso + 2} y={alto - altura} width={paso - 4} height={altura} rx="2" fill="#2A7DB5">
              <title>{`${etiqueta}: ${d.mm.toFixed(1)} mm`}</title>
            </rect>
            {mostrarEtiqueta && (
              <text x={i * paso + paso / 2} y={alto + 14} fontSize="9" fill="#51605F" textAnchor="middle">
                {etiqueta}
              </text>
            )}
          </g>
        )
      })}
    </svg>
  )
}
