import { useState, useEffect, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import solicitudService from '../../services/solicitudService'
import ServiceStatusBadge from '../../components/ServiceStatusBadge'
import Paginacion from '../../components/ui/Paginacion'

// Tarjetas de resumen por estado — filtran al hacer clic y se vuelven a pulsar para volver a
// "Todos". Mismo patrón que gestor/Solicitudes.jsx, acotado a las solicitudes del cliente.
const CAMPO_CONTEO = {
  PENDIENTE:    'pendientes',
  PROGRAMANDO:  'programando',
  EN_EJECUCION: 'enEjecucion',
  FINALIZADO:   'finalizados',
  PUBLICADO:    'publicados',
  CANCELADO:    'cancelados',
  REPROGRAMADO: 'reprogramados',
}

const TARJETAS_ESTADO = [
  { estado: 'PENDIENTE',    label: 'Pendientes',    desc: 'Requieren atención',     border: 'border-amber-200',   dot: 'bg-amber-400',   texto: 'text-amber-600',   bgActivo: 'bg-amber-50/70',   ring: 'ring-amber-300' },
  { estado: 'PROGRAMANDO',  label: 'Programando',   desc: 'En preparación',         border: 'border-blue-200',    dot: 'bg-blue-400',    texto: 'text-blue-600',    bgActivo: 'bg-blue-50/70',    ring: 'ring-blue-300' },
  { estado: 'EN_EJECUCION', label: 'En ejecución',  desc: 'Siendo trabajados',      border: 'border-primary-200', dot: 'bg-primary-400', texto: 'text-primary-600', bgActivo: 'bg-primary-50/70', ring: 'ring-primary-300' },
  { estado: 'FINALIZADO',   label: 'Finalizados',   desc: 'Listos para revisar',    border: 'border-green-200',   dot: 'bg-green-400',   texto: 'text-green-600',   bgActivo: 'bg-green-50/70',   ring: 'ring-green-300' },
  { estado: 'PUBLICADO',    label: 'Publicados',    desc: 'Entregados',             border: 'border-cyan-200',    dot: 'bg-cyan-400',    texto: 'text-cyan-600',    bgActivo: 'bg-cyan-50/70',    ring: 'ring-cyan-300' },
  { estado: 'CANCELADO',    label: 'Cancelados',    desc: 'No activos',             border: 'border-red-200',     dot: 'bg-red-400',     texto: 'text-red-500',     bgActivo: 'bg-red-50/70',     ring: 'ring-red-300' },
  { estado: 'REPROGRAMADO', label: 'Reprogramados', desc: 'Cambiaron de fecha',     border: 'border-orange-200',  dot: 'bg-orange-400',  texto: 'text-orange-600',  bgActivo: 'bg-orange-50/70',  ring: 'ring-orange-300' },
]

export default function Solicitudes() {
  const [solicitudes, setSolicitudes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const navigate = useNavigate()
  const [filtroEstado, setFiltroEstado] = useState('')
  const [pagina, setPagina] = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)
  const POR_PAGINA = 10
  const [conteos, setConteos] = useState(null)

  const cargar = useCallback(async () => {
    setCargando(true)
    setError(null)
    try {
      const resp = await solicitudService.listar({
        estado: filtroEstado || undefined,
        pagina,
        tamano: POR_PAGINA,
      })
      const data = resp.data
      setSolicitudes(data.content ?? [])
      setTotalElementos(data.totalElements ?? 0)
    } catch {
      setError('No se pudieron cargar las solicitudes. Verifica tu conexión.')
    } finally {
      setCargando(false)
    }
  }, [filtroEstado, pagina])

  useEffect(() => { cargar() }, [cargar])

  useEffect(() => {
    solicitudService.conteo().then(setConteos).catch(() => setConteos(null))
  }, [solicitudes])

  const alternarTarjeta = (estado) => {
    setFiltroEstado(prev => (prev === estado ? '' : estado))
    setPagina(0)
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-800">Mis Solicitudes</h2>
        <Link to="/cliente/nueva-solicitud"
          className="px-4 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 transition-colors">
          + Nueva Solicitud
        </Link>
      </div>

      {/* Tarjetas de resumen por estado */}
      <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-7 gap-4">
        {TARJETAS_ESTADO.map(t => {
          const activo = filtroEstado === t.estado
          return (
            <button key={t.estado} type="button" onClick={() => alternarTarjeta(t.estado)}
              className={`text-left bg-white rounded-xl border shadow-sm p-4 cursor-pointer hover:shadow-md transition-shadow ${t.border} ${
                activo ? `${t.bgActivo} ring-2 ${t.ring}` : ''
              }`}>
              <div className="flex items-center gap-2 mb-2">
                <span className={`w-2 h-2 rounded-full ${t.dot}`} />
                <span className="text-xs font-medium text-gray-500">{t.label}</span>
              </div>
              <p className={`text-3xl font-bold ${t.texto}`}>{conteos ? conteos[CAMPO_CONTEO[t.estado]] : '—'}</p>
              <p className="text-xs text-gray-400 mt-1">{t.desc}</p>
            </button>
          )
        })}
      </div>

      {/* Filtro activo + total */}
      <div className="flex items-center gap-3 flex-wrap">
        {filtroEstado && (
          <button
            onClick={() => { setFiltroEstado(''); setPagina(0) }}
            className="px-3 py-1.5 text-xs text-gray-500 border border-gray-300 rounded-lg hover:bg-gray-50 bg-white">
            × Quitar filtro "{TARJETAS_ESTADO.find(t => t.estado === filtroEstado)?.label}"
          </button>
        )}
        {totalElementos > 0 && (
          <span className="ml-auto text-xs text-gray-400">
            {totalElementos} solicitud{totalElementos !== 1 ? 'es' : ''}
          </span>
        )}
      </div>

      {/* Tabla */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        {cargando ? (
          <div className="flex items-center justify-center h-40">
            <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : error ? (
          <div className="flex flex-col items-center justify-center h-40 gap-2">
            <p className="text-red-500 text-sm">{error}</p>
            <button onClick={cargar} className="text-xs text-primary-600 hover:underline">Reintentar</button>
          </div>
        ) : solicitudes.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-40 gap-2">
            <p className="text-gray-400 text-sm">No hay solicitudes registradas.</p>
            <Link to="/cliente/nueva-solicitud" className="text-xs text-primary-600 hover:underline">
              Crear primera solicitud →
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-stack w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-4 py-3 text-left">Cédula</th>
                  <th className="px-4 py-3 text-left">Candidato</th>
                  <th className="px-4 py-3 text-left">Cargo</th>
                  <th className="px-4 py-3 text-left">Servicio</th>
                  <th className="px-4 py-3 text-left">Fecha solicitud</th>
                  <th className="px-4 py-3 text-left">Fecha límite</th>
                  <th className="px-4 py-3 text-left">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {solicitudes.map((s) => (
                  <tr
                    key={s.idServicio}
                    onClick={() => navigate(`/cliente/solicitudes/${s.idServicio}`)}
                    className="hover:bg-primary-50 cursor-pointer transition-colors">
                    <td data-label="Cédula" className="px-4 py-3 text-gray-500 font-mono text-xs">{s.cedulaEvaluado}</td>
                    <td data-label="Candidato" className="px-4 py-3 font-medium text-gray-800">
                      {s.nombresEvaluado} {s.apellidosEvaluado}
                    </td>
                    <td data-label="Cargo" className="px-4 py-3 text-gray-600">{s.cargo || '—'}</td>
                    <td data-label="Servicio" className="px-4 py-3 text-gray-600 max-w-[160px]">
                      <span className="truncate block">{s.proceso || '—'}</span>
                    </td>
                    <td data-label="Fecha solicitud" className="px-4 py-3 text-gray-500 whitespace-nowrap">
                      {s.fechaSolicitud
                        ? new Date(s.fechaSolicitud).toLocaleDateString('es-CO')
                        : '—'}
                    </td>
                    <td data-label="Fecha límite" className="px-4 py-3 text-gray-500 whitespace-nowrap">
                      {s.fechaEntregaEstimada ?? '—'}
                    </td>
                    <td data-label="Estado" className="px-4 py-3">
                      <ServiceStatusBadge status={s.estado} />
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
