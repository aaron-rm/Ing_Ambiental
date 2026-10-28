import { NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth.jsx'
import Login from './pages/Login.jsx'
import Mapa from './pages/Mapa.jsx'
import MisRios from './pages/MisRios.jsx'

function Marco() {
  const { usuario, salir } = useAuth()
  return (
    <>
      <header className="cab">
        <span className="cab__marca">Alerta de ríos</span>
        <nav className="cab__nav" aria-label="Principal">
          <NavLink to="/" end>Mapa</NavLink>
          <NavLink to="/mis-rios">Mis ríos</NavLink>
        </nav>
        <span className="cab__usuario">{usuario?.identificador}</span>
        <button type="button" className="cab__salir" onClick={salir}>Salir</button>
      </header>
      <Outlet />
    </>
  )
}

function Protegida() {
  const { token, cargando } = useAuth()
  if (cargando) return <p className="aviso centrado">Cargando…</p>
  if (!token) return <Navigate to="/login" replace />
  return <Marco />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Protegida />}>
        <Route index element={<Mapa />} />
        <Route path="mis-rios" element={<MisRios />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
