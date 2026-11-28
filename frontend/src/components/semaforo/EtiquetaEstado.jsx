import { useEstadosSubproceso } from '../../hooks/useEstadosSubproceso'

// El estado de un subproceso, siempre con el mismo nombre y color (catálogo del servidor): nunca el código interno.
export default function EtiquetaEstado({ estado, className = '', pequena = false }) {
  const { de } = useEstadosSubproceso()
  const e = de(estado)
  if (!e) return null
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full font-medium text-gray-900 whitespace-nowrap ring-1 ring-inset ${pequena ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-0.5 text-xs'} ${className}`}
      style={{ backgroundColor: `${e.color}22`, '--tw-ring-color': e.color }}>
      <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: e.color }} />
      {e.nombre}
    </span>
  )
}
