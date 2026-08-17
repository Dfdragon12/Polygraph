import ReversionesLista from '../../components/ReversionesLista'

export default function GestorReversiones() {
  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Solicitudes de reversión</h2>
        <p className="text-sm text-gray-500 mt-0.5">
          Consulta el estado de tus solicitudes de reversión — solo lectura, la aprobación la realiza un administrador
        </p>
      </div>

      <ReversionesLista base="gestor" soloLectura />
    </div>
  )
}
