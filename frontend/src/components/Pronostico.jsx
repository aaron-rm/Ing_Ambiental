import { ESTADOS } from '../estados.js'

// Barras con el caudal máximo previsto de cada día (hasta 15 días).
export default function Pronostico({ dias, t1, t2, t3 }) {
  if (!dias?.length) return null
  const maximo = Math.max(...dias.map((d) => d.caudalMaximo), t1 * 1.15)
  const alto = 90
  const ancho = 360
  const paso = ancho / dias.length
  const y = (v) => alto - (v / maximo) * alto
  return (
    <svg className="pronostico" viewBox={`0 0 ${ancho} ${alto + 18}`} role="img" aria-label="Caudal máximo previsto por día">
      {[t1, t2, t3].filter((t) => t <= maximo).map((t) => (
        <line key={t} x1="0" x2={ancho} y1={y(t)} y2={y(t)} stroke="#C2185B" strokeWidth="1" strokeDasharray="4 3" />
      ))}
      {dias.map((d, i) => {
        const color = (ESTADOS[d.estado] ?? ESTADOS.NORMAL).trazo
        const h = Math.max(2, alto - y(d.caudalMaximo))
        return <rect key={d.fecha} x={i * paso + 2} y={alto - h} width={paso - 4} height={h} rx="2" fill={color} />
      })}
      <text x="0" y={alto + 14} fontSize="10" fill="#51605F">Hoy</text>
      <text x={ancho} y={alto + 14} fontSize="10" fill="#51605F" textAnchor="end">Día {dias.length}</text>
    </svg>
  )
}
