import { useEffect, useRef, useState } from 'react'
import hallazgosService from '../../services/hallazgosService'
import { ANEXO2, NOTA_ANEXO1 } from './anexosInternos'

// AN-01 · «Consulta interna»: el Anexo 1 (catálogo de referencias LEG / ALR) y el Anexo 2 (relación del delito con el cargo) son información interna de
// Polygraph. AN-09 los retira del informe del cliente; aquí quedan para consulta del analista.

const valor = (h) => (h.tipo === 'CRITICO' ? 'Crítico' : h.tipo === 'ALERTA' ? 'Alerta · no suma' : `${h.puntos} puntos`)

function Ventana({ titulo, onCerrar, children }) {
  const ref = useRef(null)
  useEffect(() => {
    ref.current?.focus()
    const tecla = (e) => { if (e.key === 'Escape') onCerrar() }
    window.addEventListener('keydown', tecla)
    return () => window.removeEventListener('keydown', tecla)
  }, [onCerrar])
  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-3" onClick={onCerrar}>
      <div role="dialog" aria-modal="true" aria-label={titulo} tabIndex={-1} ref={ref} onClick={e => e.stopPropagation()}
        className="bg-white rounded-xl shadow-xl w-full max-w-4xl max-h-[85vh] overflow-y-auto p-4 sm:p-6 focus:outline-none">
        <div className="flex items-start justify-between gap-3 mb-3">
          <h2 className="text-base font-bold text-gray-900">{titulo}</h2>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="min-h-11 min-w-11 rounded-lg border border-gray-300 text-gray-800">✕</button>
        </div>
        {children}
      </div>
    </div>
  )
}

function Anexo1() {
  const [lista, setLista] = useState(null)
  const [error, setError] = useState(false)
  useEffect(() => {
    hallazgosService.listar(true)
      .then(todos => setLista(todos.filter(h => /^(LEG|ALR)-/.test(h.codigo))))
      .catch(() => setError(true))
  }, [])
  if (error) return <p role="alert" className="text-sm text-red-700">No se pudo cargar el catálogo. Inténtalo de nuevo.</p>
  if (!lista) return <p className="text-sm text-gray-700" aria-live="polite">Cargando…</p>
  return (
    <div className="space-y-3">
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
      <p className="text-xs text-gray-700">{NOTA_ANEXO1}</p>
    </div>
  )
}

function Anexo2() {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm border border-gray-300">
        <thead className="bg-gray-100 text-xs text-gray-700">
          <tr>
            <th scope="col" className="text-left p-2 border-b border-gray-300 w-1/4">Tipo de cargo</th>
            <th scope="col" className="text-left p-2 border-b border-gray-300">Delitos que se consideran relacionados</th>
          </tr>
        </thead>
        <tbody>
          {ANEXO2.map(f => (
            <tr key={f.tipo} className="border-b border-gray-200 align-top">
              <th scope="row" className="text-left font-semibold p-2">{f.tipo}</th>
              <td className="p-2 space-y-1">{f.delitos.map((d, i) => <p key={i} className="break-words">{d}</p>)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function ConsultaInterna() {
  const [abierto, setAbierto] = useState(null)   // 'a1' | 'a2' | null
  return (
    <>
      <p className="text-xs text-gray-700 mb-2">Solo para uso interno: no va en el informe del cliente.</p>
      <div className="flex flex-col gap-2">
        <button type="button" onClick={() => setAbierto('a1')} className="min-h-11 px-3 text-sm text-left font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">
          Anexo 1 · Catálogo de referencias
        </button>
        <button type="button" onClick={() => setAbierto('a2')} className="min-h-11 px-3 text-sm text-left font-medium rounded-lg border border-gray-300 text-gray-800 hover:bg-gray-50">
          Anexo 2 · Relación del delito con el cargo
        </button>
      </div>
      {abierto === 'a1' && <Ventana titulo="Anexo 1 · Catálogo de referencias" onCerrar={() => setAbierto(null)}><Anexo1 /></Ventana>}
      {abierto === 'a2' && <Ventana titulo="Anexo 2 · Relación del delito con el cargo" onCerrar={() => setAbierto(null)}><Anexo2 /></Ventana>}
    </>
  )
}
