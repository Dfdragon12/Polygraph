import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import dashboardService from '../../services/dashboardService'
import ServiceStatusBadge from '../../components/ServiceStatusBadge'

function KpiCard({ titulo, valor, color, icono }) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 flex items-center gap-4">
      <div className={`w-12 h-12 rounded-xl flex items-center justify-center text-2xl ${color}`}>
        {icono}
      </div>
      <div>
        <p className="text-sm text-gray-500">{titulo}</p>
        <p className="text-2xl font-bold text-gray-800">{valor}</p>
      </div>
    </div>
  )
}

export default function Dashboard() {
  const [datos, setDatos] = useState(null)
  const [bolsa, setBolsa] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    Promise.all([
      dashboardService.obtenerDashboard(),
      dashboardService.obtenerBolsaServicios().catch(() => []),
    ])
      .then(([dashData, bolsaData]) => {
        setDatos(dashData)
        setBolsa(Array.isArray(bolsaData) ? bolsaData : [])
      })
      .catch(() => setError('No se pudo cargar el dashboard. Intenta nuevamente.'))
      .finally(() => setCargando(false))
  }, [])

  if (cargando) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-indigo-600 border-t-transparent" />
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-64">
        <p className="text-red-500 text-sm">{error}</p>
      </div>
    )
  }

  const totalBolsa = bolsa?.reduce((acc, b) => acc + (b.cantidad || 0), 0) ?? 0
  const disponiblesBolsa = datos?.serviciosDisponibles ?? 0

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-800">Dashboard</h2>
        <Link to="/cliente/nueva-solicitud"
          className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700">
          + Nueva Solicitud
        </Link>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          titulo="Servicios activos"
          valor={datos?.serviciosActivos ?? 0}
          color="bg-blue-50"
          icono="⚡"
        />
        <KpiCard
          titulo="Pendientes"
          valor={datos?.serviciosPendientes ?? 0}
          color="bg-yellow-50"
          icono="⏳"
        />
        <KpiCard
          titulo="Finalizados este mes"
          valor={datos?.serviciosFinalizadosMes ?? 0}
          color="bg-green-50"
          icono="✅"
        />
        <KpiCard
          titulo="Disponibles en bolsa"
          valor={disponiblesBolsa}
          color="bg-purple-50"
          icono="📦"
        />
      </div>

      {/* Indicador bolsa */}
      <div className="bg-indigo-50 border border-indigo-100 rounded-xl px-5 py-3 flex items-center gap-3">
        <span className="text-xl">🛒</span>
        <p className="text-sm text-indigo-800">
          Tienes <strong>{disponiblesBolsa}</strong> servicios disponibles
          {totalBolsa > 0 ? ` de ${totalBolsa} comprados` : ' en tu bolsa prepago'}
        </p>
        <Link to="/cliente/comprar-servicios"
          className="ml-auto text-xs text-indigo-600 hover:underline font-medium">
          Comprar más →
        </Link>
      </div>

      {/* Últimas 5 solicitudes */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-base font-semibold text-gray-800">Últimas solicitudes</h3>
          <Link to="/cliente/solicitudes" className="text-xs text-indigo-600 hover:underline">
            Ver todas →
          </Link>
        </div>

        {!datos?.ultimasSolicitudes?.length ? (
          <p className="text-gray-400 text-sm text-center py-10">Sin solicitudes registradas.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-5 py-3 text-left">Candidato</th>
                  <th className="px-5 py-3 text-left">Servicios</th>
                  <th className="px-5 py-3 text-left">Fecha solicitud</th>
                  <th className="px-5 py-3 text-left">Entrega estimada</th>
                  <th className="px-5 py-3 text-left">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {datos.ultimasSolicitudes.map((s) => (
                  <tr key={s.idSolicitud} className="hover:bg-gray-50 transition-colors">
                    <td className="px-5 py-3 font-medium text-gray-800">
                      {s.nombresEvaluado} {s.apellidosEvaluado}
                      {s.cedulaEvaluado && (
                        <span className="ml-1 text-xs text-gray-400 font-normal">{s.cedulaEvaluado}</span>
                      )}
                    </td>
                    <td className="px-5 py-3 text-gray-600 max-w-[180px] truncate">
                      {s.servicios?.join(', ') || '—'}
                    </td>
                    <td className="px-5 py-3 text-gray-500">
                      {s.fechaSolicitud
                        ? new Date(s.fechaSolicitud).toLocaleDateString('es-CO')
                        : '—'}
                    </td>
                    <td className="px-5 py-3 text-gray-500">{s.fechaEntregaEstimada ?? '—'}</td>
                    <td className="px-5 py-3">
                      <ServiceStatusBadge status={s.estado} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
