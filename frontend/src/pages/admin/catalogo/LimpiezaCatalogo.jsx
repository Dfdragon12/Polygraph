import { useEffect, useId, useState } from 'react'
import api from '../../../services/api'
import { Modal } from '../../../components/ui/Modal'

/**
 * TP-10 · Limpieza del catálogo actual. Muestra lo que se haría (sin guardar nada) y el reporte de servicios sin precio base o sin textos.
 * Solo se aplica escribiendo «CONFIRMO», y solo conviene después de importar los precios nuevos.
 */
const PASOS = {
  ARCHIVAR_PRECIOS_CIUDAD: { titulo: 'Archivar recargos por ciudad que no aplican', ayuda: 'Antecedentes y Análisis Financiero. Se archivan (no se borran).' },
  ACTIVAR_PRECIO_CIUDAD: { titulo: 'Encender «aplica precio por ciudad»', ayuda: 'Pre Empleo, Avanzado, Poligrafía + Antecedentes y + Visita, solo si ya tienen los recargos importados.' },
  UNIFICAR_DIAS: { titulo: 'Unificar días hábiles con los textos', ayuda: 'La ficha toma lo que dicen los textos comerciales (p. ej. «12 horas» = 1 día hábil).' },
}

function Lista({ titulo, filas, vacio }) {
  return (
    <div>
      <p className="text-xs font-semibold uppercase tracking-wider text-gray-600 mb-1">{titulo} ({filas.length})</p>
      {filas.length === 0 ? <p className="text-xs text-gray-500">{vacio}</p> : (
        <ul className="space-y-1">
          {filas.map((f) => (
            <li key={f.idProceso} className="text-xs text-gray-700"><span className="font-medium">#{f.idProceso} · {f.servicio}</span> — {f.detalle}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default function LimpiezaCatalogo({ onClose, onAplicada }) {
  const idConfirmar = useId()
  const [plan, setPlan] = useState(null)
  const [error, setError] = useState(null)
  const [elegidos, setElegidos] = useState(new Set(Object.keys(PASOS)))
  const [confirmacion, setConfirmacion] = useState('')
  const [aplicando, setAplicando] = useState(false)

  const cargar = () => {
    setError(null)
    api.get('/admin/catalogo/limpieza').then((r) => setPlan(r.data)).catch(() => setError('No se pudo calcular la limpieza.'))
  }
  useEffect(cargar, [])

  const alternar = (paso) => setElegidos((prev) => {
    const n = new Set(prev)
    if (n.has(paso)) n.delete(paso); else n.add(paso)
    return n
  })

  const aplicar = async () => {
    setAplicando(true); setError(null)
    try {
      await api.post('/admin/catalogo/limpieza/aplicar', { confirmacion, pasos: [...elegidos] })
      onAplicada('Limpieza del catálogo aplicada')
      onClose()
    } catch (err) {
      setError(err.response?.data?.mensaje ?? 'No se pudo aplicar la limpieza.')
    } finally { setAplicando(false) }
  }

  const accionesDe = (paso) => (plan?.acciones ?? []).filter((a) => a.paso === paso)
  const hayAlgo = (plan?.acciones ?? []).some((a) => elegidos.has(a.paso))

  return (
    <Modal titulo="Limpieza del catálogo" ancho="max-w-3xl" onClose={onClose}>
      <div className="space-y-5">
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
          Hazla solo <strong>después de importar los precios nuevos</strong>. Aquí ves lo que cambiaría; no se guarda nada hasta que escribas CONFIRMO.
        </p>
        {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
        {!plan && !error && <p className="text-sm text-gray-500">Calculando…</p>}

        {plan && (
          <>
            {Object.entries(PASOS).map(([paso, info]) => {
              const acciones = accionesDe(paso)
              return (
                <fieldset key={paso} className="rounded-xl border border-gray-200 p-3">
                  <label className="flex items-start gap-2 cursor-pointer">
                    <input type="checkbox" checked={elegidos.has(paso)} onChange={() => alternar(paso)} disabled={acciones.length === 0}
                      className="mt-0.5 w-4 h-4 rounded border-gray-300 text-primary-600" />
                    <span>
                      <span className="text-sm font-semibold text-gray-800">{info.titulo}</span>
                      <span className="block text-xs text-gray-500">{info.ayuda}</span>
                    </span>
                  </label>
                  {acciones.length === 0 ? <p className="mt-2 text-xs text-gray-500">Nada por hacer.</p> : (
                    <ul className="mt-2 space-y-1">
                      {acciones.map((a, i) => <li key={i} className="text-xs text-gray-700"><span className="font-medium">#{a.idProceso} · {a.servicio}</span>: {a.descripcion}</li>)}
                    </ul>
                  )}
                </fieldset>
              )
            })}

            {plan.advertencias.length > 0 && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2">
                {plan.advertencias.map((a, i) => <p key={i} className="text-xs text-amber-800">⚠ {a}</p>)}
              </div>
            )}

            <div className="rounded-xl border border-gray-200 p-3 space-y-3">
              <p className="text-sm font-semibold text-gray-800">Reporte (solo informa, no oculta ni cambia nada)</p>
              <Lista titulo="Activos sin precio base" filas={plan.sinPrecioBase} vacio="Todos tienen precio base." />
              <Lista titulo="Activos sin textos comerciales" filas={plan.sinTextos} vacio="Todos tienen textos comerciales." />
            </div>

            <div>
              <label htmlFor={idConfirmar} className="block text-xs font-semibold text-gray-700 mb-1">Para aplicar, escribe CONFIRMO</label>
              <input id={idConfirmar} value={confirmacion} onChange={(e) => setConfirmacion(e.target.value)} autoComplete="off"
                className="w-48 border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500" />
            </div>
          </>
        )}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-50">Cerrar</button>
          <button type="button" onClick={aplicar} disabled={aplicando || confirmacion.trim() !== 'CONFIRMO' || !hayAlgo}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white">
            {aplicando ? 'Aplicando…' : 'Aplicar limpieza'}
          </button>
        </div>
      </div>
    </Modal>
  )
}
