/**
 * @file main.jsx
 * Punto de entrada principal de la aplicación React con Vite.
 * Monta el árbol de componentes dentro del elemento DOM raíz con enrutador y contexto de autenticación.
 */

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { AuthProvider } from './auth.jsx'
import './index.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
