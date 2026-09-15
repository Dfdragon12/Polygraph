import { useState, useEffect } from 'react'
import formulariosService from '../services/formulariosService'

let contador = 0
const idLocal = () => `hv-local-${++contador}`

const inputCls = "w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-500 disabled:bg-gray-50 disabled:text-gray-500"

const ICONO_TIPO_DATO = { TEXTO: 'Aa', NUMERO: '#', FECHA: '📅', BOOLEANO: '✓' }

function InputPorTipoSimple({ tipoDato, value, onChange, disabled }) {
  if (tipoDato === 'BOOLEANO') {
    return (
      <select value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls}>
        <option value="">—</option>
        <option value="true">Sí</option>
        <option value="false">No</option>
      </select>
    )
  }
  if (tipoDato === 'FECHA') {
    return <input type="date" value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls} />
  }
  if (tipoDato === 'NUMERO') {
    return <input type="number" value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls} />
  }
  return <input type="text" value={value ?? ''} onChange={e => onChange(e.target.value)} disabled={disabled} className={inputCls} />
}

function Campo({ etiqueta, tipoDato, children }) {
  return (
    <div>
      <label className="flex items-center gap-1.5 text-xs font-medium text-gray-500 mb-1">
        <span className="flex-shrink-0 w-4 h-4 rounded bg-gray-100 text-gray-400 text-[9px] flex items-center justify-center">
          {ICONO_TIPO_DATO[tipoDato] ?? ''}
        </span>
        <span className="truncate">{etiqueta}</span>
      </label>
      {children}
    </div>
  )
}

function IconoUsuario() {
  return (
    <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M17.982 18.725A7.488 7.488 0 0012 15.75a7.488 7.488 0 00-5.982 2.975m11.964 0a9 9 0 10-11.963 0m11.963 0A8.966 8.966 0 0112 21a8.966 8.966 0 01-5.982-2.275M15 9.75a3 3 0 11-6 0 3 3 0 016 0z" />
    </svg>
  )
}
function IconoMas() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
    </svg>
  )
}
function IconoGuardar() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M17.593 3.322c1.1.128 1.907 1.077 1.907 2.185V21L12 17.25 4.5 21V5.507c0-1.108.806-2.057 1.907-2.185a48.507 48.507 0 0111.186 0z" />
    </svg>
  )
}
function IconoPapelera() {
  return (
    <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
    </svg>
  )
}

/** Hoja de vida REAL del candidato (referencias, educación, experiencia laboral, inactividades)
 *  — se usa tanto intercalada dentro del formulario de un servicio (Formulario.jsx) como en el
 *  perfil completo del candidato (CandidatoPerfil.jsx). Cada fila se muestra como su propia
 *  tarjeta con los campos en cuadrícula y etiqueta arriba — no como una tabla angosta, para que
 *  quepan cómodos los 7-10 campos de tablas como "Experiencia laboral" sin verse amontonado.
 *  Guarda de forma independiente (su propio botón "Guardar"), sin depender del resto de la
 *  pantalla — un cambio aquí es del candidato, no de un servicio puntual. */
export default function TablaHojaVidaEditable({ idCandidato, tabla, onGuardado, onError, disabled, puedeEliminar = true }) {
  const [filas, setFilas] = useState([])
  const [guardando, setGuardando] = useState(false)
  const [editado, setEditado] = useState(false)

  useEffect(() => {
    setFilas((tabla.filas ?? []).map(f => ({ localId: idLocal(), id: f.id, valores: { ...f.valores } })))
    setEditado(false)
  }, [tabla])

  const editarValor = (localId, clave, valor) => {
    setFilas(prev => prev.map(f => f.localId === localId ? { ...f, valores: { ...f.valores, [clave]: valor } } : f))
    setEditado(true)
  }
  const agregarFila = () => {
    setFilas(prev => [...prev, { localId: idLocal(), id: null, valores: {} }])
    setEditado(true)
  }
  const quitarFila = (localId) => {
    setFilas(prev => prev.filter(f => f.localId !== localId))
    setEditado(true)
  }

  const guardar = async () => {
    setGuardando(true)
    try {
      const payload = filas.map(f => ({ id: f.id, valores: f.valores }))
      const actualizado = await formulariosService.guardarTablaHojaVidaCandidato(idCandidato, tabla.clave, payload)
      setFilas(actualizado.filas.map(f => ({ localId: idLocal(), id: f.id, valores: { ...f.valores } })))
      setEditado(false)
      onGuardado?.(actualizado)
    } catch (err) {
      onError?.(err.response?.data?.mensaje ?? 'No se pudo guardar esta tabla.')
    } finally {
      setGuardando(false)
    }
  }

  return (
    <section className="bg-white border border-gray-200 border-l-4 border-l-red-400 rounded-2xl shadow-sm p-4 sm:p-5
      print:border print:border-gray-300 print:rounded-none print:shadow-none print:break-inside-avoid">
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2.5 min-w-0">
          <span className="flex-shrink-0 w-9 h-9 rounded-xl bg-red-50 text-red-600 flex items-center justify-center">
            <IconoUsuario />
          </span>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-800 truncate">{tabla.etiqueta}</p>
            <p className="text-[11px] text-gray-400">Hoja de vida del candidato — editar aquí también actualiza sus otros servicios</p>
          </div>
        </div>
        {!disabled && editado && (
          <button type="button" onClick={guardar} disabled={guardando}
            className="flex-shrink-0 inline-flex items-center gap-1.5 bg-red-600 hover:bg-red-700 disabled:opacity-60 text-white text-xs font-medium px-3.5 py-2 rounded-lg transition-colors shadow-sm shadow-red-200 print:hidden">
            <IconoGuardar /> {guardando ? 'Guardando…' : 'Guardar'}
          </button>
        )}
      </div>

      {filas.length === 0 ? (
        <p className="text-xs text-gray-400 bg-gray-50 rounded-xl px-4 py-6 text-center">
          Todavía no hay información registrada aquí.
        </p>
      ) : (
        <div className="space-y-3">
          {filas.map((f, idx) => (
            <div key={f.localId} className="relative bg-gray-50/70 border border-gray-100 rounded-xl p-4 print:bg-transparent print:border-gray-300">
              <div className="flex items-center justify-between mb-3">
                <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider">Registro {idx + 1}</span>
                {!disabled && puedeEliminar && (
                  <button type="button" onClick={() => quitarFila(f.localId)} title="Quitar este registro"
                    className="p-1 rounded-lg text-gray-300 hover:bg-red-50 hover:text-red-500 transition-colors print:hidden">
                    <IconoPapelera />
                  </button>
                )}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-4 gap-y-3">
                {tabla.columnas.map(c => (
                  <Campo key={c.clave} etiqueta={c.etiqueta} tipoDato={c.tipoDato}>
                    <InputPorTipoSimple tipoDato={c.tipoDato} value={f.valores[c.clave]}
                      onChange={v => editarValor(f.localId, c.clave, v)} disabled={disabled} />
                  </Campo>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {!disabled && (
        <button type="button" onClick={agregarFila}
          className="w-full mt-3 inline-flex items-center justify-center gap-1.5 text-xs text-red-600 hover:text-red-800 font-medium py-2.5 border border-dashed border-red-200 rounded-xl hover:bg-red-50/40 transition-colors print:hidden">
          <IconoMas /> Agregar registro
        </button>
      )}
    </section>
  )
}
