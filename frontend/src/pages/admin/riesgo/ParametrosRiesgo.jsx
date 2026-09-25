import { useState, useEffect, useCallback } from 'react'
import api from '../../../services/api'
import Toast from '../../../components/Toast'

// Parámetros globales del motor de calificación de riesgo (docs/modelo-calificacion-riesgo.md
// §2.4 y §2.5): umbrales de nivel por criticidad del cargo y reglas de piso. Los pesos por
// subproceso NO van aquí: se configuran en cada proceso del catálogo.

const ETIQUETA_CRITICIDAD = { ESTANDAR: 'Estándar', SENSIBLE: 'Sensible', CRITICO: 'Crítico' }
const AYUDA_CRITICIDAD = {
  ESTANDAR: 'Cargos operativos o de bajo acceso.',
  SENSIBLE: 'Cargos con manejo de información o dinero.',
  CRITICO: 'Cargos de alta confianza o seguridad: con menos puntos ya se llega a MEDIO/ALTO.',
}
const inputCls = 'w-20 text-right border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500'

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
      <div className="flex items-start justify-between mb-5 flex-wrap gap-3">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Parámetros de riesgo</h2>
          <p className="text-sm text-gray-500 mt-1 max-w-2xl">
            Umbrales de nivel por criticidad del cargo y reglas de piso del motor de calificación. Los pesos de cada
            subproceso se configuran en el catálogo, dentro de cada proceso.
          </p>
        </div>
        {datos && (
          <p className="text-xs text-gray-400 text-right">
            Versión {datos.version}
            {datos.actualizadoPorNombre && <><br />Última edición: {datos.actualizadoPorNombre}</>}
            {datos.fechaActualizacion && <> · {new Date(datos.fechaActualizacion).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' })}</>}
          </p>
        )}
      </div>

      {error && (
        <div className="mb-4 bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-lg flex items-center justify-between">
          {error}<button onClick={() => setError(null)} className="text-red-400 hover:text-red-600 ml-4">×</button>
        </div>
      )}

      {cargando ? (
        <div className="flex justify-center py-16"><div className="animate-spin rounded-full h-8 w-8 border-4 border-primary-600 border-t-transparent" /></div>
      ) : (
        <div className="space-y-5">
          {/* Umbrales */}
          <section className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
            <header className="px-5 py-4 border-b border-gray-100">
              <h3 className="text-sm font-semibold text-gray-900">Umbrales de nivel por criticidad</h3>
              <p className="text-xs text-gray-500 mt-0.5">
                BAJO si el puntaje es menor o igual al límite de BAJO; MEDIO si es menor o igual al de MEDIO; ALTO si es mayor.
                El "mínimo de MEDIO" es el puntaje al que sube el resultado cuando aplica una regla de piso.
              </p>
            </header>
            <div className="divide-y divide-gray-100">
              {umbrales.map(u => {
                const err = erroresUmbral(u)
                return (
                  <div key={u.criticidad} className="px-5 py-4 grid grid-cols-1 lg:grid-cols-[200px_auto_minmax(0,1fr)] gap-4 items-center">
                    <div>
                      <p className="text-sm font-semibold text-gray-800">{ETIQUETA_CRITICIDAD[u.criticidad] ?? u.criticidad}</p>
                      <p className="text-[11px] text-gray-400">{AYUDA_CRITICIDAD[u.criticidad]}</p>
                    </div>
                    <div className="flex flex-wrap items-end gap-3">
                      {[['bajo', 'Límite BAJO'], ['medio', 'Límite MEDIO'], ['minimo', 'Mínimo MEDIO']].map(([campo, etiqueta]) => (
                        <label key={campo} className="block">
                          <span className="block text-[11px] text-gray-500 mb-0.5">{etiqueta}</span>
                          <input value={u[campo]} onChange={e => editarUmbral(u.criticidad, campo, e.target.value)} inputMode="decimal" className={inputCls} />
                        </label>
                      ))}
                    </div>
                    <div>
                      <EscalaNiveles bajo={u.bajo} medio={u.medio} />
                      {err && <p className="text-[11px] text-red-600 mt-1">{err}</p>}
                    </div>
                  </div>
                )
              })}
            </div>
          </section>

          {/* Pisos */}
          <section className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
            <header className="px-5 py-4 border-b border-gray-100">
              <h3 className="text-sm font-semibold text-gray-900">Reglas de piso</h3>
              <p className="text-xs text-gray-500 mt-0.5">
                Evitan que un resultado muy malo en un subproceso quede escondido en el promedio. El piso solo sube de BAJO a
                MEDIO — nunca a ALTO — y no cambia nada si el nivel ya es MEDIO o ALTO.
              </p>
            </header>
            <div className="px-5 py-4 grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="rounded-xl bg-gray-50 border border-gray-100 p-4">
                <p className="text-xs font-semibold text-gray-700">Regla A · un subproceso muy alto</p>
                <p className="text-sm text-gray-600 mt-2 flex flex-wrap items-center gap-1.5">
                  Si algún subproceso tiene
                  <input value={pisos.alto} onChange={e => editarPiso('alto', e.target.value)} inputMode="decimal" className={inputCls} />
                  puntos o más.
                </p>
              </div>
              <div className="rounded-xl bg-gray-50 border border-gray-100 p-4">
                <p className="text-xs font-semibold text-gray-700">Regla B · riesgo repartido</p>
                <p className="text-sm text-gray-600 mt-2 flex flex-wrap items-center gap-1.5">
                  Si
                  <input value={pisos.cantidad} onChange={e => editarPiso('cantidad', e.target.value)} inputMode="numeric" className="w-14 text-right border border-gray-200 rounded-lg px-2.5 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
                  o más subprocesos tienen más de
                  <input value={pisos.moderado} onChange={e => editarPiso('moderado', e.target.value)} inputMode="decimal" className={inputCls} />
                  puntos.
                </p>
              </div>
            </div>
          </section>

          <div className="flex items-center justify-end gap-3">
            {sucio && <span className="text-xs text-amber-600 mr-auto">Cambios sin guardar — al guardar se crea una nueva versión. Los resultados ya bloqueados no se recalculan.</span>}
            <button type="button" onClick={() => datos && aplicar(datos)} disabled={!sucio || guardando}
              className="px-4 py-2 text-sm text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50">
              Descartar
            </button>
            <button type="button" onClick={guardar} disabled={!sucio || guardando || hayErrores}
              className="px-5 py-2 text-sm font-medium text-white bg-primary-600 rounded-lg hover:bg-primary-700 disabled:opacity-50">
              {guardando ? 'Guardando…' : 'Guardar parámetros'}
            </button>
          </div>
        </div>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
