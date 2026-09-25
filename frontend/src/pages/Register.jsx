import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useNavigate } from 'react-router-dom'
import authService from '../services/authService'
import { useAuth } from '../hooks/useAuth'
import GoogleSignInButton from '../components/GoogleSignInButton'
import BrandShowcase from '../components/BrandShowcase'
import HeartbeatMonitor from '../components/HeartbeatMonitor'

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

/* ─── Esquemas de validación ─────────────────────────────────────── */

const passwordRules = z
  .string()
  .min(8, 'Mínimo 8 caracteres')
  .regex(/[A-Z]/, 'Debe tener al menos una mayúscula')
  .regex(/[0-9]/, 'Debe tener al menos un número')

const esquemaNatural = z
  .object({
    nombre: z.string().min(2, 'Mínimo 2 caracteres').max(100),
    apellido: z.string().min(2, 'Mínimo 2 caracteres').max(100),
    email: z.string().email('Email inválido'),
    password: passwordRules,
    confirmarPassword: z.string(),
    telefono: z.string().max(20).optional(),
  })
  .refine((d) => d.password === d.confirmarPassword, {
    message: 'Las contraseñas no coinciden',
    path: ['confirmarPassword'],
  })

// Toda empresa que se auto-registra entra siempre como PREPAGO — pasar a pospago requiere que un
// gestor/admin lo gestione después, no se elige libremente en el registro.
const esquemaJuridica1 = z.object({
  nit: z.string().min(5, 'NIT inválido').max(20),
  dv: z.string().max(1).optional(),
  razonSocial: z.string().min(2, 'Requerido').max(200),
  nombreComercial: z.string().max(200).optional(),
  representanteLegal: z.string().max(150).optional(),
})

const esquemaJuridica2 = z
  .object({
    email: z.string().email('Email inválido'),
    password: passwordRules,
    confirmarPassword: z.string(),
    telefono: z.string().max(20).optional(),
  })
  .refine((d) => d.password === d.confirmarPassword, {
    message: 'Las contraseñas no coinciden',
    path: ['confirmarPassword'],
  })

/* ─── Utilidad: indicador de fortaleza ──────────────────────────── */
/* Paleta acotada a la del resto de la plataforma (rojo/ámbar/primario/esmeralda) — nada de
 * amarillo o azul puro que desentonen con el resto del branding. */

function calcularFortaleza(password) {
  if (!password) return { nivel: 0, texto: '', color: '', texto_color: '' }
  let puntos = 0
  if (password.length >= 8) puntos++
  if (/[A-Z]/.test(password)) puntos++
  if (/[0-9]/.test(password)) puntos++
  if (/[^A-Za-z0-9]/.test(password)) puntos++
  const niveles = [
    { nivel: 0, texto: '', color: '', texto_color: '' },
    { nivel: 1, texto: 'Débil',      color: 'bg-red-400',     texto_color: 'text-red-500' },
    { nivel: 2, texto: 'Media',      color: 'bg-amber-400',   texto_color: 'text-amber-600' },
    { nivel: 3, texto: 'Fuerte',     color: 'bg-primary-500', texto_color: 'text-primary-600' },
    { nivel: 4, texto: 'Muy fuerte', color: 'bg-emerald-500', texto_color: 'text-emerald-600' },
  ]
  return niveles[puntos]
}

/* ─── Iconos ─────────────────────────────────────────────────────── */

const IconoUsuario = (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
  </svg>
)
const IconoEmail = (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
  </svg>
)
const IconoCandado = (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
  </svg>
)
const IconoTelefono = (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.25 6.75c0 8.284 6.716 15 15 15h1.5a2.25 2.25 0 002.25-2.25v-1.372c0-.516-.351-.966-.852-1.091l-4.423-1.106a2.25 2.25 0 00-2.239.649l-.973.973a1.125 1.125 0 01-1.191.26 12.35 12.35 0 01-7.135-7.135 1.125 1.125 0 01.26-1.191l.973-.973a2.25 2.25 0 00.649-2.239L6.963 3.102a1.125 1.125 0 00-1.091-.852H4.5A2.25 2.25 0 002.25 4.5v2.25z" />
  </svg>
)
const IconoEdificio = (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3.75 21h16.5M4.5 3h15M5.25 3v18m13.5-18v18M9 6.75h1.5m-1.5 3h1.5m-1.5 3h1.5m3-6H15m-1.5 3H15m-1.5 3H15M9 21v-3.375c0-.621.504-1.125 1.125-1.125h3.75c.621 0 1.125.504 1.125 1.125V21" />
  </svg>
)
const IconoOjoAbierto = (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
  </svg>
)
const IconoOjoCerrado = (
  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.878 9.878L3 3m6.878 6.878L21 21" />
  </svg>
)

/* ─── Sub-formularios ────────────────────────────────────────────── */

function FormNatural({ onExito, onGoogle }) {
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(false)
  const { register, handleSubmit, watch, formState: { errors } } = useForm({
    resolver: zodResolver(esquemaNatural),
  })
  const passwordActual = watch('password', '')
  const fortaleza = calcularFortaleza(passwordActual)

  const onSubmit = async (datos) => {
    setError(null)
    setCargando(true)
    try {
      await authService.registrarNatural({
        nombre: datos.nombre,
        apellido: datos.apellido,
        email: datos.email,
        password: datos.password,
        telefono: datos.telefono || undefined,
      })
      onExito()
    } catch (err) {
      setError(err.response?.data?.mensaje || 'Error al registrar. Intenta de nuevo.')
    } finally {
      setCargando(false)
    }
  }

  return (
    <div className="space-y-5">
      <GoogleSignInButton onCredential={onGoogle} texto="signup_with" />
      <Separador />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {error && <Alerta mensaje={error} />}

        <div className="grid grid-cols-2 gap-3">
          <Campo label="Nombre" error={errors.nombre?.message}>
            <CampoConIcono icono={IconoUsuario} error={errors.nombre}>
              <input {...register('nombre')} className={estiloInput(errors.nombre, true)} placeholder="Ana" />
            </CampoConIcono>
          </Campo>
          <Campo label="Apellido" error={errors.apellido?.message}>
            <input {...register('apellido')} className={estiloInput(errors.apellido)} placeholder="García" />
          </Campo>
        </div>

        <Campo label="Correo electrónico" error={errors.email?.message}>
          <CampoConIcono icono={IconoEmail} error={errors.email}>
            <input type="email" autoComplete="email" {...register('email')} className={estiloInput(errors.email, true)} placeholder="ana@empresa.com" />
          </CampoConIcono>
        </Campo>

        <CampoPassword label="Contraseña" error={errors.password} register={register('password')} fortaleza={fortaleza} />
        <CampoPassword label="Confirmar contraseña" error={errors.confirmarPassword} register={register('confirmarPassword')} />

        <Campo label="Teléfono (opcional)" error={errors.telefono?.message}>
          <CampoConIcono icono={IconoTelefono} error={errors.telefono}>
            <input {...register('telefono')} className={estiloInput(errors.telefono, true)} placeholder="3001234567" />
          </CampoConIcono>
        </Campo>

        <BotonSubmit cargando={cargando} texto="Crear cuenta" />
      </form>
    </div>
  )
}

function FormJuridica({ onExito }) {
  const [paso, setPaso] = useState(1)
  const [datosPaso1, setDatosPaso1] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(false)

  const form1 = useForm({ resolver: zodResolver(esquemaJuridica1) })
  const form2 = useForm({ resolver: zodResolver(esquemaJuridica2) })
  const passwordActual = form2.watch('password', '')
  const fortaleza = calcularFortaleza(passwordActual)

  const avanzar = (datos) => {
    setDatosPaso1(datos)
    setPaso(2)
  }

  const onSubmit = async (datos) => {
    setError(null)
    setCargando(true)
    try {
      await authService.registrarJuridica({
        ...datosPaso1,
        email: datos.email,
        password: datos.password,
        telefono: datos.telefono || undefined,
      })
      onExito()
    } catch (err) {
      setError(err.response?.data?.mensaje || 'Error al registrar. Intenta de nuevo.')
      setPaso(2)
    } finally {
      setCargando(false)
    }
  }

  return (
    <div>
      {/* Indicador de pasos */}
      <div className="flex items-center mb-6">
        {[1, 2].map((n) => (
          <div key={n} className="flex items-center">
            <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold transition-colors
              ${paso >= n ? 'bg-primary-600 text-white shadow-sm shadow-primary-600/30' : 'bg-gray-100 text-gray-400'}`}>
              {n}
            </div>
            {n < 2 && <div className={`h-0.5 w-16 mx-1.5 rounded-full transition-colors ${paso > 1 ? 'bg-primary-600' : 'bg-gray-200'}`} />}
          </div>
        ))}
        <span className="ml-3 text-xs text-gray-500">
          {paso === 1 ? 'Datos de la empresa' : 'Credenciales de acceso'}
        </span>
      </div>

      {paso === 1 && (
        <form onSubmit={form1.handleSubmit(avanzar)} className="space-y-4" noValidate>
          <div className="grid grid-cols-3 gap-3">
            <div className="col-span-2">
              <Campo label="NIT" error={form1.formState.errors.nit?.message}>
                <CampoConIcono icono={IconoEdificio} error={form1.formState.errors.nit}>
                  <input {...form1.register('nit')} className={estiloInput(form1.formState.errors.nit, true)} placeholder="900123456" />
                </CampoConIcono>
              </Campo>
            </div>
            <Campo label="DV" error={form1.formState.errors.dv?.message}>
              <input {...form1.register('dv')} maxLength={1} className={estiloInput(form1.formState.errors.dv)} placeholder="1" />
            </Campo>
          </div>

          <Campo label="Razón social" error={form1.formState.errors.razonSocial?.message}>
            <input {...form1.register('razonSocial')} className={estiloInput(form1.formState.errors.razonSocial)} placeholder="Empresa S.A.S." />
          </Campo>

          <Campo label="Nombre comercial (opcional)" error={form1.formState.errors.nombreComercial?.message}>
            <input {...form1.register('nombreComercial')} className={estiloInput(form1.formState.errors.nombreComercial)} placeholder="Mi Empresa" />
          </Campo>

          <Campo label="Representante legal (opcional)" error={form1.formState.errors.representanteLegal?.message}>
            <input {...form1.register('representanteLegal')} className={estiloInput(form1.formState.errors.representanteLegal)} placeholder="Juan Pérez" />
          </Campo>

          <BotonSubmit cargando={false} texto="Continuar →" />
        </form>
      )}

      {paso === 2 && (
        <form onSubmit={form2.handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {error && <Alerta mensaje={error} />}

          <Campo label="Correo electrónico" error={form2.formState.errors.email?.message}>
            <CampoConIcono icono={IconoEmail} error={form2.formState.errors.email}>
              <input type="email" {...form2.register('email')} className={estiloInput(form2.formState.errors.email, true)} placeholder="contacto@empresa.com" />
            </CampoConIcono>
          </Campo>

          <CampoPassword label="Contraseña" error={form2.formState.errors.password} register={form2.register('password')} fortaleza={fortaleza} />
          <CampoPassword label="Confirmar contraseña" error={form2.formState.errors.confirmarPassword} register={form2.register('confirmarPassword')} />

          <Campo label="Teléfono (opcional)" error={form2.formState.errors.telefono?.message}>
            <CampoConIcono icono={IconoTelefono} error={form2.formState.errors.telefono}>
              <input {...form2.register('telefono')} className={estiloInput(form2.formState.errors.telefono, true)} placeholder="6011234567" />
            </CampoConIcono>
          </Campo>

          <div className="flex gap-3">
            <button type="button" onClick={() => setPaso(1)}
              className="flex-1 border border-gray-200 text-gray-600 py-2.5 rounded-xl text-sm font-medium hover:bg-gray-50 transition-colors">
              ← Atrás
            </button>
            <div className="flex-1">
              <BotonSubmit cargando={cargando} texto="Crear empresa" />
            </div>
          </div>
        </form>
      )}
    </div>
  )
}

/* ─── Componentes auxiliares ─────────────────────────────────────── */

function Campo({ label, error, children }) {
  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1.5">{label}</label>
      {children}
      {error && <p className="mt-1 text-xs text-red-600">{error}</p>}
    </div>
  )
}

/* Envuelve un <input> con un ícono a la izquierda, mismo lenguaje visual que Login.jsx. */
function CampoConIcono({ icono, children }) {
  return (
    <div className="relative">
      <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none">{icono}</span>
      {children}
    </div>
  )
}

/* Campo de contraseña con ícono de candado + botón de ojo para mostrar/ocultar. */
function CampoPassword({ label, error, register, fortaleza }) {
  const [mostrar, setMostrar] = useState(false)
  return (
    <Campo label={label} error={error?.message}>
      <div className="relative">
        <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none">{IconoCandado}</span>
        <input
          type={mostrar ? 'text' : 'password'}
          autoComplete="new-password"
          {...register}
          className={estiloInput(error, true, true)}
          placeholder="••••••••"
        />
        <button type="button" onClick={() => setMostrar((v) => !v)} tabIndex={-1}
          className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
          aria-label={mostrar ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
          {mostrar ? IconoOjoCerrado : IconoOjoAbierto}
        </button>
      </div>
      {fortaleza && <IndicadorFortaleza fortaleza={fortaleza} />}
    </Campo>
  )
}

function IndicadorFortaleza({ fortaleza }) {
  if (!fortaleza.texto) return null
  return (
    <div className="mt-2 flex items-center gap-2">
      <div className="flex gap-1 flex-1">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className={`h-1 flex-1 rounded-full transition-all duration-300 ${
            i <= fortaleza.nivel ? fortaleza.color : 'bg-gray-100'
          }`} />
        ))}
      </div>
      <span className={`text-xs font-medium ${fortaleza.texto_color}`}>{fortaleza.texto}</span>
    </div>
  )
}

function Separador() {
  return (
    <div className="flex items-center gap-3">
      <div className="h-px flex-1 bg-gray-200" />
      <span className="text-xs text-gray-400">o regístrate con tu correo</span>
      <div className="h-px flex-1 bg-gray-200" />
    </div>
  )
}

function Alerta({ mensaje }) {
  return (
    <div className="flex items-start gap-2.5 bg-slate-50 border border-slate-200 text-slate-700 px-4 py-3 rounded-xl text-sm slide-up">
      <span className="w-5 h-5 rounded-full bg-primary-100 text-primary-600 flex items-center justify-center flex-shrink-0 mt-0.5">
        <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
          <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
        </svg>
      </span>
      {mensaje}
    </div>
  )
}

function BotonSubmit({ cargando, texto }) {
  return (
    <button type="submit" disabled={cargando}
      className="w-full bg-gradient-to-r from-primary-600 to-primary-700 hover:from-primary-700 hover:to-primary-800 disabled:from-primary-400 disabled:to-primary-400 text-white font-semibold py-2.5 px-4 rounded-xl text-sm transition-all hover:shadow-lg hover:shadow-primary-600/30 active:scale-[0.98] flex items-center justify-center gap-2">
      {cargando ? (
        <>
          <svg className="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Procesando...
        </>
      ) : texto}
    </button>
  )
}

function estiloInput(error, conIcono = false, conAccionDerecha = false) {
  return `w-full ${conIcono ? 'pl-10' : 'px-4'} ${conAccionDerecha ? 'pr-10' : conIcono ? 'pr-4' : ''} py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-4 focus:ring-primary-500/15 focus:border-primary-500 transition bg-white ${
    error ? 'border-red-400 bg-red-50' : 'border-gray-200'
  }`
}

/* ─── Pantalla de éxito ──────────────────────────────────────────── */

function PantallaExito() {
  return (
    <div className="text-center py-6">
      <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-4">
        <svg className="w-8 h-8 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
        </svg>
      </div>
      <h3 className="text-lg font-bold text-gray-900 mb-2">¡Registro exitoso!</h3>
      <p className="text-sm text-gray-600 mb-6">
        Te enviamos un correo de activación. Revisa tu bandeja de entrada y haz clic en el enlace para activar tu cuenta.
      </p>
      <Link to="/login" className="text-primary-600 font-semibold hover:text-primary-700 hover:underline text-sm">
        Ir al inicio de sesión →
      </Link>
    </div>
  )
}

/* ─── Componente principal ───────────────────────────────────────── */

export default function Register() {
  const navigate = useNavigate()
  const { establecerSesion } = useAuth()
  const [tab, setTab] = useState('natural')
  const [registrado, setRegistrado] = useState(false)
  const [errorGoogle, setErrorGoogle] = useState(null)
  const [cargandoGoogle, setCargandoGoogle] = useState(false)

  const onGoogle = async (idToken) => {
    setErrorGoogle(null)
    setCargandoGoogle(true)
    try {
      const datos = await authService.continuarConGoogle(idToken)
      establecerSesion(datos)
      const ruta = RUTA_POR_ROL[datos?.usuario?.rol] ?? '/'
      navigate(ruta, { replace: true })
    } catch (err) {
      setErrorGoogle(err.response?.data?.mensaje || 'No se pudo continuar con Google. Intenta de nuevo.')
    } finally {
      setCargandoGoogle(false)
    }
  }

  return (
    <div className="min-h-screen flex bg-white">
      {/* ── Panel de marca (oculto en mobile) — mismo lenguaje visual que Login.jsx ── */}
      <div className="hidden lg:flex lg:w-1/2 lg:sticky lg:top-0 lg:h-screen relative overflow-hidden bg-gradient-to-br from-slate-900 via-slate-900 to-primary-900">
        <div className="absolute -top-24 -right-16 w-[30rem] h-[30rem] bg-primary-600/50 rounded-full blur-3xl animate-float-bg" />
        <div className="absolute -bottom-24 -left-20 w-96 h-96 bg-primary-400/30 rounded-full blur-3xl animate-float-bg" style={{ animationDelay: '-4s', animationDuration: '10s' }} />
        <div className="absolute inset-0 opacity-[0.07]"
          style={{ backgroundImage: 'radial-gradient(circle, white 1px, transparent 1px)', backgroundSize: '28px 28px' }} />
        <HeartbeatMonitor />

        <div className="relative z-10 flex flex-col justify-between p-12 text-white w-full">
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

            <BrandShowcase />
          </div>

          <p className="text-xs text-slate-500">15 años de experiencia — Polygraph Service Ltda.</p>
        </div>
      </div>

      {/* ── Panel de formulario ── */}
      <div className="flex-1 flex items-center justify-center px-4 py-10 relative">
        <div className="hidden lg:block absolute inset-y-0 left-0 w-40 bg-gradient-to-r from-primary-50/70 to-transparent pointer-events-none" />

        <div className="w-full max-w-lg slide-up relative">
          {/* Header solo visible en mobile/tablet — el carrusel es contenido del panel de marca
              (solo desktop), así que en mobile/tablet se mantiene simple. */}
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
              <p className="text-slate-300 text-sm mt-1">Crear cuenta</p>
            </div>
          </div>

          <div className="bg-white rounded-2xl shadow-xl shadow-gray-200/60 border border-gray-100 px-8 py-9">
            {registrado ? (
              <PantallaExito />
            ) : (
              <>
                <h2 className="text-xl font-bold text-gray-900">Crea tu cuenta</h2>
                <p className="text-sm text-gray-500 mt-1 mb-6">Empieza a gestionar tus estudios en minutos</p>

                {cargandoGoogle && (
                  <div className="mb-4 flex items-center gap-2.5 text-sm text-gray-500 bg-gray-50 border border-gray-100 rounded-xl px-4 py-3">
                    <svg className="animate-spin h-4 w-4 text-primary-600" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    Conectando con Google...
                  </div>
                )}
                {errorGoogle && <div className="mb-4"><Alerta mensaje={errorGoogle} /></div>}

                {/* Tabs */}
                <div className="flex bg-gray-100 rounded-xl p-1 mb-6">
                  {[
                    { key: 'natural', label: 'Persona Natural' },
                    { key: 'juridica', label: 'Empresa' },
                  ].map(({ key, label }) => (
                    <button key={key} type="button" onClick={() => setTab(key)}
                      className={`flex-1 py-2 rounded-lg text-sm font-medium transition-all ${
                        tab === key
                          ? 'bg-white text-primary-700 shadow-sm'
                          : 'text-gray-500 hover:text-gray-700'
                      }`}>
                      {label}
                    </button>
                  ))}
                </div>

                {tab === 'natural'
                  ? <FormNatural onExito={() => setRegistrado(true)} onGoogle={onGoogle} />
                  : <FormJuridica onExito={() => setRegistrado(true)} />}
              </>
            )}

            <p className="text-center text-sm text-gray-500 mt-6">
              ¿Ya tienes cuenta?{' '}
              <Link to="/login" className="text-primary-600 font-semibold hover:text-primary-700 hover:underline">
                Inicia sesión
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
