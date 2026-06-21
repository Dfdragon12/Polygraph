import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import solicitudService from '../../services/solicitudService'
import ServiceStatusBadge from '../../components/ServiceStatusBadge'

export default function SolicitudDetalle() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [detalle, setDetalle] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    solicitudService.detalle(id)
      .then(r => setDetalle(r.data))
      .catch(() => setError('No se pudo cargar el detalle de la solicitud.'))
      .finally(() => setCargando(false))
  }, [id])

  if (cargando) return (
    <div className="flex items-center justify-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-4 border-indigo-600 border-t-transparent" />
    </div>
  )

  if (error || !detalle) return (
    <div className="flex flex-col items-center justify-center h-64 gap-3">
      <p className="text-red-500 text-sm">{error || 'No encontrado.'}</p>
      <button onClick={() => navigate('/cliente/solicitudes')} className="text-sm text-indigo-600 hover:underline">
        ← Volver a solicitudes
      </button>
    </div>
  )

  return (
    <div className="max-w-3xl mx-auto py-6 space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/cliente/solicitudes')}
          className="text-sm text-gray-500 hover:text-gray-800">
          ← Volver
        </button>
        <h1 className="text-2xl font-bold text-gray-900">Solicitud #{detalle.idSolicitud}</h1>
      </div>

      {/* Estado y fechas */}
      <div className="bg-white rounded-xl border border-gray-200 p-5 flex flex-wrap items-center gap-6">
        <div className="flex items-center gap-3">
          <ServiceStatusBadge status={detalle.estado} />
          <span className="text-sm font-medium text-gray-700">Estado actual</span>
        </div>
        <div>
          <p className="text-xs text-gray-400">Fecha de solicitud</p>
          <p className="text-sm font-medium text-gray-700">
            {detalle.fechaSolicitud ? new Date(detalle.fechaSolicitud).toLocaleString('es-CO') : '—'}
          </p>
        </div>
        {detalle.fechaEntregaEstimada && (
          <div className="ml-auto">
            <p className="text-xs text-gray-400">Entrega estimada</p>
            <p className="text-sm font-semibold text-indigo-700">{detalle.fechaEntregaEstimada}</p>
          </div>
        )}
      </div>

      {/* Datos del evaluado */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-4">Datos del evaluado</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-x-6 gap-y-3 text-sm">
          <div>
            <span className="block text-xs text-gray-400">Cédula</span>
            <span className="font-medium text-gray-800">{detalle.cedulaEvaluado}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Nombre</span>
            <span className="font-medium text-gray-800">{detalle.nombresEvaluado} {detalle.apellidosEvaluado}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Cargo</span>
            <span className="text-gray-800">{detalle.cargo || '—'}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Ciudad</span>
            <span className="text-gray-800">{detalle.ciudadEvaluado || '—'}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Celular</span>
            <span className="text-gray-800">{detalle.celularEvaluado || '—'}</span>
          </div>
          <div>
            <span className="block text-xs text-gray-400">Email</span>
            <span className="text-gray-800 break-all">{detalle.emailEvaluado || '—'}</span>
          </div>
        </div>
        {detalle.notas && (
          <div className="mt-4 bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm">
            <span className="block text-xs font-semibold text-amber-700 mb-1">Novedades del gestor</span>
            <p className="text-amber-800">{detalle.notas}</p>
          </div>
        )}
      </div>

      {/* Servicios contratados */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-4">Servicios contratados</h2>
        <ul className="divide-y divide-gray-50">
          {detalle.servicios?.map((sv, i) => (
            <li key={i} className="flex items-center justify-between py-2.5">
              <div>
                <p className="text-sm font-medium text-gray-800">{sv.nombre}</p>
                {sv.categoria && (
                  <p className="text-xs text-gray-400">{sv.categoria.replace(/_/g, ' ')}</p>
                )}
              </div>
              <ServiceStatusBadge status={sv.estado} />
            </li>
          ))}
        </ul>
      </div>

      {/* Historial de cambios */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-4">Historial de cambios</h2>
        {detalle.historial?.length ? (
          <ol className="relative border-l border-gray-200 space-y-5 ml-2 pl-5">
            {detalle.historial.map((h, i) => (
              <li key={i} className="relative">
                <div className="absolute w-3 h-3 bg-indigo-400 rounded-full -left-[1.65rem] top-0.5 border-2 border-white" />
                <p className="text-xs text-gray-400">
                  {h.fechaCambio ? new Date(h.fechaCambio).toLocaleString('es-CO') : '—'}
                  {h.usuario && <> · <span className="text-gray-500">{h.usuario}</span></>}
                </p>
                <p className="text-sm text-gray-800 font-medium mt-0.5">
                  {h.estadoAnterior ? `${h.estadoAnterior} → ${h.estadoNuevo}` : h.estadoNuevo}
                </p>
                {h.observacion && (
                  <p className="text-xs text-gray-500 mt-0.5 italic">{h.observacion}</p>
                )}
              </li>
            ))}
          </ol>
        ) : (
          <p className="text-sm text-gray-400">Sin cambios de estado registrados.</p>
        )}
      </div>

      {/* Descargar informe — solo si PUBLICADO */}
      {detalle.estado === 'PUBLICADO' && (
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <button className="w-full py-3 text-sm font-medium text-white bg-green-600 rounded-lg hover:bg-green-700 transition-colors flex items-center justify-center gap-2">
            <span>📄</span>
            Descargar informe
          </button>
        </div>
      )}
    </div>
  )
}
