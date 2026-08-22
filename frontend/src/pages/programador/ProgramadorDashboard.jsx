import { useAuth } from '../../hooks/useAuth'

export default function ProgramadorDashboard() {
  const { usuario } = useAuth()
  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-0.5">Hola, {usuario?.nombre}</h2>
      <p className="text-sm text-gray-500 mb-8">
        {new Date().toLocaleDateString('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
        <TarjetaModulo titulo="Agenda" descripcion="Programa poligrafías y visitas en el calendario" icono="📅" color="primary" />
        <TarjetaModulo titulo="Servicios" descripcion="Gestiona los servicios pendientes de agendar" icono="🔧" color="amber" />
      </div>
    </div>
  )
}

function TarjetaModulo({ titulo, descripcion, icono, color }) {
  const colores = {
    primary: 'bg-primary-50 border-primary-100 text-primary-600',
    amber:  'bg-amber-50  border-amber-100  text-amber-600',
  }
  return (
    <div className={`rounded-xl border p-5 ${colores[color]}`}>
      <span className="text-2xl mb-3 block">{icono}</span>
      <p className="text-sm font-semibold">{titulo}</p>
      <p className="text-xs opacity-70 mt-1">{descripcion}</p>
    </div>
  )
}
