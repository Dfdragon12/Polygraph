import { useId, useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import tiempoService from '../../services/tiempoService'
import Toast from '../../components/Toast'

// Parámetros del cálculo de tiempos (solo administrador). Los plazos y el retraso se cuentan en días hábiles de Colombia; aquí se ajusta cuándo
// se considera recibida una solicitud (hora de corte), cuándo está "por vencer" y a qué fecha se mide una reprogramación por causa de Polygraph.

const CAMPOS = [
  { clave: 'hora_corte', titulo: 'Hora de corte', tipo: 'time',
    ayuda: 'Una solicitud recibida después de esta hora (o en un día no hábil) cuenta como recibida el siguiente día hábil. Por defecto 16:00.' },
  { clave: 'umbral_por_vencer_dias', titulo: 'Umbral de "por vencer" (días hábiles)', tipo: 'number',
    ayuda: 'Una solicitud está "por vencer" cuando le quedan este número de días hábiles o menos. Con 1: vence hoy o el siguiente día hábil.' },
  { clave: 'zona_horaria', titulo: 'Zona horaria', tipo: 'text',
    ayuda: 'Con ella se cuentan los días y la hora de corte, sin importar dónde esté el servidor. Por defecto America/Bogota.' },
  { clave: 'reprogramacion_polygraph_mide', titulo: 'Reprogramación por causa de Polygraph', tipo: 'select',
    opciones: [['ORIGINAL', 'Se mide contra la fecha ORIGINAL'], ['VIGENTE', 'Se mide contra la fecha NUEVA']],
    ayuda: 'Si la causa es del cliente o del candidato, el cumplimiento siempre se mide contra la fecha nueva. (Regla por confirmar con gerencia.)' },
]

export default function ParametrosTiempo() {
  const idCampos = useId()
  const [valores, setValores] = useState({})
  const [originales, setOriginales] = useState({})
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)
  const [toast, setToast] = useState(null)

  const cargar = useCallback(() => {
    setCargando(true)
    tiempoService.parametros()
      .then(lista => {
        const v = Object.fromEntries(lista.map(p => [p.clave, p.valor]))
        setValores(v)
        setOriginales(v)
      })
      .catch(() => setToast({ mensaje: 'No se pudieron cargar los parámetros.', tipo: 'error' }))
      .finally(() => setCargando(false))
  }, [])
  useEffect(() => { cargar() }, [cargar])

  const cambiados = CAMPOS.filter(c => (valores[c.clave] ?? '') !== (originales[c.clave] ?? ''))

  const guardar = async (e) => {
    e.preventDefault()
    if (cambiados.length === 0) return
    setGuardando(true)
    try {
      for (const c of cambiados) await tiempoService.cambiarParametro(c.clave, String(valores[c.clave]).trim())
      setToast({ mensaje: 'Parámetros guardados. Los nuevos cálculos los usan desde ya.', tipo: 'exito' })
      cargar()
    } catch (err) {
      setToast({ mensaje: err.response?.data?.message ?? err.response?.data?.mensaje ?? 'No se pudo guardar. Revisa los valores.', tipo: 'error' })
    } finally {
      setGuardando(false)
    }
  }

  return (
    <div className="space-y-5 max-w-3xl">
      <div>
        <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Parámetros de tiempo</h2>
        <p className="text-sm text-gray-600 mt-0.5">
          Cómo se cuentan los plazos de entrega y el retraso, en días hábiles de Colombia. Los festivos se administran en{' '}
          <Link to="/admin/festivos" className="font-medium text-primary-700 hover:underline">Festivos y días hábiles</Link>.
        </p>
      </div>

      {cargando ? (
        <div className="flex items-center justify-center h-40">
          <div className="animate-spin rounded-full h-7 w-7 border-4 border-primary-600 border-t-transparent" />
        </div>
      ) : (
        <form onSubmit={guardar} className="bg-white rounded-2xl ring-1 ring-gray-900/5 shadow-sm divide-y divide-gray-100">
          {CAMPOS.map((c, i) => {
            const id = `${idCampos}-${i}`
            const comun = { id, 'aria-describedby': `${id}-ayuda`, value: valores[c.clave] ?? '', onChange: (e) => setValores(v => ({ ...v, [c.clave]: e.target.value })),
              className: 'h-11 w-full max-w-xs rounded-lg border border-gray-200 px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500' }
            return (
              <div key={c.clave} className="p-5">
                <label htmlFor={id} className="block text-sm font-semibold text-gray-800">{c.titulo}</label>
                <div className="mt-2">
                  {c.tipo === 'select'
                    ? <select {...comun} className={`${comun.className} max-w-md`}>{c.opciones.map(([v, t]) => <option key={v} value={v}>{t}</option>)}</select>
                    : <input {...comun} type={c.tipo} {...(c.tipo === 'number' ? { min: 0, max: 10, step: 1 } : {})} required />}
                </div>
                <p id={`${id}-ayuda`} className="mt-1.5 text-xs text-gray-600">{c.ayuda}</p>
              </div>
            )
          })}
          <div className="flex items-center justify-end gap-3 p-5">
            {cambiados.length > 0 && <span className="text-xs text-gray-600">{cambiados.length} {cambiados.length === 1 ? 'cambio sin guardar' : 'cambios sin guardar'}</span>}
            <button type="button" onClick={() => setValores(originales)} disabled={cambiados.length === 0 || guardando}
              className="px-4 py-2 text-sm font-medium text-gray-700 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50">Descartar</button>
            <button type="submit" disabled={cambiados.length === 0 || guardando}
              className="px-4 py-2 text-sm font-semibold text-white bg-primary-600 rounded-lg shadow-sm hover:bg-primary-700 disabled:opacity-50 transition-colors">
              {guardando ? 'Guardando…' : 'Guardar'}
            </button>
          </div>
        </form>
      )}

      {toast && <Toast mensaje={toast.mensaje} tipo={toast.tipo} onClose={() => setToast(null)} />}
    </div>
  )
}
