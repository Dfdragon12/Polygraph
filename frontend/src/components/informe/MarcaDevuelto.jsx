/**
 * PC-04 · La marca «Devuelto · <motivo>»: gris, sin color de estado (Devuelto NO es un estado; el subproceso sigue en Ejecución). Va junto al estado en las filas del
 * gestor y en las tarjetas del ejecutor. El detalle que explicó el gestor sale al pasar el cursor (title).
 */
export default function MarcaDevuelto({ motivo, detalle, className = '' }) {
  return (
    <span title={detalle || undefined}
      className={`inline-flex items-center rounded-full border border-gray-300 bg-gray-100 px-2 py-0.5 text-[11px] font-semibold text-gray-800 ${className}`}>
      Devuelto{motivo ? ` · ${motivo}` : ''}
    </span>
  )
}
