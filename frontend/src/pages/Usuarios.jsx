/**
 * @file Usuarios.jsx
 * Página administrativa para la gestión y asignación de roles de usuarios registrados en el sistema.
 * Exporta el componente Usuarios por defecto.
 */

import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'

/**
 * Vista de administración para consultar usuarios y actualizar sus roles (USER o INVESTIGADOR).
 *
 * @returns {JSX.Element} Tabla de gestión de usuarios o aviso de acceso restringido.
 */
export default function Usuarios() {
  const { token, usuario, cargando: authCargando } = useAuth()
  const [usuarios, setUsuarios] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState('')
  const [mensajes, setMensajes] = useState({})
  const [guardandoId, setGuardandoId] = useState(null)

  useEffect(() => {
    if (!token || usuario?.rol !== 'ADMIN') return
    let activo = true
    setCargando(true)
    setError('')
    api('/api/admin/usuarios', { token })
      .then((data) => {
        if (activo) setUsuarios(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        if (activo) setError(err.message || 'Error al cargar usuarios')
      })
      .finally(() => {
        if (activo) setCargando(false)
      })
    return () => {
      activo = false
    }
  }, [token, usuario?.rol])

  if (authCargando) return <p className="aviso centrado">Cargando…</p>
  if (!token) return <Navigate to="/login?volver=/usuarios" replace />
  if (usuario?.rol !== 'ADMIN') {
    return (
      <section className="pagina">
        <h1>Usuarios</h1>
        <p className="error" role="alert">Solo para administradores</p>
      </section>
    )
  }

  const cambiarRol = async (id, nuevoRol) => {
    setGuardandoId(id)
    setMensajes((m) => ({ ...m, [id]: null }))
    try {
      const actualizado = await api(`/api/admin/usuarios/${id}/rol`, {
        method: 'PUT',
        token,
        body: { rol: nuevoRol },
      })
      setUsuarios((prev) =>
        prev.map((u) => (u.id === id ? { ...u, rol: actualizado?.rol || nuevoRol } : u)),
      )
      setMensajes((m) => ({ ...m, [id]: { tipo: 'ok', texto: 'Rol actualizado' } }))
    } catch (err) {
      setMensajes((m) => ({ ...m, [id]: { tipo: 'error', texto: err.message || 'Error al cambiar rol' } }))
    } finally {
      setGuardandoId(null)
    }
  }

  return (
    <section className="pagina">
      <h1>Gestión de usuarios</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {cargando && <p className="aviso">Cargando lista de usuarios…</p>}

      {!cargando && (
        <div className="tabla-wrap">
          <table className="tabla tabla--usuarios">
            <thead>
              <tr>
                <th>Identificador</th>
                <th>Rol</th>
                <th>Acción / Estado</th>
              </tr>
            </thead>
            <tbody>
              {usuarios.map((u) => {
                const esAdmin = u.rol === 'ADMIN'
                const msg = mensajes[u.id]
                return (
                  <tr key={u.id}>
                    <td>
                      <strong>{u.identificador}</strong>
                    </td>
                    <td>
                      {esAdmin ? (
                        <span className="insignia" style={{ '--est-fondo': '#e0e7ff', '--est-color': '#3730a3' }}>
                          ADMIN
                        </span>
                      ) : (
                        <select
                          className="select-rol"
                          value={u.rol}
                          onChange={(e) => cambiarRol(u.id, e.target.value)}
                          disabled={guardandoId === u.id}
                          aria-label={`Cambiar rol de ${u.identificador}`}
                        >
                          <option value="USER">USER</option>
                          <option value="INVESTIGADOR">INVESTIGADOR</option>
                        </select>
                      )}
                    </td>
                    <td>
                      {guardandoId === u.id && <span className="aviso">Guardando…</span>}
                      {msg && (
                        <span className={msg.tipo === 'error' ? 'error' : 'aviso'} role={msg.tipo === 'error' ? 'alert' : 'status'}>
                          {msg.texto}
                        </span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
