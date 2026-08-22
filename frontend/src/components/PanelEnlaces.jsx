import { useState, useEffect, useCallback } from 'react'
import { useAuth } from '../hooks/useAuth'
import enlacesService from '../services/enlacesService'
import { alCambiarTexto } from '../utils/texto'
import { Modal, ConfirmModal } from './ui/Modal'

const CATEGORIAS = {
  ACADEMICOS:   { label: 'Académicos',   color: 'bg-blue-100 text-blue-700 border-blue-200',   icono: '🎓' },
  LABORALES:    { label: 'Laborales',    color: 'bg-amber-100 text-amber-700 border-amber-200', icono: '💼' },
  ANTECEDENTES: { label: 'Antecedentes', color: 'bg-red-100 text-red-700 border-red-200',       icono: '⚖️' },
  FINANCIERO:   { label: 'Financiero',   color: 'bg-green-100 text-green-700 border-green-200', icono: '💰' },
}

const ORDEN_CATS = ['ACADEMICOS', 'LABORALES', 'ANTECEDENTES', 'FINANCIERO']

const FORM_VACIO = {
  categoria: 'ACADEMICOS',
  nombreEntidad: '',
  url: '',
  correoContacto: '',
  telefonoContacto: '',
  ciudad: '',
  requiereLogin: false,
  notas: '',
}

function FormEnlace({ enlace, onGuardado, onCancelar }) {
  const [form, setForm] = useState(enlace
    ? {
        categoria:       enlace.categoria,
        nombreEntidad:   enlace.nombreEntidad,
        url:             enlace.url ?? '',
        correoContacto:  enlace.correoContacto ?? '',
        telefonoContacto: enlace.telefonoContacto ?? '',
        ciudad:          enlace.ciudad ?? '',
        requiereLogin:   enlace.requiereLogin ?? false,
        notas:           enlace.notas ?? '',
      }
    : { ...FORM_VACIO }
  )
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    const payload = {
      ...form,
      url:              form.url || null,
      correoContacto:   form.correoContacto || null,
      telefonoContacto: form.telefonoContacto || null,
      ciudad:           form.ciudad || null,
      notas:            form.notas || null,
    }
    try {
      if (enlace) {
        await enlacesService.actualizar(enlace.idEnlace, payload)
      } else {
        await enlacesService.crear(payload)
      }
      onGuardado()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al guardar.')
    } finally { setGuardando(false) }
  }

  const set = (campo, val) => setForm(p => ({ ...p, [campo]: val }))

  return (
    <Modal titulo={enlace ? 'Editar enlace' : 'Nuevo enlace'} onClose={onCancelar} ancho="max-w-md">
        <form onSubmit={handleSubmit} className="overflow-y-auto flex-1 px-6 py-4 space-y-3">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg">{error}</div>
          )}
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Categoría <span className="text-red-500">*</span></label>
            <select value={form.categoria} onChange={e => set('categoria', e.target.value)} required
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
              {ORDEN_CATS.map(cat => (
                <option key={cat} value={cat}>{CATEGORIAS[cat].icono} {CATEGORIAS[cat].label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Nombre de la entidad <span className="text-red-500">*</span></label>
            <input value={form.nombreEntidad} required maxLength={200}
              onChange={e => set('nombreEntidad', alCambiarTexto(e.target.value))}
              placeholder="Ej: Universidad Nacional de Colombia"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">URL</label>
            <input type="url" value={form.url} maxLength={500}
              onChange={e => set('url', e.target.value)}
              placeholder="https://..."
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Correo</label>
              <input type="email" value={form.correoContacto} maxLength={150}
                onChange={e => set('correoContacto', e.target.value)}
                placeholder="contacto@entidad.co"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Teléfono</label>
              <input value={form.telefonoContacto} maxLength={30}
                onChange={e => set('telefonoContacto', e.target.value)}
                placeholder="601 234 5678"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Ciudad</label>
            <input value={form.ciudad} maxLength={100}
              onChange={e => set('ciudad', alCambiarTexto(e.target.value))}
              placeholder="Bogotá"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Notas</label>
            <textarea value={form.notas} rows={2}
              onChange={e => set('notas', alCambiarTexto(e.target.value))}
              placeholder="Instrucciones de acceso, observaciones…"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
          </div>
          <label className="flex items-center gap-2 cursor-pointer select-none">
            <input type="checkbox" checked={form.requiereLogin} onChange={e => set('requiereLogin', e.target.checked)}
              className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
            <span className="text-sm text-gray-700">Requiere login / credenciales</span>
          </label>
          <div className="flex justify-end gap-3 pt-2 border-t border-gray-100">
            <button type="button" onClick={onCancelar}
              className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
            <button type="submit" disabled={guardando}
              className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
              {guardando ? 'Guardando...' : enlace ? 'Guardar cambios' : 'Crear enlace'}
            </button>
          </div>
        </form>
    </Modal>
  )
}

function ModalConfirmar({ nombre, onConfirmar, onCancelar }) {
  return (
    <ConfirmModal titulo="Eliminar enlace" danger onConfirmar={onConfirmar} onCancelar={onCancelar} confirmLabel="Eliminar">
      <p className="text-sm text-gray-600">
        ¿Estás seguro de que deseas eliminar <span className="font-medium text-gray-700">"{nombre}"</span>? Esta acción no se puede deshacer.
      </p>
    </ConfirmModal>
  )
}

function TarjetaEnlace({ enlace, esAdmin, onEditar, onEliminar, onToggle }) {
  const cfg = CATEGORIAS[enlace.categoria] ?? CATEGORIAS.ACADEMICOS
  return (
    <div className={`rounded-xl border p-4 space-y-2 ${enlace.activo ? 'bg-white' : 'bg-gray-50 opacity-60'}`}>
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-gray-900 leading-snug">{enlace.nombreEntidad}</p>
          {enlace.ciudad && <p className="text-xs text-gray-400 mt-0.5">{enlace.ciudad}</p>}
        </div>
        <div className="flex items-center gap-1 flex-shrink-0">
          {enlace.requiereLogin && (
            <span className="text-xs px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 border border-amber-200">🔑 Login</span>
          )}
          {!enlace.activo && (
            <span className="text-xs px-1.5 py-0.5 rounded bg-gray-200 text-gray-500">Inactivo</span>
          )}
        </div>
      </div>

      {enlace.url && (
        <a href={enlace.url} target="_blank" rel="noopener noreferrer"
          className="flex items-center gap-1.5 text-xs text-primary-600 hover:text-primary-800 hover:underline truncate">
          <svg className="h-3 w-3 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
          </svg>
          <span className="truncate">{enlace.url}</span>
        </a>
      )}

      {(enlace.correoContacto || enlace.telefonoContacto) && (
        <div className="flex flex-wrap gap-3 text-xs text-gray-500">
          {enlace.correoContacto && (
            <a href={`mailto:${enlace.correoContacto}`} className="flex items-center gap-1 hover:text-gray-700">
              <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
              {enlace.correoContacto}
            </a>
          )}
          {enlace.telefonoContacto && (
            <span className="flex items-center gap-1">
              <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
              </svg>
              {enlace.telefonoContacto}
            </span>
          )}
        </div>
      )}

      {enlace.notas && (
        <p className="text-xs text-gray-400 italic leading-relaxed">{enlace.notas}</p>
      )}

      <div className="flex items-center gap-1.5 pt-1 border-t border-gray-100">
        <button onClick={() => onEditar(enlace)}
          className="text-xs px-2.5 py-1 rounded-full border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors">
          Editar
        </button>
        {esAdmin && (
          <>
            <button onClick={() => onToggle(enlace)}
              className={`text-xs px-2.5 py-1 rounded-full border transition-colors ${
                enlace.activo
                  ? 'border-amber-200 text-amber-600 hover:bg-amber-50'
                  : 'border-green-200 text-green-700 hover:bg-green-50'
              }`}>
              {enlace.activo ? 'Desactivar' : 'Activar'}
            </button>
            <button onClick={() => onEliminar(enlace)}
              className="text-xs px-2.5 py-1 rounded-full border border-red-200 text-red-600 hover:bg-red-50 transition-colors">
              Eliminar
            </button>
          </>
        )}
      </div>
    </div>
  )
}

export default function PanelEnlaces({ abierto, onCerrar }) {
  const { usuario } = useAuth()
  const esAdmin = usuario?.rol === 'ADMIN_POLYGRAPH'

  const [enlaces, setEnlaces] = useState([])
  const [cargando, setCargando] = useState(false)
  const [tabActiva, setTabActiva] = useState('ACADEMICOS')
  const [formAbierto, setFormAbierto] = useState(false)
  const [editando, setEditando] = useState(null)
  const [error, setError] = useState(null)
  const [busqueda, setBusqueda] = useState('')
  const [confirmarEnlace, setConfirmarEnlace] = useState(null)

  const cargar = useCallback(async () => {
    setCargando(true); setError(null)
    try {
      const data = esAdmin
        ? await enlacesService.listarTodos()
        : await enlacesService.listar()
      setEnlaces(data)
    } catch {
      setError('Error al cargar los enlaces.')
    } finally { setCargando(false) }
  }, [esAdmin])

  useEffect(() => {
    if (abierto) cargar()
  }, [abierto, cargar])

  const abrirCrear = () => { setEditando(null); setFormAbierto(true) }
  const abrirEditar = (e) => { setEditando(e); setFormAbierto(true) }

  const onGuardado = () => { setFormAbierto(false); setEditando(null); cargar() }

  const pedirEliminar = (enlace) => setConfirmarEnlace(enlace)

  const confirmarEliminar = async () => {
    if (!confirmarEnlace) return
    try {
      await enlacesService.eliminar(confirmarEnlace.idEnlace)
      cargar()
    } catch { setError('Error al eliminar.') }
    setConfirmarEnlace(null)
  }

  const toggle = async (enlace) => {
    try {
      await enlacesService.cambiarEstado(enlace.idEnlace, !enlace.activo)
      cargar()
    } catch { setError('Error al cambiar estado.') }
  }

  const busquedaActiva = busqueda.trim().length > 0
  const q = busqueda.toLowerCase()
  const enlacesFiltrados = busquedaActiva
    ? enlaces.filter(e =>
        e.nombreEntidad.toLowerCase().includes(q) ||
        (e.url ?? '').toLowerCase().includes(q) ||
        (e.correoContacto ?? '').toLowerCase().includes(q) ||
        (e.ciudad ?? '').toLowerCase().includes(q) ||
        (e.notas ?? '').toLowerCase().includes(q)
      )
    : null
  const enlacesCat = busquedaActiva ? [] : enlaces.filter(e => e.categoria === tabActiva)

  if (!abierto) return null

  return (
    <>
      {/* Overlay */}
      <div className="fixed inset-0 z-40 bg-black/30 backdrop-blur-sm" onClick={onCerrar} />

      {/* Drawer */}
      <div className="fixed inset-y-0 right-0 z-50 flex flex-col w-full max-w-md bg-white shadow-2xl">
        {/* Header */}
        <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between flex-shrink-0">
          <div className="flex items-center gap-2">
            <svg className="h-5 w-5 text-primary-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
            </svg>
            <h2 className="text-base font-semibold text-gray-900">Referencias externas</h2>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={abrirCrear}
              className="inline-flex items-center gap-1.5 text-xs font-medium bg-emerald-600 hover:bg-emerald-700 text-white px-3 py-1.5 rounded-lg transition-colors">
              <span className="text-sm leading-none">+</span> Agregar
            </button>
            <button onClick={onCerrar}
              className="p-1.5 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100 transition-colors">
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        {/* Buscador */}
        <div className="px-4 py-2.5 border-b border-gray-100 flex-shrink-0">
          <div className="relative">
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="search"
              value={busqueda}
              onChange={e => setBusqueda(e.target.value)}
              placeholder="Buscar en todas las categorías…"
              className="w-full pl-9 pr-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white"
            />
          </div>
        </div>

        {/* Tabs de categoría — ocultos durante búsqueda */}
        <div className={`flex gap-0.5 px-4 py-2 bg-gray-50 border-b border-gray-100 flex-shrink-0 overflow-x-auto ${busquedaActiva ? 'hidden' : ''}`}>
          {ORDEN_CATS.map(cat => {
            const cfg = CATEGORIAS[cat]
            const count = enlaces.filter(e => e.categoria === cat).length
            return (
              <button key={cat} onClick={() => setTabActiva(cat)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-all ${
                  tabActiva === cat
                    ? 'bg-white shadow-sm text-primary-700 ring-1 ring-gray-200'
                    : 'text-gray-500 hover:text-gray-700 hover:bg-white/60'
                }`}>
                <span>{cfg.icono}</span>
                {cfg.label}
                <span className={`inline-flex items-center justify-center h-4 min-w-[16px] px-1 rounded-full text-xs font-semibold ${
                  tabActiva === cat ? 'bg-primary-100 text-primary-700' : 'bg-gray-200 text-gray-500'
                }`}>{count}</span>
              </button>
            )
          })}
        </div>

        {/* Contenido */}
        <div className="flex-1 overflow-y-auto p-4">
          {error && (
            <div className="mb-3 bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg flex items-center justify-between">
              {error}
              <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-2">×</button>
            </div>
          )}

          {cargando ? (
            <div className="flex justify-center py-16">
              <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
            </div>
          ) : busquedaActiva ? (
            /* ── Resultados de búsqueda global ── */
            enlacesFiltrados.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-16 text-center">
                <svg className="h-10 w-10 text-gray-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                <p className="text-sm text-gray-400">Sin resultados para <span className="font-medium">"{busqueda}"</span></p>
              </div>
            ) : (
              <div className="space-y-4">
                <p className="text-xs text-gray-400">{enlacesFiltrados.length} resultado{enlacesFiltrados.length !== 1 ? 's' : ''}</p>
                {ORDEN_CATS.map(cat => {
                  const items = enlacesFiltrados.filter(e => e.categoria === cat)
                  if (!items.length) return null
                  const cfg = CATEGORIAS[cat]
                  return (
                    <div key={cat}>
                      <div className="flex items-center gap-1.5 mb-2">
                        <span className="text-sm">{cfg.icono}</span>
                        <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${cfg.color}`}>{cfg.label}</span>
                      </div>
                      <div className="space-y-2">
                        {items.map(e => (
                          <TarjetaEnlace key={e.idEnlace} enlace={e} esAdmin={esAdmin}
                            onEditar={abrirEditar} onEliminar={pedirEliminar} onToggle={toggle} />
                        ))}
                      </div>
                    </div>
                  )
                })}
              </div>
            )
          ) : enlacesCat.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <span className="text-4xl mb-3">{CATEGORIAS[tabActiva]?.icono}</span>
              <p className="text-sm text-gray-400 mb-1">Sin enlaces en {CATEGORIAS[tabActiva]?.label}</p>
              <button onClick={abrirCrear}
                className="mt-3 text-xs text-primary-600 hover:text-primary-800 underline">
                Agregar el primero
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {enlacesCat.map(e => (
                <TarjetaEnlace key={e.idEnlace} enlace={e} esAdmin={esAdmin}
                  onEditar={abrirEditar} onEliminar={pedirEliminar} onToggle={toggle} />
              ))}
            </div>
          )}
        </div>

        {/* Footer con info */}
        <div className="px-5 py-3 border-t border-gray-100 bg-gray-50 flex-shrink-0">
          <p className="text-xs text-gray-400 text-center">
            {enlaces.filter(e => e.activo).length} enlace{enlaces.filter(e => e.activo).length !== 1 ? 's' : ''} activo{enlaces.filter(e => e.activo).length !== 1 ? 's' : ''} en total
          </p>
        </div>
      </div>

      {/* Modal formulario */}
      {formAbierto && (
        <FormEnlace
          enlace={editando}
          onGuardado={onGuardado}
          onCancelar={() => { setFormAbierto(false); setEditando(null) }}
        />
      )}

      {/* Modal confirmación eliminar */}
      {confirmarEnlace && (
        <ModalConfirmar
          nombre={confirmarEnlace.nombreEntidad}
          onConfirmar={confirmarEliminar}
          onCancelar={() => setConfirmarEnlace(null)}
        />
      )}
    </>
  )
}
