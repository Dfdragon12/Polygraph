import { parseFecha } from '../../utils/fechas'
import { useId, useState, useEffect, useCallback, useRef, useMemo } from 'react'
import soporteService from '../../services/soporteService'
import perfilService from '../../services/perfilService'
import Toast from '../../components/Toast'
import CiudadSelect from '../../components/CiudadSelect'
import { ConfirmModal } from '../../components/ui/Modal'
import TarjetaPassword from '../../components/TarjetaPassword'
import MenuAcciones from '../../components/ui/MenuAcciones'

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
  return parseFecha(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function diasHasta(fechaISO) {
  if (!fechaISO) return null
  const hoy = new Date(); hoy.setHours(0, 0, 0, 0)
  const fecha = parseFecha(fechaISO); fecha.setHours(0, 0, 0, 0)
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

/* ─── Sección: información de la empresa (mismo diseño que "Mi perfil" de los usuarios internos) ─── */
const TELEFONO_VALIDO = /^$|^[0-9+()\s-]{7,20}$/
const inputCls = 'w-full h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white focus:ring-2 focus:ring-primary-500 focus:outline-none aria-[invalid=true]:border-red-400'

const fechaHora = (iso) => (iso ? new Date(iso).toLocaleString('es-CO', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—')
const fechaLarga = (iso) => (iso ? parseFecha(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'long', year: 'numeric' }) : '—')

function Tarjeta({ titulo, descripcion, children }) {
  return (
    <section className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5">
      <h3 className="text-base font-semibold text-gray-900">{titulo}</h3>
      {descripcion && <p className="text-sm text-gray-500 mt-0.5">{descripcion}</p>}
      <div className="mt-4">{children}</div>
    </section>
  )
}

function Dato({ etiqueta, children }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-400">{etiqueta}</dt>
      <dd className="text-sm text-gray-800 mt-0.5 break-words">{children || '—'}</dd>
    </div>
  )
}

function SeccionPerfil({ onToast }) {
  const idCampos = useId()
  const [perfil, setPerfil] = useState(null)
  const [error, setError] = useState(null)
  const [form, setForm] = useState(null)
  const [intentoGuardar, setIntentoGuardar] = useState(false)
  const [guardando, setGuardando] = useState(false)
  const [errorForm, setErrorForm] = useState(null)

  const aplicar = useCallback((p) => {
    setPerfil(p)
    setForm({
      nombre: p.nombre ?? '', apellido: p.apellido ?? '',
      razonSocial: p.razonSocial ?? '', nombreComercial: p.nombreComercial ?? '', representanteLegal: p.representanteLegal ?? '',
      telefono: p.telefono ?? '', direccion: p.direccion ?? '', idCiudad: p.idCiudad ?? null,
    })
    setIntentoGuardar(false)
    setErrorForm(null)
  }, [])

  useEffect(() => {
    perfilService.obtener().then(aplicar).catch(() => setError('No se pudo cargar la información de la empresa.'))
  }, [aplicar])

  if (error) {
    return <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg" role="alert">{error}</div>
  }
  if (!perfil || !form) {
    return <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  }

  const esNatural = perfil.tipoPersona === 'NATURAL'
  const nombrePrincipal = esNatural ? `${perfil.nombre ?? ''} ${perfil.apellido ?? ''}`.trim() : (perfil.razonSocial || perfil.nombreComercial)
  const iniciales = (nombrePrincipal || '?').split(/\s+/).filter(Boolean).map(t => t[0]).join('').slice(0, 2).toUpperCase()

  const errores = {
    principal: (esNatural ? form.nombre : form.razonSocial).trim() ? null : (esNatural ? 'El nombre es obligatorio.' : 'La razón social es obligatoria.'),
    telefono: TELEFONO_VALIDO.test(form.telefono.trim()) ? null : 'Usa solo números, espacios, + , - y paréntesis (7 a 20 caracteres).',
  }
  const hayErrores = Boolean(errores.principal || errores.telefono)
  const hayCambios = ['nombre', 'apellido', 'razonSocial', 'nombreComercial', 'representanteLegal', 'telefono', 'direccion']
    .some(k => form[k] !== (perfil[k] ?? '')) || (form.idCiudad ?? null) !== (perfil.idCiudad ?? null)

  const cambiar = (campo) => (e) => setForm(f => ({ ...f, [campo]: e.target.value }))

  const guardar = async (e) => {
    e.preventDefault()
    setIntentoGuardar(true)
    setErrorForm(null)
    if (hayErrores) return
    setGuardando(true)
    try {
      aplicar(await perfilService.actualizar(form))
      onToast('Información actualizada correctamente')
    } catch (err) {
      const datos = err.response?.data
      setErrorForm((datos?.errores ? Object.values(datos.errores)[0] : null) ?? datos?.mensaje ?? 'No se pudo actualizar la información')
    } finally {
      setGuardando(false)
    }
  }

  const campoTexto = (clave, etiqueta, extra = {}) => (
    <div>
      <label htmlFor={`${idCampos}-${clave}`} className="block text-sm font-medium text-gray-700 mb-1">{etiqueta}</label>
      <input id={`${idCampos}-${clave}`} className={inputCls} value={form[clave]} onChange={cambiar(clave)} {...extra} />
    </div>
  )

  return (
    <div className="max-w-5xl">
      {/* Encabezado */}
      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm p-5 mb-4 flex flex-wrap items-center gap-4">
        <span className="h-16 w-16 rounded-full bg-primary-600 text-white text-xl font-bold flex items-center justify-center flex-shrink-0" aria-hidden="true">{iniciales}</span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-2xl font-bold text-gray-900 break-words">{nombrePrincipal}</h2>
            <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-primary-100 text-primary-700">
              {esNatural ? 'Persona natural' : 'Persona jurídica'}
            </span>
          </div>
          <p className="text-sm text-gray-500 mt-0.5 break-all">
            {perfil.nit ? `NIT ${perfil.nit}${perfil.dv ? `-${perfil.dv}` : ''} · ` : ''}{perfil.emailPrincipal}
          </p>
        </div>
        <dl className="flex gap-6 text-sm">
          <Dato etiqueta="Miembro desde">{fechaLarga(perfil.miembroDesde)}</Dato>
          <Dato etiqueta="Último acceso">{fechaHora(perfil.ultimoAcceso)}</Dato>
        </dl>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start">
        <div className="lg:col-span-2">
          <Tarjeta titulo="Datos de la empresa" descripcion="Mantén tu información al día: la usamos para contactarte y para tus informes.">
            <form onSubmit={guardar} noValidate>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {esNatural ? (
                  <>
                    <div>
                      <label htmlFor={`${idCampos}-nombre`} className="block text-sm font-medium text-gray-700 mb-1">Nombre *</label>
                      <input id={`${idCampos}-nombre`} className={inputCls} value={form.nombre} onChange={cambiar('nombre')} maxLength={100}
                        aria-invalid={intentoGuardar && Boolean(errores.principal)} aria-describedby={`${idCampos}-principal-error`} />
                      {intentoGuardar && errores.principal && <p id={`${idCampos}-principal-error`} className="text-xs text-red-600 mt-1">{errores.principal}</p>}
                    </div>
                    {campoTexto('apellido', 'Apellido', { maxLength: 100 })}
                  </>
                ) : (
                  <>
                    <div className="sm:col-span-2">
                      <label htmlFor={`${idCampos}-razonSocial`} className="block text-sm font-medium text-gray-700 mb-1">Razón social *</label>
                      <input id={`${idCampos}-razonSocial`} className={inputCls} value={form.razonSocial} onChange={cambiar('razonSocial')} maxLength={200}
                        aria-invalid={intentoGuardar && Boolean(errores.principal)} aria-describedby={`${idCampos}-principal-error`} />
                      {intentoGuardar && errores.principal && <p id={`${idCampos}-principal-error`} className="text-xs text-red-600 mt-1">{errores.principal}</p>}
                    </div>
                    {campoTexto('nombreComercial', 'Nombre comercial', { maxLength: 200 })}
                    {campoTexto('representanteLegal', 'Representante legal', { maxLength: 150 })}
                  </>
                )}
                <div>
                  <label htmlFor={`${idCampos}-telefono`} className="block text-sm font-medium text-gray-700 mb-1">Teléfono</label>
                  <input id={`${idCampos}-telefono`} type="tel" className={inputCls} value={form.telefono} onChange={cambiar('telefono')} maxLength={20}
                    placeholder="310 555 1234" autoComplete="tel" aria-invalid={Boolean(errores.telefono)} aria-describedby={`${idCampos}-telefono-ayuda`} />
                  <p id={`${idCampos}-telefono-ayuda`} className={`text-xs mt-1 ${errores.telefono ? 'text-red-600' : 'text-gray-400'}`}>
                    {errores.telefono ?? 'Para que podamos llamarte.'}
                  </p>
                </div>
                <div>
                  <label htmlFor={`${idCampos}-ciudad`} className="block text-sm font-medium text-gray-700 mb-1">Ciudad</label>
                  <CiudadSelect id={`${idCampos}-ciudad`} value={form.idCiudad} onChange={(idCiudad) => setForm(f => ({ ...f, idCiudad: idCiudad || null }))} />
                </div>
                <div className="sm:col-span-2">{campoTexto('direccion', 'Dirección', { autoComplete: 'street-address' })}</div>
              </div>

              {errorForm && <p className="mt-4 text-sm text-red-600" role="alert">{errorForm}</p>}

              <div className="mt-5 pt-4 border-t border-gray-100 flex flex-wrap items-center gap-3">
                <button type="submit" disabled={!hayCambios || guardando}
                  className="bg-primary-600 hover:bg-primary-700 disabled:opacity-40 text-white text-sm font-semibold px-5 py-2 rounded-lg transition-colors">
                  {guardando ? 'Guardando…' : 'Guardar cambios'}
                </button>
                {hayCambios && !guardando && (
                  <button type="button" onClick={() => aplicar(perfil)} className="text-sm text-gray-500 hover:text-gray-700">Descartar</button>
                )}
                <span className="text-xs text-gray-400" role="status">{hayCambios ? 'Tienes cambios sin guardar.' : 'Todo al día.'}</span>
              </div>
            </form>
          </Tarjeta>
        </div>

        <div className="space-y-4">
          <TarjetaPassword datos={perfil} />
          <Tarjeta titulo="Mi cuenta" descripcion="Estos datos no se pueden cambiar desde aquí.">
            <dl className="space-y-4">
              <Dato etiqueta="Correo principal">{perfil.emailPrincipal}</Dato>
              <Dato etiqueta="Tipo de persona">{esNatural ? 'Persona natural' : 'Persona jurídica'}</Dato>
              {perfil.nit && <Dato etiqueta="NIT">{`${perfil.nit}${perfil.dv ? `-${perfil.dv}` : ''}`}</Dato>}
              <Dato etiqueta="Tipo de cliente">{perfil.tipoCliente === 'POSPAGO' ? 'Pospago' : perfil.tipoCliente === 'PREPAGO' ? 'Prepago' : perfil.tipoCliente}</Dato>
            </dl>
          </Tarjeta>
        </div>
      </div>
    </div>
  )
}

/* ─── Documentos legales: qué falta hacer con cada uno ─── */

// Días antes del vencimiento en que se le avisa al cliente que debe renovar.
const DIAS_AVISO = 15

// Etapa del documento desde el punto de vista del cliente (cada documento cae en una sola):
//  accion   = tiene que hacer algo YA (cargarlo, corregirlo o renovarlo vencido)
//  porVencer= vigente, pero se vence pronto
//  revision = ya lo cargó y Polygraph lo revisa
//  vigente  = al día
function etapaDe(s) {
  if (s.estado === 'RECHAZADO' || s.estado === 'VENCIDO' || !s.estado || !s.nombreArchivo) return 'accion'
  if (s.estado === 'PENDIENTE') return 'revision'
  const dias = diasHasta(s.fechaVencimiento)
  if (dias !== null && dias < 0) return 'accion' // la fecha ya pasó aunque el estado aún no se haya actualizado
  return dias !== null && dias <= DIAS_AVISO ? 'porVencer' : 'vigente'
}

const ETAPAS = [
  { clave: 'accion', label: 'Requieren tu acción', desc: 'Cargar, corregir o renovar', border: 'border-red-200', dot: 'bg-red-400', texto: 'text-red-600', bgActivo: 'bg-red-50/70', ring: 'ring-red-300', orden: 0 },
  { clave: 'porVencer', label: 'Por vencer', desc: `Vencen en ${DIAS_AVISO} días o menos`, border: 'border-amber-200', dot: 'bg-amber-400', texto: 'text-amber-600', bgActivo: 'bg-amber-50/70', ring: 'ring-amber-300', orden: 1 },
  { clave: 'revision', label: 'En revisión', desc: 'Polygraph los está revisando', border: 'border-sky-200', dot: 'bg-sky-400', texto: 'text-sky-600', bgActivo: 'bg-sky-50/70', ring: 'ring-sky-300', orden: 2 },
  { clave: 'vigente', label: 'Vigentes', desc: 'Validados y al día', border: 'border-green-200', dot: 'bg-green-400', texto: 'text-green-600', bgActivo: 'bg-green-50/70', ring: 'ring-green-300', orden: 3 },
]
const ETAPA_POR_CLAVE = Object.fromEntries(ETAPAS.map(e => [e.clave, e]))

// Una línea que dice QUÉ hacer (o que no hay nada que hacer) con este documento.
function QueFalta({ s, etapa }) {
  const dias = diasHasta(s.fechaVencimiento)
  if (s.estado === 'RECHAZADO') {
    return (
      <div className="space-y-0.5">
        <p className="text-xs font-medium text-red-600">Corrígelo y vuelve a cargarlo</p>
        {s.observaciones && <p className="text-xs text-red-500">Motivo: {s.observaciones}</p>}
      </div>
    )
  }
  if (s.estado === 'VENCIDO' || (etapa === 'accion' && s.nombreArchivo && dias !== null && dias < 0)) {
    return <p className="text-xs font-medium text-orange-600">Está vencido: cárgalo de nuevo</p>
  }
  if (!s.nombreArchivo) return <p className="text-xs font-medium text-red-600">Cárgalo para poder operar</p>
  if (etapa === 'revision') return <p className="text-xs text-sky-700">Esperando la revisión de Polygraph</p>
  if (etapa === 'porVencer') {
    return <p className="text-xs font-medium text-amber-600">{dias === 0 ? 'Vence hoy' : `Vence en ${dias} día${dias !== 1 ? 's' : ''}`}: renuévalo</p>
  }
  return <p className="text-xs text-emerald-600">Al día</p>
}

function FilaSoporte({ soporte, onSubido, onToast }) {
  const [subiendo, setSubiendo] = useState(false)
  const [descargando, setDescargando] = useState(false)
  const [eliminando, setEliminando] = useState(false)
  const [confirmarEliminar, setConfirmarEliminar] = useState(false)
  const inputRef = useRef(null)
  const cfg = ESTADO_CFG[soporte.estado] ?? ESTADO_CFG.null
  const etapa = etapaDe(soporte)

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

  const iconoDescarga = (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 10l5 5 5-5M12 15V3" />
    </svg>
  )
  const iconoBorrar = (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2} aria-hidden="true">
      <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6M9.5 4h5a1 1 0 011 1v2h-7V5a1 1 0 011-1z" />
    </svg>
  )

  return (
    <>
      <tr className="hover:bg-gray-50/70 transition-colors align-top">
        <td className="px-3 py-3">
          <p className="text-sm font-medium text-gray-900">{soporte.nombreTipo}</p>
          <p className="text-[11px] text-gray-400 mt-0.5">
            {soporte.vigenciaDias ? `Se renueva cada ${soporte.vigenciaDias} días` : 'No requiere renovación periódica'}
          </p>
        </td>
        <td className="px-3 py-3">
          <span className={`inline-block text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${cfg.badge}`}>{cfg.label}</span>
        </td>
        <td className="px-3 py-3 text-xs">
          {soporte.nombreArchivo ? (
            <>
              <p className="text-gray-600 whitespace-nowrap">Cargado {formatFecha(soporte.fechaEntrega)}</p>
              {soporte.fechaVencimiento && <p className="mt-0.5"><EtiquetaVencimiento fecha={soporte.fechaVencimiento} /></p>}
            </>
          ) : <span className="text-gray-300">—</span>}
        </td>
        <td className="px-3 py-3 max-w-[16rem]"><QueFalta s={soporte} etapa={etapa} /></td>
        <td className="px-3 py-3">
          <div className="flex items-center justify-end gap-1.5">
            <input ref={inputRef} type="file" accept={EXTENSIONES_ACEPTADAS} className="hidden" onChange={manejarArchivo}
              aria-label={`Elegir archivo para ${soporte.nombreTipo}`} />
            <button type="button" onClick={() => inputRef.current?.click()} disabled={subiendo}
              className={`text-xs font-medium px-3 py-1.5 rounded-lg disabled:opacity-50 transition-colors whitespace-nowrap ${
                etapa === 'accion' ? 'bg-primary-600 hover:bg-primary-700 text-white' : 'bg-primary-50 text-primary-700 hover:bg-primary-100'}`}>
              {subiendo ? 'Cargando...' : soporte.nombreArchivo ? 'Reemplazar' : 'Cargar'}
            </button>
            {soporte.nombreArchivo && (
              <MenuAcciones opciones={[
                { label: descargando ? 'Descargando…' : 'Descargar', icono: iconoDescarga, onClick: () => !descargando && descargar() },
                { label: 'Eliminar', icono: iconoBorrar, destacado: 'text-red-600', onClick: () => setConfirmarEliminar(true) },
              ]} />
            )}
          </div>
        </td>
      </tr>

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

function SeccionDocumentos({ soportes, onSubido, onToast }) {
  const [filtro, setFiltro] = useState('')

  const conteos = useMemo(() => {
    const c = Object.fromEntries(ETAPAS.map(e => [e.clave, 0]))
    soportes.forEach(s => { c[etapaDe(s)] += 1 })
    return c
  }, [soportes])

  // Lo que requiere acción primero; dentro de cada etapa, el orden del catálogo.
  const filas = useMemo(() => soportes
    .map((s, i) => ({ s, i, etapa: etapaDe(s) }))
    .filter(x => !filtro || x.etapa === filtro)
    .sort((a, b) => ETAPA_POR_CLAVE[a.etapa].orden - ETAPA_POR_CLAVE[b.etapa].orden || a.i - b.i), [soportes, filtro])

  return (
    <>
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
        {ETAPAS.map(e => {
          const activo = filtro === e.clave
          return (
            <button key={e.clave} type="button" aria-pressed={activo} onClick={() => setFiltro(activo ? '' : e.clave)}
              className={`text-left bg-white rounded-xl border shadow-sm p-4 hover:shadow-md transition-shadow ${e.border} ${activo ? `${e.bgActivo} ring-2 ${e.ring}` : ''}`}>
              <div className="flex items-center gap-2 mb-2">
                <span className={`w-2 h-2 rounded-full ${e.dot}`} />
                <span className="text-xs font-medium text-gray-500">{e.label}</span>
              </div>
              <p className={`text-3xl font-bold ${e.texto}`}>{conteos[e.clave]}</p>
              <p className="text-xs text-gray-400 mt-1">{e.desc}</p>
            </button>
          )
        })}
      </div>

      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
        {filas.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">No tienes documentos en esta categoría.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-stack min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50/80">
                <tr>
                  {['Documento', 'Estado', 'Vigencia', 'Qué falta', '···'].map(h => (
                    <th key={h} className="px-3 py-3 text-left text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filas.map(({ s }) => (
                  <FilaSoporte key={s.tipoSoporte} soporte={s} onSubido={onSubido} onToast={onToast} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
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

  const pendientesRevision = soportes.filter(s => ['accion', 'porVencer'].includes(etapaDe(s))).length

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

      <nav className="flex flex-wrap gap-x-1 border-b border-gray-200 pb-px">
        {TABS.map(t => (
          <button key={t.key} onClick={() => setTab(t.key)}
            className={`px-3 sm:px-4 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 -mb-px transition-colors flex items-center gap-2 ${
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
            <SeccionDocumentos soportes={soportes} onSubido={cargar} onToast={mostrarToast} />
          )}
        </section>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
