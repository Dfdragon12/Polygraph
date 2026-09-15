import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import asignacionService from '../../services/asignacionService'
import { toLocalDateTimeString } from '../../utils/fechas'

function formatHora(iso) {
  return new Date(iso).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' })
}

export default function ProgramadorDashboard() {
  const { usuario } = useAuth()
  const [conteos, setConteos] = useState(null)
  const [hoyLista, setHoyLista] = useState(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    const inicio = new Date(); inicio.setHours(0, 0, 0, 0)
    const fin = new Date(); fin.setHours(23, 59, 59, 999)

    Promise.all([
      asignacionService.conteo(),
      asignacionService.calendario(toLocalDateTimeString(inicio), toLocalDateTimeString(fin)),
    ])
      .then(([c, agenda]) => {
        setConteos(c)
        setHoyLista((agenda ?? []).filter(a => a.fechaProgramada).sort((a, b) => new Date(a.fechaProgramada) - new Date(b.fechaProgramada)))
      })
      .catch(() => setError(true))
  }, [])

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-0.5">Hola, {usuario?.nombre}</h2>
      <p className="text-sm text-gray-500 mb-6">
        {new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
      </p>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg">
          No se pudo cargar el resumen. Intenta recargar la página.
        </div>
      )}

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
        <TarjetaConteo label="Pendientes"  valor={conteos?.pendientes}  color="amber" />
        <TarjetaConteo label="Asignados"   valor={conteos?.asignados}   color="blue" />
        <TarjetaConteo label="Completados" valor={conteos?.completados} color="green" />
        <TarjetaConteo label="Cancelados"  valor={conteos?.cancelados}  color="red" />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 mb-6">
        <Link to="/programador/agenda" className="rounded-xl border border-amber-100 bg-amber-50 text-amber-600 p-5 hover:shadow-md transition-shadow block">
          <span className="text-2xl mb-3 block">🔧</span>
          <p className="text-sm font-semibold">Servicios</p>
          <p className="text-xs opacity-70 mt-1">Gestiona los servicios pendientes de agendar</p>
        </Link>
        <Link to="/programador/agenda" className="rounded-xl border border-primary-100 bg-primary-50 text-primary-600 p-5 hover:shadow-md transition-shadow block">
          <span className="text-2xl mb-3 block">📅</span>
          <p className="text-sm font-semibold">Agenda</p>
          <p className="text-xs opacity-70 mt-1">Programa poligrafías y visitas en el calendario</p>
        </Link>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
        <div className="px-5 py-3.5 border-b border-gray-100 flex items-center justify-between">
          <p className="text-sm font-semibold text-gray-700">Agenda de hoy</p>
          <Link to="/programador/agenda" className="text-xs text-primary-600 hover:underline">Ver semana completa</Link>
        </div>
        {hoyLista === null ? (
          <div className="flex justify-center py-10">
            <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-600 border-t-transparent" />
          </div>
        ) : hoyLista.length === 0 ? (
          <p className="text-sm text-gray-400 text-center py-10">No hay servicios programados para hoy.</p>
        ) : (
          <div className="divide-y divide-gray-100">
            {hoyLista.map(a => (
              <div key={a.id} className="px-5 py-3 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-800 truncate">{a.subproceso} — {a.nombresEvaluado} {a.apellidosEvaluado ?? ''}</p>
                  <p className="text-xs text-gray-400 truncate">{a.nombreCiudad ?? 'Sin ciudad'} · {a.nombreAsignado ?? 'Sin asignar'}</p>
                </div>
                <span className="text-xs font-semibold text-gray-600 flex-shrink-0">{formatHora(a.fechaProgramada)}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function TarjetaConteo({ label, valor, color }) {
  const colores = {
    amber: 'border-amber-200 text-amber-600',
    blue:  'border-blue-200 text-blue-600',
    green: 'border-green-200 text-green-600',
    red:   'border-red-200 text-red-500',
  }
  return (
    <div className={`bg-white rounded-xl border shadow-sm p-4 ${colores[color]}`}>
      <p className="text-xs font-medium text-gray-500 mb-2">{label}</p>
      <p className="text-3xl font-bold">{valor ?? '—'}</p>
    </div>
  )
}
