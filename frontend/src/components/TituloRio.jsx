/**
 * @file TituloRio.jsx
 * Título del panel de un río con edición en línea del nombre. El botón de lápiz
 * solo aparece cuando se recibe `onRenombrar` (roles ADMIN e INVESTIGADOR).
 * Exporta el componente TituloRio por defecto.
 */

import { useState } from 'react'

/**
 * @param {Object} props
 * @param {string} props.nombre - Nombre actual del río.
 * @param {(nuevo: string) => Promise<void>} [props.onRenombrar] - Guarda el nuevo nombre; si falla, debe lanzar un Error.
 * @returns {JSX.Element}
 */
export default function TituloRio({ nombre, onRenombrar }) {
  const [editando, setEditando] = useState(false)
  const [valor, setValor] = useState(nombre)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState('')

  const abrir = () => {
    setValor(nombre)
    setError('')
    setEditando(true)
  }

  const guardar = async (e) => {
    e.preventDefault()
    const limpio = valor.trim()
    if (!limpio) return setError('El nombre no puede quedar vacío.')
    if (limpio === nombre) return setEditando(false)
    setGuardando(true)
    setError('')
    try {
      await onRenombrar(limpio)
      setEditando(false)
    } catch (err) {
      setError(err.message || 'No se pudo cambiar el nombre.')
    } finally {
      setGuardando(false)
    }
  }

  if (editando) {
    return (
      <form className="titulo-rio__form" onSubmit={guardar}>
        <label className="k" htmlFor="nombre-rio">Nombre del río</label>
        <input
          id="nombre-rio"
          className="titulo-rio__input"
          value={valor}
          maxLength={100}
          autoFocus
          disabled={guardando}
          onChange={(e) => setValor(e.target.value)}
          onKeyDown={(e) => e.key === 'Escape' && setEditando(false)}
        />
        {error && <p className="error" role="alert">{error}</p>}
        <div className="titulo-rio__acciones">
          <button type="submit" className="btn btn--chico" disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar'}
          </button>
          <button type="button" className="btn btn--sec btn--chico" disabled={guardando} onClick={() => setEditando(false)}>
            Cancelar
          </button>
        </div>
      </form>
    )
  }

  return (
    <h2 className="panel__titulo titulo-rio">
      <span>{nombre}</span>
      {onRenombrar && (
        <button type="button" className="titulo-rio__editar" onClick={abrir} aria-label={`Editar el nombre de ${nombre}`} title="Editar nombre">
          <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 20h4L18.5 9.5a2.1 2.1 0 0 0-4-4L4 16v4z" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
          </svg>
        </button>
      )}
    </h2>
  )
}
