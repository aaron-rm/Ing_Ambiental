/**
 * @file api.js
 * Módulo cliente HTTP para la comunicación con la API REST del backend.
 * Exporta la clave de almacenamiento del token, la clase de error ApiError y la función api.
 */

/**
 * Clave utilizada en localStorage para almacenar el token JWT de autenticación.
 * @type {string}
 */
export const TOKEN_KEY = 'alerta_token'

/**
 * Error personalizado para representar respuestas fallidas o problemas de conexión con la API REST.
 */
export class ApiError extends Error {
  /**
   * Crea una instancia de ApiError.
   * @param {number} status - Código de estado HTTP (o 0 si hubo error de red).
   * @param {string} message - Mensaje descriptivo del error.
   */
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

// Llamada al backend. Si hay token se envía como "Authorization: Bearer ...".
/**
 * Ejecuta una petición HTTP contra la API del backend.
 * Incluye automáticamente el encabezado Authorization si se proporciona un token JWT.
 *
 * @param {string} path - Ruta relativa del endpoint a consultar.
 * @param {Object} [opciones] - Opciones de configuración de la petición.
 * @param {string} [opciones.method='GET'] - Método HTTP (GET, POST, PUT, DELETE, etc.).
 * @param {*} [opciones.body] - Cuerpo de la petición serializable a JSON.
 * @param {string} [opciones.token] - Token JWT para autenticación Bearer.
 * @param {*} [opciones.raw] - Cuerpo en crudo sin serialización automática a JSON.
 * @param {string} [opciones.contentType] - Tipo de contenido para el encabezado Content-Type cuando se usa raw.
 * @returns {Promise<*|string|null>} Datos de la respuesta parseados (JSON, texto o null si status es 204).
 * @throws {ApiError} Si la petición falla por red o el servidor responde con estado no exitoso.
 */
export async function api(path, { method = 'GET', body, token, raw, contentType } = {}) {
  const headers = {}
  if (raw !== undefined) {
    if (contentType) headers['Content-Type'] = contentType
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (token) headers.Authorization = `Bearer ${token}`

  let resp
  try {
    resp = await fetch(path, {
      method,
      headers,
      body: raw !== undefined ? raw : body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor. ¿Está corriendo el backend?')
  }

  if (!resp.ok) {
    let texto = ''
    try {
      texto = await resp.text()
    } catch {
      texto = ''
    }
    if (resp.status === 401 && token) window.dispatchEvent(new Event('sesion-expirada'))
    throw new ApiError(resp.status, texto || `Error ${resp.status}`)
  }
  if (resp.status === 204) return null
  const tipo = resp.headers.get('content-type') || ''
  return tipo.includes('json') ? resp.json() : resp.text()
}
