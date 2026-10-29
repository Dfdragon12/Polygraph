import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import tiempoService from '../../services/tiempoService'
import EstadoTiempo, { NOMBRE_ESTADO_TIEMPO, COLOR_ESTADO_TIEMPO } from '../EstadoTiempo'
import Paginacion, { POR_PAGINA } from '../ui/Paginacion'

// Semáforo de TIEMPO del panel interno: tarjetas por estado de tiempo, matriz etapa × estado de tiempo (cada celda abre la lista de sus solicitudes),
// "las más atrasadas" y la lista. Todo sale del resumen ÚNICO de tiempos (GET /tiempos/resumen), el mismo que usan los demás tableros.

export const ESTADOS_TIEMPO = ['A_TIEMPO', 'POR_VENCER', 'VENCIDA', 'EN_PAUSA', 'ENTREGADA_A_TIEMPO', 'ENTREGADA_TARDE']
export const ETAPAS = [
  ['PENDIENTE', 'Pendiente'], ['PROGRAMANDO', 'Programando'], ['EN_EJECUCION', 'En ejecución'], ['REPROGRAMADO', 'Reprogramado'],
  ['FINALIZADO', 'Finalizado'], ['PUBLICADO', 'Publicado'], ['CANCELADO', 'Cancelado'],
]
export const NOMBRE_ETAPA = Object.fromEntries(ETAPAS)

const fechaCorta = (f) => f ? new Date(`${String(f).slice(0, 10)}T12:00:00`).toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' }) : '—'

/** Texto de lo que está seleccionado (para el título de la lista y el PDF). */
export function describirSeleccion(sel) {
  const partes = []
  if (sel.etapa) partes.push(NOMBRE_ETAPA[sel.etapa] ?? sel.etapa)
  if (sel.estados?.length) partes.push(sel.estados.map(e => NOMBRE_ESTADO_TIEMPO[e] ?? e).join(' + '))
  return partes.length ? partes.join(' · ') : 'Todas las solicitudes'
}

export default function SeccionTiempoSemaforo({ resumen, filtro, seleccion, onSeleccionar }) {
  const [pagina, setPagina] = useState(0)
  const [lista, setLista] = useState(null)
  const [cargandoLista, setCargandoLista] = useState(false)

  const clave = `${seleccion.etapa ?? ''}|${(seleccion.estados ?? []).join(',')}|${filtro.idCliente ?? ''}`
  useEffect(() => { setPagina(0) }, [clave])
  useEffect(() => {
    if (!seleccion.etapa && !(seleccion.estados?.length)) { setLista(null); return undefined }
    let vigente = true
    setCargandoLista(true)
    tiempoService.solicitudes({
      idCliente: filtro.idCliente || undefined, etapa: seleccion.etapa || undefined,
      estadoTiempo: seleccion.estados?.length ? seleccion.estados.join(',') : undefined, pagina, tamano: POR_PAGINA,
    }).then(r => { if (vigente) setLista(r) }).catch(() => { if (vigente) setLista(null) }).finally(() => { if (vigente) setCargandoLista(false) })
    return () => { vigente = false }
  }, [clave, pagina, filtro.idCliente]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!resumen) return null
  const seleccionado = (etapa, estados) => seleccion.etapa === etapa && (seleccion.estados ?? []).join(',') === (estados ?? []).join(',')
  const alternar = (etapa, estados) => onSeleccionar(seleccionado(etapa, estados) ? { etapa: null, estados: [] } : { etapa, estados })

  return (
    <section className="space-y-6" aria-label="Semáforo de tiempo">
      {/* Tarjetas por estado de tiempo */}
      <div>
        <h3 className="mb-3 text-sm font-semibold text-gray-800">Estado de tiempo <span className="font-normal text-gray-500">· días hábiles de Colombia</span></h3>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
          {ESTADOS_TIEMPO.map(e => {
            const activo = seleccionado(null, [e])
            return (
              <button key={e} type="button" onClick={() => alternar(null, [e])} aria-pressed={activo}
                className={`rounded-xl border bg-white p-4 text-left shadow-sm transition-shadow hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${activo ? 'ring-2 ring-primary-500' : 'border-gray-200'}`}>
                <span className="flex items-center gap-2 text-xs font-medium text-gray-700">
                  <span aria-hidden="true" className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: COLOR_ESTADO_TIEMPO[e] }} />{NOMBRE_ESTADO_TIEMPO[e]}
                </span>
                <span className="mt-1 block text-3xl font-bold tabular-nums text-gray-900">{resumen.porEstadoTiempo?.[e] ?? 0}</span>
              </button>
            )
          })}
        </div>
        {resumen.sinEstadoTiempo > 0 && (
          <p className="mt-2 text-xs text-gray-600">{resumen.sinEstadoTiempo} {resumen.sinEstadoTiempo === 1 ? 'solicitud cancelada no tiene' : 'solicitudes canceladas no tienen'} estado de tiempo.</p>
        )}
      </div>

      {/* Matriz etapa × estado de tiempo */}
      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-5 py-3">
          <h3 className="text-sm font-semibold text-gray-800">Etapa × estado de tiempo</h3>
          <p className="text-xs text-gray-600">Clic en un número para ver sus solicitudes.</p>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 text-left text-[11px] font-semibold uppercase tracking-wider text-gray-600">
                <th scope="col" className="px-4 py-2.5">Etapa</th>
                {ESTADOS_TIEMPO.map(e => <th key={e} scope="col" className="px-3 py-2.5 text-right">{NOMBRE_ESTADO_TIEMPO[e]}</th>)}
                <th scope="col" className="px-4 py-2.5 text-right">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {(resumen.matriz ?? []).map(fila => (
                <tr key={fila.etapa}>
                  <th scope="row" className="px-4 py-2 text-left font-medium text-gray-800">
                    <button type="button" onClick={() => alternar(fila.etapa, [])} className="hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500">
                      {NOMBRE_ETAPA[fila.etapa] ?? fila.etapa}
                    </button>
                  </th>
                  {ESTADOS_TIEMPO.map(e => {
                    const n = fila.porEstadoTiempo?.[e] ?? 0
                    const activo = seleccionado(fila.etapa, [e])
                    return (
                      <td key={e} className="px-3 py-2 text-right tabular-nums">
                        {n > 0 ? (
                          <button type="button" onClick={() => alternar(fila.etapa, [e])} aria-pressed={activo}
                            aria-label={`${NOMBRE_ETAPA[fila.etapa]}, ${NOMBRE_ESTADO_TIEMPO[e]}: ${n}`}
                            className={`min-w-[2.25rem] rounded-md px-2 py-1 font-semibold text-gray-900 hover:bg-gray-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500 ${activo ? 'bg-primary-50 ring-1 ring-primary-500' : ''}`}
                            style={{ borderBottom: `3px solid ${COLOR_ESTADO_TIEMPO[e]}` }}>
                            {n}
                          </button>
                        ) : <span className="text-gray-400">0</span>}
                      </td>
                    )
                  })}
                  <td className="px-4 py-2 text-right font-semibold tabular-nums text-gray-900">{fila.total}</td>
                </tr>
              ))}
              <tr className="bg-gray-50 font-semibold">
                <th scope="row" className="px-4 py-2 text-left text-gray-800">Total</th>
                {ESTADOS_TIEMPO.map(e => <td key={e} className="px-3 py-2 text-right tabular-nums text-gray-900">{resumen.porEstadoTiempo?.[e] ?? 0}</td>)}
                <td className="px-4 py-2 text-right tabular-nums text-gray-900">{resumen.total}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* Las más atrasadas */}
      <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div className="border-b border-gray-100 px-5 py-3">
          <h3 className="text-sm font-semibold text-gray-800">Las más atrasadas</h3>
          <p className="text-xs text-gray-600">Solicitudes vencidas con más días hábiles de retraso.</p>
        </div>
        {(resumen.masAtrasadas ?? []).length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-gray-600">No hay solicitudes vencidas.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="table-stack w-full text-sm">
              <thead>
                <tr className="bg-gray-50 text-left text-[11px] font-semibold uppercase tracking-wider text-gray-600">
                  <th scope="col" className="px-3 py-2.5">Evaluado</th><th scope="col" className="px-3 py-2.5">Cliente</th>
                  <th scope="col" className="px-3 py-2.5">Gestor</th><th scope="col" className="px-3 py-2.5">Etapa</th>
                  <th scope="col" className="px-3 py-2.5 text-right">Retraso</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {resumen.masAtrasadas.map(a => (
                  <tr key={a.idServicio}>
                    <td data-label="Evaluado" className="px-3 py-2.5"><span className="font-medium text-gray-900">{a.evaluado || '—'}</span><span className="block text-xs text-gray-600">#{a.idServicio}{a.proceso ? ` · ${a.proceso}` : ''}</span></td>
                    <td data-label="Cliente" className="px-3 py-2.5 text-gray-700">{a.cliente || '—'}</td>
                    <td data-label="Gestor" className="px-3 py-2.5 text-gray-700">{a.gestor || 'Sin gestor'}</td>
                    <td data-label="Etapa" className="px-3 py-2.5 text-gray-700">{NOMBRE_ETAPA[a.etapa] ?? a.etapa}</td>
                    <td data-label="Retraso" className="px-3 py-2.5 text-right"><span className="font-semibold tabular-nums text-red-800">{a.diasRetrasoHabiles} {a.diasRetrasoHabiles === 1 ? 'día hábil' : 'días hábiles'}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Lista de lo seleccionado */}
      {(seleccion.etapa || (seleccion.estados ?? []).length > 0) && (
        <div className="overflow-hidden rounded-xl border border-primary-200 bg-white shadow-sm">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 px-5 py-3">
            <div>
              <h3 className="text-sm font-semibold text-gray-800">Solicitudes: {describirSeleccion(seleccion)}</h3>
              <p className="text-xs text-gray-600">{lista ? `${lista.totalElements} ${lista.totalElements === 1 ? 'solicitud' : 'solicitudes'}` : cargandoLista ? 'Cargando…' : ''} · de la más atrasada a la menos</p>
            </div>
            <button type="button" onClick={() => onSeleccionar({ etapa: null, estados: [] })} className="text-xs font-medium text-primary-700 hover:underline">Quitar selección</button>
          </div>
          {!lista || lista.content.length === 0 ? (
            <p className="px-5 py-8 text-center text-sm text-gray-600">{cargandoLista ? 'Cargando…' : 'No hay solicitudes con esta selección.'}</p>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="table-stack w-full text-sm">
                  <thead>
                    <tr className="bg-gray-50 text-left text-[11px] font-semibold uppercase tracking-wider text-gray-600">
                      <th scope="col" className="px-3 py-2.5">Evaluado</th><th scope="col" className="px-3 py-2.5">Cliente</th>
                      <th scope="col" className="px-3 py-2.5">Gestor</th><th scope="col" className="px-3 py-2.5">Etapa</th>
                      <th scope="col" className="px-3 py-2.5">Tiempo</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-100">
                    {lista.content.map(f => (
                      <tr key={f.idServicio}>
                        <td data-label="Evaluado" className="px-3 py-2.5"><span className="font-medium text-gray-900">{f.evaluado || '—'}</span><span className="block text-xs text-gray-600">#{f.idServicio}{f.proceso ? ` · ${f.proceso}` : ''}</span></td>
                        <td data-label="Cliente" className="px-3 py-2.5 text-gray-700">{f.cliente || '—'}</td>
                        <td data-label="Gestor" className="px-3 py-2.5 text-gray-700">{f.gestor || 'Sin gestor'}</td>
                        <td data-label="Etapa" className="px-3 py-2.5 text-gray-700">{NOMBRE_ETAPA[f.etapa] ?? f.etapa}</td>
                        <td data-label="Tiempo" className="px-3 py-2.5"><EstadoTiempo tiempo={f.tiempo} />{f.tiempo?.fechaCompromisoAjustada && <span className="block text-[11px] text-gray-600">Compromiso {fechaCorta(f.tiempo.fechaCompromisoAjustada)}</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Paginacion pagina={pagina + 1} total={lista.totalElements} porPagina={POR_PAGINA} onChange={(p) => setPagina(p - 1)} />
            </>
          )}
        </div>
      )}
      <p className="text-xs text-gray-500">Los plazos se cuentan en días hábiles de Colombia. <Link to="/admin/festivos" className="font-medium text-primary-700 hover:underline">Festivos</Link> · <Link to="/admin/parametros-tiempo" className="font-medium text-primary-700 hover:underline">Parámetros</Link></p>
    </section>
  )
}
