import { useState, useEffect } from 'react'
import formulariosService from '../services/formulariosService'

// Ficha del candidato vista desde un subproceso: sus datos (identificación, contacto, otros) y los
// soportes que cargó con el link de hoja de vida, cruzados contra los documentos que exige el
// proceso — así quien ejecuta ve de un vistazo qué tiene y qué falta sin salir del formulario.

function formatoFecha(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
}

function IconoPersona() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
    </svg>
  )
}

function IconoClip() {
  return (
    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M18.375 12.739l-7.693 7.693a4.5 4.5 0 01-6.364-6.364l10.94-10.94A3 3 0 1119.5 7.372L8.552 18.32m.009-.01l-.01.01m5.699-9.941l-7.81 7.81a1.5 1.5 0 002.112 2.13" />
    </svg>
  )
}

function Encabezado({ icono, titulo, detalle, color = 'primary' }) {
  const colorIcono = color === 'sky' ? 'bg-sky-50 text-sky-600' : 'bg-primary-50 text-primary-600'
  return (
    <div className="flex items-center gap-2 mb-3">
      <span className={`flex-shrink-0 w-7 h-7 rounded-lg flex items-center justify-center ${colorIcono}`}>{icono}</span>
      <div className="min-w-0">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">{titulo}</p>
        {detalle && <p className="text-[11px] text-gray-400">{detalle}</p>}
      </div>
    </div>
  )
}

// seccion: 'datos' (va debajo de las novedades) | 'soportes' (paso propio del formulario).
// onResumen: solo con seccion="soportes" — recibe un texto corto ("3 de 5 cargados") para el índice.
export default function FichaCandidato({ idServicioSubproceso, seccion = 'datos', onError, onResumen }) {
  const [ficha, setFicha] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [descargando, setDescargando] = useState(null)
  const [verVacios, setVerVacios] = useState(false)

  useEffect(() => {
    setCargando(true)
    formulariosService.fichaCandidato(idServicioSubproceso)
      .then(setFicha)
      .catch(() => onError?.('No se pudieron cargar los datos del candidato.'))
      .finally(() => setCargando(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idServicioSubproceso])

  useEffect(() => {
    if (!ficha || seccion !== 'soportes' || !onResumen) return
    const lista = ficha.soportes ?? []
    const req = lista.filter(s => s.requerido)
    onResumen(req.length > 0
      ? `${req.filter(s => s.idDocumento).length} de ${req.length} cargados`
      : lista.length > 0 ? `${lista.length} cargado${lista.length === 1 ? '' : 's'}` : 'Sin soportes')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ficha, seccion])

  const abrir = async (soporte) => {
    setDescargando(soporte.idDocumento)
    try {
      const blob = await formulariosService.descargarSoporte(idServicioSubproceso, soporte.idDocumento)
      const url = window.URL.createObjectURL(blob)
      window.open(url, '_blank')
      setTimeout(() => window.URL.revokeObjectURL(url), 60000)
    } catch {
      onError?.('No se pudo abrir el soporte.')
    } finally {
      setDescargando(null)
    }
  }

  if (cargando) {
    return (
      <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-6 flex justify-center">
        <div className="animate-spin rounded-full h-6 w-6 border-4 border-primary-500 border-t-transparent" />
      </div>
    )
  }
  if (!ficha) return null

  const grupos = ficha.grupos.map(g => ({
    ...g,
    campos: verVacios ? g.campos : g.campos.filter(c => c.valor && c.valor.trim() !== ''),
  })).filter(g => g.campos.length > 0)
  const totalVacios = ficha.grupos.reduce((acc, g) => acc + g.campos.filter(c => !c.valor || !c.valor.trim()).length, 0)

  const soportes = ficha.soportes ?? []
  const requeridos = soportes.filter(s => s.requerido)
  const cargadosRequeridos = requeridos.filter(s => s.idDocumento).length
  const faltanObligatorios = requeridos.filter(s => s.obligatorio && !s.idDocumento).length

  if (seccion === 'soportes') {
    return (
      <section className="bg-white border border-gray-200 border-l-4 border-l-sky-400 rounded-2xl shadow-sm p-4">
        <Encabezado icono={<IconoClip />} titulo="Soportes del candidato" color="sky"
          detalle={requeridos.length > 0
            ? `${cargadosRequeridos} de ${requeridos.length} documentos del proceso cargados${faltanObligatorios > 0 ? ` · faltan ${faltanObligatorios} obligatorio${faltanObligatorios === 1 ? '' : 's'}` : ''}`
            : 'Documentos que subió con el link de hoja de vida'} />
        {requeridos.length > 0 && (
          <div className="mb-3 h-1.5 rounded-full bg-gray-100 overflow-hidden">
            <div className="h-full bg-sky-500 rounded-full transition-all"
              style={{ width: `${Math.round(cargadosRequeridos / requeridos.length * 100)}%` }} />
          </div>
        )}
        {soportes.length === 0 ? (
          <p className="text-xs text-gray-400 bg-gray-50 rounded-xl px-4 py-6 text-center">
            El candidato no ha cargado soportes y el proceso no tiene documentos configurados.
          </p>
        ) : (
          <ul className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
            {soportes.map(s => {
              const cargado = Boolean(s.idDocumento)
              return (
                <li key={`${s.clave}-${s.idDocumento ?? 'falta'}`}
                  className={`flex items-center gap-3 rounded-xl border px-3.5 py-3 ${
                    cargado ? 'bg-white border-gray-200' : s.obligatorio ? 'bg-amber-50/60 border-amber-200' : 'bg-gray-50 border-gray-200'}`}>
                  <span className={`flex-shrink-0 w-8 h-8 rounded-lg flex items-center justify-center text-sm ${
                    cargado ? 'bg-green-50 text-green-600' : s.obligatorio ? 'bg-amber-100 text-amber-600' : 'bg-gray-100 text-gray-400'}`}>
                    {cargado ? '✓' : '!'}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-gray-800 truncate">{s.nombre}</p>
                    <p className="text-[11px] text-gray-400 truncate">
                      {cargado ? `${s.nombreArchivo} · ${formatoFecha(s.fechaCarga)}`
                        : s.obligatorio ? 'Obligatorio — no lo ha cargado' : 'No lo ha cargado'}
                      {!s.requerido && ' · adicional'}
                    </p>
                  </div>
                  {cargado && (
                    <button type="button" onClick={() => abrir(s)} disabled={descargando === s.idDocumento}
                      className="flex-shrink-0 text-xs font-medium text-sky-700 hover:text-sky-800 border border-sky-200 hover:bg-sky-50 disabled:opacity-60 px-3 py-1.5 rounded-lg">
                      {descargando === s.idDocumento ? 'Abriendo…' : 'Ver'}
                    </button>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </section>
    )
  }

  return (
    <div className="space-y-4">
      <section className="bg-white border border-gray-200 rounded-2xl shadow-sm p-4">
        <div className="flex items-start justify-between gap-3">
          <Encabezado icono={<IconoPersona />} titulo="Datos del candidato" detalle="Tomados de su registro y de la hoja de vida que diligenció" />
          {totalVacios > 0 && (
            <button type="button" onClick={() => setVerVacios(v => !v)}
              className="flex-shrink-0 text-[11px] font-medium text-gray-500 hover:text-gray-700">
              {verVacios ? 'Ocultar vacíos' : `Ver vacíos (${totalVacios})`}
            </button>
          )}
        </div>
        {grupos.length === 0 ? (
          <p className="text-xs text-gray-400 bg-gray-50 rounded-xl px-4 py-6 text-center">El candidato todavía no tiene datos registrados.</p>
        ) : (
          <div className="space-y-4">
            {grupos.map(g => (
              <div key={g.nombre}>
                <p className="text-[11px] font-semibold text-gray-400 mb-1.5">{g.nombre}</p>
                <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-5 gap-y-2 bg-gray-50/70 rounded-xl px-3.5 py-3">
                  {g.campos.map(c => (
                    <div key={c.clave} className="min-w-0">
                      <dt className="text-[11px] text-gray-400">{c.etiqueta}</dt>
                      <dd className={`text-sm truncate ${c.valor ? 'font-medium text-gray-800' : 'text-gray-300'}`} title={c.valor || undefined}>
                        {c.valor || 'Sin dato'}
                      </dd>
                    </div>
                  ))}
                </dl>
              </div>
            ))}
          </div>
        )}
      </section>

    </div>
  )
}
