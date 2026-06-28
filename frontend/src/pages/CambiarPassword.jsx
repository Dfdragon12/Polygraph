import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import api from '../services/api'

const RUTA_POR_ROL = {
  ADMIN_POLYGRAPH:  '/admin/dashboard',
  GESTOR:           '/gestor/dashboard',
  ANALISTA_INTERNO: '/analista/dashboard',
  PROGRAMADOR:      '/programador/dashboard',
  POLIGRAFISTA:     '/poligrafista/dashboard',
  VISITADOR:        '/visitador/dashboard',
  ADMIN_CLIENTE:    '/cliente/dashboard',
  ANALISTA_CLIENTE: '/cliente/dashboard',
}

function OjoIcon({ visible }) {
  return visible ? (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
    </svg>
  ) : (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
    </svg>
  )
}

export default function CambiarPassword() {
  const { usuario } = useAuth()
  const navigate = useNavigate()

  const [nueva, setNueva] = useState('')
  const [confirmar, setConfirmar] = useState('')
  const [mostrarNueva, setMostrarNueva] = useState(false)
  const [mostrarConfirmar, setMostrarConfirmar] = useState(false)
  const [error, setError] = useState(null)
  const [guardando, setGuardando] = useState(false)

  const noCoinciden = confirmar !== '' && confirmar !== nueva
  const fuerte = nueva.length >= 8

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)

    if (nueva !== confirmar) {
      setError('Las contraseñas no coinciden')
      return
    }
    if (nueva.length < 8) {
      setError('La contraseña debe tener al menos 8 caracteres')
      return
    }

    setGuardando(true)
    try {
      await api.post('/auth/cambiar-password', { nuevaPassword: nueva })
      navigate(RUTA_POR_ROL[usuario?.rol] ?? '/', { replace: true })
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al cambiar la contraseña. Intenta de nuevo.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 to-indigo-900 flex items-center justify-center px-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden">
        {/* Header */}
        <div className="bg-amber-500 px-8 py-7 text-center">
          <div className="w-14 h-14 bg-white/20 rounded-full flex items-center justify-center mx-auto mb-3">
            <svg className="w-7 h-7 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
          </div>
          <h1 className="text-xl font-bold text-white">Actualiza tu contraseña</h1>
          <p className="text-amber-100 text-sm mt-1">
            Por seguridad debes establecer una nueva contraseña antes de continuar
          </p>
        </div>

        {/* Form */}
        <div className="px-8 py-8 space-y-5">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Nueva contraseña */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                Nueva contraseña <span className="text-red-500">*</span>
              </label>
              <div className="relative">
                <input
                  type={mostrarNueva ? 'text' : 'password'}
                  value={nueva}
                  onChange={e => setNueva(e.target.value)}
                  required
                  minLength={8}
                  placeholder="Mínimo 8 caracteres"
                  className={`w-full border rounded-lg px-4 py-2.5 pr-10 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 ${
                    nueva && !fuerte ? 'border-red-300 bg-red-50' : 'border-gray-300'
                  }`}
                />
                <button type="button" onClick={() => setMostrarNueva(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                  <OjoIcon visible={mostrarNueva} />
                </button>
              </div>
              {nueva && !fuerte && (
                <p className="text-xs text-red-500 mt-1">Mínimo 8 caracteres</p>
              )}
              {nueva && fuerte && (
                <p className="text-xs text-green-600 mt-1">Contraseña válida</p>
              )}
            </div>

            {/* Confirmar */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">
                Confirmar contraseña <span className="text-red-500">*</span>
              </label>
              <div className="relative">
                <input
                  type={mostrarConfirmar ? 'text' : 'password'}
                  value={confirmar}
                  onChange={e => setConfirmar(e.target.value)}
                  required
                  placeholder="Repite la nueva contraseña"
                  className={`w-full border rounded-lg px-4 py-2.5 pr-10 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 ${
                    noCoinciden ? 'border-red-300 bg-red-50' : 'border-gray-300'
                  }`}
                />
                <button type="button" onClick={() => setMostrarConfirmar(v => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                  <OjoIcon visible={mostrarConfirmar} />
                </button>
              </div>
              {noCoinciden && (
                <p className="text-xs text-red-500 mt-1">Las contraseñas no coinciden</p>
              )}
            </div>

            <button
              type="submit"
              disabled={guardando || noCoinciden || !fuerte}
              className="w-full bg-indigo-600 hover:bg-indigo-700 disabled:bg-indigo-300 text-white font-semibold py-2.5 rounded-lg text-sm transition flex items-center justify-center gap-2"
            >
              {guardando ? (
                <>
                  <svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  Guardando...
                </>
              ) : 'Cambiar contraseña y continuar'}
            </button>
          </form>

          <p className="text-xs text-gray-400 text-center">
            Hola, <strong>{usuario?.nombre}</strong>. Recuerda actualizar tu contraseña cada 30 días.
          </p>
        </div>
      </div>
    </div>
  )
}
