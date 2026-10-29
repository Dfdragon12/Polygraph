import { useId, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import BrandShowcase from '../components/BrandShowcase'
import HeartbeatMonitor from '../components/HeartbeatMonitor'
import PieLegal from '../components/legal/PieLegal'

const esquema = z.object({
  email: z.string().min(1, 'El email es requerido').email('Email inválido'),
  password: z.string().min(1, 'La contraseña es requerida'),
})

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

export default function Login() {
  const idCampos = useId()
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const cerradaPorInactividad = searchParams.get('motivo') === 'inactividad'
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(false)
  const [mostrarPassword, setMostrarPassword] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ resolver: zodResolver(esquema) })

  const onSubmit = async (datos) => {
    setError(null)
    setCargando(true)
    try {
      const resp = await login(datos.email, datos.password)
      if (resp?.usuario?.requiereCambioPassword) {
        navigate('/cambiar-password', { replace: true })
      } else {
        const ruta = RUTA_POR_ROL[resp?.usuario?.rol] ?? '/'
        navigate(ruta, { replace: true })
      }
    } catch (err) {
      setError(err.response?.data?.mensaje || 'Credenciales incorrectas')
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="min-h-screen flex bg-white">
      {/* ── Panel de marca (oculto en mobile) ── */}
      <div className="hidden lg:flex lg:w-1/2 lg:sticky lg:top-0 lg:h-screen relative overflow-hidden bg-gradient-to-br from-slate-900 via-slate-900 to-primary-900">
        {/* Movimiento del fondo del panel (negro/coral) — blobs grandes con deriva continua
            (animate-float-bg), detrás de todo. Se alcanzan a ver incluso detrás del carrusel
            gracias al difuminado arriba/abajo de las fotos. */}
        <div className="absolute -top-24 -right-16 w-[30rem] h-[30rem] bg-primary-600/50 rounded-full blur-3xl animate-float-bg" />
        <div className="absolute -bottom-24 -left-20 w-96 h-96 bg-primary-400/30 rounded-full blur-3xl animate-float-bg" style={{ animationDelay: '-4s', animationDuration: '10s' }} />
        {/* Textura sutil de puntos — le da profundidad al panel sin depender de ninguna animación */}
        <div className="absolute inset-0 opacity-[0.07]"
          style={{ backgroundImage: 'radial-gradient(circle, white 1px, transparent 1px)', backgroundSize: '28px 28px' }} />
        {/* Monitor de "señal de vida" — decoración de fondo (no un widget flotando en el
            contenido), muy sutil, detrás del carrusel */}
        <HeartbeatMonitor />

        <div className="relative z-10 flex flex-col justify-between p-12 text-white w-full">
          {/* Logo + carrusel con espacio FIJO entre ellos (gap-8) en vez de vivir directo bajo
              justify-between — así la posición del carrusel no se estira/comprime según el alto
              de la ventana, se mantiene siempre igual justo debajo del logo. */}
          <div className="flex flex-col gap-8">
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 bg-white rounded-xl flex items-center justify-center flex-shrink-0 shadow-lg shadow-black/20 p-1.5">
                <img src="/icon-192.png" alt="Polygraph" className="w-full h-full object-contain" />
              </div>
              <span className="font-semibold text-lg tracking-tight">Polygraph Service</span>
              <span className="relative flex h-2.5 w-2.5 ml-1">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary-300 opacity-75" />
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-primary-400" />
              </span>
            </div>

            {/* Carrusel informativo — el mensaje de plataforma va DENTRO del carrusel (primer
                slide), no en una franja aparte; todo comparte el mismo lenguaje visual de foto. */}
            <BrandShowcase />
          </div>

          <p className="text-xs text-slate-500">15 años de experiencia — Polygraph Service Ltda.</p>
        </div>
      </div>

      {/* ── Panel de formulario ── */}
      <div className="flex-1 flex items-center justify-center px-4 py-12 relative">
        {/* Bruma sutil que conecta el panel oscuro con el blanco, evita el corte duro */}
        <div className="hidden lg:block absolute inset-y-0 left-0 w-40 bg-gradient-to-r from-primary-50/70 to-transparent pointer-events-none" />

        <div className="w-full max-w-md slide-up relative">
          {/* Header solo visible en mobile/tablet — el carrusel es contenido del panel de marca
              (solo desktop, hidden lg:flex), así que en mobile/tablet se mantiene simple. */}
          <div className="lg:hidden relative overflow-hidden rounded-3xl bg-gradient-to-br from-slate-900 to-primary-900 text-center mb-8 px-6 py-8 shadow-xl shadow-gray-200/60">
            <HeartbeatMonitor className="absolute left-0 bottom-2 h-10 opacity-[0.18]" />
            <div className="relative z-10">
              <div className="w-14 h-14 bg-white rounded-2xl flex items-center justify-center mx-auto mb-3 shadow-lg shadow-black/20 p-2">
                <img src="/icon-192.png" alt="Polygraph" className="w-full h-full object-contain" />
              </div>
              <div className="flex items-center justify-center gap-2">
                <h1 className="text-xl font-bold text-white">Polygraph Service</h1>
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary-300 opacity-75" />
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-primary-400" />
                </span>
              </div>
              <p className="text-slate-300 text-sm mt-1">Plataforma ERP de Estudios de Seguridad</p>
            </div>
          </div>

          <div className="bg-white rounded-2xl shadow-xl shadow-gray-200/60 border border-gray-100 px-8 py-9">
            <h2 className="text-xl font-bold text-gray-900">Bienvenido de nuevo</h2>
            <p className="text-sm text-gray-500 mt-1 mb-7">Ingresa tus credenciales para continuar</p>

            <form id="contenido" tabIndex={-1} onSubmit={handleSubmit(onSubmit)} className="space-y-5 focus:outline-none" noValidate>
              {cerradaPorInactividad && !error && (
                <div className="flex items-start gap-2.5 bg-amber-50 border border-amber-200 text-amber-700 px-4 py-3 rounded-lg text-sm slide-up">
                  Tu sesión se cerró por inactividad. Vuelve a iniciar sesión para continuar.
                </div>
              )}
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
                  Correo electrónico
                </label>
                <div className="relative">
                  <svg className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                  <input id={`${idCampos}-1`}
                    type="email"
                    autoComplete="email"
                    {...register('email')}
                    className={`w-full pl-10 pr-4 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-4 focus:ring-primary-500/15 focus:border-primary-500 transition ${
                      errors.email ? 'border-red-400 bg-red-50' : 'border-gray-200 bg-white'
                    }`}
                    placeholder="usuario@empresa.com"
                  />
                </div>
                {errors.email && (
                  <p className="mt-1 text-xs text-red-600">{errors.email.message}</p>
                )}
              </div>

              <div>
                <div className="flex justify-between items-center mb-1.5">
                  <label htmlFor="login-password" className="block text-sm font-medium text-gray-700">Contraseña</label>
                  <Link to="/recuperar-password" className="text-xs text-gray-500 font-medium hover:text-primary-600 transition-colors">
                    ¿Olvidaste tu contraseña?
                  </Link>
                </div>
                <div className="relative">
                  <svg className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                  <input id="login-password"
                    type={mostrarPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    {...register('password')}
                    className={`w-full pl-10 pr-10 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-4 focus:ring-primary-500/15 focus:border-primary-500 transition ${
                      errors.password ? 'border-red-400 bg-red-50' : 'border-gray-200 bg-white'
                    }`}
                    placeholder="••••••••"
                  />
                  <button
                    type="button"
                    onClick={() => setMostrarPassword((v) => !v)}
                    tabIndex={-1}
                    className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
                    aria-label={mostrarPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  >
                    {mostrarPassword ? (
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.878 9.878L3 3m6.878 6.878L21 21" />
                      </svg>
                    ) : (
                      <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                      </svg>
                    )}
                  </button>
                </div>
                {errors.password && (
                  <p className="mt-1 text-xs text-red-600">{errors.password.message}</p>
                )}
              </div>

              <button
                type="submit"
                disabled={cargando}
                className="w-full bg-gradient-to-r from-primary-600 to-primary-700 hover:from-primary-700 hover:to-primary-800 disabled:from-primary-400 disabled:to-primary-400 text-white font-semibold py-2.5 px-4 rounded-xl text-sm transition-all hover:shadow-lg hover:shadow-primary-600/30 active:scale-[0.98] flex items-center justify-center gap-2"
              >
                {cargando ? (
                  <>
                    <svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Iniciando sesión...
                  </>
                ) : 'Iniciar sesión'}
              </button>
            </form>

            <p className="text-center text-sm text-gray-500 mt-6">
              ¿No tienes cuenta?{' '}
              <Link to="/registro" className="text-primary-600 font-semibold hover:text-primary-700 hover:underline">
                Regístrate aquí
              </Link>
            </p>
            <PieLegal className="mt-4" />
          </div>
        </div>
      </div>
    </div>
  )
}
