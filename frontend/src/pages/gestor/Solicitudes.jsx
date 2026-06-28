import { useState, useEffect, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'
import api from '../../services/api'
import Toast from '../../components/Toast'

const ESTADOS = ['TODOS', 'PENDIENTE', 'PROGRAMANDO', 'EN_EJECUCION', 'FINALIZADO', 'PUBLICADO', 'CANCELADO', 'REPROGRAMADO']

const BADGE = {
  PENDIENTE:    'bg-amber-100 text-amber-700',
  PROGRAMANDO:  'bg-blue-100 text-blue-700',
  EN_EJECUCION: 'bg-indigo-100 text-indigo-700',
  FINALIZADO:   'bg-green-100 text-green-700',
  PUBLICADO:    'bg-cyan-100 text-cyan-700',
  CANCELADO:    'bg-red-100 text-red-600',
  REPROGRAMADO: 'bg-orange-100 text-orange-700',
}

// Transiciones permitidas para GESTOR
const TRANSICIONES_GESTOR = {
  PENDIENTE:    ['PROGRAMANDO', 'CANCELADO'],
  PROGRAMANDO:  ['CANCELADO', 'REPROGRAMADO'],
  EN_EJECUCION: [],
  FINALIZADO:   ['PUBLICADO'],
  PUBLICADO:    [],
  CANCELADO:    [],
  REPROGRAMADO: ['PROGRAMANDO', 'CANCELADO'],
}

function formatFecha(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

/* ─── Modal cambio de estado ─── */
function ModalCambioEstado({ solicitud, onClose, onGuardado }) {
  const siguientes = TRANSICIONES_GESTOR[solicitud.estado] ?? []
  const [nuevoEstado, setNuevoEstado] = useState(siguientes[0] ?? '')
  const [observacion, setObservacion] = useState('')
  const [guardando, setGuardando]     = useState(false)
  const [error, setError]             = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setGuardando(true); setError(null)
    try {
      await api.patch(`/gestor/solicitudes/${solicitud.idSolicitud}/estado`, {
        estado: nuevoEstado,
        observacion: observacion || null,
      })
      onGuardado(`Estado cambiado a ${nuevoEstado}`)
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'Error al cambiar el estado.')
    } finally { setGuardando(false) }
  }

  if (!siguientes.length) return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-sm mx-4 p-6 text-center">
        <p className="text-sm text-gray-600 mb-4">No hay transiciones disponibles para el estado <strong>{solicitud.estado}</strong>.</p>
        <button onClick={onClose} className="px-4 py-2 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50">Cerrar</button>
      </div>
    </div>
  )

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md mx-4">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
          <div>
            <h3 className="text-base font-semibold text-gray-900">Cambiar estado</h3>
            <p className="text-xs text-gray-400 mt-0.5">{solicitud.nombresEvaluado} {solicitud.apellidosEvaluado}</p>
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">{error}</div>}

          <div>
            <p className="text-xs text-gray-500 mb-2">
              Estado actual: <span className={`font-medium px-2 py-0.5 rounded-full text-xs ${BADGE[solicitud.estado]}`}>{solicitud.estado}</span>
            </p>
            <label className="block text-xs font-medium text-gray-600 mb-1">Nuevo estado <span className="text-red-500">*</span></label>
            <select value={nuevoEstado} onChange={e => setNuevoEstado(e.target.value)} required
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white">
              {siguientes.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-600 mb-1">Observación</label>
            <textarea value={observacion} onChange={e => setObservacion(e.target.value)} rows={3}
              placeholder={nuevoEstado === 'PUBLICADO' ? 'Concepto final del estudio…' : 'Motivo o notas adicionales…'}
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none" />
          </div>

          <div className="flex justify-end gap-3 pt-1">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
            <button type="submit" disabled={guardando}
              className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-60 text-white text-sm font-medium px-5 py-2 rounded-lg transition-colors">
              {guardando ? 'Guardando...' : 'Confirmar cambio'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/* ─── Modal detalle ─── */
function ModalDetalle({ solicitud, onClose }) {
  const [detalle, setDetalle] = useState(null)
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    api.get(`/gestor/solicitudes/${solicitud.idSolicitud}`)
      .then(r => setDetalle(r.data))
      .finally(() => setCargando(false))
  }, [solicitud.idSolicitud])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg mx-4 max-h-[90vh] flex flex-col">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between flex-shrink-0">
          <h3 className="text-base font-semibold text-gray-900">Detalle de solicitud #{solicitud.idSolicitud}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>

        {cargando ? (
          <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-4 border-indigo-600 border-t-transparent" /></div>
        ) : (
          <div className="overflow-y-auto flex-1 px-6 py-5 space-y-5">
            {/* Evaluado */}
            <section>
              <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Evaluado</p>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <Campo label="Cédula" valor={detalle?.cedulaEvaluado} />
                <Campo label="Nombre" valor={`${detalle?.nombresEvaluado} ${detalle?.apellidosEvaluado}`} />
                <Campo label="Cargo" valor={detalle?.cargo} />
                <Campo label="Ciudad" valor={detalle?.ciudadEvaluado} />
                <Campo label="Celular" valor={detalle?.celularEvaluado} />
                <Campo label="Email" valor={detalle?.emailEvaluado} />
              </div>
            </section>

            {/* Servicios */}
            <section>
              <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Servicios solicitados</p>
              <div className="space-y-2">
                {detalle?.servicios?.map(sv => (
                  <div key={sv.idCatalogo} className="flex items-center justify-between bg-gray-50 rounded-lg px-3 py-2">
                    <span className="text-sm text-gray-800">{sv.nombre}</span>
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${BADGE[sv.estado] ?? 'bg-gray-100 text-gray-600'}`}>{sv.estado}</span>
                  </div>
                ))}
              </div>
            </section>

            {/* Historial */}
            {detalle?.historial?.length > 0 && (
              <section>
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Historial</p>
                <div className="space-y-2 max-h-40 overflow-y-auto">
                  {detalle.historial.map((h, i) => (
                    <div key={i} className="flex items-start gap-3 text-xs">
                      <div className="flex-shrink-0 mt-0.5">
                        <span className={`px-1.5 py-0.5 rounded font-medium ${BADGE[h.estadoNuevo] ?? 'bg-gray-100 text-gray-600'}`}>{h.estadoNuevo}</span>
                      </div>
                      <div className="flex-1">
                        <p className="text-gray-500">{h.usuario} · {formatFecha(h.fechaCambio)}</p>
                        {h.observacion && <p className="text-gray-700 mt-0.5">{h.observacion}</p>}
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            )}

            {detalle?.notas && (
              <section>
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1">Notas</p>
                <p className="text-sm text-gray-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">{detalle.notas}</p>
              </section>
            )}
          </div>
        )}

        <div className="px-6 py-4 border-t border-gray-100 flex-shrink-0">
          <button onClick={onClose} className="w-full py-2 text-sm text-gray-600 border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors">Cerrar</button>
        </div>
      </div>
    </div>
  )
}

function Campo({ label, valor }) {
  return (
    <div>
      <p className="text-xs text-gray-400">{label}</p>
      <p className="text-sm font-medium text-gray-800 truncate">{valor ?? '—'}</p>
    </div>
  )
}

/* ─── Página principal ─── */
export default function Solicitudes() {
  const [searchParams, setSearchParams] = useSearchParams()
  const estadoParam = searchParams.get('estado') ?? 'TODOS'

  const [solicitudes, setSolicitudes]     = useState([])
  const [cargando, setCargando]           = useState(true)
  const [error, setError]                 = useState(null)
  const [toast, setToast]                 = useState(null)
  const [pagina, setPagina]               = useState(0)
  const [totalPaginas, setTotalPaginas]   = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)
  const [busqueda, setBusqueda]           = useState('')
  const [cambiandoEstado, setCambiandoEstado] = useState(null)
  const [detalle, setDetalle]             = useState(null)

  const cargar = useCallback(async (estado, page = 0) => {
    setCargando(true); setError(null)
    try {
      const params = new URLSearchParams({ page, size: 15 })
      if (estado && estado !== 'TODOS') params.append('estado', estado)
      const r = await api.get(`/gestor/solicitudes?${params}`)
      setSolicitudes(r.data.content ?? [])
      setTotalPaginas(r.data.totalPages ?? 0)
      setTotalElementos(r.data.totalElements ?? 0)
    } catch { setError('Error al cargar solicitudes.') }
    finally { setCargando(false) }
  }, [])

  useEffect(() => {
    setPagina(0)
    cargar(estadoParam, 0)
  }, [estadoParam, cargar])

  const cambiarTab = (estado) => {
    const params = new URLSearchParams()
    if (estado !== 'TODOS') params.set('estado', estado)
    setSearchParams(params)
  }

  const solicitudesFiltradas = busqueda.trim()
    ? solicitudes.filter(s =>
        `${s.nombresEvaluado} ${s.apellidosEvaluado}`.toLowerCase().includes(busqueda.toLowerCase()) ||
        s.cedulaEvaluado?.includes(busqueda) ||
        s.cargo?.toLowerCase().includes(busqueda.toLowerCase())
      )
    : solicitudes

  return (
    <div>
      <div className="flex items-center justify-between mb-5">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Solicitudes</h2>
          <p className="text-sm text-gray-500 mt-0.5">{totalElementos} solicitud{totalElementos !== 1 ? 'es' : ''} en total</p>
        </div>
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
          {error} <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
        </div>
      )}

      {/* ── Tabs de estado ── */}
      <div className="flex gap-1 mb-4 overflow-x-auto border-b border-gray-200 pb-0">
        {ESTADOS.map(e => (
          <button key={e}
            onClick={() => cambiarTab(e)}
            className={`px-3 py-2.5 text-xs font-medium transition-colors border-b-2 -mb-px whitespace-nowrap ${
              estadoParam === e
                ? 'border-indigo-600 text-indigo-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}>
            {e === 'TODOS' ? 'Todos' : e.replace('_', ' ')}
          </button>
        ))}
      </div>

      {/* ── Buscador ── */}
      <div className="mb-4">
        <input type="search" value={busqueda} onChange={e => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre, cédula o cargo…"
          className="w-full max-w-sm border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500" />
      </div>

      {/* ── Tabla ── */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-indigo-600 border-t-transparent" />
          </div>
        ) : solicitudesFiltradas.length === 0 ? (
          <div className="text-center py-16 text-gray-400 text-sm">No hay solicitudes para este filtro.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-100">
              <thead className="bg-gray-50">
                <tr>
                  {['#', 'Evaluado', 'Cargo', 'Servicios', 'Estado', 'Solicitud', 'Entrega', 'Acciones'].map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {solicitudesFiltradas.map(s => (
                  <tr key={s.idSolicitud} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-xs text-gray-400">#{s.idSolicitud}</td>
                    <td className="px-4 py-3">
                      <p className="text-sm font-medium text-gray-900 whitespace-nowrap">{s.nombresEvaluado} {s.apellidosEvaluado}</p>
                      <p className="text-xs text-gray-400">{s.cedulaEvaluado}</p>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 whitespace-nowrap">{s.cargo ?? '—'}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {s.servicios?.slice(0, 2).map(sv => (
                          <span key={sv} className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded whitespace-nowrap">{sv}</span>
                        ))}
                        {(s.servicios?.length ?? 0) > 2 && <span className="text-xs text-gray-400">+{s.servicios.length - 2}</span>}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-medium px-2 py-0.5 rounded-full whitespace-nowrap ${BADGE[s.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                        {s.estado}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{formatFecha(s.fechaSolicitud)}</td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">{formatFecha(s.fechaEntregaEstimada)}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <button onClick={() => setDetalle(s)}
                          className="text-xs font-medium px-3 py-1 rounded-full border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors whitespace-nowrap">
                          Ver
                        </button>
                        {(TRANSICIONES_GESTOR[s.estado]?.length ?? 0) > 0 && (
                          <button onClick={() => setCambiandoEstado(s)}
                            className="text-xs font-medium px-3 py-1 rounded-full border border-indigo-200 text-indigo-600 hover:bg-indigo-50 transition-colors whitespace-nowrap">
                            Cambiar estado
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Paginación ── */}
      {totalPaginas > 1 && (
        <div className="flex items-center justify-center gap-2 mt-4">
          <button onClick={() => { setPagina(p => p - 1); cargar(estadoParam, pagina - 1) }}
            disabled={pagina === 0}
            className="px-3 py-1.5 text-xs border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed">
            Anterior
          </button>
          <span className="text-xs text-gray-500">{pagina + 1} / {totalPaginas}</span>
          <button onClick={() => { setPagina(p => p + 1); cargar(estadoParam, pagina + 1) }}
            disabled={pagina >= totalPaginas - 1}
            className="px-3 py-1.5 text-xs border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed">
            Siguiente
          </button>
        </div>
      )}

      {cambiandoEstado && (
        <ModalCambioEstado
          solicitud={cambiandoEstado}
          onClose={() => setCambiandoEstado(null)}
          onGuardado={msg => { setToast({ mensaje: msg, tipo: 'exito' }); cargar(estadoParam, pagina) }}
        />
      )}

      {detalle && <ModalDetalle solicitud={detalle} onClose={() => setDetalle(null)} />}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
