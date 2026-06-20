import { useAuth } from '../../hooks/useAuth'

export default function VisitadorDashboard() {
  const { usuario } = useAuth()
  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-0.5">Hola, {usuario?.nombre}</h2>
      <p className="text-sm text-gray-500 mb-8">
        {new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        <TarjetaModulo titulo="Mis Visitas" descripcion="Visitas domiciliarias asignadas" icono="🏠" color="indigo" />
        <TarjetaModulo titulo="Informes" descripcion="Genera informes de visita completados" icono="📄" color="emerald" />
      </div>
    </div>
  )
}

function TarjetaModulo({ titulo, descripcion, icono, color }) {
  const colores = {
    indigo:  'bg-indigo-50  border-indigo-100  text-indigo-600',
    emerald: 'bg-emerald-50 border-emerald-100 text-emerald-600',
  }
  return (
    <div className={`rounded-xl border p-5 ${colores[color]}`}>
      <span className="text-2xl mb-3 block">{icono}</span>
      <p className="text-sm font-semibold">{titulo}</p>
      <p className="text-xs opacity-70 mt-1">{descripcion}</p>
    </div>
  )
}
