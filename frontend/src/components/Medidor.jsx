// Esquema del cauce: gris = orillas, azul = nivel relativo, rosado = umbrales.
// No es una altura en metros: GEOGLOWS entrega caudal (m³/s).
const BASE = 118
const Y1 = 80
const Y2 = 52
const Y3 = 24
const TOPE = 10
const lerp = (a, b, t) => a + (b - a) * t

export function nivelY(caudal, t1, t2, t3) {
  if (!(caudal > 0)) return BASE
  if (caudal <= t1) return lerp(BASE, Y1, caudal / t1)
  if (caudal <= t2) return lerp(Y1, Y2, (caudal - t1) / (t2 - t1))
  if (caudal <= t3) return lerp(Y2, Y3, (caudal - t2) / (t3 - t2))
  return lerp(Y3, TOPE, Math.min((caudal - t3) / (t3 * 0.5), 1))
}

export default function Medidor({ caudal, t1, t2, t3, color }) {
  const y = nivelY(caudal, t1, t2, t3)
  const izq = 60 + ((y - 10) * 30) / 108
  const der = 304 - ((y - 10) * 30) / 108
  return (
    <svg className="medidor" viewBox="0 0 364 130" role="img" aria-label="Nivel relativo del río frente a los umbrales">
      <path d="M0,10 L60,10 L90,118 L274,118 L304,10 L364,10 L364,130 L0,130 Z" fill="#9AA5A3" />
      <path d={`M${izq},${y} L${der},${y} L274,118 L90,118 Z`} fill={color} />
      <g stroke="#C2185B" strokeWidth="2" strokeDasharray="6 4">
        <line x1="60" y1={Y1} x2="304" y2={Y1} />
        <line x1="60" y1={Y2} x2="304" y2={Y2} />
        <line x1="60" y1={Y3} x2="304" y2={Y3} />
      </g>
      <g fontSize="12" fill="#8E1146" fontWeight="600">
        <text x="308" y={Y1 + 4}>{Math.round(t1)}</text>
        <text x="308" y={Y2 + 4}>{Math.round(t2)}</text>
        <text x="308" y={Y3 + 4}>{Math.round(t3)}</text>
      </g>
    </svg>
  )
}
