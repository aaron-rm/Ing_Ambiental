export const TOKEN_KEY = 'alerta_token'

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

// Llamada al backend. Si hay token se envía como "Authorization: Bearer ...".
export async function api(path, { method = 'GET', body, token } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let resp
  try {
    resp = await fetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
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
