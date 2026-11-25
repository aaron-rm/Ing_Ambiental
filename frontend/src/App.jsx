/**
 * @file App.jsx
 * Componente principal de enrutamiento y estructura visual general de la aplicación.
 * Define la barra de navegación superior, rutas públicas y rutas protegidas.
 * Exporta el componente App por defecto.
 */

import { Link, NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import Login from './pages/Login.jsx'
import Mapa from './pages/Mapa.jsx'
import MisRios from './pages/MisRios.jsx'
import Usuarios from './pages/Usuarios.jsx'

function Marco() {
  const { token, usuario, salir } = useAuth()
  return (
    <>
      <header className="cab">
        <span className="cab__marca">Alerta de ríos</span>
        <nav className="cab__nav" aria-label="Principal">
          <NavLink to="/" end>Mapa</NavLink>
          <NavLink to="/mis-rios">Mis ríos</NavLink>
          {usuario?.rol === 'ADMIN' && <NavLink to="/usuarios">Usuarios</NavLink>}
        </nav>
        {token ? (
          <>
            <span className="cab__usuario">{usuario?.identificador}</span>
            <button type="button" className="cab__salir" onClick={salir}>Salir</button>
          </>
        ) : (
          <Link to="/login" className="cab__salir">Iniciar sesión</Link>
        )}
      </header>
      <Outlet />
    </>
  )
}

function Protegida({ children }) {
  const { token, cargando } = useAuth()
  if (cargando) return <p className="aviso centrado">Cargando…</p>
  if (!token) return <Navigate to="/login?volver=/mis-rios" replace />
  return children
}

/**
 * Componente raíz que define el enrutador de la aplicación y la estructura de navegación.
 *
 * @returns {JSX.Element} Árbol de rutas con React Router.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Marco />}>
        <Route index element={<Mapa />} />
        <Route
          path="mis-rios"
          element={
            <Protegida>
              <MisRios />
            </Protegida>
          }
        />
        <Route path="usuarios" element={<Usuarios />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
