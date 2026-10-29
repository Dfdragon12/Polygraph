import { useState, useEffect, useCallback } from 'react'
import api from '../../../services/api'
import Toast from '../../../components/Toast'
import {
  EncabezadoPagina, Etiqueta, TarjetaSeccion, AlertaError, EsqueletoPagina, BarraCambios,
} from '../../../components/admin/PaginaAdmin'

// Parámetros globales del motor de calificación de riesgo (docs/modelo-calificacion-riesgo.md
// §2.4 y §2.5): umbrales de nivel por criticidad del cargo y reglas de piso. Los pesos por
// subproceso NO van aquí: se configuran en cada proceso del catálogo.

const ETIQUETA_CRITICIDAD = { ESTANDAR: 'Estándar', SENSIBLE: 'Sensible', CRITICO: 'Crítico' }
const AYUDA_CRITICIDAD = {
  ESTANDAR: 'Cargos operativos o de bajo acceso.',
  SENSIBLE: 'Cargos con manejo de información o dinero.',
  CRITICO: 'Cargos de alta confianza o seguridad: con menos puntos ya se llega a MEDIO/ALTO.',
}
const inputCls = 'w-20 text-right tabular-nums border border-gray-300 rounded-lg px-2.5 py-1.5 text-sm font-medium text-gray-900 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500'
// Acento de color por criticidad del cargo (estándar → crítico).
const ACENTO_CRITICIDAD = { ESTANDAR: 'bg-emerald-500', SENSIBLE: 'bg-amber-500', CRITICO: 'bg-red-500' }
const ICONO_ESCUDO = <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}><path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" /></svg>
const ICONO_BARRAS = <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" /></svg>
const ICONO_PISO = <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path strokeLinecap="round" strokeLinejoin="round" d="M3 4.5h14.25M3 9h9.75M3 13.5h9.75m4.5-4.5v12m0 0l-3.75-3.75M17.25 21L21 17.25" /></svg>

const num = (t) => {
  const n = Number(String(t).replace(',', '.'))
  return Number.isFinite(n) ? n : NaN
}
const aTexto = (v) => (v == null ? '' : String(Number(v)))

// Barra 0–100 con los tres rangos de nivel de una criticidad.
function EscalaNiveles({ bajo, medio }) {
  const b = Math.max(0, Math.min(100, num(bajo) || 0))
  const m = Math.max(b, Math.min(100, num(medio) || 0))
  return (
    <div>
      <div className="flex h-2.5 rounded-full overflow-hidden">
        <div className="bg-green-400" style={{ width: `${b}%` }} title={`BAJO: 0 – ${b}`} />
        <div className="bg-amber-400" style={{ width: `${m - b}%` }} title={`MEDIO: hasta ${m}`} />
        <div className="bg-red-400 flex-1" title={`ALTO: más de ${m}`} />
      </div>
      <div className="flex justify-between text-[10px] text-gray-400 mt-1">
        <span>0</span>
        <span className="text-green-700">BAJO ≤ {b}</span>
        <span className="text-amber-700">MEDIO ≤ {m}</span>
        <span className="text-red-600">ALTO &gt; {m}</span>
        <span>100</span>
      </div>
    </div>
  )
}

export default function ParametrosRiesgo() {
  const [datos, setDatos] = useState(null)
  const [umbrales, setUmbrales] = useState([])
  const [pisos, setPisos] = useState({ alto: '', moderado: '', cantidad: '' })
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [sucio, setSucio] = useState(false)
  const [error, setError] = useState(null)
  const [toast, setToast] = useState(null)

  const aplicar = (d) => {
    setDatos(d)
    setUmbrales(d.umbrales.map(u => ({ criticidad: u.criticidad, bajo: aTexto(u.limiteBajo), medio: aTexto(u.limiteMedio), minimo: aTexto(u.minimoMedio) })))
    setPisos({ alto: aTexto(d.umbralServicioAlto), moderado: aTexto(d.umbralServicioModerado), cantidad: aTexto(d.cantidadServiciosModerados) })
    setSucio(false)
  }

  const cargar = useCallback(async () => {
    setCargando(true)
    try {
      aplicar((await api.get('/catalogo/riesgo/parametros')).data)
    } catch { setError('No se pudieron cargar los parámetros de riesgo.') }
    finally { setCargando(false) }
  }, [])
  useEffect(() => { cargar() }, [cargar])

  const editarUmbral = (criticidad, campo, valor) => {
    setUmbrales(prev => prev.map(u => u.criticidad === criticidad ? { ...u, [campo]: valor.replace(/[^0-9.,]/g, '') } : u))
    setSucio(true)
  }
  const editarPiso = (campo, valor) => {
    setPisos(prev => ({ ...prev, [campo]: valor.replace(campo === 'cantidad' ? /[^0-9]/g : /[^0-9.,]/g, '') }))
    setSucio(true)
  }

  // Mismas reglas que el backend (ParametrosRiesgoService.validarUmbral), para avisar antes de guardar.
  const erroresUmbral = (u) => {
    const b = num(u.bajo), m = num(u.medio), mm = num(u.minimo)
    if ([b, m, mm].some(Number.isNaN) || [u.bajo, u.medio, u.minimo].some(v => v === '')) return 'Completa los tres valores.'
    if ([b, m, mm].some(v => v < 0 || v > 100)) return 'Los valores van de 0 a 100.'
    if (b >= m) return 'El límite de BAJO debe ser menor que el de MEDIO.'
    if (mm <= b || mm > m) return 'El mínimo de MEDIO debe ser mayor que el límite de BAJO y no pasar el de MEDIO.'
    return null
  }
  const hayErrores = umbrales.some(u => erroresUmbral(u)) || ['alto', 'moderado', 'cantidad'].some(k => pisos[k] === '' || Number.isNaN(num(pisos[k])))

  const guardar = async () => {
    setGuardando(true); setError(null)
    try {
      const { data } = await api.put('/catalogo/riesgo/parametros', {
        umbrales: umbrales.map(u => ({ criticidad: u.criticidad, limiteBajo: num(u.bajo), limiteMedio: num(u.medio), minimoMedio: num(u.minimo) })),
        umbralServicioAlto: num(pisos.alto),
        umbralServicioModerado: num(pisos.moderado),
        cantidadServiciosModerados: num(pisos.cantidad),
      })
      aplicar(data)
      setToast({ mensaje: `Parámetros guardados (versión ${data.version})`, tipo: 'exito' })
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudieron guardar los parámetros.')
    } finally { setGuardando(false) }
  }

  return (
    <div className="max-w-5xl">
      <EncabezadoPagina
        seccion="Calificación de riesgo"
        titulo="Parámetros de riesgo"
        icono={ICONO_ESCUDO}
        descripcion="Umbrales de nivel por criticidad del cargo y reglas de piso del motor de calificación. Los pesos de cada subproceso se configuran en el catálogo, dentro de cada proceso."
        meta={datos && <>
          <Etiqueta tono="primario">Versión {datos.version}</Etiqueta>
          {datos.actualizadoPorNombre && <Etiqueta>Editado por {datos.actualizadoPorNombre}</Etiqueta>}
          {datos.fechaActualizacion && <Etiqueta>{new Date(datos.fechaActualizacion).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' })}</Etiqueta>}
        </>}
      />

      <AlertaError mensaje={error} onCerrar={() => setError(null)} />

      {cargando ? (
        <EsqueletoPagina indicadores={0} bloques={2} />
      ) : (
        <div className="space-y-5">
          <TarjetaSeccion
            icono={ICONO_BARRAS}
            titulo="Umbrales de nivel por criticidad"
            descripcion={'BAJO si el puntaje es menor o igual al límite de BAJO; MEDIO si es menor o igual al de MEDIO; ALTO si es mayor. El "mínimo de MEDIO" es el puntaje al que sube el resultado cuando aplica una regla de piso.'}
            sinRelleno>
            <div className="divide-y divide-gray-100">
              {umbrales.map(u => {
                const err = erroresUmbral(u)
                return (
                  <div key={u.criticidad} className="relative px-5 py-5 grid grid-cols-1 lg:grid-cols-[210px_auto_minmax(0,1fr)] gap-5 items-center">
                    <span className={`absolute left-0 top-4 bottom-4 w-1 rounded-r-full ${ACENTO_CRITICIDAD[u.criticidad] ?? 'bg-gray-300'}`} />
                    <div>
                      <p className="text-sm font-semibold text-gray-900">{ETIQUETA_CRITICIDAD[u.criticidad] ?? u.criticidad}</p>
                      <p className="mt-0.5 text-xs leading-relaxed text-gray-500">{AYUDA_CRITICIDAD[u.criticidad]}</p>
                    </div>
                    <div className="flex flex-wrap items-end gap-3">
                      {[['bajo', 'Límite BAJO'], ['medio', 'Límite MEDIO'], ['minimo', 'Mínimo MEDIO']].map(([campo, etiqueta]) => (
                        <label key={campo} className="block">
                          <span className="block text-[11px] font-medium text-gray-500 mb-1">{etiqueta}</span>
                          <input value={u[campo]} onChange={e => editarUmbral(u.criticidad, campo, e.target.value)} inputMode="decimal"
                            className={`${inputCls} ${err ? 'border-red-300 focus:ring-red-400' : ''}`} />
                        </label>
                      ))}
                    </div>
                    <div>
                      <EscalaNiveles bajo={u.bajo} medio={u.medio} />
                      {err && <p className="text-xs text-red-600 mt-1.5">{err}</p>}
                    </div>
                  </div>
                )
              })}
            </div>
          </TarjetaSeccion>

          <TarjetaSeccion
            icono={ICONO_PISO}
            titulo="Reglas de piso"
            descripcion="Evitan que un resultado muy malo en un subproceso quede escondido en el promedio. El piso solo sube de BAJO a MEDIO — nunca a ALTO — y no cambia nada si el nivel ya es MEDIO o ALTO.">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="rounded-xl border border-gray-200 bg-gradient-to-b from-gray-50 to-white p-4">
                <div className="flex items-center gap-2">
                  <span className="inline-flex h-6 w-6 items-center justify-center rounded-md bg-primary-600 text-[11px] font-bold text-white">A</span>
                  <p className="text-sm font-semibold text-gray-800">Un subproceso muy alto</p>
                </div>
                <p className="text-sm text-gray-600 mt-3 flex flex-wrap items-center gap-1.5">
                  Si algún subproceso tiene
                  <input aria-label="Puntaje mínimo del piso alto" value={pisos.alto} onChange={e => editarPiso('alto', e.target.value)} inputMode="decimal" className={inputCls} />
                  puntos o más.
                </p>
              </div>
              <div className="rounded-xl border border-gray-200 bg-gradient-to-b from-gray-50 to-white p-4">
                <div className="flex items-center gap-2">
                  <span className="inline-flex h-6 w-6 items-center justify-center rounded-md bg-primary-600 text-[11px] font-bold text-white">B</span>
                  <p className="text-sm font-semibold text-gray-800">Riesgo repartido</p>
                </div>
                <p className="text-sm text-gray-600 mt-3 flex flex-wrap items-center gap-1.5">
                  Si
                  <input aria-label="Cantidad de subprocesos" value={pisos.cantidad} onChange={e => editarPiso('cantidad', e.target.value)} inputMode="numeric"
                    className="w-14 text-right tabular-nums border border-gray-300 rounded-lg px-2.5 py-1.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-primary-500" />
                  o más subprocesos tienen más de
                  <input aria-label="Puntaje mínimo del piso moderado" value={pisos.moderado} onChange={e => editarPiso('moderado', e.target.value)} inputMode="decimal" className={inputCls} />
                  puntos.
                </p>
              </div>
            </div>
          </TarjetaSeccion>

          {!sucio && (
            <p className="text-center text-xs text-gray-400">
              Al guardar se crea una versión nueva. Los resultados ya bloqueados (servicios publicados) no se recalculan.
            </p>
          )}
        </div>
      )}

      <BarraCambios
        visible={sucio}
        mensaje={hayErrores ? 'Hay valores por corregir antes de guardar' : 'Cambios sin guardar — se creará una nueva versión'}
        onDescartar={() => datos && aplicar(datos)}
        onGuardar={guardar}
        guardando={guardando}
        deshabilitado={hayErrores}
        textoGuardar="Guardar parámetros" />

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
