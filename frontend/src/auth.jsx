import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { api, TOKEN_KEY } from './api.js'
import { desactivarPush } from './push.js'

const Ctx = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY))
  const [usuario, setUsuario] = useState(null)
  const [cargando, setCargando] = useState(Boolean(localStorage.getItem(TOKEN_KEY)))

  const salir = useCallback(() => {
    // Se intenta desactivar el dispositivo antes de borrar el JWT; si falla,
    // el cierre de sesión local continúa igual.
    desactivarPush(token).catch(() => {})
    localStorage.removeItem(TOKEN_KEY)
    setToken(null)
    setUsuario(null)
  }, [token])

  // Al abrir la app con un token guardado, se confirma que sigue siendo válido.
  useEffect(() => {
    if (!token) return undefined
    let activo = true
    api('/api/auth/me', { token })
      .then((u) => activo && setUsuario(u))
      .catch(() => activo && salir())
      .finally(() => activo && setCargando(false))
    return () => {
      activo = false
    }
  }, [token, salir])

  useEffect(() => {
    window.addEventListener('sesion-expirada', salir)
    return () => window.removeEventListener('sesion-expirada', salir)
  }, [salir])

  // modo: "login" o "registro"
  const entrar = async (modo, credenciales) => {
    const r = await api(`/api/auth/${modo}`, { method: 'POST', body: credenciales })
    localStorage.setItem(TOKEN_KEY, r.token)
    setUsuario(r.usuario)
    setToken(r.token)
  }

  return <Ctx.Provider value={{ token, usuario, cargando, entrar, salir }}>{children}</Ctx.Provider>
}

export const useAuth = () => useContext(Ctx)
