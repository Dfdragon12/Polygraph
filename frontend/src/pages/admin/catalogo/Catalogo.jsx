import { useState, useEffect, useCallback, useMemo, Fragment } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../../../services/api'
import Toast from '../../../components/Toast'
import { alCambiarTexto } from '../../../utils/texto'
import { Modal, ConfirmModal } from '../../../components/ui/Modal'

/* ─── Paleta dinámica de colores para clasificaciones ─── */
const PALETA = [
  { color: 'bg-blue-100 text-blue-700 border-blue-200',       grad: 'from-blue-500 to-blue-600'       },
  { color: 'bg-purple-100 text-purple-700 border-purple-200', grad: 'from-purple-500 to-purple-600'   },
  { color: 'bg-emerald-100 text-emerald-700 border-emerald-200', grad: 'from-emerald-500 to-emerald-600' },
  { color: 'bg-gray-100 text-gray-600 border-gray-200',       grad: 'from-gray-500 to-gray-600'       },
  { color: 'bg-orange-100 text-orange-700 border-orange-200', grad: 'from-orange-500 to-orange-600'   },
  { color: 'bg-rose-100 text-rose-700 border-rose-200',       grad: 'from-rose-500 to-rose-600'       },
  { color: 'bg-teal-100 text-teal-700 border-teal-200',       grad: 'from-teal-500 to-teal-600'       },
  { color: 'bg-amber-100 text-amber-700 border-amber-200',    grad: 'from-amber-500 to-amber-600'     },
]

/** El backend siempre manda mensaje="Errores de validación" para cualquier fallo de @Valid — el
 * detalle real (qué campo y por qué) viaja aparte en `errores: {campo: mensaje}`. Sin esto, el
 * usuario ve el mismo texto genérico sin importar cuál sea el problema real. */
function mensajeErrorValidacion(err, fallback = 'Error al guardar.') {
  const data = err.response?.data
  if (data?.errores && typeof data.errores === 'object' && Object.keys(data.errores).length) {
    return Object.entries(data.errores).map(([campo, msg]) => `${campo}: ${msg}`).join(' · ')
  }
  return data?.mensaje ?? data?.message ?? fallback
}

function cfgColor(clasificaciones, codigo) {
  const idx = clasificaciones.findIndex(c => c.codigo === codigo)
  return PALETA[(idx >= 0 ? idx : 3) % PALETA.length]
}

function fmtValor(valor) {
  if (valor == null) return null
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(valor)
}

function fmtTiempo(minutos) {
  if (minutos == null) return null
  const horas = Math.floor(minutos / 60)
  const mins = minutos % 60
  if (horas === 0) return `${mins} min`
  if (mins === 0) return `${horas} h`
  return `${horas} h ${mins} min`
}

/* ─── Componentes base ─── */
function IconoOrden({ activo, dir }) {
  if (!activo) return <span className="ml-1 text-gray-300 text-xs">↕</span>
  return <span className="ml-1 text-primary-500 text-xs">{dir === 'asc' ? '↑' : '↓'}</span>
}

function BarraFiltros({ busqueda, onBusqueda, filtro, onFiltro, total, activos, inactivos, visibles }) {
  return (
    <div className="flex flex-wrap items-center gap-3 mb-3">
      <input type="search" value={busqueda} onChange={e => onBusqueda(e.target.value)}
        placeholder="Buscar por nombre o descripción…"
        className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      <div className="flex gap-1">
        {[
          { key: 'todos',     label: 'Todos',     count: total    },
          { key: 'activos',   label: 'Activos',   count: activos  },
          { key: 'inactivos', label: 'Inactivos', count: inactivos},
        ].map(f => (
          <button key={f.key} onClick={() => onFiltro(f.key)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
              filtro === f.key
                ? f.key === 'inactivos' ? 'bg-red-100 text-red-700' : f.key === 'activos' ? 'bg-green-100 text-green-700' : 'bg-primary-100 text-primary-700'
                : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
            }`}>
            {f.label}<span className="ml-1.5 font-normal opacity-70">{f.count}</span>
          </button>
        ))}
      </div>
      {(busqueda || filtro !== 'todos') && (
        <span className="text-xs text-gray-400">{visibles} resultado{visibles !== 1 ? 's' : ''}</span>
      )}
    </div>
  )
}

function Badge({ activo }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${activo ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-600'}`}>
      {activo ? 'Activo' : 'Inactivo'}
    </span>
  )
}

/* ─── Modal confirmación desactivar progreso ─── */
function ModalConfirmacion({ progreso, impacto, cargando, onConfirmar, onCancelar }) {
  return (
    <ConfirmModal titulo="Desactivar subproceso" danger cargando={cargando}
      onConfirmar={onConfirmar} onCancelar={onCancelar} confirmLabel="Sí, desactivar">
      <p className="text-sm text-gray-600 mb-3">
        Vas a desactivar <span className="font-medium">"{progreso.nombreProgreso}"</span>.
      </p>
      {cargando ? (
        <div className="flex items-center gap-2 text-sm text-gray-400 py-2">
          <div className="animate-spin rounded-full h-4 w-4 border-2 border-primary-500 border-t-transparent" />
          Verificando impacto...
        </div>
      ) : impacto?.total > 0 ? (
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 mb-1">
          <p className="text-xs font-semibold text-amber-700 mb-2 uppercase tracking-wider">
            Procesos afectados ({impacto.total})
          </p>
          <ul className="space-y-1">
            {impacto.procesosAfectados.map(nombre => (
              <li key={nombre} className="text-sm text-amber-800 flex items-center gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-500 flex-shrink-0" />
                {nombre}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <div className="bg-green-50 border border-green-200 rounded-lg px-3 py-2 text-sm text-green-700">
          Este subproceso no está asignado a ningún proceso activo.
        </div>
      )}
    </ConfirmModal>
  )
}

/* ─── Precios por volumen de un proceso — solo disponible al editar (el proceso ya debe existir) ─── */
function SeccionTramos({ proceso, onCambio }) {
  const [tramos, setTramos] = useState(proceso.tramosPrecio ?? [])
  const [agregando, setAgregando] = useState(false)
  const [nuevoTramo, setNuevoTramo] = useState({ cantidadMinima: '', valorUnitario: '' })
  const [editandoId, setEditandoId] = useState(null)
  const [edicion, setEdicion] = useState({ cantidadMinima: '', valorUnitario: '' })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const ordenar = (lista) => [...lista].sort((a, b) => a.cantidadMinima - b.cantidadMinima)

  const crear = async () => {
    setGuardando(true); setError(null)
    try {
      const { data } = await api.post(`/catalogo/procesos/${proceso.idProceso}/tramos`, {
        cantidadMinima: Number(nuevoTramo.cantidadMinima),
        valorUnitario: Number(nuevoTramo.valorUnitario),
      })
      setTramos(prev => ordenar([...prev, data]))
      setNuevoTramo({ cantidadMinima: '', valorUnitario: '' })
      setAgregando(false)
      onCambio?.()
    } catch (err) {
      setError(mensajeErrorValidacion(err))
    } finally { setGuardando(false) }
  }

  const actualizar = async (idTramo) => {
    setGuardando(true); setError(null)
    try {
      const { data } = await api.put(`/catalogo/procesos/${proceso.idProceso}/tramos/${idTramo}`, {
        cantidadMinima: Number(edicion.cantidadMinima),
        valorUnitario: Number(edicion.valorUnitario),
      })
      setTramos(prev => ordenar(prev.map(t => t.idTramo === idTramo ? data : t)))
      setEditandoId(null)
      onCambio?.()
    } catch (err) {
      setError(mensajeErrorValidacion(err))
    } finally { setGuardando(false) }
  }

  const eliminar = async (idTramo) => {
    setError(null)
    try {
      await api.delete(`/catalogo/procesos/${proceso.idProceso}/tramos/${idTramo}`)
      setTramos(prev => prev.filter(t => t.idTramo !== idTramo))
      onCambio?.()
    } catch (err) {
      setError(mensajeErrorValidacion(err))
    }
  }

  return (
    <div className="rounded-xl border border-gray-200 overflow-hidden">
      <div className="px-4 py-3 bg-gray-50 border-b border-gray-200 flex items-center justify-between">
        <div>
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Precios por volumen</p>
          <p className="text-xs text-gray-400 mt-0.5">Descuento automático al comprar cantidades grandes</p>
        </div>
        {!agregando && (
          <button type="button" onClick={() => setAgregando(true)}
            className="text-xs font-medium text-primary-600 hover:text-primary-800">+ Agregar tramo</button>
        )}
      </div>
      <div className="p-4">
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-2 rounded-lg mb-2">{error}</div>}

        {tramos.length === 0 && !agregando && (
          <p className="text-xs text-gray-400">Sin tramos configurados — siempre se cobra el valor base.</p>
        )}

        {(tramos.length > 0 || agregando) && (
          <div className="grid grid-cols-[1fr_1fr_auto] gap-x-3 gap-y-1.5 items-center">
            <span className="text-xs font-medium text-gray-400">Cantidad mínima</span>
            <span className="text-xs font-medium text-gray-400">Valor unitario</span>
            <span />

            {tramos.map(t => (
              editandoId === t.idTramo ? (
                <Fragment key={t.idTramo}>
                  <input type="number" min={2} value={edicion.cantidadMinima} autoFocus
                    onChange={e => setEdicion(p => ({ ...p, cantidadMinima: e.target.value }))}
                    placeholder="Cant. mín" className="w-full border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm" />
                  <input type="number" min={0} step="0.01" value={edicion.valorUnitario}
                    onChange={e => setEdicion(p => ({ ...p, valorUnitario: e.target.value }))}
                    placeholder="Valor unitario" className="w-full border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm" />
                  <div className="flex items-center gap-2 whitespace-nowrap">
                    <button type="button" disabled={guardando} onClick={() => actualizar(t.idTramo)}
                      className="text-xs font-medium text-primary-600 hover:text-primary-800">Guardar</button>
                    <button type="button" onClick={() => setEditandoId(null)} className="text-xs text-gray-400">Cancelar</button>
                  </div>
                </Fragment>
              ) : (
                <Fragment key={t.idTramo}>
                  <span className="text-sm text-gray-700 bg-gray-50 rounded-lg px-3 py-2">Desde <strong>{t.cantidadMinima}</strong> und.</span>
                  <span className="text-sm font-semibold text-primary-700 bg-gray-50 rounded-lg px-3 py-2">{fmtValor(t.valorUnitario)} c/u</span>
                  <div className="flex items-center gap-3 px-1 whitespace-nowrap">
                    <button type="button"
                      onClick={() => { setEditandoId(t.idTramo); setEdicion({ cantidadMinima: String(t.cantidadMinima), valorUnitario: String(t.valorUnitario) }) }}
                      className="text-xs text-primary-600 hover:underline">Editar</button>
                    <button type="button" onClick={() => eliminar(t.idTramo)} className="text-xs text-red-500 hover:underline">Eliminar</button>
                  </div>
                </Fragment>
              )
            ))}

            {agregando && (
              <Fragment>
                <input type="number" min={2} value={nuevoTramo.cantidadMinima} autoFocus
                  onChange={e => setNuevoTramo(p => ({ ...p, cantidadMinima: e.target.value }))}
                  placeholder="Ej: 20" className="w-full border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm" />
                <input type="number" min={0} step="0.01" value={nuevoTramo.valorUnitario}
                  onChange={e => setNuevoTramo(p => ({ ...p, valorUnitario: e.target.value }))}
                  placeholder="Valor unitario" className="w-full border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm" />
                <div className="flex items-center gap-2 whitespace-nowrap">
                  <button type="button" disabled={guardando || !nuevoTramo.cantidadMinima || !nuevoTramo.valorUnitario} onClick={crear}
                    className="text-xs font-medium text-white bg-primary-600 hover:bg-primary-700 disabled:opacity-50 px-3 py-1.5 rounded-lg">
                    Guardar
                  </button>
                  <button type="button" onClick={() => { setAgregando(false); setNuevoTramo({ cantidadMinima: '', valorUnitario: '' }) }}
                    className="text-xs text-gray-400">Cancelar</button>
                </div>
              </Fragment>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

const NIVELES_CIUDAD = [
  { valor: 'PRINCIPAL',            etiqueta: 'Principal' },
  { valor: 'INTERMEDIA',           etiqueta: 'Intermedia / municipio principal' },
  { valor: 'MUNICIPIO_SECUNDARIO', etiqueta: 'Municipio secundario' },
]

/* ─── Incremento por nivel de ciudad — inputs controlados por FormProceso, se guardan junto con el proceso ─── */
function CamposPreciosCiudad({ valores, onChange }) {
  return (
    <div className="rounded-xl border border-gray-200 overflow-hidden">
      <div className="px-4 py-3 bg-gray-50 border-b border-gray-200">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Incremento por nivel de ciudad</p>
        <p className="text-xs text-gray-400 mt-0.5">
          Monto adicional (COP) que se suma al precio final según la ciudad del evaluado — independiente
          del valor base y de los tramos por volumen. Deja en blanco o pon 0 si un nivel no tiene recargo.
        </p>
      </div>
      <div className="p-4 space-y-2.5">
        {NIVELES_CIUDAD.map(n => (
          <div key={n.valor} className="flex items-center gap-3">
            <span className="w-52 flex-shrink-0 text-xs text-gray-600">{n.etiqueta}</span>
            <input type="number" min={0} step="0.01" value={valores[n.valor]}
              onChange={e => onChange(n.valor, e.target.value)}
              placeholder="Sin recargo"
              className="flex-1 border border-gray-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          </div>
        ))}
      </div>
    </div>
  )
}

/* ══════════════════════════════════════════
   PROCESOS
══════════════════════════════════════════ */
function FormProceso({ proceso, clasificaciones, clasificacionDefaultId, onClose, onGuardado }) {
  const [form, setForm] = useState({
    nombreProceso:       proceso?.nombreProceso ?? '',
    descripcion:         proceso?.descripcion  ?? '',
    idClasificacion:     proceso?.clasificacion?.idClasificacion ?? clasificacionDefaultId ?? '',
    valor:               proceso?.valor != null ? String(proceso.valor) : '',
    diasHabilesEntrega:  proceso?.diasHabilesEntrega != null ? String(proceso.diasHabilesEntrega) : '5',
    aplicaPrecioCiudad:  proceso?.aplicaPrecioCiudad ?? false,
    puntosClave:         (proceso?.puntosClave ?? []).join('\n'),
    preciosCiudad:       Object.fromEntries(NIVELES_CIUDAD.map(n => {
      const existente = proceso?.preciosCiudad?.find(p => p.nivelCiudad === n.valor)
      return [n.valor, existente ? String(existente.valor) : '']
    })),
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault(); setGuardando(true); setError(null)
    const payload = {
      nombreProceso:      form.nombreProceso,
      descripcion:        form.descripcion || null,
      idClasificacion:    Number(form.idClasificacion),
      valor:              form.valor !== '' ? Number(form.valor) : null,
      diasHabilesEntrega: form.diasHabilesEntrega !== '' ? Number(form.diasHabilesEntrega) : null,
      aplicaPrecioCiudad: form.aplicaPrecioCiudad,
      puntosClave:        form.puntosClave.split('\n').map(l => l.trim()).filter(Boolean),
    }
    try {
      const idProceso = proceso
        ? (await api.put(`/catalogo/procesos/${proceso.idProceso}`, payload)).data.idProceso
        : (await api.post('/catalogo/procesos', payload)).data.idProceso

      if (form.aplicaPrecioCiudad) {
        const items = NIVELES_CIUDAD
          .filter(n => form.preciosCiudad[n.valor] !== '')
          .map(n => ({ nivelCiudad: n.valor, valor: Number(form.preciosCiudad[n.valor]) }))
        await api.put(`/catalogo/procesos/${idProceso}/precios-ciudad`, items)
      }

      onGuardado(proceso ? 'Proceso actualizado correctamente' : 'Proceso creado correctamente')
      onClose()
    } catch (err) {
      setError(mensajeErrorValidacion(err))
    } finally { setGuardando(false) }
  }

  return (
    <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
          <input value={form.nombreProceso} onChange={e => setForm(p => ({ ...p, nombreProceso: alCambiarTexto(e.target.value) }))} required
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Clasificación <span className="text-red-500">*</span></label>
          <select value={form.idClasificacion} onChange={e => setForm(p => ({ ...p, idClasificacion: e.target.value }))} required
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
            <option value="">Seleccionar clasificación…</option>
            {clasificaciones.filter(c => c.activo).map(c => (
              <option key={c.idClasificacion} value={c.idClasificacion}>{c.codigo} — {c.nombre}</option>
            ))}
          </select>
        </div>
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Descripción</label>
        <textarea value={form.descripcion} rows={2} onChange={e => setForm(p => ({ ...p, descripcion: alCambiarTexto(e.target.value) }))}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">
          Puntos clave
          <span className="ml-1.5 text-gray-400 font-normal">— uno por línea, cortos, para la tarjeta de la tienda</span>
        </label>
        <textarea value={form.puntosClave} rows={4} onChange={e => setForm(p => ({ ...p, puntosClave: e.target.value }))}
          placeholder={'Ej:\nRegistros penales\nMedidas correctivas de policía\nBoletines de contraloría\nProcuraduría'}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none font-mono" />
        <p className="text-xs text-gray-400 mt-1">Si lo dejas vacío, la tienda muestra la descripción en su lugar.</p>
      </div>
      {/* ── Sección de tiempos y valores ── */}
      <div className="rounded-xl border border-gray-200 overflow-hidden">
        <div className="px-4 py-3 bg-gray-50 border-b border-gray-200">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Tiempos y valores</p>
        </div>
        <div className="p-4 space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Tiempo de entrega */}
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">
                Días hábiles de entrega <span className="text-red-500">*</span>
              </label>
              <div className="flex items-center gap-3">
                <input type="number" min={1} max={365} value={form.diasHabilesEntrega} required
                  onChange={e => setForm(p => ({ ...p, diasHabilesEntrega: e.target.value }))}
                  className="w-24 border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                <span className="text-xs text-gray-400">días hábiles</span>
              </div>
            </div>
            {/* Valor base — editable */}
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">
                Valor base (COP)
              </label>
              <input type="number" min={0} step="0.01" value={form.valor}
                onChange={e => setForm(p => ({ ...p, valor: e.target.value }))}
                placeholder="Ej: 150000"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
          </div>

          {/* Aplica precio por ciudad */}
          <label className="flex items-start gap-2.5 cursor-pointer">
            <input type="checkbox" checked={form.aplicaPrecioCiudad}
              onChange={e => setForm(p => ({ ...p, aplicaPrecioCiudad: e.target.checked }))}
              className="mt-0.5 w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
            <span className="text-xs text-gray-600">
              <span className="font-medium text-gray-800">Aplica precio por ciudad</span>
              <span className="block text-gray-400 mt-0.5">
                Para servicios con desplazamiento (ej. visitas). Suma un monto adicional al precio final
                según el nivel logístico de la ciudad del evaluado — se configura abajo. Es independiente
                del valor base y de los tramos por volumen: se aplica un solo incremento, sin importar la
                cantidad comprada, porque no se sabe de antemano de qué ciudad será el evaluado.
              </span>
            </span>
          </label>

          {/* Valor calculado — solo lectura */}
          {proceso ? (
            proceso.valorCalculado != null ? (
              <div className="bg-primary-50 border border-primary-200 rounded-xl px-4 py-3.5 flex items-center gap-4">
                <div className="w-10 h-10 rounded-full bg-primary-100 flex items-center justify-center flex-shrink-0">
                  <svg className="h-5 w-5 text-primary-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 11h.01M12 11h.01M15 11h.01M4 19h16a2 2 0 002-2V7a2 2 0 00-2-2H4a2 2 0 00-2 2v10a2 2 0 002 2z" />
                  </svg>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-xs font-semibold text-primary-500 uppercase tracking-wider mb-0.5">Valor calculado desde subprocesos</p>
                  <p className="text-xl font-bold text-primary-800 leading-tight">
                    {fmtValor(proceso.valorCalculado)}
                  </p>
                  <p className="text-xs text-primary-400 mt-0.5">Suma de los {proceso.totalPasos} subproceso{proceso.totalPasos !== 1 ? 's' : ''} habilitados asignados</p>
                </div>
              </div>
            ) : (
              <div className="bg-amber-50 border border-amber-200 rounded-xl px-4 py-3 flex items-center gap-3">
                <svg className="h-5 w-5 text-amber-500 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <div>
                  <p className="text-xs font-semibold text-amber-600 uppercase tracking-wider mb-0.5">Valor calculado desde subprocesos</p>
                  <p className="text-xs text-amber-600">Este proceso no tiene subprocesos con valor asignado. Configura los subprocesos para ver el cálculo automático.</p>
                </div>
              </div>
            )
          ) : (
            <div className="bg-gray-50 border border-dashed border-gray-300 rounded-xl px-4 py-3 flex items-center gap-3">
              <svg className="h-5 w-5 text-gray-400 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 11h.01M12 11h.01M15 11h.01M4 19h16a2 2 0 002-2V7a2 2 0 00-2-2H4a2 2 0 00-2 2v10a2 2 0 002 2z" />
              </svg>
              <div>
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-0.5">Valor calculado desde subprocesos</p>
                <p className="text-xs text-gray-400">Se calculará automáticamente una vez que asignes subprocesos a este proceso.</p>
              </div>
            </div>
          )}
        </div>
      </div>

      {form.aplicaPrecioCiudad && (
        <CamposPreciosCiudad
          valores={form.preciosCiudad}
          onChange={(nivel, valor) => setForm(p => ({ ...p, preciosCiudad: { ...p.preciosCiudad, [nivel]: valor } }))}
        />
      )}

      {proceso ? (
        <SeccionTramos proceso={proceso} onCambio={() => onGuardado()} />
      ) : (
        <div className="rounded-xl border border-dashed border-gray-300 px-4 py-3 text-xs text-gray-400">
          Podrás configurar precios por volumen (descuento por cantidad) después de crear el proceso.
        </div>
      )}

      <div className="flex justify-end gap-3 pt-1">
        <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
        <button type="submit" disabled={guardando}
          className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
          {guardando ? 'Guardando...' : proceso ? 'Guardar cambios' : 'Crear proceso'}
        </button>
      </div>
    </form>
  )
}

/* ─── Modal progresos de un proceso ─── */
function PasosModal({ proceso, onClose, onToast }) {
  const [pasos, setPasos] = useState([])
  const [disponibles, setDisponibles] = useState([])
  const [cargando, setCargando] = useState(true)
  const [seleccionados, setSeleccionados] = useState(new Set())
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      const [rPasos, rTodos] = await Promise.all([
        api.get(`/catalogo/procesos/${proceso.idProceso}/pasos`),
        api.get('/catalogo/tipos-progreso'),
      ])
      const asignados = new Set(rPasos.data.map(p => p.idTipoProgreso))
      setPasos(rPasos.data)
      setDisponibles(rTodos.data.filter(t => !asignados.has(t.idTipoProgreso) && t.activo))
      setSeleccionados(new Set())
    } finally { setCargando(false) }
  }, [proceso.idProceso])

  useEffect(() => { cargar() }, [cargar])

  const toggleSeleccion = (id) => {
    setSeleccionados(prev => { const s = new Set(prev); s.has(id) ? s.delete(id) : s.add(id); return s })
  }

  const seleccionarTodos = () => {
    setSeleccionados(seleccionados.size === disponibles.length ? new Set() : new Set(disponibles.map(t => t.idTipoProgreso)))
  }

  const agregarSeleccionados = async () => {
    if (!seleccionados.size) return
    setGuardando(true); setError(null)
    const items = [...seleccionados].map(id => ({ idTipoProgreso: id, habilitado: true, obligatorio: true }))
    try {
      await Promise.all(items.map(item => api.post(`/catalogo/procesos/${proceso.idProceso}/pasos`, item)))
      cargar()
      onToast?.(`${items.length} subproceso${items.length > 1 ? 's' : ''} agregado${items.length > 1 ? 's' : ''} correctamente`)
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al agregar subprocesos.')
    } finally { setGuardando(false) }
  }

  const quitar = async (paso) => {
    try {
      await api.delete(`/catalogo/procesos/${proceso.idProceso}/pasos/${paso.id}`)
      cargar(); onToast?.(`"${paso.nombreProgreso}" removido del proceso`)
    } catch { setError('Error al quitar el subproceso.') }
  }

  const toggle = async (paso, campo) => {
    const nuevoHabilitado  = campo === 'habilitado'  ? !paso.habilitado  : paso.habilitado
    const nuevoObligatorio = campo === 'obligatorio' ? !paso.obligatorio : paso.obligatorio
    try {
      await api.put(`/catalogo/procesos/${proceso.idProceso}/pasos/${paso.id}`, {
        idTipoProgreso: paso.idTipoProgreso,
        habilitado: nuevoHabilitado, obligatorio: nuevoObligatorio,
      })
      cargar()
      if (campo === 'habilitado')
        onToast?.(nuevoHabilitado ? `"${paso.nombreProgreso}" habilitado` : `"${paso.nombreProgreso}" deshabilitado`)
      else
        onToast?.(nuevoObligatorio ? `"${paso.nombreProgreso}" marcado obligatorio` : `"${paso.nombreProgreso}" marcado opcional`)
    } catch { setError('Error al actualizar.') }
  }

  return (
    <div className="px-6 py-5 space-y-5">
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
      <div>
        <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Asignados ({pasos.length})</p>
        {cargando ? (
          <div className="flex justify-center py-6"><div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" /></div>
        ) : pasos.length === 0 ? (
          <p className="text-sm text-gray-400 py-3 text-center bg-gray-50 rounded-lg">Sin subprocesos asignados.</p>
        ) : (
          <div className="space-y-2">
            {pasos.map((p, i) => {
              const inactivo = !p.tipoActivo
              return (
                <div key={p.id} className={`flex items-center gap-3 p-3 rounded-lg ${inactivo ? 'bg-red-50 opacity-70' : 'bg-gray-50'}`}>
                  <span className={`w-6 h-6 rounded-full text-xs font-bold flex items-center justify-center flex-shrink-0 ${inactivo ? 'bg-red-100 text-red-400' : 'bg-primary-100 text-primary-600'}`}>
                    {i + 1}
                  </span>
                  <span className={`flex-1 text-sm font-medium ${inactivo ? 'text-gray-400 line-through' : 'text-gray-800'}`}>{p.nombreProgreso}</span>
                  {inactivo ? (
                    <span className="text-xs px-2 py-0.5 rounded-full bg-red-100 border border-red-200 text-red-500">Subproceso inactivo</span>
                  ) : (
                    <div className="flex items-center gap-1.5">
                      <button onClick={() => toggle(p, 'obligatorio')}
                        className={`text-xs px-2 py-0.5 rounded-full border transition-colors ${p.obligatorio ? 'bg-orange-50 border-orange-200 text-orange-600' : 'bg-gray-100 border-gray-200 text-gray-400'}`}>
                        {p.obligatorio ? 'Obligatorio' : 'Opcional'}
                      </button>
                      <button onClick={() => toggle(p, 'habilitado')}
                        className={`text-xs px-2 py-0.5 rounded-full border transition-colors ${p.habilitado ? 'bg-green-50 border-green-200 text-green-600' : 'bg-gray-100 border-gray-200 text-gray-400'}`}>
                        {p.habilitado ? 'Habilitado' : 'Deshabilitado'}
                      </button>
                      <button onClick={() => quitar(p)} className="text-xs text-red-400 hover:text-red-600 px-2 py-0.5 rounded border border-transparent hover:border-red-200 transition-colors">Quitar</button>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {!cargando && disponibles.length > 0 && (
        <div>
          <div className="flex items-center justify-between mb-2">
            <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">Disponibles para agregar</p>
            <button onClick={seleccionarTodos} className="text-xs text-primary-600 hover:text-primary-800 transition-colors">
              {seleccionados.size === disponibles.length ? 'Deseleccionar todos' : 'Seleccionar todos'}
            </button>
          </div>
          <div className="border border-gray-200 rounded-lg overflow-hidden divide-y divide-gray-100 max-h-52 overflow-y-auto">
            {disponibles.map(t => (
              <label key={t.idTipoProgreso} title={t.descripcion ?? ''}
                className="flex items-center gap-3 px-4 py-2.5 hover:bg-primary-50 cursor-pointer transition-colors">
                <input type="checkbox" checked={seleccionados.has(t.idTipoProgreso)} onChange={() => toggleSeleccion(t.idTipoProgreso)}
                  className="w-4 h-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500" />
                <span className="text-sm text-gray-800">{t.nombreProgreso}</span>
              </label>
            ))}
          </div>
          {seleccionados.size > 0 && (
            <button onClick={agregarSeleccionados} disabled={guardando}
              className="mt-3 w-full bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium py-2 rounded-lg transition-colors">
              {guardando ? 'Agregando...' : `Agregar ${seleccionados.size} subproceso${seleccionados.size > 1 ? 's' : ''} seleccionado${seleccionados.size > 1 ? 's' : ''}`}
            </button>
          )}
        </div>
      )}

      {!cargando && disponibles.length === 0 && pasos.length > 0 && (
        <p className="text-sm text-gray-400 text-center py-2">Todos los subprocesos disponibles ya están asignados.</p>
      )}

      <div className="flex justify-end pt-2 border-t border-gray-100">
        <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cerrar</button>
      </div>
    </div>
  )
}

function TarjetaProceso({ p, clasificaciones, onEditar, onPasos, onEstado }) {
  const cfg = cfgColor(clasificaciones, p.clasificacion?.codigo)
  const v   = fmtValor(p.valor)
  const vc  = fmtValor(p.valorCalculado)

  return (
    <div className={`bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden flex flex-col ${!p.activo ? 'opacity-60' : ''}`}>
      <div className={`h-1.5 w-full bg-gradient-to-r ${cfg.grad}`} />
      <div className="p-5 flex flex-col flex-1">
        <div className="flex items-start justify-between gap-2 pb-2.5 mb-2.5 border-b border-gray-100">
          <h3 className="text-base font-semibold text-gray-900 leading-snug flex-1">{p.nombreProceso}</h3>
          <Badge activo={p.activo} />
        </div>
        <p className="text-xs text-gray-500 leading-relaxed line-clamp-3 flex-1 mb-3" title={p.descripcion ?? ''}>
          {p.descripcion ?? <span className="italic text-gray-300">Sin descripción</span>}
        </p>
        <div className="flex items-center gap-2 mb-3 flex-wrap">
          <span className={`inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full border ${cfg.color}`}>
            <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            {p.totalPasos} subproceso{p.totalPasos !== 1 ? 's' : ''}
          </span>
          {p.diasHabilesEntrega != null && (
            <span className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full border bg-sky-50 text-sky-700 border-sky-200">
              <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              {p.diasHabilesEntrega} día{p.diasHabilesEntrega !== 1 ? 's' : ''} hábil{p.diasHabilesEntrega !== 1 ? 'es' : ''}
            </span>
          )}
          {p.tramosPrecio?.length > 0 && (
            <span className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full border bg-green-50 text-green-700 border-green-200">
              {p.tramosPrecio.length} tramo{p.tramosPrecio.length !== 1 ? 's' : ''} de precio
            </span>
          )}
          {p.aplicaPrecioCiudad && (
            <span className="inline-flex items-center gap-1.5 text-xs font-medium px-2.5 py-1 rounded-full border bg-violet-50 text-violet-700 border-violet-200">
              <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M17.657 16.657L13.414 20.9a2 2 0 01-2.828 0l-4.243-4.243a8 8 0 1111.314 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
              + Recargo por ciudad
            </span>
          )}
        </div>
        {/* Sección de valores */}
        <div className="grid grid-cols-2 gap-2 mb-4 p-2.5 bg-gray-50 rounded-xl border border-gray-100">
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Valor base</p>
            <p className={`text-sm font-bold ${v ? 'text-gray-800' : 'text-gray-300'}`}>{v ?? '—'}</p>
          </div>
          <div>
            <p className="text-xs text-gray-400 mb-0.5">Suma subprocesos</p>
            <p className={`text-sm font-bold ${vc ? 'text-primary-700' : 'text-gray-300'}`}>{vc ?? '—'}</p>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-wrap border-t border-gray-100 pt-3 mt-auto">
          <button onClick={() => onPasos(p)}
            className="flex-1 text-xs font-medium px-3 py-1.5 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors text-center">
            Subprocesos
          </button>
          <button onClick={() => onEditar(p)}
            className="flex-1 text-xs font-medium px-3 py-1.5 rounded-lg border border-primary-200 text-primary-600 hover:bg-primary-50 transition-colors text-center">
            Editar
          </button>
          <button onClick={() => onEstado(p)}
            className={`flex-1 text-xs font-medium px-3 py-1.5 rounded-lg border transition-colors text-center ${p.activo ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'}`}>
            {p.activo ? 'Desactivar' : 'Activar'}
          </button>
        </div>
      </div>
    </div>
  )
}

function TablaProcesos({ procesos, clasificaciones, cargando, onEditar, onPasos, onEstado }) {
  if (cargando) return <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  if (!procesos.length) return <div className="text-center py-16 text-gray-400 text-sm">Sin procesos en esta clasificación.</div>
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      {procesos.map(p => (
        <TarjetaProceso key={p.idProceso} p={p} clasificaciones={clasificaciones} onEditar={onEditar} onPasos={onPasos} onEstado={onEstado} />
      ))}
    </div>
  )
}

/* ══════════════════════════════════════════
   GRID DE CLASIFICACIONES (vista inicial)
══════════════════════════════════════════ */
function GridClasificaciones({ clasificaciones, procesos, cargando, onSeleccionar }) {
  if (cargando) return <div className="flex justify-center py-20"><div className="animate-spin rounded-full h-10 w-10 border-4 border-primary-600 border-t-transparent" /></div>
  if (!clasificaciones.length) return <div className="text-center py-20 text-gray-400 text-sm">No hay clasificaciones registradas. Crea una en la pestaña Clasificaciones.</div>

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
      {clasificaciones.filter(c => c.activo).map((c, i) => {
        const cfg      = PALETA[i % PALETA.length]
        const total    = procesos.filter(p => p.clasificacion?.codigo === c.codigo).length
        const activos  = procesos.filter(p => p.clasificacion?.codigo === c.codigo && p.activo).length
        return (
          <button key={c.idClasificacion} onClick={() => onSeleccionar(c.codigo)}
            className="group text-left bg-white rounded-2xl border border-gray-200 shadow-sm hover:shadow-md hover:-translate-y-0.5 transition-all duration-200 overflow-hidden">
            <div className={`h-2 w-full bg-gradient-to-r ${cfg.grad}`} />
            <div className="p-6">
              <div className="flex items-start justify-between gap-4">
                <div className={`w-14 h-14 rounded-2xl flex flex-col items-center justify-center flex-shrink-0 ${cfg.color}`}>
                  <span className="text-lg font-bold leading-none">{c.codigo}</span>
                </div>
                <div className="text-right">
                  <p className="text-3xl font-bold text-gray-900 leading-none">{total}</p>
                  <p className="text-xs text-gray-400 mt-1">{activos} activo{activos !== 1 ? 's' : ''}</p>
                </div>
              </div>
              <div className="mt-4">
                <p className="text-sm font-semibold text-gray-800 leading-snug">{c.nombre}</p>
                {c.descripcion && <p className="text-xs text-gray-400 mt-1 line-clamp-2">{c.descripcion}</p>}
              </div>
              <div className="mt-4 flex items-center justify-between">
                <span className={`text-xs font-medium px-2.5 py-1 rounded-full border ${cfg.color}`}>
                  {total} proceso{total !== 1 ? 's' : ''}
                </span>
                <span className="text-xs text-gray-400 group-hover:text-primary-600 transition-colors flex items-center gap-1">
                  Ver procesos
                  <svg className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                  </svg>
                </span>
              </div>
            </div>
          </button>
        )
      })}
    </div>
  )
}

/* ══════════════════════════════════════════
   PROGRESOS
══════════════════════════════════════════ */
const ROLES_RESPONSABLE = [
  { valor: 'ANALISTA_INTERNO', etiqueta: 'Analista interno' },
  { valor: 'POLIGRAFISTA',     etiqueta: 'Poligrafista' },
  { valor: 'VISITADOR',        etiqueta: 'Visitador' },
]

function etiquetaRol(rol) {
  return ROLES_RESPONSABLE.find(r => r.valor === rol)?.etiqueta ?? rol ?? '—'
}

function FormProgreso({ progreso, onClose, onGuardado }) {
  const [form, setForm] = useState({
    nombreProgreso: progreso?.nombreProgreso ?? '',
    descripcion:    progreso?.descripcion    ?? '',
    orden:          progreso?.orden          ?? 0,
    valor:          progreso?.valor != null ? String(progreso.valor) : '',
    horasEstimadas:   progreso?.minutosEstimados != null ? String(Math.floor(progreso.minutosEstimados / 60)) : '',
    minutosEstimados: progreso?.minutosEstimados != null ? String(progreso.minutosEstimados % 60) : '',
    rolResponsable: progreso?.rolResponsable ?? 'ANALISTA_INTERNO',
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault(); setGuardando(true); setError(null)
    const payload = {
      nombreProgreso: form.nombreProgreso,
      descripcion:    form.descripcion || null,
      orden:          Number(form.orden),
      valor:          form.valor !== '' ? Number(form.valor) : null,
      minutosEstimados:
        form.horasEstimadas !== '' || form.minutosEstimados !== ''
          ? (Number(form.horasEstimadas || 0) * 60) + Number(form.minutosEstimados || 0)
          : null,
      rolResponsable: form.rolResponsable,
    }
    try {
      if (progreso) {
        await api.put(`/catalogo/tipos-progreso/${progreso.idTipoProgreso}`, payload)
        onGuardado('Subproceso actualizado correctamente')
      } else {
        await api.post('/catalogo/tipos-progreso', payload)
        onGuardado('Subproceso creado correctamente')
      }
      onClose()
    } catch (err) {
      setError(mensajeErrorValidacion(err))
    } finally { setGuardando(false) }
  }

  return (
    <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
        <input value={form.nombreProgreso} required onChange={e => setForm(p => ({ ...p, nombreProgreso: alCambiarTexto(e.target.value) }))}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Descripción</label>
        <textarea value={form.descripcion} rows={3} onChange={e => setForm(p => ({ ...p, descripcion: alCambiarTexto(e.target.value) }))}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">
          Rol responsable <span className="text-red-500">*</span>
          <span className="ml-1.5 text-gray-400 font-normal">— quién ejecuta este subproceso</span>
        </label>
        <select value={form.rolResponsable} required
          onChange={e => setForm(p => ({ ...p, rolResponsable: e.target.value }))}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 bg-white">
          {ROLES_RESPONSABLE.map(r => (
            <option key={r.valor} value={r.valor}>{r.etiqueta}</option>
          ))}
        </select>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Orden</label>
          <input type="number" min={0} value={form.orden} onChange={e => setForm(p => ({ ...p, orden: Number(e.target.value) }))}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Valor base (COP)</label>
          <input type="number" min={0} step="0.01" value={form.valor} placeholder="Ej: 80000"
            onChange={e => setForm(p => ({ ...p, valor: e.target.value }))}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
        </div>
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">
          Tiempo estimado
          <span className="ml-1.5 text-gray-400 font-normal">— cuánto puede tardar realizar este subproceso</span>
        </label>
        <div className="flex items-center gap-2">
          <input type="number" min={0} value={form.horasEstimadas} placeholder="0"
            onChange={e => setForm(p => ({ ...p, horasEstimadas: e.target.value }))}
            className="w-20 border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          <span className="text-xs text-gray-400">horas</span>
          <input type="number" min={0} max={59} value={form.minutosEstimados} placeholder="0"
            onChange={e => setForm(p => ({ ...p, minutosEstimados: e.target.value }))}
            className="w-20 border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
          <span className="text-xs text-gray-400">minutos</span>
        </div>
      </div>
      <div className="flex justify-end gap-3 pt-1">
        <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
        <button type="submit" disabled={guardando}
          className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
          {guardando ? 'Guardando...' : progreso ? 'Guardar cambios' : 'Crear subproceso'}
        </button>
      </div>
    </form>
  )
}

function TablaProgresos({ progresos, cargando, onEditar, onDesactivar, onActivar, ordenCol, ordenDir, onOrdenar }) {
  if (cargando) return <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  if (!progresos.length) return <div className="text-center py-16 text-gray-400 text-sm">No hay subprocesos que coincidan con el filtro.</div>

  const cols = [
    { key: 'orden',          label: 'Orden',       sortable: true  },
    { key: 'nombreProgreso', label: 'Nombre',      sortable: true  },
    { key: 'descripcion',    label: 'Descripción', sortable: false },
    { key: 'valor',            label: 'Valor',         sortable: true  },
    { key: 'minutosEstimados', label: 'Tiempo est.',   sortable: true  },
    { key: 'rolResponsable',   label: 'Rol',           sortable: true  },
    { key: 'activo',           label: 'Estado',        sortable: true  },
    { key: '_acc',             label: 'Acciones',      sortable: false },
  ]

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-100">
        <thead className="bg-gray-50">
          <tr>
            {cols.map(c => (
              <th key={c.key} onClick={c.sortable ? () => onOrdenar(c.key) : undefined}
                className={`px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider select-none ${c.sortable ? 'cursor-pointer hover:text-gray-700' : ''}`}>
                {c.label}{c.sortable && <IconoOrden activo={ordenCol === c.key} dir={ordenDir} />}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {progresos.map(t => (
            <tr key={t.idTipoProgreso} className="hover:bg-gray-50 transition-colors">
              <td className="px-4 py-3 text-sm text-gray-500 text-center w-16">{t.orden ?? 0}</td>
              <td className="px-4 py-3 text-sm font-medium text-gray-900">{t.nombreProgreso}</td>
              <td className="px-4 py-3 text-sm text-gray-500 max-w-xs truncate">{t.descripcion ?? '—'}</td>
              <td className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">{fmtValor(t.valor) ?? <span className="text-gray-300">—</span>}</td>
              <td className="px-4 py-3 text-sm text-gray-700 whitespace-nowrap">
                {fmtTiempo(t.minutosEstimados) ?? <span className="text-gray-300">—</span>}
              </td>
              <td className="px-4 py-3 text-sm text-gray-600 whitespace-nowrap">{etiquetaRol(t.rolResponsable)}</td>
              <td className="px-4 py-3"><Badge activo={t.activo} /></td>
              <td className="px-4 py-3">
                <div className="flex items-center gap-2">
                  <button onClick={() => onEditar(t)}
                    className="text-xs font-medium px-3 py-1 rounded-full border border-primary-200 text-primary-600 hover:bg-primary-50">
                    Editar
                  </button>
                  {t.activo ? (
                    <button onClick={() => onDesactivar(t)}
                      className="text-xs font-medium px-3 py-1 rounded-full border border-red-200 text-red-600 hover:bg-red-50">
                      Desactivar
                    </button>
                  ) : (
                    <button onClick={() => onActivar(t)}
                      className="text-xs font-medium px-3 py-1 rounded-full border border-green-200 text-green-700 hover:bg-green-50">
                      Activar
                    </button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/* ══════════════════════════════════════════
   CLASIFICACIONES — CRUD
══════════════════════════════════════════ */
function FormClasificacion({ clasificacion, onClose, onGuardado }) {
  const [form, setForm] = useState({
    codigo:      clasificacion?.codigo      ?? '',
    nombre:      clasificacion?.nombre      ?? '',
    descripcion: clasificacion?.descripcion ?? '',
  })
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault(); setGuardando(true); setError(null)
    try {
      if (clasificacion) {
        await api.put(`/catalogo/clasificaciones/${clasificacion.idClasificacion}`, form)
        onGuardado('Clasificación actualizada correctamente')
      } else {
        await api.post('/catalogo/clasificaciones', form)
        onGuardado('Clasificación creada correctamente')
      }
      onClose()
    } catch (err) {
      setError(mensajeErrorValidacion(err))
    } finally { setGuardando(false) }
  }

  return (
    <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Código <span className="text-red-500">*</span></label>
        <input value={form.codigo} onChange={e => setForm(p => ({ ...p, codigo: e.target.value.toUpperCase() }))} required maxLength={5}
          placeholder="Ej: A, B, EXT…"
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm font-mono uppercase focus:outline-none focus:ring-2 focus:ring-primary-500" />
        <p className="text-xs text-gray-400 mt-1">Máximo 5 caracteres. Se convierte automáticamente a mayúsculas.</p>
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Nombre <span className="text-red-500">*</span></label>
        <input value={form.nombre} onChange={e => setForm(p => ({ ...p, nombre: alCambiarTexto(e.target.value) }))} required maxLength={100}
          placeholder="Ej: Pruebas de Confiabilidad"
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600 mb-1">Descripción</label>
        <textarea value={form.descripcion} rows={3} onChange={e => setForm(p => ({ ...p, descripcion: alCambiarTexto(e.target.value) }))}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
      </div>
      <div className="flex justify-end gap-3 pt-1">
        <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
        <button type="submit" disabled={guardando}
          className="bg-primary-600 hover:bg-primary-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg">
          {guardando ? 'Guardando...' : clasificacion ? 'Guardar cambios' : 'Crear clasificación'}
        </button>
      </div>
    </form>
  )
}

function TablaClasificaciones({ clasificaciones, cargando, onEditar, onEstado }) {
  if (cargando) return <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
  if (!clasificaciones.length) return (
    <div className="text-center py-16">
      <p className="text-gray-400 text-sm mb-1">No hay clasificaciones registradas.</p>
      <p className="text-gray-300 text-xs">Usa el botón "Nueva clasificación" para crear la primera.</p>
    </div>
  )

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full divide-y divide-gray-100">
        <thead className="bg-gray-50">
          <tr>
            {['Código', 'Nombre', 'Descripción', 'Estado', 'Acciones'].map(h => (
              <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {clasificaciones.map((c, i) => {
            const cfg = PALETA[i % PALETA.length]
            return (
              <tr key={c.idClasificacion} className="hover:bg-gray-50 transition-colors">
                <td className="px-4 py-3">
                  <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold border ${cfg.color}`}>
                    {c.codigo}
                  </span>
                </td>
                <td className="px-4 py-3 text-sm font-medium text-gray-900">{c.nombre}</td>
                <td className="px-4 py-3 text-sm text-gray-500 max-w-xs truncate">{c.descripcion ?? '—'}</td>
                <td className="px-4 py-3"><Badge activo={c.activo} /></td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <button onClick={() => onEditar(c)}
                      className="text-xs font-medium px-3 py-1 rounded-full border border-primary-200 text-primary-600 hover:bg-primary-50">
                      Editar
                    </button>
                    <button onClick={() => onEstado(c)}
                      className={`text-xs font-medium px-3 py-1 rounded-full border transition-colors ${c.activo ? 'border-red-200 text-red-600 hover:bg-red-50' : 'border-green-200 text-green-700 hover:bg-green-50'}`}>
                      {c.activo ? 'Desactivar' : 'Activar'}
                    </button>
                  </div>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

/* ══════════════════════════════════════════
   PÁGINA PRINCIPAL
══════════════════════════════════════════ */
export default function Catalogo() {
  const { clasificacion: clasificacionActiva } = useParams()
  const navigate = useNavigate()

  const [pestana, setPestana] = useState('procesos')
  const [error, setError]     = useState(null)
  const [toast, setToast]     = useState(null)

  const mostrarToast = useCallback((mensaje, tipo = 'exito') => setToast({ mensaje, tipo }), [])

  /* ── Clasificaciones ── */
  const [clasificaciones,         setClasificaciones]         = useState([])
  const [cargandoClasif,          setCargandoClasif]          = useState(true)
  const [modalCrearClasif,        setModalCrearClasif]        = useState(false)
  const [editandoClasif,          setEditandoClasif]          = useState(null)

  /* ── Procesos ── */
  const [procesos,         setProcesos]         = useState([])
  const [cargandoProcesos, setCargandoProcesos] = useState(true)
  const [modalCrearProceso,    setModalCrearProceso]    = useState(false)
  const [editandoProceso,      setEditandoProceso]      = useState(null)
  const [configurandoProceso,  setConfigurandoProceso]  = useState(null)

  /* ── Progresos ── */
  const [progresos,         setProgresos]         = useState([])
  const [cargandoProgresos, setCargandoProgresos] = useState(true)
  const [modalCrearProgreso, setModalCrearProgreso] = useState(false)
  const [editandoProgreso,   setEditandoProgreso]   = useState(null)
  const [confirmacion,    setConfirmacion]    = useState(null)
  const [cargandoImpacto, setCargandoImpacto] = useState(false)

  /* ── Filtros procesos ── */
  const [busquedaP, setBusquedaP] = useState('')
  const [filtroP,   setFiltroP]   = useState('todos')

  /* ── Filtros progresos ── */
  const [busquedaProg, setBusquedaProg] = useState('')
  const [filtroProg,   setFiltroProg]   = useState('todos')
  const [ordenColProg, setOrdenColProg] = useState(null)
  const [ordenDirProg, setOrdenDirProg] = useState('asc')

  /* ── Filtros clasificaciones ── */
  const [busquedaClasif, setBusquedaClasif] = useState('')

  const cargarClasificaciones = useCallback(async (msg) => {
    setCargandoClasif(true)
    try {
      const r = await api.get('/catalogo/clasificaciones')
      setClasificaciones(r.data)
      if (msg) mostrarToast(msg)
    } catch { setError('Error al cargar clasificaciones.') }
    finally { setCargandoClasif(false) }
  }, [mostrarToast])

  const cargarProcesos = useCallback(async (msg) => {
    setCargandoProcesos(true)
    try {
      const r = await api.get('/catalogo/procesos')
      setProcesos(r.data)
      if (msg) mostrarToast(msg)
    } catch { setError('Error al cargar procesos.') }
    finally { setCargandoProcesos(false) }
  }, [mostrarToast])

  const cargarProgresos = useCallback(async (msg) => {
    setCargandoProgresos(true)
    try {
      const r = await api.get('/catalogo/tipos-progreso')
      setProgresos(r.data)
      if (msg) mostrarToast(msg)
    } catch { setError('Error al cargar subprocesos.') }
    finally { setCargandoProgresos(false) }
  }, [mostrarToast])

  useEffect(() => { cargarClasificaciones(); cargarProcesos(); cargarProgresos() },
    [cargarClasificaciones, cargarProcesos, cargarProgresos])

  /* ── Clasificación default para el form de proceso ── */
  const clasificacionDefaultId = useMemo(() => {
    return clasificaciones.find(c => c.codigo === clasificacionActiva)?.idClasificacion
  }, [clasificaciones, clasificacionActiva])

  /* ── Acciones ── */
  const cambiarEstadoProceso = async (p) => {
    try {
      await api.patch(`/catalogo/procesos/${p.idProceso}/${p.activo ? 'desactivar' : 'activar'}`)
      cargarProcesos(p.activo ? 'Proceso desactivado' : 'Proceso activado')
    } catch { setError('Error al cambiar el estado del proceso.') }
  }

  const iniciarDesactivarProgreso = async (t) => {
    setCargandoImpacto(true); setConfirmacion({ progreso: t, impacto: null })
    try {
      const r = await api.get(`/catalogo/tipos-progreso/${t.idTipoProgreso}/impacto`)
      setConfirmacion({ progreso: t, impacto: r.data })
    } catch { setError('Error al verificar el impacto.'); setConfirmacion(null) }
    finally { setCargandoImpacto(false) }
  }

  const confirmarDesactivar = async () => {
    if (!confirmacion) return
    const nombre = confirmacion.progreso.nombreProgreso
    try {
      await api.patch(`/catalogo/tipos-progreso/${confirmacion.progreso.idTipoProgreso}/desactivar`)
      setConfirmacion(null); cargarProgresos(`"${nombre}" desactivado`)
    } catch { setError('Error al desactivar el subproceso.') }
  }

  const activarProgreso = async (t) => {
    try {
      await api.patch(`/catalogo/tipos-progreso/${t.idTipoProgreso}/activar`)
      cargarProgresos('Subproceso activado')
    } catch { setError('Error al activar el subproceso.') }
  }

  const cambiarEstadoClasif = async (c) => {
    try {
      await api.patch(`/catalogo/clasificaciones/${c.idClasificacion}/${c.activo ? 'desactivar' : 'activar'}`)
      cargarClasificaciones(c.activo ? 'Clasificación desactivada' : 'Clasificación activada')
    } catch { setError('Error al cambiar el estado de la clasificación.') }
  }

  /* ── Ordenar ── */
  const manejarOrdenProg = (col) => {
    if (ordenColProg === col) setOrdenDirProg(d => d === 'asc' ? 'desc' : 'asc')
    else { setOrdenColProg(col); setOrdenDirProg('asc') }
  }

  const ordenar = (lista, col, dir) => {
    if (!col) return lista
    return [...lista].sort((a, b) => {
      let va = col === 'activo' ? (a.activo ? 1 : 0) : (a[col] ?? '')
      let vb = col === 'activo' ? (b.activo ? 1 : 0) : (b[col] ?? '')
      if (typeof va === 'string') va = va.toLowerCase()
      if (typeof vb === 'string') vb = vb.toLowerCase()
      if (va < vb) return dir === 'asc' ? -1 : 1
      if (va > vb) return dir === 'asc' ? 1 : -1
      return 0
    })
  }

  /* ── Listas filtradas ── */
  const procesosClasificacion = useMemo(() => {
    let lista = procesos.filter(p => p.clasificacion?.codigo === clasificacionActiva)
    if (filtroP === 'activos')   lista = lista.filter(p => p.activo)
    if (filtroP === 'inactivos') lista = lista.filter(p => !p.activo)
    if (busquedaP.trim()) {
      const q = busquedaP.toLowerCase()
      lista = lista.filter(p => p.nombreProceso.toLowerCase().includes(q) || (p.descripcion ?? '').toLowerCase().includes(q))
    }
    return lista
  }, [procesos, clasificacionActiva, filtroP, busquedaP])

  const progresosVisibles = useMemo(() => {
    let lista = progresos
    if (filtroProg === 'activos')   lista = lista.filter(t => t.activo)
    if (filtroProg === 'inactivos') lista = lista.filter(t => !t.activo)
    if (busquedaProg.trim()) {
      const q = busquedaProg.toLowerCase()
      lista = lista.filter(t => t.nombreProgreso.toLowerCase().includes(q) || (t.descripcion ?? '').toLowerCase().includes(q))
    }
    return ordenar(lista, ordenColProg, ordenDirProg)
  }, [progresos, filtroProg, busquedaProg, ordenColProg, ordenDirProg])

  const clasificacionesVisibles = useMemo(() => {
    if (!busquedaClasif.trim()) return clasificaciones
    const q = busquedaClasif.toLowerCase()
    return clasificaciones.filter(c => c.codigo.toLowerCase().includes(q) || c.nombre.toLowerCase().includes(q))
  }, [clasificaciones, busquedaClasif])

  const volverAlGrid = () => { setBusquedaP(''); setFiltroP('todos'); navigate('/admin/catalogo') }

  const cfgActiva = useMemo(() => {
    if (!clasificacionActiva) return null
    const idx = clasificaciones.findIndex(c => c.codigo === clasificacionActiva)
    return { cfg: PALETA[idx >= 0 ? idx % PALETA.length : 3], data: clasificaciones[idx] }
  }, [clasificaciones, clasificacionActiva])

  const PESTANAS = [
    { id: 'procesos',        label: 'Procesos',        count: procesos.length        },
    { id: 'progresos',       label: 'Subprocesos',     count: progresos.length       },
    { id: 'clasificaciones', label: 'Clasificaciones', count: clasificaciones.length },
  ]

  return (
    <div>
      {/* ── Encabezado ── */}
      <div className="flex items-center justify-between mb-2">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">
            {clasificacionActiva && cfgActiva?.data
              ? `${cfgActiva.data.codigo} — ${cfgActiva.data.nombre}`
              : 'Catálogo'}
          </h2>
          <p className="text-sm text-gray-500 mt-0.5">
            {pestana === 'progresos'
              ? 'Subprocesos disponibles para asignar a procesos'
              : pestana === 'clasificaciones'
              ? 'Tipos de proceso disponibles en el sistema'
              : clasificacionActiva
              ? `${procesosClasificacion.length} proceso${procesosClasificacion.length !== 1 ? 's' : ''} en esta clasificación`
              : 'Selecciona una clasificación para gestionar sus procesos'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {pestana === 'procesos' && clasificacionActiva && (
            <button onClick={volverAlGrid}
              className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 border border-gray-200 px-3 py-1.5 rounded-lg hover:bg-gray-50 transition-colors">
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
              </svg>
              Clasificaciones
            </button>
          )}
          {pestana === 'procesos' && clasificacionActiva && (
            <button onClick={() => setModalCrearProceso(true)}
              className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
              <span className="text-base leading-none">+</span> Nuevo proceso
            </button>
          )}
          {pestana === 'progresos' && (
            <button onClick={() => setModalCrearProgreso(true)}
              className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors">
              <span className="text-base leading-none">+</span> Nuevo Subproceso
            </button>
          )}
        </div>
      </div>

      {error && (
        <div className="my-3 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
          {error}
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
        </div>
      )}

      {/* ── Tabs ── */}
      <div className="flex gap-0.5 mb-5 p-1 bg-gray-100 rounded-xl w-fit border border-gray-200/60">
        {PESTANAS.map(p => (
          <button key={p.id}
            onClick={() => { setPestana(p.id); if (p.id !== 'procesos' || clasificacionActiva) volverAlGrid() }}
            className={`flex items-center gap-2.5 px-5 py-2 rounded-[10px] text-sm font-medium transition-all duration-150 ${
              pestana === p.id
                ? 'bg-white shadow text-primary-700 ring-1 ring-gray-200/80'
                : 'text-gray-500 hover:text-gray-700 hover:bg-white/60'
            }`}>
            {p.label}
            <span className={`inline-flex items-center justify-center h-5 min-w-[22px] px-1.5 rounded-full text-xs font-semibold ${
              pestana === p.id ? 'bg-primary-100 text-primary-700' : 'bg-gray-200/80 text-gray-500'
            }`}>
              {p.count}
            </span>
          </button>
        ))}
      </div>

      {/* ── Contenido ── */}
      {pestana === 'procesos' ? (
        !clasificacionActiva ? (
          <GridClasificaciones
            clasificaciones={clasificaciones}
            procesos={procesos}
            cargando={cargandoProcesos || cargandoClasif}
            onSeleccionar={codigo => navigate(`/admin/catalogo/${codigo}`)}
          />
        ) : (
          <>
            <BarraFiltros
              busqueda={busquedaP} onBusqueda={setBusquedaP} filtro={filtroP} onFiltro={setFiltroP}
              total={procesos.filter(p => p.clasificacion?.codigo === clasificacionActiva).length}
              activos={procesos.filter(p => p.clasificacion?.codigo === clasificacionActiva && p.activo).length}
              inactivos={procesos.filter(p => p.clasificacion?.codigo === clasificacionActiva && !p.activo).length}
              visibles={procesosClasificacion.length}
            />
            <TablaProcesos
              procesos={procesosClasificacion}
              clasificaciones={clasificaciones}
              cargando={cargandoProcesos}
              onEditar={setEditandoProceso}
              onPasos={setConfigurandoProceso}
              onEstado={cambiarEstadoProceso}
            />
          </>
        )
      ) : pestana === 'progresos' ? (
        <>
          <BarraFiltros
            busqueda={busquedaProg} onBusqueda={setBusquedaProg} filtro={filtroProg} onFiltro={setFiltroProg}
            total={progresos.length} activos={progresos.filter(t => t.activo).length}
            inactivos={progresos.filter(t => !t.activo).length} visibles={progresosVisibles.length}
          />
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            <TablaProgresos progresos={progresosVisibles} cargando={cargandoProgresos}
              onEditar={setEditandoProgreso} onDesactivar={iniciarDesactivarProgreso} onActivar={activarProgreso}
              ordenCol={ordenColProg} ordenDir={ordenDirProg} onOrdenar={manejarOrdenProg} />
          </div>
        </>
      ) : (
        /* ── Tab clasificaciones ── */
        <>
          <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
            <input type="search" value={busquedaClasif} onChange={e => setBusquedaClasif(e.target.value)}
              placeholder="Buscar por código o nombre…"
              className="flex-1 min-w-[200px] max-w-xs border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            <button onClick={() => setModalCrearClasif(true)}
              className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2 rounded-lg transition-colors whitespace-nowrap">
              <span className="text-base leading-none">+</span> Nueva clasificación
            </button>
          </div>
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            <TablaClasificaciones
              clasificaciones={clasificacionesVisibles}
              cargando={cargandoClasif}
              onEditar={setEditandoClasif}
              onEstado={cambiarEstadoClasif}
            />
          </div>
        </>
      )}

      {/* ── Modales procesos ── */}
      {modalCrearProceso && (
        <Modal titulo="Nuevo proceso" ancho="max-w-2xl" onClose={() => setModalCrearProceso(false)}>
          <FormProceso proceso={null} clasificaciones={clasificaciones}
            clasificacionDefaultId={clasificacionDefaultId}
            onClose={() => setModalCrearProceso(false)} onGuardado={cargarProcesos} />
        </Modal>
      )}
      {editandoProceso && (
        <Modal titulo={`Editar — ${editandoProceso.nombreProceso}`} ancho="max-w-2xl" onClose={() => setEditandoProceso(null)}>
          <FormProceso proceso={editandoProceso} clasificaciones={clasificaciones}
            clasificacionDefaultId={clasificacionDefaultId}
            onClose={() => setEditandoProceso(null)} onGuardado={cargarProcesos} />
        </Modal>
      )}
      {configurandoProceso && (
        <Modal titulo={`Subprocesos — ${configurandoProceso.nombreProceso}`} ancho="max-w-2xl"
          onClose={() => { setConfigurandoProceso(null); cargarProcesos() }}>
          <PasosModal proceso={configurandoProceso}
            onClose={() => { setConfigurandoProceso(null); cargarProcesos() }}
            onToast={mostrarToast} />
        </Modal>
      )}

      {/* ── Modales progresos ── */}
      {modalCrearProgreso && (
        <Modal titulo="Nuevo subproceso" onClose={() => setModalCrearProgreso(false)}>
          <FormProgreso progreso={null} onClose={() => setModalCrearProgreso(false)} onGuardado={cargarProgresos} />
        </Modal>
      )}
      {editandoProgreso && (
        <Modal titulo={`Editar — ${editandoProgreso.nombreProgreso}`} onClose={() => setEditandoProgreso(null)}>
          <FormProgreso progreso={editandoProgreso} onClose={() => setEditandoProgreso(null)} onGuardado={cargarProgresos} />
        </Modal>
      )}
      {confirmacion && (
        <ModalConfirmacion progreso={confirmacion.progreso} impacto={confirmacion.impacto}
          cargando={cargandoImpacto} onConfirmar={confirmarDesactivar} onCancelar={() => setConfirmacion(null)} />
      )}

      {/* ── Modales clasificaciones ── */}
      {modalCrearClasif && (
        <Modal titulo="Nueva clasificación" onClose={() => setModalCrearClasif(false)}>
          <FormClasificacion clasificacion={null} onClose={() => setModalCrearClasif(false)} onGuardado={cargarClasificaciones} />
        </Modal>
      )}
      {editandoClasif && (
        <Modal titulo={`Editar — ${editandoClasif.nombre}`} onClose={() => setEditandoClasif(null)}>
          <FormClasificacion clasificacion={editandoClasif} onClose={() => setEditandoClasif(null)} onGuardado={cargarClasificaciones} />
        </Modal>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
