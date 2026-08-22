import ReversionesLista from '../../components/ReversionesLista'

export default function AdminReversiones() {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Solicitudes de reversión</h2>
        <p className="text-sm text-gray-500 mt-0.5">Reversiones de solicitudes CANCELADAS pedidas por un gestor</p>
      </div>

      <ReversionesLista base="admin" soloLectura={false} />
    </div>
  )
}
