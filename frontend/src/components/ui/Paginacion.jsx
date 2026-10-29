// Pie de página compartido para toda tabla paginada de la plataforma — antes estaba duplicado
// tal cual (carácter por carácter) en Clientes.jsx y UsuariosInternos.jsx; se centraliza acá para
// que todas las tablas nuevas usen el mismo diseño sin copiar/pegar de nuevo.
import { useEffect, useState } from 'react'

/** Filas por página de todas las tablas de la plataforma. */
export const POR_PAGINA = 10

/** Paginación en el navegador para una lista que ya está completa en memoria (ya filtrada por el
 *  buscador, así que la búsqueda siempre recorre TODOS los registros). Vuelve a la página 1 cuando
 *  cambia `reiniciarCon` (texto del buscador, filtro...) y nunca queda en una página que ya no existe. */
export function usePaginacion(items, reiniciarCon = '', porPagina = POR_PAGINA) {
  const [pagina, setPagina] = useState(1)
  useEffect(() => { setPagina(1) }, [reiniciarCon])
  const lista = items ?? []
  const ultima = Math.max(1, Math.ceil(lista.length / porPagina))
  const actual = Math.min(pagina, ultima)
  return {
    pagina: actual, setPagina, porPagina, total: lista.length,
    visibles: lista.slice((actual - 1) * porPagina, actual * porPagina),
    // Número de fila global (1, 2, … 11, 12 en la página 2)
    desde: (actual - 1) * porPagina,
  }
}

export default function Paginacion({ pagina, total, porPagina, onChange }) {
  const totalPags = Math.ceil(total / porPagina)
  if (totalPags <= 1) return null
  const inicio = (pagina - 1) * porPagina + 1
  const fin    = Math.min(pagina * porPagina, total)
  const nums = []
  for (let i = Math.max(1, pagina - 2); i <= Math.min(totalPags, pagina + 2); i++) nums.push(i)
  return (
    <div className="flex items-center justify-between px-4 py-3 border-t border-gray-100">
      <span className="text-xs text-gray-400">{inicio}–{fin} de {total}</span>
      <div className="flex items-center gap-1">
        <button onClick={() => onChange(pagina - 1)} disabled={pagina === 1}
          className="px-2.5 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          ← Anterior
        </button>
        {nums[0] > 1 && <span className="text-xs text-gray-400 px-1">…</span>}
        {nums.map(p => (
          <button key={p} onClick={() => onChange(p)}
            className={`w-8 h-8 text-xs rounded-lg border transition-colors ${
              p === pagina ? 'bg-primary-600 border-primary-600 text-white font-semibold' : 'border-gray-200 text-gray-500 hover:bg-gray-50'
            }`}>
            {p}
          </button>
        ))}
        {nums[nums.length - 1] < totalPags && <span className="text-xs text-gray-400 px-1">…</span>}
        <button onClick={() => onChange(pagina + 1)} disabled={pagina === totalPags}
          className="px-2.5 py-1.5 text-xs rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
          Siguiente →
        </button>
      </div>
    </div>
  )
}
