import { useState, useEffect, useCallback, useRef } from 'react'
import soporteService from '../../services/soporteService'
import perfilService from '../../services/perfilService'
import Toast from '../../components/Toast'
import CiudadSelect from '../../components/CiudadSelect'
import { Modal, ConfirmModal } from '../../components/ui/Modal'

const EXTENSIONES_ACEPTADAS = '.pdf,.jpg,.jpeg,.png'

const ESTADO_CFG = {
  null:       { label: 'Sin cargar',  badge: 'bg-gray-100 text-gray-500',   barra: 'bg-gray-200' },
  PENDIENTE:  { label: 'En revisión', badge: 'bg-amber-100 text-amber-700', barra: 'bg-amber-400' },
  VALIDADO:   { label: 'Vigente',     badge: 'bg-green-100 text-green-700', barra: 'bg-green-500' },
  RECHAZADO:  { label: 'Rechazado',   badge: 'bg-red-100 text-red-600',     barra: 'bg-red-500' },
  VENCIDO:    { label: 'Vencido',     badge: 'bg-orange-100 text-orange-700', barra: 'bg-orange-500' },
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function diasHasta(fechaISO) {
  if (!fechaISO) return null
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0)
  const fecha = new Date(fechaISO); fecha.setHours(0, 0, 0, 0)
  return Math.round((fecha - hoy) / 86400000)
}

function EtiquetaVencimiento({ fecha }) {
  const dias = diasHasta(fecha)
  if (dias === null) return <span className="text-gray-400">No vence</span>
  if (dias < 0) return <span className="text-red-600 font-medium">Venció hace {Math.abs(dias)} día{Math.abs(dias) !== 1 ? 's' : ''}</span>
  if (dias === 0) return <span className="text-orange-600 font-medium">Vence hoy</span>
  if (dias <= 15) return <span className="text-amber-600 font-medium">Vence en {dias} día{dias !== 1 ? 's' : ''}</span>
  return <span className="text-gray-500">Vence {formatFecha(fecha)}</span>
}

/* ─── Sección: información de la empresa ─── */
function Campo({ label, children }) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>
      {children}
    </div>
  )
}

function CampoInfo({ label, valor }) {
  return (
    <div className="rounded-lg bg-gray-50 px-3 py-2.5">
      <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wide">{label}</p>
      <p className="text-sm text-gray-700 truncate">{valor || '—'}</p>
    </div>
  )
}

function ModalEditarPerfil({ perfil, onClose, onGuardado, onToast }) {
  const esNatural = perfil.tipoPersona === 'NATURAL'
  const [form, setForm] = useState({
    nombre: perfil.nombre ?? '',
    apellido: perfil.apellido ?? '',
    razonSocial: perfil.razonSocial ?? '',
    nombreComercial: perfil.nombreComercial ?? '',
    representanteLegal: perfil.representanteLegal ?? '',
    telefono: perfil.telefono ?? '',
    direccion: perfil.direccion ?? '',
    idCiudad: perfil.idCiudad ?? null,
  })
  const [guardando, setGuardando] = useState(false)

  const cambiar = (e) => {
    const { name, value } = e.target
    setForm((p) => ({ ...p, [name]: value }))
  }

  const guardar = async (e) => {
    e.preventDefault()
    setGuardando(true)
    try {
      const actualizado = await perfilService.actualizar(form)
      onGuardado(actualizado)
      onToast('Información actualizada correctamente')
      onClose()
    } catch (err) {
      onToast(err.response?.data?.mensaje ?? 'No se pudo actualizar la información', 'error')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <Modal titulo="Editar información de la empresa" subtitulo={esNatural ? 'Persona natural' : 'Persona jurídica'} onClose={onClose} ancho="max-w-lg">
      <form onSubmit={guardar} className="p-6 space-y-4">
        {esNatural ? (
          <div className="grid grid-cols-2 gap-4">
            <Campo label="Nombre">
              <input name="nombre" value={form.nombre} onChange={cambiar} required
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </Campo>
            <Campo label="Apellido">
              <input name="apellido" value={form.apellido} onChange={cambiar}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </Campo>
          </div>
        ) : (
          <>
            <Campo label="Razón social">
              <input name="razonSocial" value={form.razonSocial} onChange={cambiar} required
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </Campo>
            <div className="grid grid-cols-2 gap-4">
              <Campo label="Nombre comercial">
                <input name="nombreComercial" value={form.nombreComercial} onChange={cambiar}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
              </Campo>
              <Campo label="Representante legal">
                <input name="representanteLegal" value={form.representanteLegal} onChange={cambiar}
                  className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
              </Campo>
            </div>
          </>
        )}

        <div className="grid grid-cols-2 gap-4">
          <Campo label="Teléfono">
            <input name="telefono" value={form.telefono} onChange={cambiar} placeholder="Ej: 3001234567"
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </Campo>
          <Campo label="Ciudad">
            <CiudadSelect value={form.idCiudad} onChange={(idCiudad) => setForm((p) => ({ ...p, idCiudad }))} />
          </Campo>
        </div>
        <Campo label="Dirección">
          <input name="direccion" value={form.direccion} onChange={cambiar}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </Campo>

        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">
            Cancelar
          </button>
          <button type="submit" disabled={guardando}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
            {guardando ? 'Guardando...' : 'Guardar cambios'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

function SeccionPerfil({ onToast }) {
  const [perfil, setPerfil] = useState(null)
  const [error, setError] = useState(null)
  const [modalAbierto, setModalAbierto] = useState(false)

  const cargar = useCallback(() => {
    perfilService.obtener()
      .then(setPerfil)
      .catch(() => setError('No se pudo cargar la información de la empresa.'))
  }, [])

  useEffect(() => { cargar() }, [cargar])

  if (error) {
    return <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 text-sm text-red-500">{error}</div>
  }

  if (!perfil) {
    return (
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 flex justify-center">
        <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" />
      </div>
    )
  }

  const esNatural = perfil.tipoPersona === 'NATURAL'
  const nombrePrincipal = esNatural
    ? `${perfil.nombre ?? ''} ${perfil.apellido ?? ''}`.trim()
    : (perfil.razonSocial || perfil.nombreComercial)
  const iniciales = (nombrePrincipal || '?').slice(0, 2).toUpperCase()

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden transition-shadow hover:shadow-md">
      <div className="p-5 flex items-start gap-4 bg-gradient-to-br from-primary-50/60 to-transparent">
        <div className="w-12 h-12 rounded-xl bg-primary-600 text-white flex items-center justify-center text-base font-bold flex-shrink-0 shadow-sm">
          {iniciales}
        </div>
        <div className="flex-1 min-w-0 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <p className="text-sm font-semibold text-gray-800 truncate">{nombrePrincipal}</p>
              <span className="flex-shrink-0 text-[10px] font-medium px-2 py-0.5 rounded-full bg-primary-100 text-primary-700">
                {esNatural ? 'Persona natural' : 'Persona jurídica'}
              </span>
            </div>
            <p className="text-xs text-gray-400 mt-0.5">
              {perfil.nit ? `NIT: ${perfil.nit}${perfil.dv ? `-${perfil.dv}` : ''}` : perfil.emailPrincipal}
            </p>
          </div>
          <button onClick={() => setModalAbierto(true)}
            className="flex-shrink-0 flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg bg-white border border-primary-200 text-primary-700 hover:bg-primary-50 transition-colors shadow-sm">
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
            Editar
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 px-5 pb-5 pt-1">
        {!esNatural && <CampoInfo label="Representante legal" valor={perfil.representanteLegal} />}
        {!esNatural && <CampoInfo label="Nombre comercial" valor={perfil.nombreComercial} />}
        <CampoInfo label="Teléfono" valor={perfil.telefono} />
        <CampoInfo label="Ciudad" valor={perfil.nombreCiudad} />
        <CampoInfo label="Dirección" valor={perfil.direccion} />
      </div>

      {modalAbierto && (
        <ModalEditarPerfil
          perfil={perfil}
          onClose={() => setModalAbierto(false)}
          onGuardado={setPerfil}
          onToast={onToast}
        />
      )}
    </div>
  )
}

/* ─── Tarjeta de documento ─── */
function TarjetaSoporte({ soporte, onSubido, onToast }) {
  const [subiendo, setSubiendo] = useState(false)
  const [descargando, setDescargando] = useState(false)
  const [eliminando, setEliminando] = useState(false)
  const [confirmarEliminar, setConfirmarEliminar] = useState(false)
  const inputRef = useRef(null)
  const cfg = ESTADO_CFG[soporte.estado] ?? ESTADO_CFG.null

  const manejarArchivo = async (e) => {
    const archivo = e.target.files?.[0]
    e.target.value = ''
    if (!archivo) return
    setSubiendo(true)
    try {
      await soporteService.subirCliente(soporte.tipoSoporte, archivo)
      onToast(`${soporte.nombreTipo} cargado — queda pendiente de revisión`)
      onSubido()
    } catch (err) {
      onToast(err.response?.data?.mensaje ?? 'No se pudo cargar el archivo', 'error')
    } finally {
      setSubiendo(false)
    }
  }

  const descargar = async () => {
    setDescargando(true)
    try {
      const blob = await soporteService.descargarCliente(soporte.idSoporte)
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = soporte.nombreArchivo || 'documento'
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      onToast('No se pudo descargar el documento', 'error')
    } finally {
      setDescargando(false)
    }
  }

  const eliminar = async () => {
    setEliminando(true)
    try {
      await soporteService.eliminarCliente(soporte.idSoporte)
      onToast(`${soporte.nombreTipo} eliminado`)
      setConfirmarEliminar(false)
      onSubido()
    } catch (err) {
      onToast(err.response?.data?.mensaje ?? 'No se pudo eliminar el documento', 'error')
    } finally {
      setEliminando(false)
    }
  }

  return (
    <>
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden flex flex-col transition-all hover:shadow-md hover:-translate-y-0.5">
      <div className={`h-1 ${cfg.barra}`} />
      <div className="p-5 flex flex-col gap-3 flex-1">
        <div className="flex items-start justify-between gap-3">
          <h3 className="text-sm font-semibold text-gray-800 leading-snug">{soporte.nombreTipo}</h3>
          <span className={`flex-shrink-0 text-xs font-medium px-2 py-0.5 rounded-full ${cfg.badge}`}>{cfg.label}</span>
        </div>

        <div className="text-xs text-gray-500 space-y-1">
          {soporte.vigenciaDias
            ? <p>Se debe renovar cada {soporte.vigenciaDias} días.</p>
            : <p>No requiere renovación periódica.</p>}
          {soporte.nombreArchivo && (
            <p className="truncate" title={soporte.nombreArchivo}>Cargado: {formatFecha(soporte.fechaEntrega)}</p>
          )}
          {soporte.fechaVencimiento && (
            <p><EtiquetaVencimiento fecha={soporte.fechaVencimiento} /></p>
          )}
        </div>

        {soporte.estado === 'RECHAZADO' && soporte.observaciones && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-lg">
            <strong>Motivo del rechazo:</strong> {soporte.observaciones}
          </div>
        )}

        <div className="mt-auto pt-2 flex items-center gap-2">
          <input ref={inputRef} type="file" accept={EXTENSIONES_ACEPTADAS} className="hidden" onChange={manejarArchivo} />
          <button onClick={() => inputRef.current?.click()} disabled={subiendo}
            className="flex-1 text-xs font-medium px-3 py-2 rounded-lg bg-primary-50 text-primary-700 hover:bg-primary-100 disabled:opacity-50 transition-colors">
            {subiendo ? 'Cargando...' : soporte.nombreArchivo ? 'Reemplazar' : 'Cargar documento'}
          </button>
          {soporte.nombreArchivo && (
            <>
              <button onClick={descargar} disabled={descargando} title="Descargar"
                className="text-xs font-medium p-2 rounded-lg border border-gray-200 text-gray-600 hover:bg-gray-50 disabled:opacity-50 transition-colors">
                {descargando
                  ? <span className="block h-4 w-4 animate-spin rounded-full border-2 border-gray-400 border-t-transparent" />
                  : (
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 10l5 5 5-5M12 15V3" />
                    </svg>
                  )}
              </button>
              <button onClick={() => setConfirmarEliminar(true)} title="Eliminar"
                className="text-xs font-medium p-2 rounded-lg border border-gray-200 text-red-500 hover:bg-red-50 hover:border-red-200 transition-colors">
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6M9.5 4h5a1 1 0 011 1v2h-7V5a1 1 0 011-1z" />
                </svg>
              </button>
            </>
          )}
        </div>
      </div>
    </div>

    {confirmarEliminar && (
      <ConfirmModal
        titulo={`¿Eliminar "${soporte.nombreTipo}"?`}
        danger
        cargando={eliminando}
        confirmLabel="Eliminar"
        onConfirmar={eliminar}
        onCancelar={() => setConfirmarEliminar(false)}>
        <p className="text-sm text-gray-500">
          Se eliminará el archivo cargado y el documento volverá a quedar pendiente de carga. Esta acción no se puede deshacer.
        </p>
      </ConfirmModal>
    )}
    </>
  )
}

const TABS = [
  { key: 'perfil',      label: 'Información de la empresa' },
  { key: 'documentos',  label: 'Documentos legales' },
]

export default function Documentos() {
  const [tab, setTab] = useState('perfil')
  const [soportes, setSoportes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  const cargar = useCallback(async () => {
    setCargando(true)
    setError(null)
    try {
      setSoportes(await soporteService.listarCliente())
    } catch {
      setError('No se pudieron cargar los documentos. Verifica tu conexión.')
    } finally {
      setCargando(false)
    }
  }, [])

  useEffect(() => { cargar() }, [cargar])

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  const pendientesRevision = soportes.filter(s =>
    s.estado === 'RECHAZADO' || (diasHasta(s.fechaVencimiento) !== null && diasHasta(s.fechaVencimiento) <= 15)
  ).length

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-gray-800">Actualización de información</h2>
        <p className="text-sm text-gray-500 mt-1">
          Mantén al día los datos de tu empresa y los soportes legales requeridos para operar con
          Polygraph Service. Algunos documentos deben renovarse periódicamente — te avisaremos antes
          de que venzan.
        </p>
      </div>

      <nav className="flex gap-1 overflow-x-auto border-b border-gray-200 pb-px">
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-4 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 -mb-px transition-colors flex items-center gap-2 ${
              tab === t.key
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}>
            {t.label}
            {t.key === 'documentos' && pendientesRevision > 0 && (
              <span className="bg-amber-100 text-amber-700 text-[10px] font-semibold rounded-full px-1.5 py-0.5">{pendientesRevision}</span>
            )}
          </button>
        ))}
      </nav>

      {tab === 'perfil' && (
        <section className="animate-fade-in">
          <SeccionPerfil onToast={mostrarToast} />
        </section>
      )}

      {tab === 'documentos' && (
        <section className="animate-fade-in">
          {cargando ? (
            <div className="flex items-center justify-center h-40">
              <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" />
            </div>
          ) : error ? (
            <div className="flex flex-col items-center justify-center h-40 gap-2">
              <p className="text-red-500 text-sm">{error}</p>
              <button onClick={cargar} className="text-xs text-primary-600 hover:underline">Reintentar</button>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
              {soportes.map((s) => (
                <TarjetaSoporte key={s.tipoSoporte} soporte={s} onSubido={cargar} onToast={mostrarToast} />
              ))}
            </div>
          )}
        </section>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
