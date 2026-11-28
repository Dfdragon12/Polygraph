import { useId, useState } from 'react'
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
    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878L3 3m6.878 6.878L21 21" />
    </svg>
  ) : (
    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
    </svg>
  )
}

function IconoCandado({ className = 'absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400' }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
    </svg>
  )
}

export default function CambiarPassword() {
  const idCampos = useId()
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
    <div className="min-h-screen flex bg-white">
      {/* ── Panel de marca (oculto en mobile) — mismo panel que Login, para que toda la parte de
          autenticación se sienta como un solo flujo, no dos pantallas distintas. ── */}
      <div className="hidden lg:flex lg:w-1/2 relative overflow-hidden bg-slate-900">
        <div className="absolute -top-24 -right-16 w-[28rem] h-[28rem] bg-primary-600/70 rounded-full blur-3xl" />
        <div className="absolute -bottom-24 -left-16 w-96 h-96 bg-primary-700/60 rounded-full blur-3xl" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-72 h-72 bg-primary-500/10 rounded-full blur-3xl" />

        <div className="relative z-10 flex flex-col justify-between p-12 text-white w-full">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 bg-primary-600 rounded-xl flex items-center justify-center flex-shrink-0 shadow-lg shadow-primary-600/40">
              <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
              </svg>
            </div>
            <span className="font-semibold text-lg tracking-tight">Polygraph Service</span>
          </div>

          <div className="max-w-md">
            <div className="w-14 h-14 rounded-2xl bg-amber-500/15 border border-amber-400/30 flex items-center justify-center mb-6">
              <svg className="w-7 h-7 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>
            <h1 className="text-3xl font-bold leading-tight">
              Un último paso para proteger tu cuenta
            </h1>
            <p className="text-slate-300 mt-4 text-sm leading-relaxed">
              Por seguridad, todas las cuentas deben establecer una contraseña propia antes de
              acceder a la plataforma — nunca compartas la tuya con nadie más.
            </p>

            <div className="mt-8 space-y-4">
              {[
                'Mínimo 8 caracteres',
                'Evita datos obvios como tu nombre o fecha de nacimiento',
                'Usa una contraseña distinta a la de otros sitios',
              ].map((texto) => (
                <div key={texto} className="flex items-center gap-3">
                  <div className="w-6 h-6 rounded-full bg-primary-600/20 border border-primary-500/40 flex items-center justify-center flex-shrink-0">
                    <svg className="w-3.5 h-3.5 text-primary-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                    </svg>
                  </div>
                  <span className="text-sm text-slate-200">{texto}</span>
                </div>
              ))}
            </div>
          </div>

          <p className="text-xs text-slate-500">15 años de experiencia — Polygraph Service Ltda.</p>
        </div>
      </div>

      {/* ── Panel de formulario ── */}
      <div className="flex-1 flex items-center justify-center px-4 py-12 relative">
        <div className="hidden lg:block absolute inset-y-0 left-0 w-40 bg-gradient-to-r from-primary-50/70 to-transparent pointer-events-none" />

        <div className="w-full max-w-md slide-up relative">
          <div className="lg:hidden text-center mb-8">
            <div className="w-14 h-14 bg-amber-500 rounded-2xl flex items-center justify-center mx-auto mb-3 shadow-lg shadow-amber-500/30">
              <IconoCandado className="w-7 h-7 text-white static" />
            </div>
            <h1 className="text-xl font-bold text-gray-900">Actualiza tu contraseña</h1>
            <p className="text-gray-500 text-sm mt-1">Por seguridad, antes de continuar</p>
          </div>

          <div className="bg-white rounded-2xl shadow-xl shadow-gray-200/60 border border-gray-100 px-8 py-9">
            <h2 className="text-xl font-bold text-gray-900">Actualiza tu contraseña</h2>
            <p className="text-sm text-gray-500 mt-1 mb-7">
              Hola, <strong className="text-gray-700">{usuario?.nombre}</strong> — establece una
              nueva contraseña para continuar.
            </p>

            <form onSubmit={handleSubmit} className="space-y-5" noValidate>
              {error && (
                <div className="flex items-start gap-2.5 bg-slate-50 border border-slate-200 text-slate-700 px-4 py-3 rounded-lg text-sm slide-up">
                  <span className="w-5 h-5 rounded-full bg-primary-100 text-primary-600 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                    </svg>
                  </span>
                  {error}
                </div>
              )}

              <div>
                <label htmlFor={`${idCampos}-1`} className="block text-sm font-medium text-gray-700 mb-1.5">
                  Nueva contraseña
                </label>
                <div className="relative">
                  <IconoCandado />
                  <input id={`${idCampos}-1`}
                    type={mostrarNueva ? 'text' : 'password'}
                    value={nueva}
                    onChange={e => setNueva(e.target.value)}
                    autoComplete="new-password"
                    required
                    minLength={8}
                    placeholder="Mínimo 8 caracteres"
                    className={`w-full pl-10 pr-10 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-4 focus:ring-primary-500/15 focus:border-primary-500 transition ${
                      nueva && !fuerte ? 'border-red-400 bg-red-50' : 'border-gray-200 bg-white'
                    }`}
                  />
                  <button type="button" onClick={() => setMostrarNueva(v => !v)} tabIndex={-1}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
                    aria-label={mostrarNueva ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
                    <OjoIcon visible={mostrarNueva} />
                  </button>
                </div>
                {nueva && !fuerte && (
                  <p className="mt-1 text-xs text-red-600">Mínimo 8 caracteres</p>
                )}
                {nueva && fuerte && (
                  <p className="mt-1 text-xs text-green-600">Contraseña válida</p>
                )}
              </div>

              <div>
                <label htmlFor={`${idCampos}-2`} className="block text-sm font-medium text-gray-700 mb-1.5">
                  Confirmar contraseña
                </label>
                <div className="relative">
                  <IconoCandado />
                  <input id={`${idCampos}-2`}
                    type={mostrarConfirmar ? 'text' : 'password'}
                    value={confirmar}
                    onChange={e => setConfirmar(e.target.value)}
                    autoComplete="new-password"
                    required
                    placeholder="Repite la nueva contraseña"
                    className={`w-full pl-10 pr-10 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-4 focus:ring-primary-500/15 focus:border-primary-500 transition ${
                      noCoinciden ? 'border-red-400 bg-red-50' : 'border-gray-200 bg-white'
                    }`}
                  />
                  <button type="button" onClick={() => setMostrarConfirmar(v => !v)} tabIndex={-1}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
                    aria-label={mostrarConfirmar ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
                    <OjoIcon visible={mostrarConfirmar} />
                  </button>
                </div>
                {noCoinciden && (
                  <p className="mt-1 text-xs text-red-600">Las contraseñas no coinciden</p>
                )}
              </div>

              <button
                type="submit"
                disabled={guardando || noCoinciden || !fuerte}
                className="w-full bg-gradient-to-r from-primary-600 to-primary-700 hover:from-primary-700 hover:to-primary-800 disabled:from-primary-300 disabled:to-primary-300 text-white font-semibold py-2.5 px-4 rounded-xl text-sm transition-all hover:shadow-lg hover:shadow-primary-600/30 active:scale-[0.98] flex items-center justify-center gap-2"
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

            <p className="text-center text-xs text-gray-400 mt-6">
              Recuerda actualizar tu contraseña cada 30 días.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
