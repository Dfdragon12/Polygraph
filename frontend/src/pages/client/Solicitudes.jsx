import { useState, useEffect, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import solicitudService from '../../services/solicitudService'
import ServiceStatusBadge from '../../components/ServiceStatusBadge'

const ESTADOS_OPCIONES = [
  { value: 'PENDIENTE',    label: 'Pendiente' },
  { value: 'PROGRAMANDO',  label: 'Programando' },
  { value: 'EN_EJECUCION', label: 'En Ejecución' },
  { value: 'FINALIZADO',   label: 'Finalizado' },
  { value: 'PUBLICADO',    label: 'Publicado' },
  { value: 'CANCELADO',    label: 'Cancelado' },
  { value: 'REPROGRAMADO', label: 'Reprogramado' },
]

export default function Solicitudes() {
  const [solicitudes, setSolicitudes] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)
  const navigate = useNavigate()
  const [filtroEstado, setFiltroEstado] = useState('')
  const [pagina, setPagina] = useState(0)
  const [totalPaginas, setTotalPaginas] = useState(0)
  const [totalElementos, setTotalElementos] = useState(0)

  const cargar = useCallback(async () => {
    setCargando(true)
    setError(null)
    try {
      const resp = await solicitudService.listar({
        estado: filtroEstado || undefined,
        pagina,
        tamano: 10,
      })
      const data = resp.data
      setSolicitudes(data.content ?? [])
      setTotalPaginas(data.totalPages ?? 0)
      setTotalElementos(data.totalElements ?? 0)
    } catch {
      setError('No se pudieron cargar las solicitudes. Verifica tu conexión.')
    } finally {
      setCargando(false)
    }
  }, [filtroEstado, pagina])

  useEffect(() => { cargar() }, [cargar])

  const limpiarFiltros = () => {
    setFiltroEstado('')
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

      {/* Filtros */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div>
            <label className="block text-xs text-gray-500 mb-1">Estado</label>
            <select
              className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none bg-white"
              value={filtroEstado}
              onChange={(e) => { setFiltroEstado(e.target.value); setPagina(0) }}>
              <option value="">Todos los estados</option>
              {ESTADOS_OPCIONES.map(({ value, label }) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
          {filtroEstado && (
            <button
              onClick={limpiarFiltros}
              className="px-3 py-1.5 text-xs text-gray-500 border border-gray-300 rounded-lg hover:bg-gray-50">
              Limpiar filtros
            </button>
          )}
          {totalElementos > 0 && (
            <span className="ml-auto text-xs text-gray-400">
              {totalElementos} solicitud{totalElementos !== 1 ? 'es' : ''}
            </span>
          )}
        </div>
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
            <table className="w-full text-sm">
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
                    <td className="px-4 py-3 text-gray-500 font-mono text-xs">{s.cedulaEvaluado}</td>
                    <td className="px-4 py-3 font-medium text-gray-800">
                      {s.nombresEvaluado} {s.apellidosEvaluado}
                    </td>
                    <td className="px-4 py-3 text-gray-600">{s.cargo || '—'}</td>
                    <td className="px-4 py-3 text-gray-600 max-w-[160px]">
                      <span className="truncate block">{s.proceso || '—'}</span>
                    </td>
                    <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                      {s.fechaSolicitud
                        ? new Date(s.fechaSolicitud).toLocaleDateString('es-CO')
                        : '—'}
                    </td>
                    <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                      {s.fechaEntregaEstimada ?? '—'}
                    </td>
                    <td className="px-4 py-3">
                      <ServiceStatusBadge status={s.estado} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Paginación */}
        {totalPaginas > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
            <p className="text-xs text-gray-500">
              Página {pagina + 1} de {totalPaginas}
            </p>
            <div className="flex items-center gap-2">
              <button
                onClick={() => setPagina(p => p - 1)}
                disabled={pagina === 0}
                className="px-3 py-1 text-xs text-gray-600 border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50">
                ← Anterior
              </button>
              <button
                onClick={() => setPagina(p => p + 1)}
                disabled={pagina >= totalPaginas - 1}
                className="px-3 py-1 text-xs text-gray-600 border border-gray-300 rounded-lg disabled:opacity-40 hover:bg-gray-50">
                Siguiente →
              </button>
            </div>
          </div>
        )}
      </div>

    </div>
  )
}
