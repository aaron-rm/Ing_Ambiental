// Nombres que devuelve el backend en "nivelAlerta" y cómo se muestran.
export const ESTADOS = {
  NORMAL: { nivel: 'NORMAL', etiqueta: 'Normal', color: '#14603A', fondo: '#E6F4EC', trazo: '#2A7DB5' },
  VIGILANCIA: { nivel: 'VIGILANCIA', etiqueta: 'Vigilancia', color: '#8A5300', fondo: '#FFF1D6', trazo: '#E08A00' },
  ALERTA: { nivel: 'ALERTA', etiqueta: 'Riesgo de inundación', color: '#9A3B00', fondo: '#FFE6D5', trazo: '#E8590C' },
  EMERGENCIA: { nivel: 'EMERGENCIA', etiqueta: 'Peligro de inundación', color: '#A61B1B', fondo: '#FDECEC', trazo: '#C62828' },
}

const CONSULTANDO = { nivel: 'CARGANDO', etiqueta: 'Consultando…', color: '#3F4B4A', fondo: '#E7ECEB', trazo: '#7A8887' }
const SIN_DATOS = { nivel: 'SIN_DATOS', etiqueta: 'Datos no disponibles', color: '#3F4B4A', fondo: '#E7ECEB', trazo: '#7A8887' }

export function visual(dto) {
  if (!dto) return CONSULTANDO
  if (dto.datosConfiables === false) return SIN_DATOS
  return ESTADOS[dto.nivelAlerta] ?? ESTADOS.NORMAL
}

export const fmt = (n, d = 1) =>
  n === null || n === undefined || Number.isNaN(Number(n))
    ? '—'
    : Number(n).toLocaleString('es-PA', { maximumFractionDigits: d })

export function fechaCorta(iso) {
  if (!iso) return ''
  const d = new Date(`${String(iso).slice(0, 10)}T12:00:00`)
  return d.toLocaleDateString('es-PA', { weekday: 'short', day: 'numeric', month: 'short' })
}
