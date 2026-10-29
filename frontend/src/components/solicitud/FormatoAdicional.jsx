import { useId } from 'react'

// Formato adicional que el cliente diligencia al pedir una poligrafía (pre-empleo, rutina o
// específica). Los campos vienen del servidor (GET /solicitudes/formatos), así que lo que se pide
// aquí es siempre lo que el servidor valida.
const inputCls = 'w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:outline-none'

const TIPO_INPUT = { TEXTO: 'text', FECHA: 'date', NUMERO: 'number', CORREO: 'email' }

// ¿Falta algún campo obligatorio, o hay uno mal escrito? Devuelve el mensaje o null.
export function validarFormato(formato, datos) {
  if (!formato) return null
  for (const c of formato.campos) {
    const v = (datos[c.clave] ?? '').trim()
    if (!v) {
      if (c.obligatorio) return `«${c.etiqueta}» es obligatorio.`
      continue
    }
    if (c.tipo === 'CORREO' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v)) return `«${c.etiqueta}» no es un correo válido.`
    if (c.tipo === 'NUMERO' && !(Number.isInteger(Number(v)) && Number(v) >= 1)) return `«${c.etiqueta}» debe ser un número entero mayor que cero.`
  }
  return null
}

// resaltar: marca en rojo los obligatorios vacíos (se activa cuando el cliente intenta avanzar)
export default function FormatoAdicional({ formato, datos, onChange, resaltar = false }) {
  const idCampos = useId()
  if (!formato) return null

  return (
    <section className="bg-white rounded-xl border border-gray-200 shadow-sm">
      <header className="flex items-start gap-3 px-6 py-4 border-b border-gray-100">
        <span className="mt-0.5 flex-shrink-0 w-6 h-6 rounded-full bg-gray-900 text-white text-xs font-semibold flex items-center justify-center" aria-hidden="true">3</span>
        <div>
          <h2 className="text-base font-semibold text-gray-900">{formato.titulo}</h2>
          <p className="text-xs text-gray-600 mt-0.5">Información que necesita el poligrafista. Los campos con * son obligatorios.</p>
        </div>
      </header>
      <div className="px-6 py-5 grid grid-cols-1 sm:grid-cols-2 gap-4">
        {formato.campos.map((c, i) => {
          const id = `${idCampos}-${i}`
          const largo = c.tipo === 'AREA_TEXTO'
          const falta = resaltar && c.obligatorio && !(datos[c.clave] ?? '').trim()
          const cls = falta ? `${inputCls} border-red-500` : inputCls
          return (
            <div key={c.clave} className={largo ? 'sm:col-span-2' : ''}>
              <label htmlFor={id} className="block text-sm font-medium text-gray-700 mb-1">
                {c.etiqueta}{c.obligatorio && <span className="text-red-600"> *</span>}
              </label>
              {largo ? (
                <textarea id={id} rows={3} maxLength={2000} spellCheck lang="es" className={`${cls} resize-y`}
                  value={datos[c.clave] ?? ''} onChange={(e) => onChange(c.clave, e.target.value)}
                  aria-describedby={c.ayuda ? `${id}-ayuda` : undefined} />
              ) : (
                <input id={id} type={TIPO_INPUT[c.tipo] ?? 'text'} className={cls} aria-invalid={falta || undefined} maxLength={c.tipo === 'FECHA' ? undefined : 300}
                  min={c.tipo === 'NUMERO' ? 1 : undefined} step={c.tipo === 'NUMERO' ? 1 : undefined}
                  value={datos[c.clave] ?? ''} onChange={(e) => onChange(c.clave, e.target.value)}
                  aria-describedby={c.ayuda ? `${id}-ayuda` : undefined} />
              )}
              {falta && <p className="mt-1 text-[11px] font-medium text-red-600">Este campo es obligatorio.</p>}
              {c.ayuda && <p id={`${id}-ayuda`} className="mt-1 text-[11px] text-gray-500">{c.ayuda}</p>}
            </div>
          )
        })}
      </div>
    </section>
  )
}
