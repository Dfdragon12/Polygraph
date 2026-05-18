import { useEffect, useState } from 'react'
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
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    dashboardService.obtenerDashboard()
      .then(setDatos)
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

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-bold text-gray-800">Dashboard</h2>

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
          valor={datos?.serviciosDisponibles ?? 0}
          color="bg-purple-50"
          icono="📦"
        />
      </div>

      {/* Tabla de últimas solicitudes */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100">
          <h3 className="text-base font-semibold text-gray-800">Últimas solicitudes</h3>
        </div>

        {!datos?.ultimasSolicitudes?.length ? (
          <p className="text-gray-400 text-sm text-center py-10">Sin solicitudes registradas.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-6 py-3 text-left">#</th>
                  <th className="px-6 py-3 text-left">Servicio</th>
                  <th className="px-6 py-3 text-left">Candidato</th>
                  <th className="px-6 py-3 text-left">Fecha solicitud</th>
                  <th className="px-6 py-3 text-left">Entrega estimada</th>
                  <th className="px-6 py-3 text-left">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {datos.ultimasSolicitudes.map((s) => (
                  <tr key={s.idServicio} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 text-gray-400 font-mono text-xs">{s.idServicio}</td>
                    <td className="px-6 py-4 font-medium text-gray-800">{s.tipoProceso}</td>
                    <td className="px-6 py-4 text-gray-600">{s.nombreCandidato ?? '—'}</td>
                    <td className="px-6 py-4 text-gray-500">{s.fechaSolicitud ?? '—'}</td>
                    <td className="px-6 py-4 text-gray-500">{s.fechaEntregaEstudio ?? '—'}</td>
                    <td className="px-6 py-4">
                      <ServiceStatusBadge estado={s.estado} />
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
