import { formatoMoneda, soloDigitos } from './calculosVisita'

// VD-05 · Campo de dinero: acepta solo números, se ve como «$4.800.000» y guarda el número (solo dígitos).
export default function CampoDinero({ value, onChange, disabled, id, ariaLabel, className }) {
  const mostrado = value === '' || value === undefined || value === null ? '' : formatoMoneda(soloDigitos(value))
  return (
    <input id={id} aria-label={ariaLabel} inputMode="numeric" autoComplete="off" disabled={disabled} value={mostrado} placeholder="$0"
      onChange={e => onChange(soloDigitos(e.target.value))}
      className={className ?? 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'} />
  )
}
