import { useEffect, useRef, useState } from 'react'
import hallazgosService from '../../services/hallazgosService'

// LB-01 · «Consulta interna»: el catálogo de referencias (LAB / ALR-LAB en laboral, ACA / ALR-ACA en académica) es información interna de Polygraph; no va en el informe del cliente.

const valor = (h) => (h.tipo === 'CRITICO' ? 'Crítico' : h.tipo === 'ALERTA' ? 'Alerta · no suma' : `${h.puntos} puntos`)

export default function ConsultaLaboral({ patron = /^(LAB|ALR-LAB)-/, titulo = 'Catálogo de referencias · Verificación laboral' }) {
  const [abierto, setAbierto] = useState(false)
  const [lista, setLista] = useState(null)
  const [error, setError] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    if (!abierto || lista) return
    hallazgosService.listar(true)
      .then(todos => setLista(todos.filter(h => patron.test(h.codigo))))
      .catch(() => setError(true))
  }, [abierto, lista])
  useEffect(() => {
    if (!abierto) return undefined
    ref.current?.focus()
    const tecla = (e) => { if (e.key === 'Escape') setAbierto(false) }
    window.addEventListener('keydown', tecla)
    return () => window.removeEventListener('keydown', tecla)
  }, [abierto])

  return (
    <>
      <p className="text-xs text-gray-700 mb-2">Solo para uso interno: no va en el informe del cliente.</p>
      <button type="button" onClick={() => setAbierto(true)}
        className="w-full min-h-11 px-3 text-sm text-left font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">
        {titulo}
      </button>
      {abierto && (
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-3" onClick={() => setAbierto(false)}>
          <div role="dialog" aria-modal="true" aria-label={titulo} tabIndex={-1} ref={ref} onClick={e => e.stopPropagation()}
            className="bg-white rounded-xl shadow-xl w-full max-w-4xl max-h-[85vh] overflow-y-auto p-4 sm:p-6 focus:outline-none">
            <div className="flex items-start justify-between gap-3 mb-3">
              <h2 className="text-base font-bold text-gray-900">{titulo}</h2>
              <button type="button" onClick={() => setAbierto(false)} aria-label="Cerrar" className="min-h-11 min-w-11 rounded-lg border border-gray-300 text-gray-800">✕</button>
            </div>
            {error && <p role="alert" className="text-sm text-red-700">No se pudo cargar el catálogo. Inténtalo de nuevo.</p>}
            {!error && !lista && <p className="text-sm text-gray-700" aria-live="polite">Cargando…</p>}
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
        </div>
      )}
    </>
  )
}
