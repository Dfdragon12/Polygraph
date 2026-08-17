import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Link, useNavigate } from 'react-router-dom'
import authService from '../services/authService'

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

const esquemaJuridica1 = z.object({
  nit: z.string().min(5, 'NIT inválido').max(20),
  dv: z.string().max(1).optional(),
  razonSocial: z.string().min(2, 'Requerido').max(200),
  nombreComercial: z.string().max(200).optional(),
  representanteLegal: z.string().max(150).optional(),
  tipoCliente: z.enum(['PREPAGO', 'POSPAGO'], { errorMap: () => ({ message: 'Selecciona un tipo' }) }),
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

function calcularFortaleza(password) {
  if (!password) return { nivel: 0, texto: '', color: '' }
  let puntos = 0
  if (password.length >= 8) puntos++
  if (/[A-Z]/.test(password)) puntos++
  if (/[0-9]/.test(password)) puntos++
  if (/[^A-Za-z0-9]/.test(password)) puntos++
  const niveles = [
    { nivel: 0, texto: '', color: '' },
    { nivel: 1, texto: 'Débil', color: 'bg-red-500' },
    { nivel: 2, texto: 'Media', color: 'bg-yellow-500' },
    { nivel: 3, texto: 'Fuerte', color: 'bg-blue-500' },
    { nivel: 4, texto: 'Muy fuerte', color: 'bg-green-500' },
  ]
  return niveles[puntos]
}

/* ─── Sub-formularios ────────────────────────────────────────────── */

function FormNatural({ onExito }) {
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
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
      {error && <Alerta mensaje={error} />}

      <div className="grid grid-cols-2 gap-3">
        <Campo label="Nombre" error={errors.nombre?.message}>
          <input {...register('nombre')} className={estiloInput(errors.nombre)} placeholder="Ana" />
        </Campo>
        <Campo label="Apellido" error={errors.apellido?.message}>
          <input {...register('apellido')} className={estiloInput(errors.apellido)} placeholder="García" />
        </Campo>
      </div>

      <Campo label="Correo electrónico" error={errors.email?.message}>
        <input type="email" {...register('email')} className={estiloInput(errors.email)} placeholder="ana@empresa.com" />
      </Campo>

      <Campo label="Contraseña" error={errors.password?.message}>
        <input type="password" {...register('password')} className={estiloInput(errors.password)} placeholder="••••••••" />
        <IndicadorFortaleza fortaleza={fortaleza} />
      </Campo>

      <Campo label="Confirmar contraseña" error={errors.confirmarPassword?.message}>
        <input type="password" {...register('confirmarPassword')} className={estiloInput(errors.confirmarPassword)} placeholder="••••••••" />
      </Campo>

      <Campo label="Teléfono (opcional)" error={errors.telefono?.message}>
        <input {...register('telefono')} className={estiloInput(errors.telefono)} placeholder="3001234567" />
      </Campo>

      <BotonSubmit cargando={cargando} texto="Crear cuenta" />
    </form>
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
            <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold
              ${paso >= n ? 'bg-primary-600 text-white' : 'bg-gray-200 text-gray-500'}`}>
              {n}
            </div>
            {n < 2 && <div className={`h-0.5 w-16 mx-1 ${paso > 1 ? 'bg-primary-600' : 'bg-gray-200'}`} />}
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
                <input {...form1.register('nit')} className={estiloInput(form1.formState.errors.nit)} placeholder="900123456" />
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

          <Campo label="Tipo de cliente" error={form1.formState.errors.tipoCliente?.message}>
            <select {...form1.register('tipoCliente')} className={estiloInput(form1.formState.errors.tipoCliente)}>
              <option value="">Selecciona...</option>
              <option value="PREPAGO">Prepago</option>
              <option value="POSPAGO">Pospago (requiere estudio de crédito)</option>
            </select>
          </Campo>

          <BotonSubmit cargando={false} texto="Continuar →" />
        </form>
      )}

      {paso === 2 && (
        <form onSubmit={form2.handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {error && <Alerta mensaje={error} />}

          <Campo label="Correo electrónico" error={form2.formState.errors.email?.message}>
            <input type="email" {...form2.register('email')} className={estiloInput(form2.formState.errors.email)} placeholder="contacto@empresa.com" />
          </Campo>

          <Campo label="Contraseña" error={form2.formState.errors.password?.message}>
            <input type="password" {...form2.register('password')} className={estiloInput(form2.formState.errors.password)} placeholder="••••••••" />
            <IndicadorFortaleza fortaleza={fortaleza} />
          </Campo>

          <Campo label="Confirmar contraseña" error={form2.formState.errors.confirmarPassword?.message}>
            <input type="password" {...form2.register('confirmarPassword')} className={estiloInput(form2.formState.errors.confirmarPassword)} placeholder="••••••••" />
          </Campo>

          <Campo label="Teléfono (opcional)" error={form2.formState.errors.telefono?.message}>
            <input {...form2.register('telefono')} className={estiloInput(form2.formState.errors.telefono)} placeholder="6011234567" />
          </Campo>

          <div className="flex gap-3">
            <button type="button" onClick={() => setPaso(1)}
              className="flex-1 border border-gray-300 text-gray-700 py-2.5 rounded-lg text-sm font-medium hover:bg-gray-50 transition">
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

function IndicadorFortaleza({ fortaleza }) {
  if (!fortaleza.texto) return null
  return (
    <div className="mt-1.5 flex items-center gap-2">
      <div className="flex gap-1 flex-1">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className={`h-1 flex-1 rounded-full transition-all ${
            i <= fortaleza.nivel ? fortaleza.color : 'bg-gray-200'
          }`} />
        ))}
      </div>
      <span className="text-xs text-gray-500">{fortaleza.texto}</span>
    </div>
  )
}

function Alerta({ mensaje }) {
  return (
    <div className="flex items-start gap-2 bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg text-sm">
      <svg className="w-4 h-4 mt-0.5 shrink-0" fill="currentColor" viewBox="0 0 20 20">
        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
      </svg>
      {mensaje}
    </div>
  )
}

function BotonSubmit({ cargando, texto }) {
  return (
    <button type="submit" disabled={cargando}
      className="w-full bg-primary-600 hover:bg-primary-700 disabled:bg-primary-400 text-white font-semibold py-2.5 px-4 rounded-lg text-sm transition flex items-center justify-center gap-2">
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

function estiloInput(error) {
  return `w-full px-4 py-2.5 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 transition bg-white ${
    error ? 'border-red-400 bg-red-50' : 'border-gray-300'
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
      <Link to="/login" className="text-primary-600 font-medium hover:underline text-sm">
        Ir al inicio de sesión →
      </Link>
    </div>
  )
}

/* ─── Componente principal ───────────────────────────────────────── */

export default function Register() {
  const [tab, setTab] = useState('natural')
  const [registrado, setRegistrado] = useState(false)

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-900 to-primary-900 px-4 py-10">
      <div className="w-full max-w-lg">
        <div className="bg-white rounded-2xl shadow-2xl overflow-hidden">
          {/* Header */}
          <div className="bg-primary-600 px-8 py-6 text-center">
            <h1 className="text-xl font-bold text-white">Polygraph Service</h1>
            <p className="text-primary-200 text-sm mt-1">Crear cuenta</p>
          </div>

          <div className="px-8 py-8">
            {registrado ? (
              <PantallaExito />
            ) : (
              <>
                {/* Tabs */}
                <div className="flex bg-gray-100 rounded-lg p-1 mb-6">
                  {[
                    { key: 'natural', label: 'Persona Natural' },
                    { key: 'juridica', label: 'Empresa' },
                  ].map(({ key, label }) => (
                    <button key={key} type="button" onClick={() => setTab(key)}
                      className={`flex-1 py-2 rounded-md text-sm font-medium transition ${
                        tab === key
                          ? 'bg-white text-primary-700 shadow-sm'
                          : 'text-gray-500 hover:text-gray-700'
                      }`}>
                      {label}
                    </button>
                  ))}
                </div>

                {tab === 'natural'
                  ? <FormNatural onExito={() => setRegistrado(true)} />
                  : <FormJuridica onExito={() => setRegistrado(true)} />}
              </>
            )}

            <p className="text-center text-sm text-gray-500 mt-6">
              ¿Ya tienes cuenta?{' '}
              <Link to="/login" className="text-primary-600 font-medium hover:underline">
                Inicia sesión
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
