import { useEffect, useRef, useState } from 'react'

// AN-08 · Fecha con formato dd/mm/aaaa, igual en cualquier navegador (el selector nativo cambia según el idioma del equipo). Se guarda como siempre,
// en formato AAAA-MM-DD. Se puede escribir (las barras se ponen solas) o elegir con el calendario.

export const aTexto = (iso) => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso ?? ''))
  return m ? `${m[3]}/${m[2]}/${m[1]}` : ''
}

/** "03/11/2026" → "2026-11-03"; null si no es una fecha real. */
export const aIso = (texto) => {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(String(texto ?? ''))
  if (!m) return null
  const [, d, mes, a] = m
  const f = new Date(Number(a), Number(mes) - 1, Number(d))
  if (f.getFullYear() !== Number(a) || f.getMonth() !== Number(mes) - 1 || f.getDate() !== Number(d)) return null
  return `${a}-${mes}-${d}`
}

// Pone las barras mientras se escribe: "03112026" → "03/11/2026".
export const enmascarar = (crudo) => {
  const d = String(crudo ?? '').replace(/\D/g, '').slice(0, 8)
  if (d.length <= 2) return d
  if (d.length <= 4) return `${d.slice(0, 2)}/${d.slice(2)}`
  return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`
}

export default function CampoFecha({ value, onChange, disabled, id, 'aria-label': ariaLabel, className }) {
  const [texto, setTexto] = useState(aTexto(value))
  const nativo = useRef(null)
  // Si el valor cambia desde fuera (por ejemplo la fecha de hoy que pone el sistema), se refleja.
  useEffect(() => { setTexto(aTexto(value)) }, [value])

  const alEscribir = (e) => {
    const t = enmascarar(e.target.value)
    setTexto(t)
    if (t === '') onChange('')
    else { const iso = aIso(t); if (iso) onChange(iso) }
  }
  const invalida = texto.length === 10 && !aIso(texto)
  const base = className ?? 'w-full min-h-11 border border-gray-300 rounded-lg px-3 text-sm bg-white disabled:bg-gray-100'
  return (
    <div className="relative">
      <input id={id} aria-label={ariaLabel} inputMode="numeric" autoComplete="off" placeholder="dd/mm/aaaa" maxLength={10} disabled={disabled} value={texto}
        onChange={alEscribir} aria-invalid={invalida || undefined} className={`${base} pr-11 ${invalida ? 'border-red-500' : ''}`} />
      {!disabled && (
        <>
          <button type="button" aria-label="Elegir la fecha en el calendario" onClick={() => nativo.current?.showPicker?.()}
            className="absolute right-0 top-0 h-full min-w-11 text-gray-700" tabIndex={-1}>📅</button>
          <input ref={nativo} type="date" tabIndex={-1} aria-hidden="true" value={value ?? ''} onChange={e => onChange(e.target.value)}
            className="absolute right-0 top-0 h-full w-11 opacity-0 pointer-events-none" />
        </>
      )}
      {invalida && <p role="alert" className="text-xs text-red-700 mt-1">Fecha no válida. Usa dd/mm/aaaa.</p>}
    </div>
  )
}
