/**
 * @file Login.jsx
 * Página de inicio de sesión y registro de usuarios en el sistema.
 * Permite conmutar entre los modos de login y creación de cuenta USER.
 * Exporta el componente Login por defecto.
 */

import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth.jsx'

/**
 * Vista de autenticación para iniciar sesión o registrar una nueva cuenta de usuario.
 *
 * @returns {JSX.Element} Pantalla interactiva con formulario de acceso y registro.
 */
export default function Login() {
  const { entrar, token } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const volver = searchParams.get('volver')
  const destino = volver && volver.startsWith('/') && !volver.startsWith('//') ? volver : '/'

  const [modo, setModo] = useState('login')
  const [identificador, setIdentificador] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [ocupado, setOcupado] = useState(false)

  if (token) return <Navigate to={destino} replace />

  const enviar = async (e) => {
    e.preventDefault()
    setError('')
    if (identificador.trim().length < 3) return setError('El usuario debe tener al menos 3 caracteres.')
    if (password.length < 8) return setError('La contraseña debe tener al menos 8 caracteres.')
    setOcupado(true)
    try {
      await entrar(modo, { identificador: identificador.trim(), password })
      navigate(destino, { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setOcupado(false)
    }
  }

  return (
    <main className="login">
      <h1>Alerta de ríos de Panamá</h1>
      <p className="k">Sistema de alerta temprana ante el aumento del caudal.</p>
      <div className="pestanas" role="tablist">
        <button type="button" role="tab" aria-selected={modo === 'login'} onClick={() => setModo('login')}>Iniciar sesión</button>
        <button type="button" role="tab" aria-selected={modo === 'registro'} onClick={() => setModo('registro')}>Registrarme</button>
      </div>
      <form onSubmit={enviar} noValidate>
        <label htmlFor="usuario">Usuario</label>
        <input id="usuario" value={identificador} onChange={(e) => setIdentificador(e.target.value)} autoComplete="username" />
        <label htmlFor="clave">Contraseña (mínimo 8 caracteres)</label>
        <input id="clave" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete={modo === 'login' ? 'current-password' : 'new-password'} />
        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" className="btn" disabled={ocupado}>
          {ocupado ? 'Enviando…' : modo === 'login' ? 'Entrar' : 'Crear cuenta'}
        </button>
      </form>
      <p style={{ textAlign: 'center', marginTop: 16 }}>
        <Link to="/">Ver el mapa sin iniciar sesión</Link>
      </p>
    </main>
  )
}
