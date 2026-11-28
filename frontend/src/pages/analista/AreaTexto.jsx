// LB-06 · Texto largo con su tope de caracteres: el contador solo aparece cuando el texto pasa del 80 % del límite (igual que en los demás formatos).
const LIMITE_POR_DEFECTO = 2000

export const NoImprime = () => <span className="text-[11px] rounded bg-gray-200 text-gray-800 px-2 py-0.5 whitespace-nowrap">No se imprime</span>

export default function AreaTexto({ id, ariaLabel, ariaLabelledby, value, onChange, disabled, max, rows = 2, className }) {
  const tope = max ?? LIMITE_POR_DEFECTO
  const texto = value ?? ''
  return (
    <>
      <textarea id={id} aria-label={ariaLabel} aria-labelledby={ariaLabelledby} rows={rows} maxLength={tope} disabled={disabled} value={texto}
        onChange={e => onChange(e.target.value)}
        className={className ?? 'w-full min-h-11 border border-gray-300 rounded-lg px-3 py-2 text-sm bg-white disabled:bg-gray-100'} />
      {texto.length >= tope * 0.8 && <p className="text-xs text-amber-700" role="status">{texto.length} / {tope}</p>}
    </>
  )
}
