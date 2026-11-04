import { useState } from 'react'
import hallazgosService from '../../services/hallazgosService'

// VD-09 · «Ver catálogo de referencias»: consulta INTERNA de los hallazgos VIS y las alertas ALR-VIS (con sus puntos). Ya no va en el Word ni en el PDF del cliente.

const valor = (h) => (h.tipo === 'CRITICO' ? 'Crítico' : h.tipo === 'ALERTA' ? 'Alerta · no suma' : `${h.puntos} puntos`)

export default function CatalogoReferencias() {
  const [abierto, setAbierto] = useState(false)
  const [lista, setLista] = useState(null)
  const [error, setError] = useState(false)

  const alternar = async () => {
    const abrir = !abierto
    setAbierto(abrir)
    if (abrir && lista === null) {
      try {
        const todos = await hallazgosService.listar(true)
        setLista(todos.filter(h => /^(ALR-)?VIS-/.test(h.codigo) || /^ALR-VIS-/.test(h.codigo)))
        setError(false)
      } catch { setError(true) }
    }
  }

  return (
    <section aria-labelledby="catalogo-ref">
      <h3 id="catalogo-ref" className="sr-only">Catálogo de referencias</h3>
      <button type="button" onClick={alternar} aria-expanded={abierto}
        className="min-h-11 px-4 text-sm font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">
        {abierto ? 'Ocultar el catálogo de referencias' : 'Ver catálogo de referencias'}
      </button>
      {abierto && (
        <div className="mt-3">
          <p className="text-xs text-gray-700 mb-2">Solo para uso interno de Polygraph: no aparece en el informe del cliente. Cada código cuenta una sola vez por estudio.</p>
          {error && <p role="alert" className="text-sm text-red-700">No se pudo cargar el catálogo. Inténtalo de nuevo.</p>}
          {lista === null && !error && <p className="text-sm text-gray-700" aria-live="polite">Cargando…</p>}
          {lista && (
            <div className="overflow-x-auto">
              <table className="w-full text-sm border border-gray-300">
                <thead className="bg-gray-100 text-xs text-gray-700">
                  <tr>
                    <th scope="col" className="text-left p-2 border-b border-gray-300">Ref.</th>
                    <th scope="col" className="text-left p-2 border-b border-gray-300">Hallazgo</th>
                    <th scope="col" className="text-left p-2 border-b border-gray-300">Calificación</th>
                  </tr>
                </thead>
                <tbody>
                  {lista.map(h => (
                    <tr key={h.codigo} className="border-b border-gray-200 align-top">
                      <th scope="row" className="text-left font-semibold p-2 whitespace-nowrap">{h.codigo}</th>
                      <td className="p-2 break-words">{h.descripcion}</td>
                      <td className="p-2 whitespace-nowrap">{valor(h)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
