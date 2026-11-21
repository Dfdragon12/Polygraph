import { useId, useState } from 'react'
import api from '../../../services/api'
import { Modal } from '../../../components/ui/Modal'

const ETIQUETAS = {
  tituloComercial: 'Título comercial', frasePrincipal: 'Frase principal', descripcionCorta: 'Qué es', incluye: 'Qué incluye',
  idealPara: 'Ideal para', unidadSingular: 'Unidad (singular)', unidadPlural: 'Unidad (plural)', costoDirecto: 'Costo directo',
  diasHabilesEntrega: 'Días hábiles de entrega', visibleEnTienda: 'Visible en la tienda', aplicaIva: 'Aplica IVA',
  ofrecerPospago: 'Ofrecer en pospago', precioPospagoPropio: 'Precio pospago propio', recargoIntermedia: 'Recargo ciudad intermedia',
  recargoMunicipio: 'Recargo municipio secundario', aplicaPrecioCiudad: 'Aplica precio por ciudad', beneficios: 'Beneficios',
}

const etiqueta = (campo) => ETIQUETAS[campo] ?? campo

function TarjetaServicio({ s }) {
  return (
    <div className={`rounded-xl border p-3 ${s.errores.length ? 'border-red-300 bg-red-50/40' : 'border-gray-200 bg-white'}`}>
      <p className="text-sm font-semibold text-gray-800">
        #{s.idProceso ?? '?'} · {s.nombreActual ?? s.nombreProceso ?? 'Sin nombre'}
        {!s.errores.length && s.cambios.length === 0 && <span className="ml-2 text-xs font-normal text-gray-400">sin cambios</span>}
      </p>
      {s.errores.map((e, i) => <p key={i} className="text-xs text-red-700 mt-1">✕ {e}</p>)}
      {s.advertencias.map((a, i) => <p key={i} className="text-xs text-amber-700 mt-1">⚠ {a}</p>)}
      {s.cambios.length > 0 && (
        <table className="w-full text-xs mt-2">
          <thead>
            <tr className="text-left text-[11px] uppercase tracking-wider text-gray-400">
              <th className="py-1 pr-2 font-semibold w-1/4">Campo</th><th className="py-1 pr-2 font-semibold">Antes</th><th className="py-1 font-semibold">Después</th>
            </tr>
          </thead>
          <tbody>
            {s.cambios.map((c, i) => (
              <tr key={i} className="border-t border-gray-100 align-top">
                <td className="py-1 pr-2 text-gray-600">{etiqueta(c.campo)}</td>
                <td className="py-1 pr-2 text-gray-400 break-words">{c.antes ?? '—'}</td>
                <td className="py-1 text-gray-800 font-medium break-words">{c.despues ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

/**
 * Importar precios y textos (TP-05): pega o carga el JSON de la página «Precios del catálogo Polygraph», revisa qué cambiaría
 * (simulación, no guarda nada) y solo entonces aplica. Si un servicio falla no se importa ninguno.
 */
export default function ImportarPrecios({ onClose, onImportado }) {
  const idTexto = useId()
  const [texto, setTexto] = useState('')
  const [simulado, setSimulado] = useState(null)         // { texto, resultado }
  const [trabajando, setTrabajando] = useState(false)
  const [error, setError] = useState(null)

  const leerArchivo = async (e) => {
    const f = e.target.files?.[0]
    if (f) { setTexto(await f.text()); setSimulado(null); setError(null) }
  }

  const enviar = async (modo) => {
    let cuerpo
    try { cuerpo = JSON.parse(texto) } catch { setError('El texto no es un JSON válido.'); return }
    setTrabajando(true); setError(null)
    try {
      const { data } = await api.post(`/admin/catalogo/importar-precios?modo=${modo}`, cuerpo)
      if (modo === 'aplicar') { onImportado?.(`Importación aplicada: ${data.serviciosConCambios} servicio(s) actualizado(s)`); onClose(); return }
      setSimulado({ texto, resultado: data })
    } catch (err) {
      const data = err.response?.data
      if (data?.servicios) setSimulado({ texto, resultado: data })   // aplicar con errores (422): no se importó nada
      else setError(data?.mensaje ?? 'No se pudo procesar el JSON.')
    } finally { setTrabajando(false) }
  }

  const r = simulado?.resultado
  const vigente = simulado && simulado.texto === texto        // el JSON no cambió desde la simulación
  const puedeAplicar = r && vigente && r.serviciosConErrores === 0 && r.serviciosConCambios > 0

  return (
    <Modal titulo="Importar precios (JSON)" subtitulo="Desde la página «Precios del catálogo Polygraph»" ancho="max-w-4xl" onClose={trabajando ? undefined : onClose}>
      <div className="p-6 space-y-4">
        <div>
          <div className="flex items-center justify-between mb-1">
            <label htmlFor={idTexto} className="text-xs font-medium text-gray-600">JSON de servicios aprobados</label>
            <label className="text-xs font-medium text-primary-600 hover:text-primary-700 cursor-pointer">
              Cargar archivo .json
              <input type="file" accept=".json,application/json" onChange={leerArchivo} className="sr-only" />
            </label>
          </div>
          <textarea id={idTexto} rows={7} value={texto} onChange={(e) => { setTexto(e.target.value); setError(null) }}
            placeholder={'{ "servicios": [ { "idProceso": 1, "nombreProceso": "Antecedentes", … } ] }'}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-primary-500" />
          <p className="text-[11px] text-gray-400 mt-1">
            Solo actualiza servicios que ya existen (se emparejan por idProceso) y nunca crea nuevos. Lo que no viene en el JSON no se toca.
          </p>
        </div>

        {error && <div role="alert" className="bg-red-50 border border-red-200 text-red-700 text-sm px-3 py-2 rounded-lg">{error}</div>}

        {r && (
          <div className="space-y-3">
            <div className="flex flex-wrap gap-2 text-xs">
              <span className="px-2.5 py-1 rounded-full bg-gray-100 text-gray-700">{r.totalServicios} servicio{r.totalServicios !== 1 ? 's' : ''}</span>
              <span className="px-2.5 py-1 rounded-full bg-green-100 text-green-700">{r.serviciosConCambios} con cambios</span>
              {r.serviciosConErrores > 0 && <span className="px-2.5 py-1 rounded-full bg-red-100 text-red-700">{r.serviciosConErrores} con errores</span>}
            </div>
            {r.serviciosConErrores > 0 && (
              <p className="text-sm text-red-700">Hay servicios con errores: no se importa ninguno hasta corregirlos.</p>
            )}
            {!vigente && <p className="text-sm text-amber-700">El JSON cambió desde la simulación: vuelve a simular antes de aplicar.</p>}
            <div className="space-y-2 max-h-[45vh] overflow-y-auto pr-1">
              {r.servicios.map((s, i) => <TarjetaServicio key={`${s.idProceso}-${i}`} s={s} />)}
            </div>
          </div>
        )}

        <div className="flex justify-end gap-3 pt-1">
          <button type="button" onClick={onClose} disabled={trabajando} className="px-4 py-2 text-sm text-gray-600 hover:text-gray-800">Cancelar</button>
          <button type="button" onClick={() => enviar('simulacion')} disabled={trabajando || !texto.trim()}
            className="px-4 py-2 text-sm font-medium rounded-lg border border-primary-600 text-primary-700 hover:bg-primary-50 disabled:opacity-50">
            {trabajando ? 'Procesando…' : 'Simular'}
          </button>
          <button type="button" onClick={() => enviar('aplicar')} disabled={trabajando || !puedeAplicar}
            className="bg-primary-600 hover:bg-primary-700 disabled:opacity-50 text-white text-sm font-medium px-5 py-2 rounded-lg">
            Aplicar importación
          </button>
        </div>
      </div>
    </Modal>
  )
}
