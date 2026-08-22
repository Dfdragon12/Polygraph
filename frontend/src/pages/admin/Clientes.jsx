import { useState, useEffect, useMemo, useCallback } from 'react'
import api from '../../services/api'
import Toast from '../../components/Toast'
import { Modal } from '../../components/ui/Modal'
import CiudadSelect from '../../components/CiudadSelect'
import ModalDocumentosCliente from '../../components/ModalDocumentosCliente'

/* ─── Helpers ─── */

/** El backend siempre manda mensaje="Errores de validación" para cualquier fallo de @Valid — el
 * detalle real (qué campo y por qué) viaja aparte en `errores: {campo: mensaje}`. */
function mensajeErrorValidacion(err, fallback = 'Error al guardar.') {
  const data = err.response?.data
  if (data?.errores && typeof data.errores === 'object' && Object.keys(data.errores).length) {
    return Object.entries(data.errores).map(([campo, msg]) => `${campo}: ${msg}`).join(' · ')
  }
  return data?.mensaje ?? data?.message ?? fallback
}

function Badge({ activo }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
      activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'
    }`}>
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

const OPCIONES_MORA = [
  { valor: 'NORMAL',     etiqueta: 'Al día' },
  { valor: 'EN_MORA',    etiqueta: 'En mora' },
  { valor: 'SUSPENDIDO', etiqueta: 'Suspendido' },
]

function BadgeMora({ mora }) {
  const cfg = {
    NORMAL:      { cls: 'bg-green-100 text-green-700',  label: 'Al día' },
    EN_MORA:     { cls: 'bg-red-100 text-red-700',      label: 'En mora' },
    SUSPENDIDO:  { cls: 'bg-gray-200 text-gray-700',    label: 'Suspendido' },
  }
  const c = cfg[mora] ?? { cls: 'bg-gray-100 text-gray-500', label: mora }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${c.cls}`}>
      {c.label}
    </span>
  )
}

const ESTADOS_DOCUMENTOS = [
  { key: 'pendientes', label: 'Pendientes de revisión', color: 'bg-amber-500'  },
  { key: 'rechazados', label: 'Rechazados',              color: 'bg-red-500'   },
  { key: 'vencidos',   label: 'Vencidos',                color: 'bg-orange-500' },
  { key: 'validados',  label: 'Vigentes',                color: 'bg-green-500' },
  { key: 'sinCargar',  label: 'Sin cargar',              color: 'bg-gray-400'  },
]

function BolitasDocumentos({ resumen }) {
  const items = resumen ? ESTADOS_DOCUMENTOS.filter(e => resumen[e.key] > 0) : []
  if (items.length === 0) return <span className="text-gray-300 text-sm">—</span>
  return (
    <div className="flex items-center gap-1">
      {items.map(e => (
        <span key={e.key} title={`${e.label}: ${resumen[e.key]}`}
          className={`flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full text-white text-[10px] font-bold flex-shrink-0 ${e.color}`}>
          {resumen[e.key]}
        </span>
      ))}
    </div>
  )
}

function IconoOrden({ activo, dir }) {
  if (!activo) return <span className="ml-1 text-gray-300 text-xs">↕</span>
  return <span className="ml-1 text-primary-500 text-xs">{dir === 'asc' ? '↑' : '↓'}</span>
}

const ROL_CLIENTE_STYLE = {
  ADMIN_CLIENTE: {
    label: 'Admin Cliente',
    badge: 'bg-primary-50 text-primary-700 border border-primary-100',
    avatar: 'from-primary-500 to-primary-600',
    chipOn: 'bg-primary-600 border-primary-600 text-white shadow-sm',
    chipOff: 'border-gray-200 text-gray-500 hover:border-primary-300 hover:text-primary-600',
  },
  ANALISTA_CLIENTE: {
    label: 'Analista Cliente',
    badge: 'bg-purple-50 text-purple-700 border border-purple-100',
    avatar: 'from-purple-500 to-purple-600',
    chipOn: 'bg-purple-600 border-purple-600 text-white shadow-sm',
    chipOff: 'border-gray-200 text-gray-500 hover:border-purple-300 hover:text-purple-600',
  },
}
const ETIQUETA_ROL_CLIENTE = Object.fromEntries(
  Object.entries(ROL_CLIENTE_STYLE).map(([k, v]) => [k, v.label])
)

function iniciales(nombre, apellido) {
  const a = nombre?.trim()?.[0] ?? ''
  const b = apellido?.trim()?.[0] ?? ''
  return (a + b).toUpperCase() || '?'
}

/* ─── Iconos ─── */
function IconLapiz() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
    </svg>
  )
}
function IconPower() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5.636 5.636a9 9 0 1012.728 0M12 3v9" />
    </svg>
  )
}
function IconUsuarios({ className = 'h-5 w-5' }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M17 20h5v-2a4 4 0 00-3-3.87M9 20H4v-2a4 4 0 013-3.87m6-1.13a4 4 0 10-4-4 4 4 0 004 4zm6 0a4 4 0 10-1.13-7.87" />
    </svg>
  )
}
function IconUserPlus() {
  return (
    <svg className="h-9 w-9" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zm7 0h4m-2-2v4" />
    </svg>
  )
}
function IconReloj() {
  return (
    <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
  )
}
function IconPlus() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
    </svg>
  )
}

function Avatar({ nombre, apellido, rol, size = 'h-11 w-11 text-sm' }) {
  const gradient = ROL_CLIENTE_STYLE[rol]?.avatar ?? 'from-slate-400 to-slate-500'
  return (
    <div className={`${size} rounded-full bg-gradient-to-br ${gradient} text-white font-semibold flex items-center justify-center flex-shrink-0 shadow-sm`}>
      {iniciales(nombre, apellido)}
    </div>
  )
}

/* ─── Paginación ─── */
function Paginacion({ pagina, total, porPagina, onChange }) {
  const totalPags = Math.ceil(total / porPagina)
  if (totalPags <= 1) return null
  const inicio = (pagina - 1) * porPagina + 1
  const fin    = Math.min(pagina * porPagina, total)
  const nums = []
  for (let i = Math.max(1, pagina - 2); i <= Math.min(totalPags, pagina + 2); i++) nums.push(i)
  return (
    <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
      <span className="text-xs text-gray-400">{inicio}–{fin} de {total}</span>
      <div className="flex items-center gap-1">
        <button onClick={() => onChange(pagina - 1)} disabled={pagina === 1}
          className="px-2.5 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          ← Anterior
        </button>
        {nums[0] > 1 && <span className="text-xs text-gray-400 px-1">…</span>}
        {nums.map(p => (
          <button key={p} onClick={() => onChange(p)}
            className={`w-8 h-8 text-xs rounded-lg border transition-colors ${
              p === pagina ? 'bg-primary-600 border-primary-600 text-white font-semibold' : 'border-gray-200 text-gray-500 hover:bg-gray-50'
            }`}>
            {p}
          </button>
        ))}
        {nums[nums.length - 1] < totalPags && <span className="text-xs text-gray-400 px-1">…</span>}
        <button onClick={() => onChange(pagina + 1)} disabled={pagina === totalPags}
          className="px-2.5 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          Siguiente →
        </button>
      </div>
    </div>
  )
}

/* ─── Modal editar cliente ─── */
function ModalEditar({ cliente, onClose, onGuardado }) {
  const esNatural = cliente.tipoPersona === 'NATURAL'
  const [form, setForm] = useState({
    nombre:             cliente.nombreDisplay?.split(' ')[0] ?? '',
    apellido:           esNatural ? (cliente.nombreDisplay?.split(' ').slice(1).join(' ') ?? '') : '',
    razonSocial:        !esNatural ? cliente.nombreDisplay : '',
    nombreComercial:    cliente.nombreComercial ?? '',
    representanteLegal: cliente.representanteLegal ?? '',
    telefono:           cliente.telefono ?? '',
    direccion:          cliente.direccion ?? '',
    observaciones:      cliente.observaciones ?? '',
    idGestor:           cliente.gestorAsignado?.idUsuario ?? '',
    idCiudad:           cliente.idCiudad ?? null,
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)
  const [gestores, setGestores] = useState([])

  useEffect(() => {
    api.get('/usuarios-internos')
      .then(r => setGestores(r.data.filter(u => u.rol === 'GESTOR' && u.activo)))
      .catch(() => {})
  }, [])

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm(p => ({ ...p, [name]: value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    try {
      const payload = { ...form, idGestor: form.idGestor ? Number(form.idGestor) : null }
      await api.put(`/clientes/${cliente.idCliente}`, payload)
      onGuardado()
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al actualizar el cliente.')
    } finally { setGuardando(false) }
  }

  return (
    <Modal titulo="Editar cliente" subtitulo={`${cliente.tipoPersona} · ${cliente.tipoCliente}`} onClose={onClose} ancho="max-w-lg">
      <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
        )}

        {esNatural ? (
          <div className="grid grid-cols-2 gap-4">
            <Campo label="Nombre" name="nombre" value={form.nombre} onChange={handleChange} required />
            <Campo label="Apellido" name="apellido" value={form.apellido} onChange={handleChange} />
          </div>
        ) : (
          <>
            <Campo label="Razón social" name="razonSocial" value={form.razonSocial} onChange={handleChange} required />
            <Campo label="Nombre comercial" name="nombreComercial" value={form.nombreComercial} onChange={handleChange} />
            <Campo label="Representante legal" name="representanteLegal" value={form.representanteLegal} onChange={handleChange} />
          </>
        )}

        <Campo label="Teléfono" name="telefono" value={form.telefono} onChange={handleChange} placeholder="Ej: 3001234567" />

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Gestor asignado</label>
          <select name="idGestor" value={form.idGestor} onChange={handleChange}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
            <option value="">Sin gestor asignado</option>
            {gestores.map(g => (
              <option key={g.idUsuario} value={g.idUsuario}>{g.nombre} {g.apellido ?? ''}</option>
            ))}
          </select>
          <p className="text-xs text-gray-400 mt-1">Es el punto de contacto de Polygraph para este cliente.</p>
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Ciudad</label>
          <CiudadSelect value={form.idCiudad} onChange={(idCiudad) => setForm(p => ({ ...p, idCiudad }))} />
          <p className="text-xs text-gray-400 mt-1">Se usará para tarifas y disponibilidad por ciudad.</p>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Dirección</label>
          <input name="direccion" value={form.direccion} onChange={handleChange}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Observaciones</label>
          <textarea name="observaciones" value={form.observaciones} onChange={handleChange} rows={3}
            placeholder="Notas internas sobre el cliente…"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
        </div>

        <div className="flex justify-end gap-3 pt-2">
          <button type="button" onClick={onClose}
            className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function Campo({ label, name, value, onChange, type = 'text', required = false, placeholder = '' }) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-600 mb-1">
        {label} {required && <span className="text-red-500">*</span>}
      </label>
      <input name={name} type={type} value={value} onChange={onChange} required={required} placeholder={placeholder}
        className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
    </div>
  )
}

/** Asignar/ajustar el cupo de crédito de un cliente pospago — no hay facturación automática todavía,
 * así que el crédito disponible lo administra el admin manualmente hasta que exista ese módulo. */
function ModalCredito({ cliente, onClose, onGuardado }) {
  const p = cliente.pospago
  const [form, setForm] = useState({
    limiteCredito:      p?.limiteCredito != null ? String(p.limiteCredito) : '',
    creditoDisponible:  p?.creditoDisponible != null ? String(p.creditoDisponible) : '',
    estadoMora:         p?.estadoMora ?? 'NORMAL',
    requiereAprobacion: p?.requiereAprobacion ?? false,
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    try {
      const payload = {
        limiteCredito:      form.limiteCredito !== '' ? Number(form.limiteCredito) : null,
        creditoDisponible:  form.creditoDisponible !== '' ? Number(form.creditoDisponible) : null,
        estadoMora:         form.estadoMora,
        requiereAprobacion: form.requiereAprobacion,
      }
      await api.patch(`/clientes/${cliente.idCliente}/pospago`, payload)
      onGuardado()
      onClose()
    } catch (err) {
      setError(mensajeErrorValidacion(err, 'Error al actualizar el crédito.'))
    } finally { setGuardando(false) }
  }

  return (
    <Modal titulo="Crédito pospago" subtitulo={cliente.nombreDisplay} onClose={onClose} ancho="max-w-md">
      <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>
        )}
        <div className="bg-amber-50 border border-amber-200 text-amber-700 text-xs px-3 py-2 rounded-lg">
          Todavía no hay facturación automática — el crédito disponible se ajusta manualmente aquí
          hasta que ese módulo exista.
        </div>

        <div className="grid grid-cols-2 gap-4">
          <Campo label="Límite de crédito (COP)" name="limiteCredito" type="number" value={form.limiteCredito}
            onChange={e => setForm(p2 => ({ ...p2, limiteCredito: e.target.value }))} placeholder="Ej: 5000000" />
          <Campo label="Crédito disponible (COP)" name="creditoDisponible" type="number" value={form.creditoDisponible}
            onChange={e => setForm(p2 => ({ ...p2, creditoDisponible: e.target.value }))} placeholder="Ej: 5000000" />
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Estado de mora</label>
          <select value={form.estadoMora} onChange={e => setForm(p2 => ({ ...p2, estadoMora: e.target.value }))}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
            {OPCIONES_MORA.map(o => (
              <option key={o.valor} value={o.valor}>{o.etiqueta}</option>
            ))}
          </select>
          {form.estadoMora !== 'NORMAL' && (
            <p className="text-xs text-amber-600 mt-1">
              Mientras esté en este estado, el cliente no podrá crear nuevas solicitudes.
            </p>
          )}
        </div>

        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={form.requiereAprobacion}
            onChange={e => setForm(p2 => ({ ...p2, requiereAprobacion: e.target.checked }))}
            className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
          Requiere aprobación de estudio de crédito
        </label>

        <div className="flex justify-end gap-3 pt-2">
          <button type="button" onClick={onClose}
            className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Guardando...' : 'Guardar'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/* ─── Icono ojo (mostrar/ocultar contraseña) ─── */
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

/* ─── Formulario compartido: usuario de cliente ─── */
function FormularioUsuarioCliente({ form, onChange, onSubmit, onCancel, guardando, error, esEdicion }) {
  const [mostrarPwd, setMostrarPwd] = useState(false)

  return (
    <form onSubmit={onSubmit} className="px-6 py-5 space-y-4">
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg slide-up">{error}</div>
      )}

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1.5">
          Rol <span className="text-red-500">*</span>
        </label>
        <div className="grid grid-cols-2 gap-2">
          {Object.entries(ROL_CLIENTE_STYLE).map(([value, cfg]) => (
            <button
              key={value}
              type="button"
              onClick={() => onChange({ target: { name: 'rol', value } })}
              className={`text-sm font-medium px-3 py-2.5 rounded-xl border-2 transition-all duration-150 ${
                form.rol === value ? cfg.chipOn + ' scale-[1.02]' : cfg.chipOff
              }`}>
              {cfg.label}
            </button>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Campo label="Nombre" name="nombre" value={form.nombre} onChange={onChange} required />
        <Campo label="Apellido" name="apellido" value={form.apellido} onChange={onChange} />
      </div>

      <Campo label="Email" name="email" type="email" value={form.email} onChange={onChange} required />

      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">
          Contraseña {!esEdicion && <span className="text-red-500">*</span>}
          {esEdicion && <span className="text-gray-400 font-normal text-[10px]"> (vacío = sin cambios)</span>}
        </label>
        <div className="relative">
          <input
            name="password" type={mostrarPwd ? 'text' : 'password'}
            value={form.password} onChange={onChange}
            required={!esEdicion} minLength={form.password ? 8 : undefined}
            placeholder="Mín. 8 caracteres"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 pr-9 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 transition-shadow"
          />
          <button type="button" onClick={() => setMostrarPwd(v => !v)}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
            <OjoIcon visible={mostrarPwd} />
          </button>
        </div>
      </div>

      <div className="flex justify-end gap-3 pt-2">
        <button type="button" onClick={onCancel}
          className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800 transition-colors">
          Cancelar
        </button>
        <button type="submit" disabled={guardando}
          className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-all hover:shadow-md active:scale-[0.98]">
          {guardando ? 'Guardando...' : esEdicion ? 'Guardar cambios' : 'Crear usuario'}
        </button>
      </div>
    </form>
  )
}

/* ─── Modal crear usuario de cliente ─── */
function ModalCrearUsuarioCliente({ idCliente, onClose, onGuardado }) {
  const [form, setForm] = useState({ nombre: '', apellido: '', email: '', password: '', rol: 'ANALISTA_CLIENTE' })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm(p => ({ ...p, [name]: value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    try {
      await api.post(`/clientes/${idCliente}/usuarios`, form)
      onGuardado()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al crear el usuario.')
    } finally { setGuardando(false) }
  }

  return (
    <Modal titulo="Nuevo usuario"
      subtitulo={form.nombre ? `${form.nombre} ${form.apellido}`.trim() : 'Completa los datos de acceso'}
      onClose={onClose} ancho="max-w-md">
      <div className="px-6 pt-5 flex justify-center">
        <Avatar nombre={form.nombre || '?'} apellido={form.apellido} rol={form.rol} size="h-14 w-14 text-lg" />
      </div>
      <FormularioUsuarioCliente
        form={form} onChange={handleChange} onSubmit={handleSubmit} onCancel={onClose}
        guardando={guardando} error={error} esEdicion={false}
      />
    </Modal>
  )
}

/* ─── Modal editar usuario de cliente ─── */
function ModalEditarUsuarioCliente({ idCliente, usuario, onClose, onGuardado }) {
  const [form, setForm] = useState({
    nombre: usuario.nombre ?? '',
    apellido: usuario.apellido ?? '',
    email: usuario.email ?? '',
    password: '',
    rol: usuario.rol ?? 'ANALISTA_CLIENTE',
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleChange = (e) => {
    const { name, value } = e.target
    setForm(p => ({ ...p, [name]: value }))
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    try {
      const payload = { ...form, password: form.password.trim() || null }
      await api.put(`/clientes/${idCliente}/usuarios/${usuario.idUsuario}`, payload)
      onGuardado()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al actualizar el usuario.')
    } finally { setGuardando(false) }
  }

  return (
    <Modal titulo="Editar usuario" subtitulo={usuario.email} onClose={onClose} ancho="max-w-md">
      <div className="px-6 pt-5 flex justify-center">
        <Avatar nombre={form.nombre || '?'} apellido={form.apellido} rol={form.rol} size="h-14 w-14 text-lg" />
      </div>
      <FormularioUsuarioCliente
        form={form} onChange={handleChange} onSubmit={handleSubmit} onCancel={onClose}
        guardando={guardando} error={error} esEdicion={true}
      />
    </Modal>
  )
}

/* ─── Modal usuarios asociados ─── */
function ModalUsuarios({ cliente, onClose, onCambio }) {
  const usuarios = cliente.usuariosAsociados ?? []
  const [crear, setCrear] = useState(false)
  const [editar, setEditar] = useState(null)
  const [accionando, setAccionando] = useState(null)
  const [error, setError] = useState(null)

  const toggleActivo = async (u) => {
    setAccionando(u.idUsuario); setError(null)
    try {
      await api.patch(`/clientes/${cliente.idCliente}/usuarios/${u.idUsuario}/${u.activo ? 'desactivar' : 'activar'}`)
      onCambio(u.activo ? 'Usuario desactivado correctamente' : 'Usuario activado correctamente')
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al cambiar el estado del usuario.')
    } finally { setAccionando(null) }
  }

  const activos = usuarios.filter(u => u.activo).length

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-auto max-h-[88vh] flex flex-col slide-up overflow-hidden">

        {/* Header */}
        <div className="bg-primary-600 px-6 py-5 flex items-center gap-4 flex-shrink-0">
          <div className="w-12 h-12 rounded-full bg-white/15 text-white flex items-center justify-center flex-shrink-0">
            <IconUsuarios className="h-6 w-6" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-white font-semibold text-base leading-tight truncate">{cliente.nombreDisplay}</p>
            <p className="text-primary-100 text-xs mt-0.5">
              {usuarios.length} usuario{usuarios.length !== 1 ? 's' : ''} · {activos} activo{activos !== 1 ? 's' : ''}
            </p>
          </div>
          <button onClick={onClose} className="text-primary-100 hover:text-white flex-shrink-0 text-xl leading-none p-1">×</button>
        </div>

        {/* Barra de acción */}
        <div className="px-6 py-3 border-b border-gray-100 flex-shrink-0 bg-gray-50/60">
          <button onClick={() => setCrear(true)}
            className="w-full inline-flex items-center justify-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-xl transition-all hover:shadow-md active:scale-[0.98]">
            <IconPlus /> Nuevo usuario
          </button>
        </div>

        <div className="px-6 py-4 overflow-y-auto flex-1 scrollbar-thin">
          {error && (
            <div className="mb-3 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg slide-up">{error}</div>
          )}
          {usuarios.length === 0 ? (
            <div className="flex flex-col items-center text-center py-10 text-gray-400">
              <div className="w-16 h-16 rounded-full bg-primary-50 text-primary-400 flex items-center justify-center mb-3">
                <IconUserPlus />
              </div>
              <p className="text-sm font-medium text-gray-600">Aún no hay usuarios</p>
              <p className="text-xs text-gray-400 mt-1 max-w-[240px]">Crea el primer acceso para que este cliente pueda solicitar y ver sus servicios.</p>
              <button onClick={() => setCrear(true)}
                className="mt-4 inline-flex items-center gap-1.5 bg-primary-600 hover:bg-primary-700 text-white text-xs font-medium px-4 py-2 rounded-full transition-all hover:shadow-md">
                <IconPlus /> Crear primer usuario
              </button>
            </div>
          ) : (
            <div className="space-y-2.5">
              {usuarios.map(u => (
                <div key={u.idUsuario}
                  className={`group border rounded-xl px-4 py-3 transition-all duration-150 hover:shadow-md hover:-translate-y-0.5 ${
                    u.activo ? 'border-gray-100 bg-white' : 'border-gray-100 bg-gray-50/70'
                  }`}>
                  <div className="flex items-start gap-3">
                    <Avatar nombre={u.nombre} apellido={u.apellido} rol={u.rol} />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-sm font-semibold text-gray-900 truncate">
                          {u.nombre} {u.apellido ?? ''}
                        </p>
                        <Badge activo={u.activo} />
                      </div>
                      <p className="text-xs text-gray-500 truncate">{u.email}</p>
                      <div className="flex items-center gap-2 mt-1.5 flex-wrap">
                        <span className={`text-[11px] px-2 py-0.5 rounded-full font-medium ${ROL_CLIENTE_STYLE[u.rol]?.badge ?? 'bg-gray-100 text-gray-600'}`}>
                          {ETIQUETA_ROL_CLIENTE[u.rol] ?? u.rol}
                        </span>
                        <span className="inline-flex items-center gap-1 text-[11px] text-gray-400">
                          <IconReloj /> {formatRelativo(u.ultimoAcceso)}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 mt-3 pt-3 border-t border-gray-50">
                    <button onClick={() => setEditar(u)}
                      className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-full border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors">
                      <IconLapiz /> Editar
                    </button>
                    <button onClick={() => toggleActivo(u)} disabled={accionando === u.idUsuario}
                      className={`inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-full border transition-colors disabled:opacity-50 ${
                        u.activo ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'
                      }`}>
                      <IconPower /> {accionando === u.idUsuario ? 'Espera...' : u.activo ? 'Desactivar' : 'Activar'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="px-6 py-3.5 border-t border-gray-100 flex-shrink-0">
          <button onClick={onClose} className="w-full py-2 text-sm text-gray-600 hover:text-gray-800 border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors">
            Cerrar
          </button>
        </div>
      </div>

      {crear && (
        <ModalCrearUsuarioCliente
          idCliente={cliente.idCliente}
          onClose={() => setCrear(false)}
          onGuardado={() => { onCambio('Usuario creado correctamente'); setCrear(false) }}
        />
      )}

      {editar && (
        <ModalEditarUsuarioCliente
          idCliente={cliente.idCliente}
          usuario={editar}
          onClose={() => setEditar(null)}
          onGuardado={() => { onCambio('Usuario actualizado correctamente'); setEditar(null) }}
        />
      )}
    </div>
  )
}

function formatRelativo(iso) {
  if (!iso) return '—'
  const diff = Date.now() - new Date(iso).getTime()
  const d = Math.floor(diff / 86400000)
  if (d === 0) return 'Hoy'
  if (d === 1) return 'Ayer'
  if (d < 30) return `Hace ${d} días`
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

/* ─── Página principal ─── */
export default function Clientes() {
  const [clientes, setClientes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  const [editando, setEditando] = useState(null)
  const [documentosDe, setDocumentosDe] = useState(null)
  const [usuariosDe, setUsuariosDe] = useState(null)
  const [editandoCredito, setEditandoCredito] = useState(null)

  const [busqueda, setBusqueda] = useState('')
  const [filtroEstado, setFiltroEstado] = useState('todos')
  const [filtroTipo, setFiltroTipo] = useState('todos')
  const [ordenCol, setOrdenCol] = useState(null)
  const [ordenDir, setOrdenDir] = useState('asc')
  const [pagina, setPagina] = useState(1)
  const POR_PAGINA = 7

  const mostrarToast = useCallback((msg, tipo = 'exito') => setToast({ mensaje: msg, tipo }), [])

  const cargar = useCallback(async () => {
    setCargando(true); setError(null)
    try {
      const res = await api.get('/clientes')
      setClientes(res.data)
      // Si hay modales abiertos sobre un cliente, se refrescan con los datos nuevos
      setUsuariosDe(prev => prev ? (res.data.find(c => c.idCliente === prev.idCliente) ?? null) : prev)
    } catch { setError('No se pudieron cargar los clientes.') }
    finally { setCargando(false) }
  }, [])

  useEffect(() => { cargar() }, [cargar])
  useEffect(() => { setPagina(1) }, [busqueda, filtroEstado, filtroTipo])

  const cambiarEstado = async (cliente, activar) => {
    try {
      await api.patch(`/clientes/${cliente.idCliente}/${activar ? 'activar' : 'desactivar'}`)
      cargar()
      mostrarToast(activar ? 'Cliente activado correctamente' : 'Cliente desactivado correctamente')
    } catch { setError('Error al cambiar el estado del cliente.') }
  }

  const manejarOrden = (col) => {
    if (ordenCol === col) setOrdenDir(d => d === 'asc' ? 'desc' : 'asc')
    else { setOrdenCol(col); setOrdenDir('asc') }
  }

  const clientesFiltrados = useMemo(() => {
    let lista = clientes

    if (filtroEstado === 'activos')   lista = lista.filter(c => c.estado === 'ACTIVO')
    if (filtroEstado === 'inactivos') lista = lista.filter(c => c.estado !== 'ACTIVO')
    if (filtroTipo === 'natural')     lista = lista.filter(c => c.tipoPersona === 'NATURAL')
    if (filtroTipo === 'juridica')    lista = lista.filter(c => c.tipoPersona === 'JURIDICA')
    if (filtroTipo === 'prepago')     lista = lista.filter(c => c.tipoCliente === 'PREPAGO')
    if (filtroTipo === 'pospago')     lista = lista.filter(c => c.tipoCliente === 'POSPAGO')

    if (busqueda.trim()) {
      const q = busqueda.toLowerCase()
      lista = lista.filter(c =>
        c.nombreDisplay?.toLowerCase().includes(q) ||
        c.emailPrincipal?.toLowerCase().includes(q) ||
        c.nit?.toLowerCase().includes(q) ||
        c.telefono?.toLowerCase().includes(q)
      )
    }

    if (ordenCol) {
      lista = [...lista].sort((a, b) => {
        let va = a[ordenCol] ?? ''
        let vb = b[ordenCol] ?? ''
        if (typeof va === 'string') va = va.toLowerCase()
        if (typeof vb === 'string') vb = vb.toLowerCase()
        if (va < vb) return ordenDir === 'asc' ? -1 : 1
        if (va > vb) return ordenDir === 'asc' ? 1 : -1
        return 0
      })
    }
    return lista
  }, [clientes, filtroEstado, filtroTipo, busqueda, ordenCol, ordenDir])

  const activos   = clientes.filter(c => c.estado === 'ACTIVO').length
  const inactivos = clientes.filter(c => c.estado !== 'ACTIVO').length

  const clientesVisibles = clientesFiltrados.slice((pagina - 1) * POR_PAGINA, pagina * POR_PAGINA)

  const columnas = [
    { key: 'nombreDisplay',  label: 'Nombre / Razón social', sortable: true  },
    { key: 'tipoCliente',    label: 'Modalidad',              sortable: true  },
    { key: 'telefono',       label: 'Teléfono',               sortable: false },
    { key: '_usuarios',      label: 'Usuarios',                sortable: false },
    { key: 'estado',         label: 'Estado',                 sortable: true  },
    { key: '_docs',          label: 'Documentos',              sortable: false },
    { key: '_acc',           label: 'Acciones',               sortable: false },
  ]

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Clientes</h2>
          <p className="text-sm text-gray-500 mt-0.5">Gestión y validaciones de cuentas cliente</p>
        </div>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
          {error}
          <button onClick={() => setError(null)} className="ml-4 text-red-400 hover:text-red-600">×</button>
        </div>
      )}

      {/* Filtros */}
      <div className="flex flex-wrap items-center gap-3 mb-3">
        <input
          type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre, email, NIT…"
          className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
        />

        {/* Estado */}
        <div className="flex gap-1">
          {[
            { k: 'todos',    l: 'Todos',    n: clientes.length },
            { k: 'activos',  l: 'Activos',  n: activos         },
            { k: 'inactivos',l: 'Inactivos',n: inactivos       },
          ].map(f => (
            <button key={f.k} onClick={() => setFiltroEstado(f.k)}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                filtroEstado === f.k
                  ? f.k === 'inactivos' ? 'bg-red-100 text-red-700' : f.k === 'activos' ? 'bg-green-100 text-green-700' : 'bg-primary-100 text-primary-700'
                  : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}>
              {f.l} <span className="ml-1 opacity-70">{f.n}</span>
            </button>
          ))}
        </div>

        {/* Tipo */}
        <select value={filtroTipo} onChange={e => setFiltroTipo(e.target.value)}
          className="border border-gray-200 rounded-lg px-3 py-1.5 text-xs text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
          <option value="todos">Todos los tipos</option>
          <option value="natural">Persona natural</option>
          <option value="juridica">Persona jurídica</option>
          <option value="prepago">Prepago</option>
          <option value="pospago">Pospago</option>
        </select>

        <span className="text-xs text-gray-400">{clientesFiltrados.length} resultado{clientesFiltrados.length !== 1 ? 's' : ''}</span>
      </div>

      {/* Tabla */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex items-center justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : clientesFiltrados.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">
            {clientes.length === 0 ? 'No hay clientes registrados.' : 'No hay clientes que coincidan con el filtro.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50">
                <tr>
                  {columnas.map(col => (
                    <th key={col.key}
                      onClick={col.sortable ? () => manejarOrden(col.key) : undefined}
                      className={`px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider select-none whitespace-nowrap ${col.sortable ? 'cursor-pointer hover:text-gray-700' : ''}`}>
                      {col.label}
                      {col.sortable && <IconoOrden activo={ordenCol === col.key} dir={ordenDir} />}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {clientesVisibles.map(c => (
                  <tr key={c.idCliente} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3">
                      <p className="text-sm font-medium text-gray-900">{c.nombreDisplay}</p>
                      {c.nit && <p className="text-xs text-gray-400">NIT: {c.nit}{c.dv ? `-${c.dv}` : ''}</p>}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-col gap-1">
                        <span className={`text-xs px-2 py-0.5 rounded font-medium w-fit ${
                          c.tipoCliente === 'PREPAGO' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'
                        }`}>
                          {c.tipoCliente === 'PREPAGO' ? 'Prepago' : 'Pospago'}
                        </span>
                        {c.pospago?.estadoMora && c.pospago.estadoMora !== 'NORMAL' && (
                          <BadgeMora mora={c.pospago.estadoMora} />
                        )}
                        {c.tipoCliente === 'POSPAGO' && (
                          <button onClick={() => setEditandoCredito(c)}
                            className="text-[10px] font-medium text-primary-600 hover:text-primary-800 hover:underline text-left w-fit">
                            {c.pospago ? 'Editar crédito' : 'Asignar crédito'}
                          </button>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">{c.telefono ?? '—'}</td>
                    <td className="px-4 py-3">
                      <button onClick={() => setUsuariosDe(c)}
                        className="group inline-flex items-center gap-2 pl-1 pr-3 py-1 rounded-full bg-slate-50 border border-slate-200 hover:border-primary-300 hover:bg-primary-50 transition-all whitespace-nowrap">
                        <span className="flex -space-x-1.5">
                          {(c.usuariosAsociados?.length ?? 0) === 0 ? (
                            <span className="w-5 h-5 rounded-full bg-slate-200 flex items-center justify-center">
                              <IconUsuarios className="h-3 w-3 text-slate-400" />
                            </span>
                          ) : (
                            c.usuariosAsociados.slice(0, 3).map(u => (
                              <span key={u.idUsuario} className="ring-2 ring-white rounded-full">
                                <Avatar nombre={u.nombre} apellido={u.apellido} rol={u.rol} size="h-5 w-5 text-[9px]" />
                              </span>
                            ))
                          )}
                        </span>
                        <span className="text-xs font-medium text-slate-600 group-hover:text-primary-700">
                          {(c.usuariosAsociados?.length ?? 0)}
                        </span>
                      </button>
                    </td>
                    <td className="px-4 py-3"><Badge activo={c.estado === 'ACTIVO'} /></td>
                    <td className="px-4 py-3">
                      <BolitasDocumentos resumen={c.documentosResumen} />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1.5 flex-nowrap">
                        <button onClick={() => setDocumentosDe(c)}
                          className="text-xs font-medium px-2.5 py-1 rounded-full border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors whitespace-nowrap">
                          Documentos
                        </button>
                        <button onClick={() => setEditando(c)}
                          className="text-xs font-medium px-2.5 py-1 rounded-full border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors whitespace-nowrap">
                          Editar
                        </button>
                        <button
                          onClick={() => cambiarEstado(c, c.estado !== 'ACTIVO')}
                          className={`text-xs font-medium px-2.5 py-1 rounded-full border transition-colors whitespace-nowrap ${
                            c.estado === 'ACTIVO'
                              ? 'border-red-200 text-red-600 hover:bg-red-50'
                              : 'border-green-200 text-green-700 hover:bg-green-50'
                          }`}>
                          {c.estado === 'ACTIVO' ? 'Desactivar' : 'Activar'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Paginacion
          pagina={pagina}
          total={clientesFiltrados.length}
          porPagina={POR_PAGINA}
          onChange={setPagina}
        />
      </div>

      {editando && (
        <ModalEditar
          cliente={editando}
          onClose={() => setEditando(null)}
          onGuardado={() => { cargar(); mostrarToast('Cliente actualizado correctamente') }}
        />
      )}

      {editandoCredito && (
        <ModalCredito
          cliente={editandoCredito}
          onClose={() => setEditandoCredito(null)}
          onGuardado={() => { cargar(); mostrarToast('Crédito actualizado correctamente') }}
        />
      )}

      {documentosDe && (
        <ModalDocumentosCliente cliente={documentosDe} onClose={() => { setDocumentosDe(null); cargar() }} />
      )}

      {usuariosDe && (
        <ModalUsuarios
          cliente={usuariosDe}
          onClose={() => setUsuariosDe(null)}
          onCambio={(msg) => { cargar(); mostrarToast(msg) }}
        />
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
