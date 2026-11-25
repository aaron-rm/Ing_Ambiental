/**
 * @file estados.js
 * Definiciones de estados de alerta, colores, etiquetas y utilidades de formato hidrológico/fechas.
 * Exporta la constante ESTADOS, la función visual, la función de formato numérico fmt y fechaCorta.
 */

// Nombres que devuelve el backend en "nivelAlerta" y cómo se muestran.
/**
 * Mapeo de niveles de alerta a configuraciones visuales (etiquetas, colores y fondos).
 * @type {Object.<string, {nivel: string, etiqueta: string, color: string, fondo: string, trazo: string}>}
 */
export const ESTADOS = {
  NORMAL: { nivel: 'NORMAL', etiqueta: 'Normal', color: '#14603A', fondo: '#E6F4EC', trazo: '#2A7DB5' },
  VIGILANCIA: { nivel: 'VIGILANCIA', etiqueta: 'Vigilancia', color: '#8A5300', fondo: '#FFF1D6', trazo: '#E08A00' },
  ALERTA: { nivel: 'ALERTA', etiqueta: 'Riesgo de inundación', color: '#9A3B00', fondo: '#FFE6D5', trazo: '#E8590C' },
  EMERGENCIA: { nivel: 'EMERGENCIA', etiqueta: 'Peligro de inundación', color: '#A61B1B', fondo: '#FDECEC', trazo: '#C62828' },
}

const CONSULTANDO = { nivel: 'CARGANDO', etiqueta: 'Consultando…', color: '#3F4B4A', fondo: '#E7ECEB', trazo: '#7A8887' }
const SIN_DATOS = { nivel: 'SIN_DATOS', etiqueta: 'Datos no disponibles', color: '#3F4B4A', fondo: '#E7ECEB', trazo: '#7A8887' }

/** Río cuyo pronóstico todavía no se pidió (no se sigue y no se ha tocado). */
export const SIN_CONSULTAR = { nivel: 'SIN_CONSULTAR', etiqueta: 'Sin consultar', color: '#51605F', fondo: '#F1F4F3', trazo: '#94A3B8' }

/**
 * Configuración visual a partir de la entrada guardada de un río ({dto, cargando, error}),
 * distinguiendo «sin consultar», «consultando» y «sin datos» antes de tener el DTO.
 *
 * @param {{dto?: Object, cargando?: boolean, error?: string}|undefined} entrada - Estado cargado del río.
 * @returns {{nivel: string, etiqueta: string, color: string, fondo: string, trazo: string}}
 */
export function visualRio(entrada) {
  if (!entrada) return SIN_CONSULTAR
  if (entrada.dto) return visual(entrada.dto)
  if (entrada.cargando) return CONSULTANDO
  return entrada.error ? SIN_DATOS : SIN_CONSULTAR
}

/**
 * Determina la configuración visual adecuada según el estado y confiabilidad del pronóstico del río.
 *
 * @param {Object|null} dto - Objeto DTO de alerta del río.
 * @returns {{nivel: string, etiqueta: string, color: string, fondo: string, trazo: string}} Configuración visual correspondiente.
 */
export function visual(dto) {
  if (!dto) return CONSULTANDO
  if (dto.datosConfiables === false) return SIN_DATOS
  return ESTADOS[dto.nivelAlerta] ?? ESTADOS.NORMAL
}

/**
 * Formatea un valor numérico para presentación en pantalla con localización de Panamá.
 *
 * @param {number|string|null|undefined} n - Número o valor a formatear.
 * @param {number} [d=1] - Cantidad máxima de decimales permitidos.
 * @returns {string} Cadena numérica formateada o '—' si el valor es nulo o inválido.
 */
export const fmt = (n, d = 1) =>
  n === null || n === undefined || Number.isNaN(Number(n))
    ? '—'
    : Number(n).toLocaleString('es-PA', { maximumFractionDigits: d })

/**
 * Formatea una fecha ISO a una representación corta en español (día de la semana, día y mes).
 *
 * @param {string|null|undefined} iso - Cadena de fecha en formato ISO.
 * @returns {string} Fecha formateada corta o cadena vacía si no se proporcionó fecha.
 */
export function fechaCorta(iso) {
  if (!iso) return ''
  const d = new Date(`${String(iso).slice(0, 10)}T12:00:00`)
  return d.toLocaleDateString('es-PA', { weekday: 'short', day: 'numeric', month: 'short' })
}
