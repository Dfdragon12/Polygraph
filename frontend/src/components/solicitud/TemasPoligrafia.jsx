import { useId } from 'react'

// Temas que el cliente pide evaluar en una poligrafía: hasta 4 del catálogo que administra el
// admin (Admin → Novedades → Temas de poligrafía). "Otros ¿Cuál?" pide escribir cuál.
export const MAX_TEMAS = 4

const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none'

// Falta escribir cuál en un tema que lo pide → mensaje; si no, null.
export function validarTemas(temas, elegidos) {
  for (const e of elegidos) {
    const t = temas.find((x) => x.id === e.idPregunta)
    if (t?.requiereDetalle && !(e.detalle ?? '').trim()) return `Escribe cuál es el tema en «${t.nombre}».`
  }
  return null
}

// elegidos: [{ idPregunta, detalle }]
export default function TemasPoligrafia({ temas, elegidos, onChange, numero = 4 }) {
  const idCampos = useId()
  if (!temas?.length) return null
  const lleno = elegidos.length >= MAX_TEMAS

  const alternar = (t) => {
    if (elegidos.some((e) => e.idPregunta === t.id)) onChange(elegidos.filter((e) => e.idPregunta !== t.id))
    else if (!lleno) onChange([...elegidos, { idPregunta: t.id, detalle: '' }])
  }
  const escribir = (idPregunta, detalle) =>
    onChange(elegidos.map((e) => (e.idPregunta === idPregunta ? { ...e, detalle } : e)))

  return (
    <section className="bg-white rounded-xl border border-gray-200 shadow-sm">
      <header className="flex items-start justify-between gap-3 flex-wrap px-6 py-4 border-b border-gray-100">
        <div className="flex items-start gap-3">
          <span className="mt-0.5 flex-shrink-0 w-6 h-6 rounded-full bg-gray-900 text-white text-xs font-semibold flex items-center justify-center" aria-hidden="true">{numero}</span>
          <div>
            <h2 className="text-base font-semibold text-gray-900">Temas a evaluar en la poligrafía</h2>
            <p className="text-xs text-gray-600 mt-0.5">Opcional. Elige máximo {MAX_TEMAS}; el poligrafista los verá en su formulario.</p>
          </div>
        </div>
        <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${lleno ? 'bg-primary-100 text-primary-700' : 'bg-gray-100 text-gray-600'}`}
          aria-live="polite">
          {elegidos.length} de {MAX_TEMAS}
        </span>
      </header>

      <div className="px-6 py-5 grid grid-cols-1 sm:grid-cols-2 gap-2">
        {temas.map((t, i) => {
          const elegido = elegidos.find((e) => e.idPregunta === t.id)
          const deshabilitado = !elegido && lleno
          const id = `${idCampos}-${i}`
          return (
            <div key={t.id} className={`rounded-lg border px-3 py-2.5 transition-colors ${
              elegido ? 'border-primary-300 bg-primary-50/60' : deshabilitado ? 'border-gray-100 bg-gray-50' : 'border-gray-200 hover:border-gray-300'
            }`}>
              <div className="flex items-start gap-2.5">
                <input id={id} type="checkbox" checked={!!elegido} disabled={deshabilitado} onChange={() => alternar(t)}
                  className="mt-0.5 h-4 w-4 rounded border-gray-300 text-primary-600 focus:ring-primary-500 disabled:opacity-40" />
                <label htmlFor={id} className={`text-sm cursor-pointer ${deshabilitado ? 'text-gray-400' : 'text-gray-800'}`}>{t.nombre}</label>
              </div>
              {elegido && t.requiereDetalle && (
                <input aria-label={`¿Cuál? — ${t.nombre}`} className={`${inputCls} mt-2`} maxLength={300} autoFocus
                  placeholder="Escribe cuál" value={elegido.detalle} onChange={(e) => escribir(t.id, e.target.value)} />
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}
