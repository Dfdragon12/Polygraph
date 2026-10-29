// Piezas que comparten el tablero del administrador y sus pestañas.
import { useState, useEffect, useRef } from 'react'

export const pesos = (valor) => Number(valor ?? 0).toLocaleString('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })
export const pesosDeCentavos = (centavos) => pesos(Number(centavos ?? 0) / 100)
export const entero = (n) => Number(n ?? 0).toLocaleString('es-CO')

/** Un número que se anima un instante cuando cambia: así se nota qué se movió en vivo. */
export function Numero({ valor, className = '' }) {
  const anterior = useRef(valor)
  const [cambio, setCambio] = useState(false)
  useEffect(() => {
    if (anterior.current === valor) return undefined
    anterior.current = valor
    setCambio(true)
    const t = setTimeout(() => setCambio(false), 1500)
    return () => clearTimeout(t)
  }, [valor])
  return (
    <span className={`inline-block transition-transform duration-300 ${cambio ? 'scale-125' : 'scale-100'} ${className}`}>
      {typeof valor === 'number' ? entero(valor) : valor}
    </span>
  )
}

export function Seccion({ titulo, subtitulo, derecha, children, className = '' }) {
  return (
    <section className={`bg-white rounded-xl border border-gray-200 shadow-sm p-5 ${className}`}>
      <div className="flex items-start justify-between gap-3 mb-4">
        <div>
          <h3 className="text-sm font-semibold text-gray-800">{titulo}</h3>
          {subtitulo && <p className="text-xs text-gray-500 mt-0.5">{subtitulo}</p>}
        </div>
        {derecha}
      </div>
      {children}
    </section>
  )
}

const COLORES = {
  primary: 'border-primary-100 text-primary-700',
  emerald: 'border-emerald-100 text-emerald-700',
  amber:   'border-amber-100 text-amber-700',
  red:     'border-red-100 text-red-700',
  sky:     'border-sky-100 text-sky-700',
  violet:  'border-violet-100 text-violet-700',
  gray:    'border-gray-200 text-gray-700',
}

/** Recuadro con un número. Si trae `onClick` es un botón (abre el detalle o va a la pantalla). */
export function Recuadro({ titulo, valor, texto, tono = 'gray', onClick, activo = false }) {
  const [borde, color] = COLORES[tono].split(' ')
  const interior = (
    <>
      <p className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">{titulo}</p>
      <p className={`text-2xl font-bold mt-0.5 break-words ${color}`}><Numero valor={valor} /></p>
      {texto && <p className="text-xs text-gray-500 mt-0.5">{texto}</p>}
    </>
  )
  const base = `text-left bg-white rounded-xl border ${borde} shadow-sm p-3.5 w-full`
  return onClick
    ? <button type="button" onClick={onClick} aria-pressed={activo}
        className={`${base} hover:shadow-md transition-shadow ${activo ? 'ring-2 ring-primary-500' : ''}`}>{interior}</button>
    : <div className={base}>{interior}</div>
}

/** Barra horizontal con su etiqueta: para rankings (procesos, clientes). */
export function BarrasRanking({ datos, vacio = 'Sin datos en este periodo.', color = 'bg-primary-500' }) {
  if (!datos?.length) return <p className="text-sm text-gray-500">{vacio}</p>
  const max = Math.max(1, ...datos.map(d => d.total))
  return (
    <ul className="space-y-2.5">
      {datos.map(d => (
        <li key={d.nombre}>
          <div className="flex items-center justify-between text-xs mb-1 gap-2">
            <span className="text-gray-700 truncate">{d.nombre}</span>
            <span className="font-semibold text-gray-800">{entero(d.total)}</span>
          </div>
          <div className="h-1.5 rounded-full bg-gray-100 overflow-hidden" aria-hidden="true">
            <div className={`h-full rounded-full ${color} transition-all duration-500`} style={{ width: `${(d.total / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ul>
  )
}
