import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import dashboardService from '../../services/dashboardService'
import ServiceStatusBadge from '../../components/ServiceStatusBadge'

function iniciales(nombre) {
  return (nombre?.[0] ?? '?').toUpperCase()
}

const ICONO_ACTIVOS = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
  </svg>
)
const ICONO_PENDIENTES = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
)
const ICONO_FINALIZADOS = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
  </svg>
)
const ICONO_BOLSA = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M20 7h-3V6a4 4 0 00-8 0v1H6a1 1 0 00-1 1v11a2 2 0 002 2h10a2 2 0 002-2V8a1 1 0 00-1-1zM9 6a3 3 0 016 0v1H9V6zm1 6a1 1 0 11-2 0 1 1 0 012 0zm6 0a1 1 0 11-2 0 1 1 0 012 0z" />
  </svg>
)
const ICONO_CARRITO = (
  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 00-3 3h15.75m-12.75-3h11.218c1.121-2.3 1.907-4.723 2.269-7.234.061-.422-.256-.766-.681-.766H5.106M7.5 14.25L5.106 5.272M6 20.25a.75.75 0 11-1.5 0 .75.75 0 011.5 0zm12.75 0a.75.75 0 11-1.5 0 .75.75 0 011.5 0z" />
  </svg>
)
const ICONO_MAS = (
  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
  </svg>
)

function KpiCard({ titulo, valor, subtitulo, color, icono }) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
      <div className="flex items-center gap-3">
        <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 ${color}`}>
          {icono}
        </div>
        <p className="text-sm text-gray-500">{titulo}</p>
      </div>
      <p className="text-3xl font-bold text-gray-800 mt-3">{valor}</p>
      {subtitulo && <p className="text-xs text-gray-400 mt-1">{subtitulo}</p>}
    </div>
  )
}

export default function Dashboard() {
  const { usuario } = useAuth()
  const navigate = useNavigate()
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
        <div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" />
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
  const pctBolsa = totalBolsa > 0 ? Math.round((disponiblesBolsa / totalBolsa) * 100) : 0

  return (
    <div className="space-y-6">
      {/* Encabezado */}
      <div className="flex items-center justify-between flex-wrap gap-4 bg-white rounded-2xl border border-gray-100 shadow-sm px-6 py-5">
        <div className="flex items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-primary-600 text-white flex items-center justify-center text-lg font-semibold flex-shrink-0">
            {iniciales(usuario?.nombre)}
          </div>
          <div>
            <h2 className="text-xl font-bold text-gray-900">Hola, {usuario?.nombre ?? 'bienvenido'}</h2>
            <p className="text-sm text-gray-500 capitalize mt-0.5">
              {new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
            </p>
          </div>
        </div>
        <Link to="/cliente/nueva-solicitud"
          className="inline-flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white text-sm font-medium px-4 py-2.5 rounded-xl transition-colors shadow-sm">
          {ICONO_MAS}
          Nueva Solicitud
        </Link>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          titulo="Servicios activos"
          valor={datos?.serviciosActivos ?? 0}
          subtitulo="En proceso ahora mismo"
          color="bg-blue-50 text-blue-600"
          icono={ICONO_ACTIVOS}
        />
        <KpiCard
          titulo="Pendientes"
          valor={datos?.serviciosPendientes ?? 0}
          subtitulo="Por programar"
          color="bg-amber-50 text-amber-600"
          icono={ICONO_PENDIENTES}
        />
        <KpiCard
          titulo="Finalizados este mes"
          valor={datos?.serviciosFinalizadosMes ?? 0}
          subtitulo="Completados en el mes actual"
          color="bg-green-50 text-green-600"
          icono={ICONO_FINALIZADOS}
        />
        <KpiCard
          titulo="Disponibles en bolsa"
          valor={disponiblesBolsa}
          subtitulo={totalBolsa > 0 ? `De ${totalBolsa} comprados` : 'Bolsa prepago'}
          color="bg-purple-50 text-purple-600"
          icono={ICONO_BOLSA}
        />
      </div>

      {/* Indicador bolsa */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm px-5 py-4">
        <div className="flex items-center gap-3 flex-wrap">
          <div className="w-10 h-10 rounded-lg bg-primary-50 text-primary-600 flex items-center justify-center flex-shrink-0">
            {ICONO_CARRITO}
          </div>
          <div className="min-w-0">
            <p className="text-sm text-gray-700">
              Tienes <strong className="text-primary-700">{disponiblesBolsa}</strong> servicio{disponiblesBolsa !== 1 ? 's' : ''} disponible{disponiblesBolsa !== 1 ? 's' : ''}
              {totalBolsa > 0 ? ` de ${totalBolsa} comprados` : ' en tu bolsa prepago'}
            </p>
          </div>
          <Link to="/cliente/comprar-servicios"
            className="ml-auto text-xs font-semibold text-primary-600 hover:text-primary-800 border border-primary-200 px-3 py-1.5 rounded-lg hover:bg-primary-50 transition-colors whitespace-nowrap">
            Comprar más →
          </Link>
        </div>
        {totalBolsa > 0 && (
          <div className="w-full h-1.5 bg-gray-100 rounded-full overflow-hidden mt-3">
            <div className="h-full bg-primary-500 rounded-full transition-all duration-500" style={{ width: `${pctBolsa}%` }} />
          </div>
        )}
      </div>

      {/* Últimas 5 solicitudes */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
          <h3 className="text-base font-semibold text-gray-800">Últimas solicitudes</h3>
          <Link to="/cliente/solicitudes" className="text-xs font-medium text-primary-600 hover:text-primary-700">
            Ver todas →
          </Link>
        </div>

        {!datos?.ultimasSolicitudes?.length ? (
          <div className="flex flex-col items-center justify-center py-14 gap-2">
            <p className="text-gray-400 text-sm">Sin solicitudes registradas.</p>
            <Link to="/cliente/nueva-solicitud" className="text-xs text-primary-600 hover:underline">
              Crear la primera →
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-gray-500 text-xs uppercase">
                <tr>
                  <th className="px-5 py-3 text-left">Candidato</th>
                  <th className="px-5 py-3 text-left">Servicio</th>
                  <th className="px-5 py-3 text-left">Fecha solicitud</th>
                  <th className="px-5 py-3 text-left">Entrega estimada</th>
                  <th className="px-5 py-3 text-left">Estado</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {datos.ultimasSolicitudes.map((s) => (
                  <tr key={s.idServicio} onClick={() => navigate(`/cliente/solicitudes/${s.idServicio}`)}
                    className="hover:bg-primary-50/40 cursor-pointer transition-colors">
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-2.5">
                        <span className="w-7 h-7 rounded-full bg-slate-100 text-slate-600 text-xs font-semibold flex items-center justify-center flex-shrink-0">
                          {iniciales(s.nombresEvaluado)}
                        </span>
                        <div className="min-w-0">
                          <p className="font-medium text-gray-800 truncate">{s.nombresEvaluado} {s.apellidosEvaluado}</p>
                          {s.cedulaEvaluado && <p className="text-xs text-gray-400">{s.cedulaEvaluado}</p>}
                        </div>
                      </div>
                    </td>
                    <td className="px-5 py-3 text-gray-600 max-w-[180px] truncate">
                      {s.proceso || '—'}
                    </td>
                    <td className="px-5 py-3 text-gray-500 whitespace-nowrap">
                      {s.fechaSolicitud
                        ? new Date(s.fechaSolicitud).toLocaleDateString('es-CO')
                        : '—'}
                    </td>
                    <td className="px-5 py-3 text-gray-500 whitespace-nowrap">{s.fechaEntregaEstimada ?? '—'}</td>
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
