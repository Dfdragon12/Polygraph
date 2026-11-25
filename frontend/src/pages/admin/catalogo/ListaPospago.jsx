import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import api from '../../../services/api'
import { ConfirmModal } from '../../../components/ui/Modal'
import { useConfigTienda } from '../../../hooks/useConfigTienda'
import { parseFecha } from '../../../utils/fechas'

/**
 * Pestaña «Lista pospago» (TP-11 / LP-04): una sola lista de precios para todos los clientes pospago. Cada servicio toma por defecto el precio por
 * unidad del plan de 100 (Automático); el administrador puede escribir otro (Manual), volver al plan, decidir si se ofrece y ver el historial.
 * El servidor calcula todo (precio efectivo, IVA, margen); aquí el «Con IVA» de una fila sin guardar es solo una vista previa.
 */

const FILTROS = [
  { id: 'todos', etiqueta: 'Todos', cumple: () => true },
  { id: 'manuales', etiqueta: 'Manuales', cumple: (f) => f.modo === 'MANUAL' },
  { id: 'noOfrecidos', etiqueta: 'No ofrecidos', cumple: (f) => !f.ofrecido },
  { id: 'margenBajo', etiqueta: 'Margen bajo', cumple: (f) => f.margenBajo },
  { id: 'sinPrecio', etiqueta: 'Sin precio', cumple: (f) => f.precio == null },
]

const numero = (v) => (v == null ? null : Math.round(Number(v)))
const pesos = (v) => (v == null ? '—' : `$ ${Math.round(Number(v)).toLocaleString('es-CO')}`)
const soloDigitos = (t) => String(t ?? '').replace(/\D/g, '')
const conMiles = (digitos) => (digitos ? Number(digitos).toLocaleString('es-CO') : '')
const origenTexto = (modo) => (modo === 'MANUAL' ? 'Manual' : 'Automático')
const fechaHora = (iso) => {
  const d = parseFecha(iso)
  return d ? d.toLocaleString('es-CO', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' }) : '—'
}

function ChipOrigen({ modo }) {
  const manual = modo === 'MANUAL'
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${manual ? 'bg-sky-100 text-sky-900' : 'bg-gray-100 text-gray-700'}`}>
      {origenTexto(modo)}
    </span>
  )
}

function Interruptor({ activo, onCambiar, deshabilitado, etiqueta, titulo }) {
  return (
    <button type="button" role="switch" aria-checked={activo} aria-label={etiqueta} title={titulo} disabled={deshabilitado} onClick={onCambiar}
      className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 disabled:cursor-not-allowed disabled:opacity-50 ${activo ? 'bg-primary-600' : 'bg-gray-300'}`}>
      <span className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${activo ? 'translate-x-6' : 'translate-x-1'}`} />
    </button>
  )
}

function textoCambio(h) {
  const a = pesos(h.precioAnterior)
  const n = pesos(h.precioNuevo)
  switch (h.accion) {
    case 'PRECIO_MANUAL': return `Precio manual: ${a} → ${n}`
    case 'VOLVER_AUTO': return `Volvió al plan de 100: ${a} → ${n}`
    case 'RESTABLECER_TODOS': return `Restablecido al plan de 100: ${a} → ${n}`
    case 'OFRECIDO_SI': return 'Ahora se ofrece en pospago'
    case 'OFRECIDO_NO': return 'Ya no se ofrece en pospago'
    default: return h.accion
  }
}

function PanelHistorial({ fila, onCerrar }) {
  const [items, setItems] = useState(null)
  const [error, setError] = useState(null)
  const cerrar = useRef(null)
  const titulo = useId()

  useEffect(() => {
    api.get(`/admin/catalogo/pospago/lista/${fila.idProceso}/historial`).then((r) => setItems(r.data))
      .catch((e) => setError(e.response?.data?.message || 'No se pudo cargar el historial.'))
  }, [fila.idProceso])

  useEffect(() => {
    cerrar.current?.focus()
    const tecla = (e) => { if (e.key === 'Escape') onCerrar() }
    window.addEventListener('keydown', tecla)
    return () => window.removeEventListener('keydown', tecla)
  }, [onCerrar])

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-black/40" onClick={onCerrar} aria-hidden="true" />
      <aside role="dialog" aria-modal="true" aria-labelledby={titulo} className="relative flex h-full w-full max-w-md flex-col bg-white shadow-xl">
        <div className="flex items-start justify-between gap-3 border-b border-gray-100 px-5 py-4">
          <div className="min-w-0">
            <h3 id={titulo} className="text-base font-semibold text-gray-900">Historial de precio</h3>
            <p className="truncate text-sm text-gray-500">{fila.servicio}</p>
          </div>
          <button ref={cerrar} type="button" onClick={onCerrar}
            className="min-h-[44px] rounded-lg border border-gray-200 px-4 text-sm text-gray-700 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600">
            Cerrar
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">
          {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
          {!error && !items && <p className="text-sm text-gray-500">Cargando…</p>}
          {items && items.length === 0 && <p className="text-sm text-gray-500">Este servicio todavía no tiene cambios en la lista pospago.</p>}
          {items && items.length > 0 && (
            <ol className="space-y-3">
              {items.map((h) => (
                <li key={h.id} className="rounded-lg border border-gray-100 p-3">
                  <p className="text-sm font-medium text-gray-900">{textoCambio(h)}</p>
                  <p className="mt-1 text-xs text-gray-500">{fechaHora(h.fecha)} · {h.usuario}</p>
                </li>
              ))}
            </ol>
          )}
        </div>
      </aside>
    </div>
  )
}

export default function ListaPospago({ onToast, onSinGuardar }) {
  const config = useConfigTienda()
  const [params, setParams] = useSearchParams()
  const filtro = FILTROS.some((f) => f.id === params.get('filtro')) ? params.get('filtro') : 'todos'

  const [filas, setFilas] = useState(null)
  const [error, setError] = useState(null)
  const [borradores, setBorradores] = useState({})       // idProceso -> dígitos escritos aún sin guardar
  const [errores, setErrores] = useState({})             // idProceso -> mensaje de la fila
  const [ocupados, setOcupados] = useState({})           // idProceso -> true mientras se guarda
  const [aviso, setAviso] = useState('')
  const [historial, setHistorial] = useState(null)
  const [confirmarVolver, setConfirmarVolver] = useState(null)
  const [confirmarRestablecer, setConfirmarRestablecer] = useState(false)
  const [restableciendo, setRestableciendo] = useState(false)
  const idBase = useId()

  const editable = config.pospagoPlan100
  const pct = Number(config.ivaPorcentaje || 0)

  const avisar = useCallback((mensaje, tipo = 'exito') => {
    setAviso(mensaje)
    if (onToast) onToast(mensaje, tipo)
  }, [onToast])

  useEffect(() => {
    api.get('/catalogo/pospago/lista').then((r) => setFilas(r.data)).catch(() => setError('No se pudo cargar la lista pospago.'))
  }, [])

  const sinGuardar = useMemo(() => (filas || []).filter((f) => borradores[f.idProceso] !== undefined
    && borradores[f.idProceso] !== String(numero(f.precio) ?? '')), [filas, borradores])

  useEffect(() => { onSinGuardar?.(sinGuardar.length) }, [sinGuardar.length, onSinGuardar])
  useEffect(() => () => onSinGuardar?.(0), [onSinGuardar])
  useEffect(() => {
    if (sinGuardar.length === 0) return undefined
    const antes = (e) => { e.preventDefault(); e.returnValue = '' }
    window.addEventListener('beforeunload', antes)
    return () => window.removeEventListener('beforeunload', antes)
  }, [sinGuardar.length])

  const reemplazar = (fila) => setFilas((prev) => prev.map((f) => (f.idProceso === fila.idProceso ? fila : f)))
  const quitarBorrador = (id) => setBorradores((b) => { const c = { ...b }; delete c[id]; return c })
  const ponerError = (id, msg) => setErrores((e) => ({ ...e, [id]: msg }))

  const llamar = async (f, cuerpo, mensajeOk) => {
    setOcupados((o) => ({ ...o, [f.idProceso]: true }))
    ponerError(f.idProceso, null)
    try {
      const r = await api.put(`/admin/catalogo/pospago/lista/${f.idProceso}`, cuerpo)
      reemplazar(r.data)
      quitarBorrador(f.idProceso)
      avisar(mensajeOk(r.data))
    } catch (e) {
      const msg = e.response?.data?.message || 'No se pudo guardar. Revisa tu conexión e inténtalo de nuevo.'
      ponerError(f.idProceso, msg)
      avisar(msg, 'error')
    } finally {
      setOcupados((o) => ({ ...o, [f.idProceso]: false }))
    }
  }

  const guardar = (f) => {
    const digitos = borradores[f.idProceso]
    const valor = Number(digitos || 0)
    if (!digitos || valor <= 0) { ponerError(f.idProceso, 'El precio debe ser mayor que 0'); return }
    const plan = numero(f.precioPlan100)
    const esPlan = plan != null && valor === plan          // escribir el mismo valor del plan de 100 = Automático
    llamar(f, { modo: esPlan ? 'AUTO' : 'MANUAL', precioManual: esPlan ? null : valor, ofrecido: f.ofrecido },
      (r) => `Precio guardado · ${r.servicio} ${pesos(r.precio)} (${origenTexto(r.modo)})`)
  }

  const volverAlPlan = (f) => {
    setConfirmarVolver(null)
    llamar(f, { modo: 'AUTO', precioManual: null, ofrecido: f.ofrecido },
      (r) => `Precio guardado · ${r.servicio} ${pesos(r.precio)} (Automático)`)
  }

  const cambiarOfrecido = (f) => llamar(f, { modo: f.modo, precioManual: f.modo === 'MANUAL' ? numero(f.precioManual) : null, ofrecido: !f.ofrecido },
    (r) => (r.ofrecido ? 'Ofrecido en pospago' : 'Ya no se ofrece en pospago'))

  const restablecer = async () => {
    setRestableciendo(true)
    try {
      const r = await api.post('/admin/catalogo/pospago/lista/restablecer')
      setFilas(r.data)
      setBorradores({})
      setConfirmarRestablecer(false)
      avisar('Todos los servicios volvieron al plan de 100')
    } catch (e) {
      setConfirmarRestablecer(false)
      avisar(e.response?.data?.message || 'No se pudo restablecer la lista.', 'error')
    } finally { setRestableciendo(false) }
  }

  const exportar = async () => {
    try {
      const r = await api.get('/catalogo/pospago/lista/exportar', { responseType: 'blob' })
      const url = URL.createObjectURL(r.data)
      const a = document.createElement('a')
      a.href = url; a.download = 'lista-pospago.xlsx'; a.click()
      URL.revokeObjectURL(url)
    } catch { avisar('No se pudo exportar la lista.', 'error') }
  }

  const elegirFiltro = (id) => {
    const p = new URLSearchParams(params)
    if (id === 'todos') p.delete('filtro'); else p.set('filtro', id)
    setParams(p, { replace: true })
  }

  if (error) return <p role="alert" className="text-sm text-red-700">{error}</p>
  if (!filas) return <p className="text-sm text-gray-500">Cargando…</p>

  const manuales = filas.filter((f) => f.modo === 'MANUAL')
  const visibles = filas.filter(FILTROS.find((f) => f.id === filtro).cumple)

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <p className="max-w-3xl text-sm text-gray-600">
          Una sola lista para todos los clientes pospago. Cada servicio toma por defecto el precio por unidad del plan de 100; puedes cambiarlo.
          La ven solo los clientes pospago.
        </p>
        <div className="flex flex-wrap items-center gap-2">
          <button type="button" onClick={() => setConfirmarRestablecer(true)} disabled={!editable || manuales.length === 0}
            title={!editable ? 'La lista de precios pospago está apagada' : manuales.length === 0 ? 'Ningún servicio está en Manual' : undefined}
            className="min-h-[44px] rounded-lg border border-gray-200 px-4 text-sm font-medium text-gray-700 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600 disabled:cursor-not-allowed disabled:opacity-50">
            Restablecer todo al plan de 100
          </button>
          <button type="button" onClick={exportar}
            className="min-h-[44px] rounded-lg border border-gray-200 px-4 text-sm font-medium text-gray-700 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600">
            Exportar a Excel
          </button>
        </div>
      </div>
      {editable && manuales.length === 0 && <p className="text-xs text-gray-500">Ningún servicio está en Manual.</p>}
      {!editable && config.cargada && (
        <p role="status" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
          La lista de precios pospago está apagada en Admin › Catálogo › Planes de la tienda. Aquí solo se puede consultar.
        </p>
      )}

      <div role="group" aria-label="Filtrar la lista" className="flex flex-wrap gap-2">
        {FILTROS.map((f) => (
          <button key={f.id} type="button" aria-pressed={filtro === f.id} onClick={() => elegirFiltro(f.id)}
            className={`min-h-[44px] rounded-full border px-4 text-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600 ${filtro === f.id ? 'border-primary-600 bg-primary-50 font-semibold text-primary-800' : 'border-gray-200 text-gray-700 hover:bg-gray-50'}`}>
            {f.etiqueta} <span className="text-gray-500">({filas.filter(f.cumple).length})</span>
          </button>
        ))}
      </div>

      {sinGuardar.length > 0 && (
        <p role="status" className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm font-medium text-amber-900">
          Tienes {sinGuardar.length} {sinGuardar.length === 1 ? 'precio sin guardar' : 'precios sin guardar'}
        </p>
      )}
      <p className="sr-only" role="status" aria-live="polite">{aviso}</p>

      <div className="rounded-xl border border-gray-100 bg-white shadow-sm">
        <table className="table-stack w-full text-sm">
          <thead className="bg-gray-50 text-xs uppercase text-gray-500">
            <tr>
              <th className="px-3 py-3 text-left">Servicio</th>
              <th className="hidden px-3 py-3 text-right xl:table-cell">Plan de 100</th>
              <th className="px-3 py-3 text-right">Precio pospago</th>
              <th className="px-3 py-3 text-left">Origen</th>
              <th className="px-3 py-3 text-right">Con IVA</th>
              <th className="px-3 py-3 text-right">Margen</th>
              <th className="px-3 py-3 text-left">Ofrecido</th>
              <th className="px-3 py-3 text-left">Acciones</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {visibles.length === 0 && (
              <tr><td colSpan={8} className="px-3 py-6 text-center text-gray-500">Ningún servicio cumple este filtro.</td></tr>
            )}
            {visibles.map((f) => {
              const id = f.idProceso
              const borrador = borradores[id]
              const editando = borrador !== undefined && borrador !== String(numero(f.precio) ?? '')
              const digitos = borrador !== undefined ? borrador : String(numero(f.precio) ?? '')
              const conIva = editando && Number(digitos) > 0
                ? Number(digitos) + (f.aplicaIva ? Math.round((Number(digitos) * pct) / 100) : 0)
                : f.precioConIva
              const campo = `${idBase}-p-${id}`
              const errorId = `${campo}-err`
              const ocupado = !!ocupados[id]
              return (
                <tr key={id} className={editando ? 'bg-amber-50/40' : ''}>
                  <td className="px-3 py-2.5 font-medium text-gray-800">
                    {f.servicio}
                    {f.precio == null && <span className="block text-xs font-normal text-gray-500">Sin precio</span>}
                    {editando && <span className="block text-xs font-semibold text-amber-800">Sin guardar</span>}
                  </td>
                  <td className="hidden px-3 py-2.5 text-right tabular-nums text-gray-500 xl:table-cell">{f.precioPlan100 != null ? pesos(f.precioPlan100) : 'Sin precio'}</td>
                  <td className="px-3 py-2.5 text-right">
                    <div className="flex flex-col items-end">
                      <label htmlFor={campo} className="sr-only">Precio pospago de {f.servicio}, en pesos sin IVA</label>
                      <div className="relative">
                        <span className="pointer-events-none absolute inset-y-0 left-3 flex items-center text-gray-500" aria-hidden="true">$</span>
                        <input id={campo} type="text" inputMode="numeric" autoComplete="off" disabled={!editable || ocupado}
                          value={conMiles(digitos)} placeholder={f.precio == null ? 'Sin precio' : ''}
                          aria-invalid={errores[id] ? 'true' : undefined} aria-describedby={errores[id] ? errorId : undefined}
                          onChange={(e) => { setBorradores((b) => ({ ...b, [id]: soloDigitos(e.target.value) })); ponerError(id, null) }}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' && editando) { e.preventDefault(); guardar(f) }
                            if (e.key === 'Escape' && borrador !== undefined) { e.preventDefault(); quitarBorrador(id); ponerError(id, null) }
                          }}
                          className="h-11 w-36 rounded-lg border border-gray-300 pl-7 pr-3 text-right tabular-nums focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600 disabled:bg-gray-50" />
                      </div>
                      {errores[id] && <p id={errorId} role="alert" className="mt-1 max-w-[16rem] text-xs text-red-700">{errores[id]}</p>}
                    </div>
                  </td>
                  <td className="px-3 py-2.5"><ChipOrigen modo={f.modo} /></td>
                  <td className="px-3 py-2.5 text-right tabular-nums">
                    {pesos(conIva)}{editando && <span className="block text-xs text-gray-500">vista previa</span>}
                  </td>
                  <td className="px-3 py-2.5 text-right tabular-nums">
                    {f.margenPorcentaje != null ? (
                      <span className={f.margenBajo ? 'font-semibold text-amber-800' : 'text-gray-700'}>
                        {Number(f.margenPorcentaje).toLocaleString('es-CO')} %
                        {f.margenBajo && <span className="block text-xs">Margen bajo</span>}
                      </span>
                    ) : '—'}
                  </td>
                  <td className="px-3 py-2.5">
                    <div className="flex flex-col items-start gap-1">
                      <div className="flex items-center gap-2">
                        <Interruptor activo={f.ofrecido} deshabilitado={!editable || ocupado || f.precio == null}
                          etiqueta={`Ofrecer ${f.servicio} en pospago`} titulo={f.precio == null ? 'Ponle precio para ofrecerlo' : undefined}
                          onCambiar={() => cambiarOfrecido(f)} />
                        <span className="text-sm text-gray-700">{f.ofrecido ? 'Sí' : 'No'}</span>
                      </div>
                      {f.precio == null && <span className="text-xs text-gray-500">Ponle precio para ofrecerlo</span>}
                    </div>
                  </td>
                  <td className="px-3 py-2.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <button type="button" onClick={() => guardar(f)} disabled={!editable || !editando || ocupado}
                        title={!editando ? 'Cambia el precio para poder guardar' : undefined}
                        className="min-h-[44px] rounded-lg bg-primary-600 px-4 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 disabled:cursor-not-allowed disabled:opacity-50 md:min-h-[36px]">
                        {ocupado ? 'Guardando…' : 'Guardar'}
                      </button>
                      {f.modo === 'MANUAL' && (
                        <button type="button" onClick={() => setConfirmarVolver(f)} disabled={!editable || ocupado}
                          className="min-h-[44px] rounded-lg border border-gray-200 px-3 text-sm text-gray-700 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600 disabled:opacity-50 md:min-h-[36px]">
                          Volver al plan de 100
                        </button>
                      )}
                      <button type="button" onClick={() => setHistorial(f)}
                        className="min-h-[44px] rounded-lg border border-gray-200 px-3 text-sm text-gray-700 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary-600 md:min-h-[36px]">
                        Historial
                      </button>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {historial && <PanelHistorial fila={historial} onCerrar={() => setHistorial(null)} />}

      {confirmarVolver && (
        <ConfirmModal titulo="Volver al plan de 100" confirmLabel="Continuar" onCancelar={() => setConfirmarVolver(null)} onConfirmar={() => volverAlPlan(confirmarVolver)}>
          <p className="text-sm text-gray-600">
            {confirmarVolver.servicio} volverá a {pesos(confirmarVolver.precioPlan100)} (plan de 100). ¿Continuar?
          </p>
        </ConfirmModal>
      )}

      {confirmarRestablecer && (
        <ConfirmModal titulo="Restablecer todo al plan de 100" confirmLabel={restableciendo ? 'Restableciendo…' : 'Restablecer'} cargando={restableciendo}
          onCancelar={() => setConfirmarRestablecer(false)} onConfirmar={restablecer}>
          <p className="text-sm text-gray-600">
            {manuales.length === 1 ? '1 servicio en Manual volverá' : `${manuales.length} servicios en Manual volverán`} al precio del plan de 100.
            No cambia cuáles servicios se ofrecen. ¿Continuar?
          </p>
        </ConfirmModal>
      )}
    </div>
  )
}
