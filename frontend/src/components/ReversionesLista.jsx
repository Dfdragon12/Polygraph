import { useState, useEffect, useCallback } from 'react'
import reversionService from '../services/reversionService'
import Toast from './Toast'
import { Modal } from './ui/Modal'

const BADGE_ESTADO_SERVICIO = {
  PENDIENTE:    'bg-amber-100 text-amber-700',
  PROGRAMANDO:  'bg-blue-100 text-blue-700',
  EN_EJECUCION: 'bg-primary-100 text-primary-700',
  FINALIZADO:   'bg-green-100 text-green-700',
  PUBLICADO:    'bg-cyan-100 text-cyan-700',
  CANCELADO:    'bg-red-100 text-red-600',
  REPROGRAMADO: 'bg-orange-100 text-orange-700',
}

const BADGE_ESTADO_REVISION = {
  PENDIENTE: 'bg-amber-100 text-amber-700',
  APROBADA:  'bg-green-100 text-green-700',
  RECHAZADA: 'bg-red-100 text-red-600',
}

const FILTROS = [
  { key: '',           label: 'Todas'      },
  { key: 'PENDIENTE',  label: 'Pendientes' },
  { key: 'APROBADA',   label: 'Aprobadas'  },
  { key: 'RECHAZADA',  label: 'Rechazadas' },
]

function formatFechaHora(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-CO', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

/* ─── Modal: aprobar / rechazar ─── */
function ModalRevision({ item, accion, onConfirmar, onCancelar }) {
  const [comentario, setComentario] = useState('')
  const [enviando, setEnviando]     = useState(false)
  const esRechazo = accion === 'rechazar'

  const confirmar = async () => {
    setEnviando(true)
    await onConfirmar(comentario)
    setEnviando(false)
  }

  return (
    <Modal
      titulo={esRechazo ? 'Rechazar solicitud de reversión' : 'Aprobar solicitud de reversión'}
      subtitulo={`${item.nombresEvaluado} ${item.apellidosEvaluado} · #${item.idServicio}`}
      onClose={onCancelar} ancho="max-w-md">
      <div className="px-6 py-5 space-y-4">
        <div className={`text-xs px-3 py-2.5 rounded-lg ${esRechazo ? 'bg-red-50 border border-red-200 text-red-700' : 'bg-emerald-50 border border-emerald-200 text-emerald-700'}`}>
          {esRechazo
            ? 'La solicitud seguirá cancelada. Se notificará al gestor que la solicitó.'
            : `El estado de la solicitud cambiará de CANCELADO a ${item.estadoDeseado}. Se notificará al gestor que la solicitó.`}
        </div>

        <div className="bg-gray-50 rounded-lg px-3 py-2.5">
          <p className="text-xs text-gray-400 mb-1">Motivo del gestor</p>
          <p className="text-sm text-gray-700">{item.motivo}</p>
        </div>

        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">
            Comentario {esRechazo ? '(recomendado)' : '(opcional)'}
          </label>
          <textarea value={comentario} onChange={e => setComentario(e.target.value)} rows={3}
            placeholder={esRechazo ? 'Explica por qué se rechaza…' : 'Notas adicionales para el gestor…'}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none" />
        </div>

        <div className="flex justify-end gap-3 pt-1">
          <button onClick={onCancelar} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button onClick={confirmar} disabled={enviando}
            className={`px-5 py-2 text-sm font-medium text-white rounded-lg transition-colors disabled:opacity-60 ${
              esRechazo ? 'bg-red-600 hover:bg-red-700' : 'bg-primary-600 hover:bg-primary-700'
            }`}>
            {enviando ? 'Procesando...' : (esRechazo ? 'Rechazar' : 'Aprobar')}
          </button>
        </div>
      </div>
    </Modal>
  )
}

/**
 * Listado de solicitudes de reversión, compartido entre Admin (con acciones
 * de aprobar/rechazar) y Gestor (solo lectura).
 */
export default function ReversionesLista({ base, soloLectura = false }) {
  const [items, setItems]               = useState([])
  const [pagina, setPagina]             = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(0)
  const [filtro, setFiltro]             = useState('PENDIENTE')
  const [busqueda, setBusqueda]         = useState('')
  const [busquedaAplicada, setBusquedaAplicada] = useState('')
  const [cargando, setCargando]         = useState(true)
  const [pendientes, setPendientes]     = useState(0)
  const [revisando, setRevisando]       = useState(null) // { item, accion }
  const [toast, setToast]               = useState(null)

  const mostrarToast = (mensaje, tipo = 'exito') => setToast({ mensaje, tipo })

  // Debounce de la búsqueda
  useEffect(() => {
    const t = setTimeout(() => { setBusquedaAplicada(busqueda.trim()); setPagina(0) }, 350)
    return () => clearTimeout(t)
  }, [busqueda])

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      const peticiones = [
        reversionService.listar({ estado: filtro || undefined, q: busquedaAplicada || undefined, page: pagina, base }),
      ]
      if (!soloLectura) peticiones.push(reversionService.contarPendientes())

      const [pag, count] = await Promise.all(peticiones)
      setItems(pag.content ?? [])
      setTotalPaginas(pag.totalPages ?? 0)
      if (!soloLectura) setPendientes(count ?? 0)
    } catch (e) {
      const status = e.response?.status
      const detalle = e.response?.data?.mensaje ?? e.message
      console.error('Error al cargar reversiones:', status, e.response?.data ?? e)
      mostrarToast(`Error al cargar las solicitudes de reversión${status ? ` (HTTP ${status})` : ''}: ${detalle}`, 'error')
    } finally { setCargando(false) }
  }, [filtro, busquedaAplicada, pagina, base, soloLectura])

  useEffect(() => { cargar() }, [cargar])

  const resolver = async (comentario) => {
    const { item, accion } = revisando
    try {
      if (accion === 'aprobar') {
        await reversionService.aprobar(item.id, comentario)
        mostrarToast(`Solicitud #${item.idServicio} revertida a ${item.estadoDeseado}.`)
      } else {
        await reversionService.rechazar(item.id, comentario)
        mostrarToast('Solicitud de reversión rechazada.')
      }
      setRevisando(null)
      cargar()
    } catch (e) {
      mostrarToast(e.response?.data?.mensaje ?? 'Error al procesar la solicitud.', 'error')
    }
  }

  return (
    <div className="space-y-5">
      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}

      {!soloLectura && pendientes > 0 && (
        <div className="flex justify-end">
          <span className="inline-flex items-center gap-2 bg-amber-100 text-amber-700 text-sm font-medium px-3 py-1.5 rounded-full">
            <span className="w-2 h-2 rounded-full bg-amber-500 animate-pulse" />
            {pendientes} pendiente{pendientes !== 1 ? 's' : ''}
          </span>
        </div>
      )}

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        {/* Filtros + búsqueda */}
        <div className="px-5 py-3 border-b border-gray-100 flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-1 flex-wrap">
            {FILTROS.map(f => (
              <button key={f.key} onClick={() => { setFiltro(f.key); setPagina(0) }}
                className={`text-xs px-3 py-1.5 rounded-full transition-colors ${
                  filtro === f.key ? 'bg-primary-100 text-primary-700 font-medium' : 'text-gray-500 hover:bg-gray-100'
                }`}>
                {f.label}
                {f.key === 'PENDIENTE' && !soloLectura && pendientes > 0 && (
                  <span className="ml-1 bg-amber-500 text-white text-xs rounded-full px-1.5">{pendientes}</span>
                )}
              </button>
            ))}
          </div>

          <div className="relative w-full sm:w-72">
            <svg className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 115 11a6 6 0 0112 0z" />
            </svg>
            <input
              type="search"
              value={busqueda}
              onChange={e => setBusqueda(e.target.value)}
              placeholder="Buscar por evaluado, cédula, cliente o motivo…"
              className="w-full border border-gray-200 rounded-lg pl-9 pr-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </div>

        {cargando ? (
          <div className="flex justify-center py-16">
            <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center gap-2">
            <svg className="h-10 w-10 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p className="text-sm text-gray-400">
              {busquedaAplicada ? `Sin resultados para "${busquedaAplicada}"` : `Sin solicitudes${filtro ? ` con estado "${filtro}"` : ''}`}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 text-xs font-semibold text-gray-500 uppercase tracking-wide">
                  <th className="px-4 py-3 text-left">#</th>
                  <th className="px-4 py-3 text-left">Evaluado / Cliente</th>
                  <th className="px-4 py-3 text-left">Cambio</th>
                  <th className="px-4 py-3 text-left">Motivo</th>
                  <th className="px-4 py-3 text-left">Solicitado por</th>
                  <th className="px-4 py-3 text-left">Estado</th>
                  {!soloLectura && <th className="px-4 py-3 text-center">Acciones</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {items.map(r => (
                  <tr key={r.id} className="hover:bg-gray-50 transition-colors align-top">
                    <td className="px-4 py-3 text-gray-400 text-xs">#{r.idServicio}</td>
                    <td className="px-4 py-3 max-w-[180px]">
                      <p className="font-medium text-gray-900 whitespace-nowrap">{r.nombresEvaluado} {r.apellidosEvaluado}</p>
                      <p className="text-xs text-gray-400">{r.cedulaEvaluado}</p>
                      <p className="text-xs text-gray-400 truncate">{r.nombreCliente}</p>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${BADGE_ESTADO_SERVICIO.CANCELADO}`}>CANCELADO</span>
                      <span className="text-gray-300 mx-1">→</span>
                      <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${BADGE_ESTADO_SERVICIO[r.estadoDeseado] ?? 'bg-gray-100 text-gray-600'}`}>{r.estadoDeseado}</span>
                    </td>
                    <td className="px-4 py-3 max-w-xs">
                      <p className="text-gray-700 text-xs">{r.motivo}</p>
                      {r.comentarioRevision && (
                        <p className="text-xs text-gray-400 mt-1">Comentario: {r.comentarioRevision}</p>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                      <p>{r.solicitadoPorNombre ?? '—'}</p>
                      <p className="text-gray-400">{formatFechaHora(r.fechaSolicitud)}</p>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${BADGE_ESTADO_REVISION[r.estado] ?? 'bg-gray-100 text-gray-600'}`}>
                        {r.estado}
                      </span>
                      {r.estado !== 'PENDIENTE' && (
                        <p className="text-xs text-gray-400 mt-1 whitespace-nowrap">{r.revisadoPorNombre}</p>
                      )}
                    </td>
                    {!soloLectura && (
                      <td className="px-4 py-3">
                        {r.estado === 'PENDIENTE' ? (
                          <div className="flex items-center justify-center gap-1.5">
                            <button onClick={() => setRevisando({ item: r, accion: 'aprobar' })}
                              className="text-xs font-medium px-2.5 py-1 rounded-lg bg-primary-600 hover:bg-primary-700 text-white transition-colors whitespace-nowrap">
                              Aprobar
                            </button>
                            <button onClick={() => setRevisando({ item: r, accion: 'rechazar' })}
                              className="text-xs font-medium px-2.5 py-1 rounded-lg border border-red-200 text-red-600 hover:bg-red-50 transition-colors whitespace-nowrap">
                              Rechazar
                            </button>
                          </div>
                        ) : (
                          <p className="text-xs text-gray-300 text-center">—</p>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {totalPaginas > 1 && (
          <div className="px-5 py-3 border-t border-gray-100 flex items-center justify-between text-xs text-gray-500">
            <span>Página {pagina + 1} de {totalPaginas}</span>
            <div className="flex gap-1">
              <button onClick={() => setPagina(p => Math.max(0, p - 1))} disabled={pagina === 0}
                className="px-3 py-1.5 rounded-lg hover:bg-gray-100 disabled:opacity-40 transition-colors">← Anterior</button>
              <button onClick={() => setPagina(p => Math.min(totalPaginas - 1, p + 1))} disabled={pagina >= totalPaginas - 1}
                className="px-3 py-1.5 rounded-lg hover:bg-gray-100 disabled:opacity-40 transition-colors">Siguiente →</button>
            </div>
          </div>
        )}
      </div>

      {!soloLectura && revisando && (
        <ModalRevision
          item={revisando.item}
          accion={revisando.accion}
          onConfirmar={resolver}
          onCancelar={() => setRevisando(null)}
        />
      )}
    </div>
  )
}
