import { useState, useEffect, useCallback } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import solicitudService from '../../services/solicitudService'
import { useCambios } from '../../hooks/useCambios'
import Paginacion from '../../components/ui/Paginacion'
import EstadoTiempo, { FiltroTiempo, Reprogramada } from '../../components/EstadoTiempo'
import SemaforoEstados from '../../components/semaforo/SemaforoEstados'
import AlertasAparte from '../../components/semaforo/AlertasAparte'
import EtiquetaEstado from '../../components/semaforo/EtiquetaEstado'
import { useEstadosSubproceso } from '../../hooks/useEstadosSubproceso'

const POR_PAGINA = 10

const fechaCorta = (valor) => valor
  ? new Date(`${String(valor).slice(0, 10)}T00:00:00`).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' }).replace(/ de /g, ' ')
  : '—'

function IconoFlecha() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
    </svg>
  )
}

function IconoCarpeta() {
  return (
    <svg className="h-10 w-10" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z" />
    </svg>
  )
}

export default function Solicitudes() {
  const [solicitudes, setSolicitudes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { nombre } = useEstadosSubproceso()
  // Desde el dashboard: ?filtro=vencidas o ?estado=PROGRAMANDO abren la lista ya filtrada.
  const [filtroEstado, setFiltroEstado] = useState(searchParams.get('filtro') === 'vencidas' ? 'VENCIDAS' : (searchParams.get('estado') ?? ''))
  const [filtroTiempo, setFiltroTiempo] = useState(searchParams.get('tiempo') ?? '')
  const [ordenRetraso, setOrdenRetraso] = useState(false)
  const [pagina, setPagina] = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)

  const cargar = useCallback(async (silencioso = false) => {
    if (!silencioso) { setCargando(true); setError(null) }
    try {
      const resp = await solicitudService.listar({
        estado: filtroEstado && filtroEstado !== 'VENCIDAS' ? filtroEstado : undefined,
        estadoTiempo: filtroEstado === 'VENCIDAS' ? 'VENCIDA' : filtroTiempo,
        orden: ordenRetraso ? 'retraso' : undefined,
        pagina,
        tamano: POR_PAGINA,
      })
      const data = resp.data
      setSolicitudes(data.content ?? [])
      setTotalElementos(data.totalElements ?? 0)
    } catch {
      if (!silencioso) setError('No se pudieron cargar las solicitudes. Verifica tu conexión.')
    } finally {
      if (!silencioso) setCargando(false)
    }
  }, [filtroEstado, filtroTiempo, ordenRetraso, pagina])

  useEffect(() => { cargar() }, [cargar])
  useCambios(() => cargar(true))


  const alternarFiltro = (estado) => {
    setFiltroEstado(estado)
    setPagina(0)
  }

  return (
    <div className="space-y-5">
      {/* Encabezado */}
      <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Mis solicitudes</h2>
          <p className="text-sm text-gray-500 mt-0.5">Seguimiento de los estudios de tus candidatos, desde la solicitud hasta el informe final.</p>
        </div>
        <Link to="/cliente/nueva-solicitud"
          className="inline-flex items-center justify-center gap-1.5 px-4 py-2.5 text-sm font-semibold text-white bg-primary-600 rounded-lg shadow-sm hover:bg-primary-700 transition-colors">
          <span className="text-base leading-none">+</span> Nueva solicitud
        </Link>
      </div>

      <SemaforoEstados seleccionado={filtroEstado} onSeleccionar={alternarFiltro} interno={false} textoCliente titulo="Estado de tus solicitudes" />
      <AlertasAparte />

      {filtroEstado && (
        <div>
          <button type="button" onClick={() => { setFiltroEstado(''); setPagina(0) }}
            className="px-3 py-1.5 text-xs text-gray-500 border border-gray-300 rounded-lg hover:bg-gray-50 bg-white">
            × Quitar filtro «{filtroEstado === 'VENCIDAS' ? 'Vencidas' : nombre(filtroEstado)}»
          </button>
        </div>
      )}

      <FiltroTiempo tiempo={filtroEstado === 'VENCIDAS' ? 'VENCIDA' : filtroTiempo}
        onTiempo={(v) => { if (filtroEstado === 'VENCIDAS') setFiltroEstado(''); setFiltroTiempo(v); setPagina(0) }}
        ordenRetraso={ordenRetraso} onOrden={(v) => { setOrdenRetraso(v); setPagina(0) }} />

      {/* Tabla */}
      <div className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm overflow-hidden">
        <div className="px-5 py-3.5 border-b border-gray-100 flex items-center justify-between">
          <p className="text-sm font-semibold text-gray-800">
            {filtroEstado ? (filtroEstado === 'VENCIDAS' ? 'Vencidas' : nombre(filtroEstado)) : 'Todas las solicitudes'}
          </p>
          {!cargando && totalElementos > 0 && (
            <p className="text-xs text-gray-400">{totalElementos} {totalElementos === 1 ? 'solicitud' : 'solicitudes'}</p>
          )}
        </div>

        {cargando ? (
          <div className="flex items-center justify-center h-48">
            <div className="animate-spin rounded-full h-7 w-7 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : error ? (
          <div className="flex flex-col items-center justify-center h-48 gap-2">
            <p className="text-red-500 text-sm">{error}</p>
            <button onClick={cargar} className="text-xs font-medium text-primary-600 hover:underline">Reintentar</button>
          </div>
        ) : solicitudes.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-14 px-6 text-center">
            <div className="text-gray-300 mb-3"><IconoCarpeta /></div>
            <p className="text-sm font-medium text-gray-700">
              {filtroEstado || filtroTiempo ? 'No hay solicitudes con este filtro' : 'Todavía no tienes solicitudes'}
            </p>
            <p className="text-xs text-gray-400 mt-1">
              {filtroEstado || filtroTiempo ? 'Prueba con otro filtro o quítalo para ver todas.' : 'Crea tu primera solicitud para empezar el estudio de un candidato.'}
            </p>
            {!filtroEstado && !filtroTiempo && (
              <Link to="/cliente/nueva-solicitud" className="mt-4 text-sm font-semibold text-primary-600 hover:text-primary-700">
                Crear solicitud →
              </Link>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-stack w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100">
                  {['N° servicio', 'Evaluado', 'Cargo', 'Servicio', 'Solicitada', 'Compromiso', 'Tiempo', 'Estado', ''].map((h, i) => (
                    <th key={h || i} className={`px-3 py-3 text-left text-[11px] font-semibold text-gray-400 uppercase tracking-wider bg-gray-50/60 ${h === 'Cargo' ? 'hidden xl:table-cell' : ''}`}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {solicitudes.map((s) => (
                  <tr key={s.idServicio}
                    onClick={() => navigate(`/cliente/solicitudes/${s.idServicio}`)}
                    className="group hover:bg-gray-50/80 cursor-pointer transition-colors">
                    <td data-label="N° servicio" className="px-3 py-3 whitespace-nowrap">
                      <span className="text-xs font-semibold text-gray-700 bg-gray-100 px-2 py-1 rounded-md">#{s.idServicio}</span>
                    </td>
                    <td data-label="Evaluado" className="px-3 py-3">
                      <p className="font-semibold text-gray-900">{s.nombresEvaluado} {s.apellidosEvaluado}</p>
                      <p className="text-xs text-gray-400 mt-0.5">CC {s.cedulaEvaluado}</p>
                      {s.cargo && <p className="hidden md:block xl:hidden text-xs text-gray-500 mt-0.5">{s.cargo}</p>}
                    </td>
                    <td data-label="Cargo" className="hidden xl:table-cell px-3 py-3 text-gray-600">{s.cargo || '—'}</td>
                    <td data-label="Servicio" className="px-3 py-3 text-gray-700 max-w-[180px]">
                      <span className="block" title={s.proceso ?? undefined}>{s.proceso || '—'}</span>
                    </td>
                    <td data-label="Solicitada" className="px-3 py-3 text-gray-600 whitespace-nowrap">{fechaCorta(s.fechaSolicitud)}</td>
                    <td data-label="Compromiso" className="px-3 py-3 whitespace-nowrap">
                      <span className={s.estado === 'PUBLICADO' || s.estado === 'CANCELADO' ? 'text-gray-500' : 'font-medium text-gray-800'}>
                        {fechaCorta(s.fechaEntregaEstimada)}
                      </span>
                      <Reprogramada tiempo={s.tiempo} className="block" />
                    </td>
                    <td data-label="Tiempo" className="px-3 py-3">
                      {s.tiempo ? <EstadoTiempo tiempo={s.tiempo} /> : <span className="text-xs text-gray-500">—</span>}
                    </td>
                    <td data-label="Estado" className="px-3 py-3">
                      <ul className="flex flex-wrap gap-1" aria-label="Subprocesos por estado">
                        {(s.resumen ?? []).map(r => (
                          <li key={r.codigo} className={filtroEstado === r.codigo ? 'rounded-full ring-2 ring-offset-1 ring-gray-900' : ''}>
                            <EtiquetaEstado estado={r.codigo} pequena /><span className="sr-only">{r.cantidad}</span>
                            <span aria-hidden="true" className="ml-0.5 text-[11px] font-semibold text-gray-700">{r.cantidad}</span>
                          </li>
                        ))}
                      </ul>
                      {s.vigentes > 0 && (
                        <p className="mt-1 text-xs text-gray-700">
                          {s.publicados} de {s.vigentes} publicados{s.etiquetaPublicacion ? ` · ${s.etiquetaPublicacion}` : ''}
                        </p>
                      )}
                    </td>
                    <td className="px-3 py-3 text-gray-300 group-hover:text-primary-500 transition-colors">
                      <IconoFlecha />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {!cargando && solicitudes.length > 0 && (
          <Paginacion pagina={pagina + 1} total={totalElementos} porPagina={POR_PAGINA}
            onChange={(p) => setPagina(p - 1)} />
        )}
      </div>
    </div>
  )
}
